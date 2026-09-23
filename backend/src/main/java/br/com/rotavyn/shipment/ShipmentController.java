package br.com.rotavyn.shipment;

import br.com.rotavyn.identity.DemoTenants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/shipments")
public class ShipmentController {
    private final JdbcTemplate jdbc;
    private final DemoTenants tenants;

    public ShipmentController(JdbcTemplate jdbc, DemoTenants tenants) {
        this.jdbc = jdbc;
        this.tenants = tenants;
    }

    public record CreateShipment(
        @NotBlank @Size(max = 80) String trackingCode,
        @NotBlank @Size(max = 160) String senderName,
        @NotBlank @Size(max = 160) String recipientName,
        @NotBlank String destinationAddress,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String destinationCountry,
        @NotNull OffsetDateTime promisedAt) {}

    public record Shipment(UUID id, String trackingCode, String senderName, String recipientName,
        String destinationAddress, String destinationCountry, OffsetDateTime promisedAt, ShipmentStatus status) {}

    @PostMapping
    public ResponseEntity<Shipment> create(@Valid @RequestBody CreateShipment request, Authentication authentication) {
        UUID tenant = tenants.tenantFor(authentication.getName());
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO shipment(id,tenant_id,tracking_code,sender_name,recipient_name,destination_address,destination_country,promised_at)
            VALUES (?,?,?,?,?,?,?,?)
            """, id, tenant, request.trackingCode(), request.senderName(), request.recipientName(),
            request.destinationAddress(), request.destinationCountry(), request.promisedAt());
        return ResponseEntity.created(URI.create("/api/v1/shipments/" + id))
            .body(new Shipment(id, request.trackingCode(), request.senderName(), request.recipientName(),
                request.destinationAddress(), request.destinationCountry(), request.promisedAt(), ShipmentStatus.CREATED));
    }

    @GetMapping
    public List<Shipment> list(Authentication authentication) {
        return jdbc.query("""
            SELECT id,tracking_code,sender_name,recipient_name,destination_address,destination_country,promised_at,status
            FROM shipment WHERE tenant_id=? ORDER BY promised_at,id
            """, (rs, row) -> mapShipment(rs), tenants.tenantFor(authentication.getName()));
    }

    @GetMapping("/{id}")
    public Shipment get(@PathVariable UUID id, Authentication authentication) {
        var results = jdbc.query("""
            SELECT id,tracking_code,sender_name,recipient_name,destination_address,destination_country,promised_at,status
            FROM shipment WHERE tenant_id=? AND id=?
            """, (rs, row) -> mapShipment(rs), tenants.tenantFor(authentication.getName()), id);
        if (results.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found");
        return results.getFirst();
    }

    private Shipment mapShipment(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Shipment(rs.getObject("id", UUID.class), rs.getString("tracking_code"),
            rs.getString("sender_name"), rs.getString("recipient_name"), rs.getString("destination_address"),
            rs.getString("destination_country"), rs.getObject("promised_at", OffsetDateTime.class),
            ShipmentStatus.valueOf(rs.getString("status")));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<String> duplicate() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Tracking code already exists for this tenant");
    }
}
