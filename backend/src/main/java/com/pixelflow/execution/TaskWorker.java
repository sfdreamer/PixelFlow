package com.pixelflow.execution;

import java.util.Optional;

import com.pixelflow.task.TaskEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Single scheduler thread. Each tick claims and fully executes at most one task, then waits for the
 * fixed delay. A serial queue keeps ordering observable and avoids unbounded threads; it is not a
 * message broker and is not multi-instance safe.
 */
@Component
public class TaskWorker {

    private static final Logger log = LoggerFactory.getLogger(TaskWorker.class);

    private final TaskClaimService claimService;
    private final TaskExecutionService executionService;
    private final StartupState startupState;
    private final WorkerControl workerControl;

    public TaskWorker(TaskClaimService claimService, TaskExecutionService executionService,
                      StartupState startupState, WorkerControl workerControl) {
        this.claimService = claimService;
        this.executionService = executionService;
        this.startupState = startupState;
        this.workerControl = workerControl;
    }

    @Scheduled(fixedDelayString = "${pixelflow.worker.fixed-delay-ms:500}")
    public void poll() {
        if (!startupState.isReady() || workerControl.isPaused()) {
            return;
        }
        Optional<TaskEntity> claimed;
        try {
            claimed = claimService.claimNext();
        } catch (DataAccessException e) {
            log.error("queue claim failed; pausing this scheduling round", e);
            return;
        }
        if (claimed.isEmpty()) {
            return;
        }
        try {
            executionService.execute(claimed.get());
        } catch (DataAccessException e) {
            log.error("task={} stopped after a database failure", claimed.get().getId(), e);
        } catch (RuntimeException e) {
            log.error("task={} worker iteration failed", claimed.get().getId(), e);
        }
    }
}
