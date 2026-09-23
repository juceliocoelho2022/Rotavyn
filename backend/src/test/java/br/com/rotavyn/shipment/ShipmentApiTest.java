package br.com.rotavyn.shipment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "ROTAVYN_DEMO_USER_A=operator-a", "ROTAVYN_DEMO_PASSWORD_A=superlong-test-password-a",
    "ROTAVYN_DEMO_TENANT_A=00000000-0000-0000-0000-00000000000a",
    "ROTAVYN_DEMO_USER_B=operator-b", "ROTAVYN_DEMO_PASSWORD_B=superlong-test-password-b",
    "ROTAVYN_DEMO_TENANT_B=00000000-0000-0000-0000-00000000000b"
})
class ShipmentApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @BeforeEach void tenants() {
        jdbc.update("INSERT INTO tenant(id,name) VALUES (?,'Empresa A') ON CONFLICT DO NOTHING", UUID.fromString("00000000-0000-0000-0000-00000000000a"));
        jdbc.update("INSERT INTO tenant(id,name) VALUES (?,'Empresa B') ON CONFLICT DO NOTHING", UUID.fromString("00000000-0000-0000-0000-00000000000b"));
    }

    @Test void createsAndIsolatesShipments() throws Exception {
        var code = "R-" + UUID.randomUUID();
        var body = """
            {"trackingCode":"%s","senderName":"Origem","recipientName":"Destino",
             "destinationAddress":"Rua Exemplo 1","destinationCountry":"BR",
             "promisedAt":"2030-01-01T12:00:00Z"}
            """.formatted(code);
        mvc.perform(post("/api/v1/shipments").with(httpBasic("operator-a","superlong-test-password-a"))
                .contentType("application/json").content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.trackingCode").value(code));
        mvc.perform(get("/api/v1/shipments").with(httpBasic("operator-b","superlong-test-password-b")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.trackingCode=='" + code + "')]").isEmpty());
        mvc.perform(post("/api/v1/shipments").with(httpBasic("operator-a","superlong-test-password-a"))
                .contentType("application/json").content(body))
            .andExpect(status().isConflict());
    }

    @Test void recordsEventsOnceAndKeepsHistoryPrivate() throws Exception {
        var body = """
            {"trackingCode":"R-%s","senderName":"Origem","recipientName":"Destino",
             "destinationAddress":"Rua Exemplo 1","destinationCountry":"BR",
             "promisedAt":"2030-01-01T12:00:00Z"}
            """.formatted(UUID.randomUUID());
        String response = mvc.perform(post("/api/v1/shipments")
                .with(httpBasic("operator-a", "superlong-test-password-a"))
                .contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var id = mapper.readTree(response).get("id").asText();
        String event = """
            {"eventType":"CANCEL","idempotencyKey":"%s","note":"Solicitação do remetente"}
            """.formatted(UUID.randomUUID());
        var url = "/api/v1/shipments/" + id + "/events";
        mvc.perform(post(url).with(httpBasic("operator-a", "superlong-test-password-a"))
                .contentType("application/json").content(event))
            .andExpect(status().isOk()).andExpect(jsonPath("$.eventType").value("CANCEL"));
        mvc.perform(post(url).with(httpBasic("operator-a", "superlong-test-password-a"))
                .contentType("application/json").content(event))
            .andExpect(status().isOk());
        mvc.perform(get(url).with(httpBasic("operator-a", "superlong-test-password-a")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/shipments/" + id).with(httpBasic("operator-a", "superlong-test-password-a")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post(url).with(httpBasic("operator-a", "superlong-test-password-a"))
                .contentType("application/json").content("""
                    {"eventType":"CANCEL","idempotencyKey":"%s"}
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(url).with(httpBasic("operator-b", "superlong-test-password-b")))
            .andExpect(status().isNotFound());
        mvc.perform(post(url).with(httpBasic("operator-b", "superlong-test-password-b"))
                .contentType("application/json").content(event))
            .andExpect(status().isNotFound());
    }

    @Test void requiresAuthenticationAndValidInput() throws Exception {
        mvc.perform(get("/api/v1/shipments")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/shipments").with(httpBasic("operator-a","superlong-test-password-a"))
                .contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }
}
