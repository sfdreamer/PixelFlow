package com.pixelflow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.pixelflow.common.PixelFlowProperties;
import com.pixelflow.support.AbstractMySqlIT;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** B08: file-level failures are caught at the right stage and never produce a fake success. */
class FileFailureIT extends AbstractMySqlIT {

    @Autowired
    private PixelFlowProperties properties;

    private static Map<String, Object> delays(int assetCheck, int generate, int finalizeStage) {
        Map<String, Object> stageDelayMs = new LinkedHashMap<>();
        stageDelayMs.put("ASSET_CHECK", assetCheck);
        stageDelayMs.put("GENERATE", generate);
        stageDelayMs.put("FINALIZE", finalizeStage);
        Map<String, Object> simulation = new LinkedHashMap<>();
        simulation.put("stageDelayMs", stageDelayMs);
        return simulation;
    }

    private Path storagePath(String storageKey) {
        return properties.storageRootPath().resolve(storageKey).normalize();
    }

    @Test
    void missingFileIsRejectedAtCreation() throws IOException {
        String cookie = newSession();
        String assetId = uploadImage(cookie, TEMPLATE_ONE_IMAGE, "gone.png");
        String storageKey = jdbc.queryForObject("SELECT storage_key FROM asset WHERE id = ?", String.class, assetId);
        Files.delete(storagePath(storageKey));

        ResponseEntity<String> response = createTask(cookie, TEMPLATE_ONE_IMAGE, List.of(assetId), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(parse(response).get("code").asText()).isEqualTo("ASSET_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task", Integer.class)).isZero();
    }

    @Test
    void missingFileDuringAssetCheckFailsAtTheFirstStage() throws IOException {
        String cookie = newSession();
        String assetId = uploadImage(cookie, TEMPLATE_ONE_IMAGE, "vanishing.png");
        String storageKey = jdbc.queryForObject("SELECT storage_key FROM asset WHERE id = ?", String.class, assetId);
        String taskId = parse(createTask(cookie, TEMPLATE_ONE_IMAGE, List.of(assetId), delays(1500, 0, 0)))
                .get("id").asText();

        Files.delete(storagePath(storageKey));

        JsonNode task = awaitTerminal(cookie, taskId, 20_000);
        assertThat(task.get("status").asText()).isEqualTo("FAILED");
        assertThat(task.get("currentStage").asText()).isEqualTo("ASSET_CHECK");
        assertThat(task.get("error").get("code").asText()).isEqualTo("ASSET_UNAVAILABLE");
        assertThat(task.get("error").get("failedStage").asText()).isEqualTo("ASSET_CHECK");
        assertThat(task.get("result").isNull()).isTrue();
        assertThat(task.get("steps").get(1).get("status").asText()).isEqualTo("SKIPPED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_result", Integer.class)).isZero();
    }

    @Test
    void resultWriteFailureFailsFinalizeWithoutSuccess() throws IOException {
        String cookie = newSession();
        String assetId = uploadImage(cookie, TEMPLATE_ONE_IMAGE, "ok.png");
        String taskId = parse(createTask(cookie, TEMPLATE_ONE_IMAGE, List.of(assetId), delays(0, 2500, 0)))
                .get("id").asText();

        // Occupy the deterministic result path with a NON-EMPTY directory: neither the atomic move
        // nor the replace fallback can rename a file over it, so the finalize write really fails.
        Path blocked = properties.storageRootPath().resolve("results").resolve(taskId + ".png");
        Path marker = blocked.resolve("occupied.txt");
        Files.createDirectories(blocked);
        Files.write(marker, new byte[] {0});

        try {
            JsonNode task = awaitTerminal(cookie, taskId, 20_000);
            assertThat(task.get("status").asText()).isEqualTo("FAILED");
            assertThat(task.get("currentStage").asText()).isEqualTo("FINALIZE");
            assertThat(task.get("error").get("code").asText()).isEqualTo("RESULT_WRITE_FAILED");
            assertThat(task.get("result").isNull()).isTrue();
            assertThat(task.get("steps").get(0).get("status").asText()).isEqualTo("SUCCEEDED");
            assertThat(task.get("steps").get(1).get("status").asText()).isEqualTo("SUCCEEDED");
            assertThat(task.get("steps").get(2).get("status").asText()).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_result", Integer.class)).isZero();
        } finally {
            // Only the two paths this test created are removed; no recursive directory cleanup.
            Files.deleteIfExists(marker);
            Files.deleteIfExists(blocked);
        }
    }
}
