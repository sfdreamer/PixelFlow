package com.pixelflow.task.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskAssetMapper {

    void insertBatch(@Param("taskId") String taskId, @Param("assetIds") List<String> assetIds);
}
