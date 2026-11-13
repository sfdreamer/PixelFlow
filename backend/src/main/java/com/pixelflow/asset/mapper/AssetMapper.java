package com.pixelflow.asset.mapper;

import com.pixelflow.asset.Asset;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssetMapper {

    void insert(Asset asset);

    Asset findByIdAndUser(@Param("id") String id, @Param("userId") String userId);
}
