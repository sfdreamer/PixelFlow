package com.pixelflow;

import java.time.Instant;

import com.pixelflow.execution.StartupRecoveryService;
import com.pixelflow.execution.StartupState;
import com.pixelflow.support.AbstractMySqlIT;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B11: on startup, leftover QUEUED/RUNNING tasks are closed as EXECUTION_INTERRUPTED while finished
 * history stays untouched. The scheduler stays parked so the fixture is not consumed before
 * recovery runs.
 */
class StartupRecoveryIT extends AbstractMySqlIT {

    private static final String USER = "11111111-1111-4111-8111-111111111111";
    private static final String QUEUED_TASK = "22222222-2222-4222-8222-222222222222";
    private static final String RUNNING_TASK = "33333333-3333-4333-8333-333333333333";
    private static final String SUCCEEDED_TASK = "44444444-4444-4444-8444-444444444444";
    private static final String SNAPSHOT =
            "{\"title\":\"柔光宠物肖像\",\"requiredImageCount\":1,\"acceptedMimeTypes\":[\"image/png\"],"
                    + "\"maxFileSizeBytes\":5242880,\"demoResultKey\":\"demo/result-pet-soft.png\"}";
    private static final String SIMULATION =
            "{\"failAt\":null,\"stageDelayMs\":{\"ASSET_CHECK\":0,\"GENERATE\":0,\"FINALIZE\":0}}";

    @Autowired
    private StartupRecoveryService recoveryService;

    @Autowired
    private StartupState startupState;

    @Override
    protected boolean schedulerNeeded() {
        return false;
    }

    @Test
    void closesInterruptedWorkAndKeepsFinishedHistory() {
        jdbc.update("INSERT INTO app_user (id, display_name, created_at) VALUES (?, ?, NOW(3))", USER, "游客-TEST");
        insertTask(QUEUED_TASK, "QUEUED", null, 0, null, null);
        insertTask(RUNNING_TASK, "RUNNING", "GENERATE", 3, Instant.now().minusSeconds(60), null);
        insertTask(SUCCEEDED_TASK, "SUCCEEDED", "FINALIZE", 5, Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(30));

        insertStep(QUEUED_TASK, "ASSET_CHECK", 1, "PENDING", false, false);
        insertStep(QUEUED_TASK, "GENERATE", 2, "PENDING", false, false);
        insertStep(QUEUED_TASK, "FINALIZE", 3, "PENDING", false, false);

        insertStep(RUNNING_TASK, "ASSET_CHECK", 1, "SUCCEEDED", true, true);
        insertStep(RUNNING_TASK, "GENERATE", 2, "RUNNING", true, false);
        insertStep(RUNNING_TASK, "FINALIZE", 3, "PENDING", false, false);

        insertStep(SUCCEEDED_TASK, "ASSET_CHECK", 1, "SUCCEEDED", true, true);
        insertStep(SUCCEEDED_TASK, "GENERATE", 2, "SUCCEEDED", true, true);
        insertStep(SUCCEEDED_TASK, "FINALIZE", 3, "SUCCEEDED", true, true);
        jdbc.update("INSERT INTO task_result (task_id, storage_key, mime_type, size_bytes, width, height, created_at)"
                + " VALUES (?, ?, 'image/png', 100, 1200, 900, NOW(3))", SUCCEEDED_TASK, "results/history.png");

        recoveryService.recoverInterruptedTasks();

        assertThat(status(QUEUED_TASK)).isEqualTo("FAILED");
        assertThat(string(QUEUED_TASK, "current_stage")).isNull();
        assertThat(string(QUEUED_TASK, "error_code")).isEqualTo("EXECUTION_INTERRUPTED");
        assertThat(jdbc.queryForObject("SELECT finished_at IS NOT NULL FROM task WHERE id = ?", Boolean.class,
                QUEUED_TASK)).isTrue();
        assertThat(revision(QUEUED_TASK)).isEqualTo(1);
        assertThat(stepStatus(QUEUED_TASK, "ASSET_CHECK")).isEqualTo("SKIPPED");
        assertThat(stepStatus(QUEUED_TASK, "GENERATE")).isEqualTo("SKIPPED");
        assertThat(jdbc.queryForObject("SELECT started_at FROM task_step WHERE task_id = ? AND stage = 'GENERATE'",
                Object.class, QUEUED_TASK)).isNull();

        assertThat(status(RUNNING_TASK)).isEqualTo("FAILED");
        assertThat(string(RUNNING_TASK, "current_stage")).isEqualTo("GENERATE");
        assertThat(string(RUNNING_TASK, "error_code")).isEqualTo("EXECUTION_INTERRUPTED");
        assertThat(revision(RUNNING_TASK)).isEqualTo(4);
        assertThat(stepStatus(RUNNING_TASK, "ASSET_CHECK")).isEqualTo("SUCCEEDED");
        assertThat(stepStatus(RUNNING_TASK, "GENERATE")).isEqualTo("FAILED");
        assertThat(stepStatus(RUNNING_TASK, "FINALIZE")).isEqualTo("SKIPPED");

        assertThat(status(SUCCEEDED_TASK)).isEqualTo("SUCCEEDED");
        assertThat(revision(SUCCEEDED_TASK)).isEqualTo(5);
        assertThat(string(SUCCEEDED_TASK, "error_code")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_result WHERE task_id = ?", Integer.class,
                SUCCEEDED_TASK)).isEqualTo(1);

        assertThat(startupState.isReady()).isTrue();
    }

    private void insertTask(String id, String status, String currentStage, long revision, Instant startedAt,
                            Instant finishedAt) {
        jdbc.update("""
                INSERT INTO task (id, user_id, template_id, template_snapshot, simulation_snapshot, status,
                                  current_stage, error_code, error_message, revision, created_at, updated_at,
                                  started_at, finished_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?, NOW(3), NOW(3), ?, ?)
                """, id, USER, TEMPLATE_ONE_IMAGE, SNAPSHOT, SIMULATION, status, currentStage, revision,
                startedAt == null ? null : java.sql.Timestamp.from(startedAt),
                finishedAt == null ? null : java.sql.Timestamp.from(finishedAt));
    }

    private void insertStep(String taskId, String stage, int order, String status, boolean started,
                            boolean finished) {
        jdbc.update("""
                INSERT INTO task_step (task_id, stage, step_order, status, error_code, error_message,
                                       started_at, finished_at)
                VALUES (?, ?, ?, ?, NULL, NULL, ?, ?)
                """, taskId, stage, order, status,
                started ? java.sql.Timestamp.from(Instant.now().minusSeconds(30)) : null,
                finished ? java.sql.Timestamp.from(Instant.now().minusSeconds(10)) : null);
    }

    private String status(String taskId) {
        return string(taskId, "status");
    }

    private String string(String taskId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM task WHERE id = ?", String.class, taskId);
    }

    private long revision(String taskId) {
        return jdbc.queryForObject("SELECT revision FROM task WHERE id = ?", Long.class, taskId);
    }

    private String stepStatus(String taskId, String stage) {
        return jdbc.queryForObject("SELECT status FROM task_step WHERE task_id = ? AND stage = ?", String.class,
                taskId, stage);
    }
}
