package com.pixelflow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.pixelflow.support.AbstractMySqlIT;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** B13: simulation controls are refused in a production-like profile but normal flow still works. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"pixelflow.demo-controls-enabled=false"})
@ActiveProfiles("test")
class SimulationDisabledIT extends AbstractMySqlIT {

    @Test
    void rejectsSimulationAndStillAcceptsNormalTasks() {
        String cookie = newSession();
        String assetId = uploadImage(cookie, TEMPLATE_ONE_IMAGE, "one.png");

        Map<String, Object> simulation = new LinkedHashMap<>();
        simulation.put("failAt", "GENERATE");
        ResponseEntity<String> rejected = createTask(cookie, TEMPLATE_ONE_IMAGE, List.of(assetId), simulation);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(parse(rejected).get("code").asText()).isEqualTo("SIMULATION_DISABLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task", Integer.class)).isZero();

        ResponseEntity<String> accepted = createTask(cookie, TEMPLATE_ONE_IMAGE, List.of(assetId), null);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode task = awaitTerminal(cookie, parse(accepted).get("id").asText(), 20_000);
        assertThat(task.get("status").asText()).isEqualTo("SUCCEEDED");
    }
}
