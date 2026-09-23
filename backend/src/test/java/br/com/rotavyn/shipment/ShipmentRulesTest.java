package br.com.rotavyn.shipment;

import java.util.UUID;

public class ShipmentRulesTest {
    public static void main(String[] args) {
        var tenantA = UUID.randomUUID();
        var tenantB = UUID.randomUUID();
        var actor = new Actor(UUID.randomUUID(), tenantA, Role.DISPATCHER);
        var rules = new ShipmentRules();
        expect(rules.next(ShipmentStatus.CREATED, ShipmentEventType.ASSIGN) == ShipmentStatus.ASSIGNED);
        expect(rules.next(ShipmentStatus.ASSIGNED, ShipmentEventType.PICK_UP) == ShipmentStatus.PICKED_UP);
        expect(rules.next(ShipmentStatus.PICKED_UP, ShipmentEventType.DEPART) == ShipmentStatus.IN_TRANSIT);
        expect(rules.next(ShipmentStatus.IN_TRANSIT, ShipmentEventType.ATTEMPT) == ShipmentStatus.DELIVERY_ATTEMPTED);
        expect(rules.next(ShipmentStatus.DELIVERY_ATTEMPTED, ShipmentEventType.RETRY) == ShipmentStatus.IN_TRANSIT);
        expect(rules.next(ShipmentStatus.IN_TRANSIT, ShipmentEventType.DELIVER) == ShipmentStatus.DELIVERED);
        fails(() -> rules.next(ShipmentStatus.CREATED, ShipmentEventType.DELIVER));
        fails(() -> rules.requireTenant(tenantB, actor));
        rules.requireTenant(tenantA, actor);
        System.out.println("ShipmentRulesTest: OK");
    }

    private static void expect(boolean value) { if (!value) throw new AssertionError(); }
    private static void fails(Runnable action) {
        try { action.run(); throw new AssertionError("expected failure"); }
        catch (IllegalArgumentException expected) { /* correct */ }
    }
}
