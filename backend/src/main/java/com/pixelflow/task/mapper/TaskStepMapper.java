package com.pixelflow.task.mapper;

import java.time.Instant;
import java.util.List;

import com.pixelflow.task.TaskStep;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskStepMapper {

    void insertBatch(@Param("steps") List<TaskStep> steps);

    List<TaskStep> findByTaskIds(@Param("taskIds") List<String> taskIds);

    int startStep(@Param("taskId") String taskId, @Param("stage") String stage, @Param("startedAt") Instant startedAt);

    int completeStep(@Param("taskId") String taskId, @Param("stage") String stage, @Param("finishedAt") Instant finishedAt);

    int failStep(@Param("taskId") String taskId, @Param("stage") String stage, @Param("errorCode") String errorCode,
                 @Param("errorMessage") String errorMessage, @Param("finishedAt") Instant finishedAt);

    int skipPending(@Param("taskId") String taskId, @Param("finishedAt") Instant finishedAt);
}
