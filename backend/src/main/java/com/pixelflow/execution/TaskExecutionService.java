package com.pixelflow.execution;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelflow.asset.Asset;
import com.pixelflow.common.ApiException;
import com.pixelflow.common.ErrorCodes;
import com.pixelflow.storage.StorageService;
import com.pixelflow.task.SimulationSnapshot;
import com.pixelflow.task.Stage;
import com.pixelflow.task.TaskEntity;
import com.pixelflow.task.TemplateSnapshot;
import com.pixelflow.task.mapper.TaskMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Executes the three stages of one claimed task on the scheduler thread.
 *
 * <p>Ordering guarantees: the current stage is committed as RUNNING before its work starts; a
 * simulated failure stops the remaining stages and writes no result; the result file is written
 * before the success transaction, so a committed SUCCEEDED task always has a readable file.
 */
@Service
public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    private final TaskMapper taskMapper;
    private final TaskTransitionService transitions;
    private final StorageService storageService;
    private final StageWaiter waiter;
    private final ObjectMapper objectMapper;

    public TaskExecutionService(TaskMapper taskMapper, TaskTransitionService transitions,
                                StorageService storageService, StageWaiter waiter, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.transitions = transitions;
        this.storageService = storageService;
        this.waiter = waiter;
        this.objectMapper = objectMapper;
    }

    public void execute(TaskEntity task) {
        SimulationSnapshot simulation = read(task.getSimulationSnapshot(), SimulationSnapshot.class);
        TemplateSnapshot template = read(task.getTemplateSnapshot(), TemplateSnapshot.class);
        String taskId = task.getId();
        Stage current = Stage.ASSET_CHECK;

        try {
            for (Stage stage : Stage.values()) {
                current = stage;
                long started = System.nanoTime();
                waiter.sleep(simulation.stageDelayMs().delayFor(stage));
                if (simulation.failsAt(stage)) {
                    throw new ExecutionFailure(stage, ErrorCodes.SIMULATED_FAILURE,
                            "模拟失败：" + label(stage) + "阶段");
                }
                switch (stage) {
                    case ASSET_CHECK -> checkAssets(taskId, template);
                    case GENERATE -> simulateGeneration(taskId);
                    case FINALIZE -> {
                        StorageService.StoredFile file = storageService.writeResultFromDemo(
                                taskId, template.demoResultKey());
                        transitions.succeed(taskId, file);
                        log.info("task={} stage={} status=SUCCEEDED durationMs={}",
                                taskId, stage, elapsedMs(started));
                        return;
                    }
                }
                transitions.completeStageAndStartNext(taskId, stage);
                log.info("task={} stage={} status=SUCCEEDED durationMs={}", taskId, stage, elapsedMs(started));
            }
        } catch (ExecutionFailure failure) {
            fail(taskId, failure.getStage(), failure.getCode(), failure.getMessage());
        } catch (ApiException e) {
            String code = e.getCode() == null ? ErrorCodes.INTERNAL_EXECUTION_ERROR : e.getCode();
            fail(taskId, current, code, e.getMessage());
        } catch (InterruptedException e) {
            // Shutdown or interruption: leave the task RUNNING. The next startup closes it.
            Thread.currentThread().interrupt();
            log.warn("task={} stage={} interrupted; it will be reconciled on next startup", taskId, current);
        } catch (DataAccessException e) {
            // Never announce success in memory when persistence fails. A restart reconciles the row.
            log.error("task={} stage={} database failure; stopping this task's execution", taskId, current, e);
            throw e;
        } catch (RuntimeException e) {
            log.error("task={} stage={} unexpected failure", taskId, current, e);
            fail(taskId, current, ErrorCodes.INTERNAL_EXECUTION_ERROR, "执行过程中发生内部错误");
        }
    }

    private void checkAssets(String taskId, TemplateSnapshot template) {
        List<Asset> assets = taskMapper.findTaskAssets(taskId);
        if (assets.size() != template.requiredImageCount()) {
            throw new ExecutionFailure(Stage.ASSET_CHECK, ErrorCodes.ASSET_UNAVAILABLE, "素材数量与模板要求不一致");
        }
        for (Asset asset : assets) {
            if (!storageService.existsReadable(asset.getStorageKey(), asset.getSizeBytes())) {
                throw new ExecutionFailure(Stage.ASSET_CHECK, ErrorCodes.ASSET_UNAVAILABLE,
                        "素材文件不可用，请重新上传后再提交");
            }
        }
    }

    /** Simulated generation: no real image processing, only an internal candidate key. */
    private void simulateGeneration(String taskId) {
        log.debug("task={} simulated generation candidate=results/{}.png", taskId, taskId);
    }

    private void fail(String taskId, Stage stage, String code, String message) {
        try {
            transitions.fail(taskId, stage, code, message);
            log.info("task={} stage={} status=FAILED errorCode={}", taskId, stage, code);
        } catch (DataAccessException e) {
            log.error("task={} stage={} could not persist failure {}", taskId, stage, code, e);
            throw e;
        }
    }

    private static String label(Stage stage) {
        return switch (stage) {
            case ASSET_CHECK -> "素材检查";
            case GENERATE -> "模拟生成";
            case FINALIZE -> "结果整理";
        };
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("invalid task snapshot", e);
        }
    }
}
