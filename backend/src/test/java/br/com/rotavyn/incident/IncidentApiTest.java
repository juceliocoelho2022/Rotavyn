package br.com.rotavyn.incident;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
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
class IncidentApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test void overdueRecommendationRequiresHumanDecisionAndDoesNotMoveShipment() throws Exception {
        var a = httpBasic("operator-a", "superlong-test-password-a");
        var b = httpBasic("operator-b", "superlong-test-password-b");
        String body = """
            {"trackingCode":"INC-%s","senderName":"Origem","recipientName":"Destino",
             "destinationAddress":"Rua Exemplo 1","destinationCountry":"BR",
             "promisedAt":"2020-01-01T12:00:00Z"}
            """.formatted(UUID.randomUUID());
        String shipment = mvc.perform(post("/api/v1/shipments").with(a).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String shipmentId = mapper.readTree(shipment).get("id").asText();
        mvc.perform(get("/api/v1/incidents").with(a))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.shipmentId=='" + shipmentId + "')]").isNotEmpty());
        mvc.perform(get("/api/v1/incidents").with(b))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.shipmentId=='" + shipmentId + "')]").isEmpty());
        mvc.perform(post("/api/v1/shipments/" + shipmentId + "/recommendations").with(b))
            .andExpect(status().isNotFound());
        String recommendation = mvc.perform(post("/api/v1/shipments/" + shipmentId + "/recommendations").with(a))
            .andExpect(status().isOk()).andExpect(jsonPath("$.provider").value("DETERMINISTIC_DEMO"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn().getResponse().getContentAsString();
        String recommendationId = mapper.readTree(recommendation).get("id").asText();
        mvc.perform(get("/api/v1/recommendations/" + shipmentId).with(b))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/recommendations/" + recommendationId + "/decision").with(b)
                .contentType("application/json").content("{\"decision\":\"ACCEPTED\"}"))
            .andExpect(status().isNotFound());
        String decision = "{\"decision\":\"ACCEPTED\",\"note\":\"Contatar motorista\"}";
        mvc.perform(post("/api/v1/recommendations/" + recommendationId + "/decision").with(a)
                .contentType("application/json").content(decision))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(post("/api/v1/recommendations/" + recommendationId + "/decision").with(a)
                .contentType("application/json").content(decision))
            .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/shipments/" + shipmentId).with(a))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CREATED"));
        Integer tasks = jdbc.queryForObject("SELECT count(*) FROM follow_up_task WHERE recommendation_id=?", Integer.class,
            UUID.fromString(recommendationId));
        assertEquals(1, tasks);
    }

    @Test void futureShipmentHasNoRecommendation() throws Exception {
        var a = httpBasic("operator-a", "superlong-test-password-a");
        String body = """
            {"trackingCode":"INC-%s","senderName":"Origem","recipientName":"Destino",
             "destinationAddress":"Rua Exemplo 1","destinationCountry":"BR",
             "promisedAt":"2030-01-01T12:00:00Z"}
            """.formatted(UUID.randomUUID());
        String shipment = mvc.perform(post("/api/v1/shipments").with(a).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(shipment).get("id").asText();
        mvc.perform(post("/api/v1/shipments/" + id + "/recommendations").with(a))
            .andExpect(status().isUnprocessableEntity());
    }
}
