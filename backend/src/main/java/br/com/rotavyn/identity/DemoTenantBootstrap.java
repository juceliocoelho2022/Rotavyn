package br.com.rotavyn.identity;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class DemoTenantBootstrap {
    @Bean CommandLineRunner seedDemoTenants(JdbcTemplate jdbc,
        @Value("${ROTAVYN_DEMO_TENANT_A}") UUID tenantA,
        @Value("${ROTAVYN_DEMO_TENANT_B}") UUID tenantB,
        @Value("${ROTAVYN_DEMO_USER_A}") String userA, @Value("${ROTAVYN_DEMO_PASSWORD_A}") String passA,
        @Value("${ROTAVYN_DEMO_USER_B}") String userB, @Value("${ROTAVYN_DEMO_PASSWORD_B}") String passB) {
        return args -> {
            jdbc.update("INSERT INTO tenant(id,name) VALUES (?,?) ON CONFLICT (id) DO NOTHING", tenantA, "Empresa A");
            jdbc.update("INSERT INTO tenant(id,name) VALUES (?,?) ON CONFLICT (id) DO NOTHING", tenantB, "Empresa B");
            var encoder = new BCryptPasswordEncoder();
            seedUser(jdbc, tenantA, userA, encoder.encode(passA));
            seedUser(jdbc, tenantB, userB, encoder.encode(passB));
        };
    }
    private void seedUser(JdbcTemplate jdbc, UUID tenant, String username, String hash) {
        UUID id = UUID.nameUUIDFromBytes((tenant + ":" + username).getBytes(StandardCharsets.UTF_8));
        jdbc.update("""
            INSERT INTO user_account(id,tenant_id,email,password_hash,role) VALUES (?,?,?,?,?)
            ON CONFLICT (id) DO NOTHING
            """, id, tenant, username + "@demo.rotavyn.invalid", hash, "DISPATCHER");
    }
}
