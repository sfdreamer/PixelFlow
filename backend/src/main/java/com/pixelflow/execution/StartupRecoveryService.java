package com.pixelflow.execution;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.pixelflow.task.mapper.TaskMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Startup cleanup for a previous run. QUEUED and RUNNING tasks are closed as EXECUTION_INTERRUPTED;
 * finished history is untouched. This is explicitly single-instance behaviour and does not resume
 * work: there is no lease and no stage-level idempotency in this version.
 */
@Service
public class StartupRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(StartupRecoveryService.class);
    private static final String MESSAGE = "服务重启导致任务中断，请重新提交";

    private final TaskMapper taskMapper;

    public StartupRecoveryService(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Transactional
    public void recoverInterruptedTasks() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        int failedSteps = taskMapper.failRunningSteps(now, MESSAGE);
        int skippedSteps = taskMapper.skipPendingSteps(now);
        int interruptedTasks = taskMapper.failActiveTasks(now, MESSAGE);
        if (interruptedTasks > 0) {
            log.warn("startup recovery closed {} interrupted task(s); failed steps={}, skipped steps={}",
                    interruptedTasks, failedSteps, skippedSteps);
        }
    }
}
