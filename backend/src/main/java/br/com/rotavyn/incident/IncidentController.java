package br.com.rotavyn.incident;

import br.com.rotavyn.identity.DemoTenants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class IncidentController {
    private final JdbcTemplate jdbc;
    private final DemoTenants tenants;

    public IncidentController(JdbcTemplate jdbc, DemoTenants tenants) {
        this.jdbc = jdbc;
        this.tenants = tenants;
    }

    public record Incident(UUID shipmentId, String trackingCode, String status, OffsetDateTime promisedAt,
        String reason) {}
    public record Recommendation(UUID id, UUID shipmentId, String reason, String recommendation,
        String rationale, String provider, String status, OffsetDateTime createdAt, OffsetDateTime decidedAt) {}
    public record Decision(@NotNull DecisionType decision, @Size(max = 1000) String note) {}
    public enum DecisionType { ACCEPTED, DISMISSED }

    @GetMapping("/incidents")
    public List<Incident> incidents(Authentication auth) {
        return jdbc.query("""
            SELECT id,tracking_code,status,promised_at FROM shipment
            WHERE tenant_id=? AND status NOT IN ('DELIVERED','CANCELLED')
              AND (status='EXCEPTION' OR promised_at<CURRENT_TIMESTAMP)
            ORDER BY promised_at,id
            """, (rs, row) -> {
                String status = rs.getString("status");
                return new Incident(rs.getObject("id", UUID.class), rs.getString("tracking_code"), status,
                    rs.getObject("promised_at", OffsetDateTime.class),
                    status.equals("EXCEPTION") ? "EXCEPTION" : "OVERDUE");
            }, tenants.tenantFor(auth.getName()));
    }

    @PostMapping("/shipments/{shipmentId}/recommendations")
    @Transactional
    public Recommendation recommend(@PathVariable UUID shipmentId, Authentication auth) {
        UUID tenant = tenants.tenantFor(auth.getName());
        var matches = jdbc.query("SELECT status,promised_at FROM shipment WHERE tenant_id=? AND id=?",
            (rs, row) -> new Candidate(rs.getString("status"), rs.getObject("promised_at", OffsetDateTime.class)), tenant, shipmentId);
        if (matches.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found");
        var candidate = matches.getFirst();
        if (candidate.status().equals("DELIVERED") || candidate.status().equals("CANCELLED")
            || (!candidate.status().equals("EXCEPTION") && !candidate.promisedAt().isBefore(OffsetDateTime.now())))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Shipment has no active incident");
        boolean exception = candidate.status().equals("EXCEPTION");
        String reason = exception ? "EXCEPTION" : "OVERDUE";
        String suggestion = exception ? "Verificar a ocorrência e contatar o responsável pela entrega."
            : "Confirmar a situação da remessa e combinar novo prazo com o destinatário.";
        String rationale = exception ? "A remessa está com status de ocorrência."
            : "O prazo de entrega foi ultrapassado e a remessa permanece em aberto.";
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
            INSERT INTO ai_recommendation(id,tenant_id,shipment_id,reason,recommendation,rationale,provider,created_at)
            VALUES (?,?,?,?,?,?,?,?)
            """, id, tenant, shipmentId, reason, suggestion, rationale, "DETERMINISTIC_DEMO", now);
        return new Recommendation(id, shipmentId, reason, suggestion, rationale, "DETERMINISTIC_DEMO", "PENDING", now, null);
    }

    @GetMapping("/recommendations/{shipmentId}")
    public List<Recommendation> recommendations(@PathVariable UUID shipmentId, Authentication auth) {
        UUID tenant = tenants.tenantFor(auth.getName());
        Integer count = jdbc.queryForObject("SELECT count(*) FROM shipment WHERE tenant_id=? AND id=?", Integer.class, tenant, shipmentId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found");
        return jdbc.query("""
            SELECT id,shipment_id,reason,recommendation,rationale,provider,status,created_at,decided_at
            FROM ai_recommendation WHERE tenant_id=? AND shipment_id=? ORDER BY created_at DESC,id
            """, (rs, row) -> map(rs), tenant, shipmentId);
    }

    @PostMapping("/recommendations/{id}/decision")
    @Transactional
    public Recommendation decide(@PathVariable UUID id, @Valid @RequestBody Decision command, Authentication auth) {
        UUID tenant = tenants.tenantFor(auth.getName());
        var matches = jdbc.query("""
            SELECT id,shipment_id,reason,recommendation,rationale,provider,status,created_at,decided_at
            FROM ai_recommendation WHERE tenant_id=? AND id=? FOR UPDATE
            """, (rs, row) -> map(rs), tenant, id);
        if (matches.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found");
        var current = matches.getFirst();
        if (!current.status().equals("PENDING")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Already decided");
        OffsetDateTime now = OffsetDateTime.now();
        UUID actor = UUID.nameUUIDFromBytes((tenant + ":" + auth.getName()).getBytes(StandardCharsets.UTF_8));
        jdbc.update("""
            UPDATE ai_recommendation SET status=?,decided_at=?,decided_by=?,decision_note=?
            WHERE tenant_id=? AND id=?
            """, command.decision().name(), now, actor, command.note(), tenant, id);
        if (command.decision() == DecisionType.ACCEPTED) {
            jdbc.update("""
                INSERT INTO follow_up_task(id,tenant_id,recommendation_id,shipment_id,description,created_at)
                VALUES (?,?,?,?,?,?)
                """, UUID.randomUUID(), tenant, id, current.shipmentId(), current.recommendation(), now);
        }
        return new Recommendation(id, current.shipmentId(), current.reason(), current.recommendation(),
            current.rationale(), current.provider(), command.decision().name(), current.createdAt(), now);
    }

    private record Candidate(String status, OffsetDateTime promisedAt) {}

    private Recommendation map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Recommendation(rs.getObject("id", UUID.class), rs.getObject("shipment_id", UUID.class),
            rs.getString("reason"), rs.getString("recommendation"), rs.getString("rationale"),
            rs.getString("provider"), rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("decided_at", OffsetDateTime.class));
    }
}
