package com.pixelflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.pixelflow.support.AbstractMySqlIT;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** B01-B05 and B12: acceptance, background execution, stage failures, read-only queries and cursors. */
class TaskFlowIT extends AbstractMySqlIT {

    private static Map<String, Object> delays(int millis) {
        Map<String, Object> delays = new LinkedHashMap<>();
        delays.put("ASSET_CHECK", millis);
        delays.put("GENERATE", millis);
        delays.put("FINALIZE", millis);
        Map<String, Object> simulation = new LinkedHashMap<>();
        simulation.put("stageDelayMs", delays);
        return simulation;
    }

    private static Map<String, Object> failAt(String stage, int millis) {
        Map<String, Object> simulation = delays(millis);
        simulation.put("failAt", stage);
        return simulation;
    }

    @Test
    void b01_acceptsWith202WhileStagesAreStillPending() {
        String cookie = newSession();
        String assetId = uploadImage(cookie, TEMPLATE_ONE_IMAGE, "one.png");

        ResponseEntity<String> response = createTask(cookie, TEMPLATE_ONE_IMAGE, List.of(assetId), delays(600));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("/api/v1/tasks/" + parse(response).get("id").asText());

        JsonNode body = parse(response);
        assertThat(body.get("status").asText()).isEqualTo("QUEUED");
        assertThat(body.get("revision").asInt()).isZero();
        assertThat(body.get("steps")).hasSize(3);
        assertThat(body.get("result").isNull()).isTrue();
        assertThat(body.get("error").isNull()).isTrue();

        String taskId = body.get("id").asText();
        JsonNode snapshot = getTask(cookie, taskId);
        assertThat(snapshot.get("status").asText()).isIn("QUEUED", "RUNNING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_step WHERE task_id = ?", Integer.class, taskId))
                .isEqualTo(3);

        JsonNode terminal = awaitTerminal(cookie, taskId, 15_000);
        assertThat(terminal.get("status").asText()).isEqualTo("SUCCEEDED");
    }

    @Test
    void b02_completesWithoutAnyQueryAndPersistsTheResult() {
        String cookie = newSession();
        String assetId = uploadImage(cookie, TEMPLATE_ONE_IMAGE, "one.png");
        String taskId = parse(createTask(cookie, TEMPLATE_ONE_IMAGE, List.of(assetId), delays(0))).get("id").asText();

        JsonNode task = awaitTerminal(cookie, taskId, 20_000);
        assertThat(task.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(task.get("currentStage").asText()).isEqualTo("FINALIZE");
        assertThat(task.get("result").get("previewUrl").asText()).isEqualTo("/api/v1/tasks/" + taskId + "/result");
        assertThat(task.get("steps")).allSatisfy(step -> {
            assertThat(step.get("status").asText()).isEqualTo("SUCCEEDED");
            assertThat(step.get("startedAt").isNull()).isFalse();
            assertThat(step.get("finishedAt").isNull()).isFalse();
        });
        assertThat(task.get("steps").get(0).get("stage").asText()).isEqualTo("ASSET_CHECK");
        assertThat(task.get("steps").get(1).get("stage").asText()).isEqualTo("GENERATE");
        assertThat(task.get("steps").get(2).get("stage").asText()).isEqualTo("FINALIZE");
        assertThat(task.get("error").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_result WHERE task_id = ?", Integer.class, taskId))
                .isEqualTo(1);

        ResponseEntity<byte[]> result = rest.exchange("/api/v1/tasks/" + taskId + "/result", HttpMethod.GET,
                new HttpEntity<>(browserHeaders(cookie)), byte[].class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(result.getBody()).isNotEmpty();
        assertThat(result.getBody()[0] & 0xFF).isEqualTo(0x89);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASSET_CHECK", "GENERATE", "FINALIZE"})
    void b03_failsAtTheRequestedStageAndSkipsTheRest(String stage) {
        String cookie = newSession();
        String assetId = uploadImage(cookie, TEMPLATE_ONE_IMAGE, "one.png");
        String taskId = parse(createTask(cookie, TEMPLATE_ONE_IMAGE, List.of(assetId), failAt(stage, 0)))
                .get("id").asText();

        JsonNode task = awaitTerminal(cookie, taskId, 20_000);
        assertThat(task.get("status").asText()).isEqualTo("FAILED");
        assertThat(task.get("currentStage").asText()).isEqualTo(stage);
        assertThat(task.get("error").get("code").asText()).isEqualTo("SIMULATED_FAILURE");
        assertThat(task.get("error").get("failedStage").asText()).isEqualTo(stage);
        assertThat(task.get("result").isNull()).isTrue();
        assertThat(task.get("finishedAt").isNull()).isFalse();

        List<String> stages = List.of("ASSET_CHECK", "GENERATE", "FINALIZE");
        int failedIndex = stages.indexOf(stage);
        JsonNode steps = task.get("steps");
        for (int i = 0; i < stages.size(); i++) {
            String status = steps.get(i).get("status").asText();
            if (i < failedIndex) {
                assertThat(status).isEqualTo("SUCCEEDED");
            } else if (i == failedIndex) {
                assertThat(status).isEqualTo("FAILED");
            } else {
                assertThat(status).isEqualTo("SKIPPED");
                assertThat(steps.get(i).get("startedAt").isNull()).isTrue();
            }
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_result WHERE task_id = ?", Integer.class, taskId))
                .isZero();
    }

    @Test
    void b04_repeatedReadsNeverChangeRevisionOrTimestamps() {
        String cookie = newSession();
        String assetId = uploadImage(cookie, TEMPLATE_ONE_IMAGE, "one.png");
        String taskId = parse(createTask(cookie, TEMPLATE_ONE_IMAGE, List.of(assetId), delays(0))).get("id").asText();
        JsonNode terminal = awaitTerminal(cookie, taskId, 20_000);
        long revision = terminal.get("revision").asLong();
        String updatedAt = terminal.get("updatedAt").asText();

        for (int i = 0; i < 3; i++) {
            JsonNode again = getTask(cookie, taskId);
            assertThat(again.get("revision").asLong()).isEqualTo(revision);
            assertThat(again.get("updatedAt").asText()).isEqualTo(updatedAt);
            assertThat(again.get("status").asText()).isEqualTo("SUCCEEDED");
        }
        assertThat(jdbc.queryForObject("SELECT revision FROM task WHERE id = ?", Long.class, taskId))
                .isEqualTo(revision);
    }

    @Test
    void b05_threeTasksKeepIndependentStateAndRunInCreationOrder() {
        String cookie = newSession();
        List<String> taskIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String assetId = uploadImage(cookie, TEMPLATE_ONE_IMAGE, "img-" + i + ".png");
            taskIds.add(parse(createTask(cookie, TEMPLATE_ONE_IMAGE, List.of(assetId), delays(150)))
                    .get("id").asText());
        }
        assertThat(taskIds).doesNotHaveDuplicates();

        List<JsonNode> tasks = new ArrayList<>();
        for (String taskId : taskIds) {
            JsonNode task = awaitTerminal(cookie, taskId, 30_000);
            assertThat(task.get("status").asText()).isEqualTo("SUCCEEDED");
            tasks.add(task);
        }

        // tasks keeps creation order; the serial queue must not reorder them.
        for (int i = 1; i < tasks.size(); i++) {
            Instant previousStarted = Instant.parse(tasks.get(i - 1).get("startedAt").asText());
            Instant currentStarted = Instant.parse(tasks.get(i).get("startedAt").asText());
            assertThat(currentStarted)
                    .as("task %d must start no earlier than task %d (creation order)", i, i - 1)
                    .isAfterOrEqualTo(previousStarted);
        }
    }

    @Test
    void b12_cursorKeepsPagingStableWhenNewTasksArrive() {
        String cookie = newSession();
        for (int i = 0; i < 5; i++) {
            String assetId = uploadImage(cookie, TEMPLATE_ONE_IMAGE, "p-" + i + ".png");
            String taskId = parse(createTask(cookie, TEMPLATE_ONE_IMAGE, List.of(assetId), delays(0)))
                    .get("id").asText();
            awaitTerminal(cookie, taskId, 20_000);
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        JsonNode firstPage = listTasks(cookie, 2, null);
        assertThat(firstPage.get("items")).hasSize(2);
        String cursor = firstPage.get("nextCursor").asText();
        assertThat(cursor).isNotBlank();

        String newAsset = uploadImage(cookie, TEMPLATE_ONE_IMAGE, "new.png");
        String newTaskId = parse(createTask(cookie, TEMPLATE_ONE_IMAGE, List.of(newAsset), delays(0)))
                .get("id").asText();
        awaitTerminal(cookie, newTaskId, 20_000);

        JsonNode secondPage = listTasks(cookie, 2, cursor);
        List<String> ids = new ArrayList<>();
        secondPage.get("items").forEach(item -> ids.add(item.get("id").asText()));
        assertThat(ids).doesNotContain(newTaskId);
        assertThat(ids).doesNotContain(firstPage.get("items").get(0).get("id").asText());
        assertThat(ids).doesNotContain(firstPage.get("items").get(1).get("id").asText());

        JsonNode bad = listTasksRaw(cookie, 2, "not-a-cursor");
        assertThat(bad.get("code").asText()).isEqualTo("INVALID_CURSOR");
    }

    private JsonNode listTasks(String cookie, int limit, String cursor) {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/tasks?limit=" + limit + (cursor == null ? "" : "&cursor=" + cursor),
                HttpMethod.GET, new HttpEntity<>(browserHeaders(cookie)), String.class);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        return parse(response);
    }

    private JsonNode listTasksRaw(String cookie, int limit, String cursor) {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/tasks?limit=" + limit + (cursor == null ? "" : "&cursor=" + cursor),
                HttpMethod.GET, new HttpEntity<>(browserHeaders(cookie)), String.class);
        return parse(response);
    }
}
