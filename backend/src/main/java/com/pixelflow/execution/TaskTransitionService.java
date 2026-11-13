package com.pixelflow.execution;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.pixelflow.storage.StorageService;
import com.pixelflow.task.Stage;
import com.pixelflow.task.TaskResultEntity;
import com.pixelflow.task.mapper.TaskMapper;
import com.pixelflow.task.mapper.TaskResultMapper;
import com.pixelflow.task.mapper.TaskStepMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Short transactions around each visible state transition. Kept in its own bean so the scheduler's
 * self-invocation cannot bypass the transactional proxy.
 */
@Service
public class TaskTransitionService {

    private static final Logger log = LoggerFactory.getLogger(TaskTransitionService.class);

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final TaskResultMapper taskResultMapper;
    private final Clock clock;

    public TaskTransitionService(TaskMapper taskMapper, TaskStepMapper taskStepMapper,
                                 TaskResultMapper taskResultMapper, Clock clock) {
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
        this.taskResultMapper = taskResultMapper;
        this.clock = clock;
    }

    /** Marks one stage SUCCEEDED and the next stage RUNNING in a single commit. */
    @Transactional
    public void completeStageAndStartNext(String taskId, Stage stage) {
        Instant now = now();
        int completed = taskStepMapper.completeStep(taskId, stage.name(), now);
        if (completed != 1) {
            throw new IllegalStateException("stage " + stage + " was not RUNNING for task " + taskId);
        }
        Stage next = stage.next();
        if (next == null) {
            throw new IllegalStateException("FINALIZE must use succeed()");
        }
        taskStepMapper.startStep(taskId, next.name(), now);
        int advanced = taskMapper.advanceStage(taskId, stage.name(), next.name(), now);
        if (advanced != 1) {
            throw new IllegalStateException("task " + taskId + " left RUNNING during stage " + stage);
        }
    }

    /** Result row, FINALIZE success and task SUCCEEDED commit together. */
    @Transactional
    public void succeed(String taskId, StorageService.StoredFile file) {
        Instant now = now();
        int completed = taskStepMapper.completeStep(taskId, Stage.FINALIZE.name(), now);
        if (completed != 1) {
            throw new IllegalStateException("FINALIZE was not RUNNING for task " + taskId);
        }
        TaskResultEntity result = new TaskResultEntity();
        result.setTaskId(taskId);
        result.setStorageKey(file.storageKey());
        result.setMimeType(file.mimeType());
        result.setSizeBytes(file.sizeBytes());
        result.setWidth(file.width());
        result.setHeight(file.height());
        result.setCreatedAt(now);
        taskResultMapper.insert(result);
        int updated = taskMapper.markSucceeded(taskId, now);
        if (updated != 1) {
            throw new IllegalStateException("task " + taskId + " left RUNNING before success");
        }
    }

    /** Failure keeps the failed stage visible and marks every later stage SKIPPED. */
    @Transactional
    public void fail(String taskId, Stage stage, String errorCode, String message) {
        Instant now = now();
        String stageName = stage == null ? null : stage.name();
        if (stage != null) {
            taskStepMapper.failStep(taskId, stageName, errorCode, message, now);
        }
        taskStepMapper.skipPending(taskId, now);
        int updated = taskMapper.markFailed(taskId, stageName, errorCode, message, now);
        if (updated != 1) {
            log.warn("task {} was no longer RUNNING when recording failure {}", taskId, errorCode);
        }
    }

    public Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }
}
