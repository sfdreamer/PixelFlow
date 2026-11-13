package com.pixelflow.catalog.mapper;

import java.util.List;

import com.pixelflow.catalog.Category;
import com.pixelflow.catalog.Template;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CatalogMapper {

    List<Category> findCategories();

    List<Template> findActiveTemplates(@Param("categoryId") String categoryId);

    Template findActiveTemplateById(@Param("id") String id);
}
