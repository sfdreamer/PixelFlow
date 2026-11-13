package com.pixelflow.task.mapper;

import java.time.Instant;
import java.util.List;

import com.pixelflow.asset.Asset;
import com.pixelflow.task.TaskEntity;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskMapper {

    void insert(TaskEntity task);

    TaskEntity findByIdAndUser(@Param("id") String id, @Param("userId") String userId);

    TaskEntity findById(@Param("id") String id);

    /** Oldest QUEUED task: the queue is serialized by created_at then id. */
    TaskEntity findQueueHead();

    int markRunning(@Param("id") String id, @Param("startedAt") Instant startedAt);

    int advanceStage(@Param("id") String id, @Param("currentStage") String currentStage,
                     @Param("nextStage") String nextStage, @Param("updatedAt") Instant updatedAt);

    int markSucceeded(@Param("id") String id, @Param("finishedAt") Instant finishedAt);

    int markFailed(@Param("id") String id, @Param("failedStage") String failedStage,
                   @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
                   @Param("finishedAt") Instant finishedAt);

    List<TaskEntity> findPage(@Param("userId") String userId, @Param("templateId") String templateId,
                              @Param("cursorCreatedAt") Instant cursorCreatedAt, @Param("cursorId") String cursorId,
                              @Param("limit") int limit);

    List<Asset> findTaskAssets(@Param("taskId") String taskId);

    int failRunningSteps(@Param("now") Instant now, @Param("message") String message);

    int skipPendingSteps(@Param("now") Instant now);

    int failActiveTasks(@Param("now") Instant now, @Param("message") String message);
}
