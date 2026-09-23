package br.com.rotavyn.shipment;

import java.util.Objects;
import java.util.UUID;

public record Actor(UUID userId, UUID tenantId, Role role) {
    public Actor {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(role);
    }
}
