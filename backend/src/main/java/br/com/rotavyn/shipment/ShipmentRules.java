package br.com.rotavyn.shipment;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ShipmentRules {
    private static final Map<ShipmentStatus, Map<ShipmentEventType, ShipmentStatus>> TRANSITIONS = Map.of(
        ShipmentStatus.CREATED, Map.of(ShipmentEventType.ASSIGN, ShipmentStatus.ASSIGNED, ShipmentEventType.CANCEL, ShipmentStatus.CANCELLED),
        ShipmentStatus.ASSIGNED, Map.of(ShipmentEventType.PICK_UP, ShipmentStatus.PICKED_UP, ShipmentEventType.CANCEL, ShipmentStatus.CANCELLED),
        ShipmentStatus.PICKED_UP, Map.of(ShipmentEventType.DEPART, ShipmentStatus.IN_TRANSIT, ShipmentEventType.REPORT_EXCEPTION, ShipmentStatus.EXCEPTION),
        ShipmentStatus.IN_TRANSIT, Map.of(ShipmentEventType.ATTEMPT, ShipmentStatus.DELIVERY_ATTEMPTED, ShipmentEventType.DELIVER, ShipmentStatus.DELIVERED, ShipmentEventType.REPORT_EXCEPTION, ShipmentStatus.EXCEPTION),
        ShipmentStatus.DELIVERY_ATTEMPTED, Map.of(ShipmentEventType.RETRY, ShipmentStatus.IN_TRANSIT, ShipmentEventType.DELIVER, ShipmentStatus.DELIVERED, ShipmentEventType.REPORT_EXCEPTION, ShipmentStatus.EXCEPTION),
        ShipmentStatus.EXCEPTION, Map.of(ShipmentEventType.RESUME, ShipmentStatus.IN_TRANSIT)
    );

    public ShipmentStatus next(ShipmentStatus current, ShipmentEventType event) {
        Objects.requireNonNull(current);
        Objects.requireNonNull(event);
        var next = TRANSITIONS.getOrDefault(current, Map.of()).get(event);
        if (next == null) throw new IllegalArgumentException("Transição não permitida: " + current + " / " + event);
        return next;
    }

    public void requireTenant(UUID resourceTenant, Actor actor) {
        Objects.requireNonNull(actor);
        if (!actor.tenantId().equals(resourceTenant)) throw new IllegalArgumentException("Recurso indisponível");
    }
}
