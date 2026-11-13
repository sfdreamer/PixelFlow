package com.pixelflow.execution;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.pixelflow.task.Stage;
import com.pixelflow.task.TaskEntity;
import com.pixelflow.task.mapper.TaskMapper;
import com.pixelflow.task.mapper.TaskStepMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serialized queue claim: read the oldest QUEUED id, then flip it with a conditional update and
 * check the affected row count. Two concurrent workers can never both win the same task.
 */
@Service
public class TaskClaimService {

    private static final Logger log = LoggerFactory.getLogger(TaskClaimService.class);

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;

    public TaskClaimService(TaskMapper taskMapper, TaskStepMapper taskStepMapper) {
        this.taskMapper = taskMapper;
        this.taskStepMapper = taskStepMapper;
    }

    @Transactional
    public Optional<TaskEntity> claimNext() {
        TaskEntity head = taskMapper.findQueueHead();
        if (head == null) {
            return Optional.empty();
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        int updated = taskMapper.markRunning(head.getId(), now);
        if (updated != 1) {
            log.debug("task {} was claimed by another worker", head.getId());
            return Optional.empty();
        }
        taskStepMapper.startStep(head.getId(), Stage.ASSET_CHECK.name(), now);
        return Optional.ofNullable(taskMapper.findById(head.getId()));
    }
}
