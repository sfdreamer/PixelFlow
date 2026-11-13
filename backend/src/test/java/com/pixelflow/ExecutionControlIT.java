package com.pixelflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.pixelflow.execution.TaskClaimService;
import com.pixelflow.execution.TaskExecutionService;
import com.pixelflow.support.AbstractMySqlIT;
import com.pixelflow.task.TaskEntity;
import com.pixelflow.task.mapper.TaskResultMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * B09 and B10. The scheduler stays parked for this test so the test drives claim/execute itself.
 */
class ExecutionControlIT extends AbstractMySqlIT {

    @Autowired
    private TaskClaimService claimService;

    @Autowired
    private TaskExecutionService executionService;

    @MockitoSpyBean
    private TaskResultMapper taskResultMapper;

    @Override
    protected boolean schedulerNeeded() {
        return false;
    }

    @Test
    void b09_concurrentClaimsHandOutEachTaskExactlyOnce() throws Exception {
        String cookie = newSession();
        for (int i = 0; i < 3; i++) {
            String assetId = uploadImage(cookie, TEMPLATE_ONE_IMAGE, "c-" + i + ".png");
            JsonNode created = parse(createTask(cookie, TEMPLATE_ONE_IMAGE, List.of(assetId),
                    Map.of("stageDelayMs", Map.of("ASSET_CHECK", 0, "GENERATE", 0, "FINALIZE", 0))));
            assertThat(created.get("status").asText()).isEqualTo("QUEUED");
        }

        int workers = 8;
        Set<String> claimed = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            for (int i = 0; i < workers; i++) {
                pool.submit(() -> {
                    start.await();
                    Optional<TaskEntity> task = claimService.claimNext();
                    task.ifPresent(value -> {
                        if (!claimed.add(value.getId())) {
                            throw new AssertionError("task claimed twice: " + value.getId());
                        }
                    });
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(claimed).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task WHERE status = 'RUNNING'", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void b10_failureInsideTheSuccessTransactionLeavesNoPartialResult() {
        String cookie = newSession();
        String assetId = uploadImage(cookie, TEMPLATE_ONE_IMAGE, "r.png");
        String taskId = parse(createTask(cookie, TEMPLATE_ONE_IMAGE, List.of(assetId),
                Map.of("stageDelayMs", Map.of("ASSET_CHECK", 0, "GENERATE", 0, "FINALIZE", 0))))
                .get("id").asText();

        Optional<TaskEntity> claimed = claimService.claimNext();
        assertThat(claimed).isPresent();
        assertThat(claimed.get().getId()).isEqualTo(taskId);

        doThrow(new DataIntegrityViolationException("injected result insert failure"))
                .when(taskResultMapper).insert(any());

        assertThatThrownBy(() -> executionService.execute(claimed.get()))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbc.queryForObject("SELECT status FROM task WHERE id = ?", String.class, taskId))
                .isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject("SELECT current_stage FROM task WHERE id = ?", String.class, taskId))
                .isEqualTo("FINALIZE");
        assertThat(jdbc.queryForObject("SELECT status FROM task_step WHERE task_id = ? AND stage = 'FINALIZE'",
                String.class, taskId)).isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_result WHERE task_id = ?", Integer.class, taskId))
                .isZero();
    }
}
