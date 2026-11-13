package com.pixelflow.task.mapper;

import java.util.List;

import com.pixelflow.task.TaskResultEntity;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskResultMapper {

    void insert(TaskResultEntity result);

    TaskResultEntity findByTaskId(@Param("taskId") String taskId);

    List<TaskResultEntity> findByTaskIds(@Param("taskIds") List<String> taskIds);
}
