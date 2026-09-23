package br.com.rotavyn.shipment;

import br.com.rotavyn.identity.DemoTenants;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShipmentEventService {
    private final JdbcTemplate jdbc;
    private final DemoTenants tenants;
    private final ShipmentRules rules = new ShipmentRules();

    public ShipmentEventService(JdbcTemplate jdbc, DemoTenants tenants) {
        this.jdbc = jdbc;
        this.tenants = tenants;
    }

    public record Event(UUID id, UUID shipmentId, ShipmentEventType eventType, OffsetDateTime occurredAt,
        String note, String idempotencyKey) {}

    @Transactional
    public Event append(UUID shipmentId, ShipmentEventType type, String key, String note, Authentication auth) {
        if (type == ShipmentEventType.ASSIGN)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Use the assignment endpoint with driver and vehicle");
        return appendInternal(shipmentId, type, key, note, auth);
    }

    @Transactional
    public Event assign(UUID shipmentId, UUID driverId, UUID vehicleId, String key, String note, Authentication auth) {
        UUID tenant = tenants.tenantFor(auth.getName());
        var details = jdbc.query("SELECT status,driver_id,vehicle_id FROM shipment WHERE tenant_id=? AND id=? FOR UPDATE",
            (rs, row) -> new AssignmentState(ShipmentStatus.valueOf(rs.getString(1)),
                rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)), tenant, shipmentId);
        if (details.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found");
        var existing = jdbc.query("SELECT id,shipment_id,event_type,occurred_at,note,idempotency_key FROM shipment_event WHERE tenant_id=? AND idempotency_key=?",
            (rs, row) -> map(rs), tenant, key);
        if (!existing.isEmpty()) {
            var event = existing.getFirst();
            if (!event.shipmentId().equals(shipmentId) || event.eventType() != ShipmentEventType.ASSIGN
                || !driverId.equals(details.getFirst().driverId()) || !vehicleId.equals(details.getFirst().vehicleId()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used");
            return event;
        }
        if (details.getFirst().status() != ShipmentStatus.CREATED)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Shipment cannot be assigned in this status");
        Integer drivers = jdbc.queryForObject("SELECT count(*) FROM driver WHERE tenant_id=? AND id=?", Integer.class, tenant, driverId);
        Integer vehicles = jdbc.queryForObject("SELECT count(*) FROM vehicle WHERE tenant_id=? AND id=?", Integer.class, tenant, vehicleId);
        if (drivers == null || drivers == 0 || vehicles == null || vehicles == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver or vehicle not found");
        jdbc.update("UPDATE shipment SET driver_id=?,vehicle_id=? WHERE tenant_id=? AND id=?", driverId, vehicleId, tenant, shipmentId);
        return appendInternal(shipmentId, ShipmentEventType.ASSIGN, key, note, auth);
    }

    private record AssignmentState(ShipmentStatus status, UUID driverId, UUID vehicleId) {}

    private Event appendInternal(UUID shipmentId, ShipmentEventType type, String key, String note, Authentication auth) {
        UUID tenant = tenants.tenantFor(auth.getName());
        // Serializa as alterações de uma remessa para proteger status e histórico na mesma transação.
        var current = jdbc.query("SELECT status FROM shipment WHERE tenant_id=? AND id=? FOR UPDATE",
            (rs, row) -> ShipmentStatus.valueOf(rs.getString(1)), tenant, shipmentId);
        if (current.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found");
        var prior = jdbc.query("""
            SELECT id,shipment_id,event_type,occurred_at,note,idempotency_key FROM shipment_event
            WHERE tenant_id=? AND idempotency_key=?
            """, (rs, row) -> map(rs), tenant, key);
        if (!prior.isEmpty()) {
            if (!prior.getFirst().shipmentId().equals(shipmentId) || prior.getFirst().eventType() != type)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used");
            return prior.getFirst();
        }
        ShipmentStatus next;
        try { next = rules.next(current.getFirst(), type); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()); }
        UUID actor = UUID.nameUUIDFromBytes((tenant + ":" + auth.getName()).getBytes(StandardCharsets.UTF_8));
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("UPDATE shipment SET status=?,version=version+1 WHERE tenant_id=? AND id=?", next.name(), tenant, shipmentId);
        jdbc.update("""
            INSERT INTO shipment_event(id,tenant_id,shipment_id,actor_id,event_type,occurred_at,note,idempotency_key)
            VALUES (?,?,?,?,?,?,?,?)
            """, id, tenant, shipmentId, actor, type.name(), now, note, key);
        return new Event(id, shipmentId, type, now, note, key);
    }

    public List<Event> history(UUID shipmentId, Authentication auth) {
        UUID tenant = tenants.tenantFor(auth.getName());
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM shipment WHERE tenant_id=? AND id=?", Integer.class, tenant, shipmentId);
        if (exists == null || exists == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found");
        return jdbc.query("""
            SELECT id,shipment_id,event_type,occurred_at,note,idempotency_key FROM shipment_event
            WHERE tenant_id=? AND shipment_id=? ORDER BY occurred_at,id
            """, (rs, row) -> map(rs), tenant, shipmentId);
    }

    private Event map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Event(rs.getObject("id", UUID.class), rs.getObject("shipment_id", UUID.class),
            ShipmentEventType.valueOf(rs.getString("event_type")), rs.getObject("occurred_at", OffsetDateTime.class),
            rs.getString("note"), rs.getString("idempotency_key"));
    }
}
