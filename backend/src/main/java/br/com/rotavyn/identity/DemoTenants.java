package br.com.rotavyn.identity;

import java.util.Map;
import java.util.UUID;

public record DemoTenants(Map<String, UUID> users) {
    public UUID tenantFor(String username) {
        var id = users.get(username);
        if (id == null) throw new IllegalArgumentException("Unknown demo operator");
        return id;
    }
}
