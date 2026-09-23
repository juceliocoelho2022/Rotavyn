package br.com.rotavyn.identity;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health").permitAll().anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults()).build();
    }

    @Bean CorsConfigurationSource corsConfigurationSource(
        @Value("${ROTAVYN_FRONTEND_ORIGIN:http://localhost:5173}") String frontendOrigin) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean DemoTenants demoTenants(
        @Value("${ROTAVYN_DEMO_USER_A}") String userA, @Value("${ROTAVYN_DEMO_TENANT_A}") UUID tenantA,
        @Value("${ROTAVYN_DEMO_USER_B}") String userB, @Value("${ROTAVYN_DEMO_TENANT_B}") UUID tenantB) {
        if (userA.equals(userB) || tenantA.equals(tenantB)) throw new IllegalArgumentException("Demo identities must be distinct");
        return new DemoTenants(Map.of(userA, tenantA, userB, tenantB));
    }

    @Bean UserDetailsService users(
        @Value("${ROTAVYN_DEMO_USER_A}") String userA, @Value("${ROTAVYN_DEMO_PASSWORD_A}") String passA,
        @Value("${ROTAVYN_DEMO_USER_B}") String userB, @Value("${ROTAVYN_DEMO_PASSWORD_B}") String passB) {
        if (passA.length() < 16 || passB.length() < 16) throw new IllegalArgumentException("Demo passwords require 16 characters");
        var encoder = new BCryptPasswordEncoder();
        return new InMemoryUserDetailsManager(
            User.withUsername(userA).password("{bcrypt}" + encoder.encode(passA)).roles("DISPATCHER").build(),
            User.withUsername(userB).password("{bcrypt}" + encoder.encode(passB)).roles("DISPATCHER").build());
    }
}
