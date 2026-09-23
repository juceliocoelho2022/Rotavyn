package br.com.rotavyn.identity;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class DemoTenantBootstrap {
    @Bean CommandLineRunner seedDemoTenants(JdbcTemplate jdbc,
        @Value("${ROTAVYN_DEMO_TENANT_A}") UUID tenantA,
        @Value("${ROTAVYN_DEMO_TENANT_B}") UUID tenantB) {
        return args -> {
            jdbc.update("INSERT INTO tenant(id,name) VALUES (?,?) ON CONFLICT (id) DO NOTHING", tenantA, "Empresa A");
            jdbc.update("INSERT INTO tenant(id,name) VALUES (?,?) ON CONFLICT (id) DO NOTHING", tenantB, "Empresa B");
        };
    }
}
