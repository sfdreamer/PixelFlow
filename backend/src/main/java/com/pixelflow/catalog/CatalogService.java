package com.pixelflow.catalog;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelflow.catalog.mapper.CatalogMapper;
import com.pixelflow.common.ApiException;
import com.pixelflow.common.ErrorCodes;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {

    private final CatalogMapper catalogMapper;
    private final ObjectMapper objectMapper;

    public CatalogService(CatalogMapper catalogMapper, ObjectMapper objectMapper) {
        this.catalogMapper = catalogMapper;
        this.objectMapper = objectMapper;
    }

    public record CategoryView(String id, String name, int sortOrder) {
    }

    public record TemplateView(String id, String categoryId, String title, String description, String coverUrl,
                               int requiredImageCount, List<String> acceptedMimeTypes, long maxFileSizeBytes) {
    }

    public List<CategoryView> categories() {
        return catalogMapper.findCategories().stream()
                .map(c -> new CategoryView(c.getId(), c.getName(), c.getSortOrder()))
                .toList();
    }

    public List<TemplateView> templates(String categoryId) {
        return catalogMapper.findActiveTemplates(categoryId).stream()
                .map(this::toView)
                .toList();
    }

    public TemplateView template(String templateId) {
        return toView(requireTemplate(templateId));
    }

    public Template requireTemplate(String templateId) {
        Template template = catalogMapper.findActiveTemplateById(templateId);
        if (template == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.TEMPLATE_NOT_FOUND, "模板不存在或已下架");
        }
        return template;
    }

    public TemplateView toView(Template template) {
        return new TemplateView(
                template.getId(),
                template.getCategoryId(),
                template.getTitle(),
                template.getDescription(),
                "/api/v1/templates/" + template.getId() + "/cover",
                template.getRequiredImageCount(),
                parseMimeTypes(template.getAcceptedMimeTypes()),
                template.getMaxFileSizeBytes());
    }

    public List<String> parseMimeTypes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("template.accepted_mime_types is not a JSON array", e);
        }
    }
}
