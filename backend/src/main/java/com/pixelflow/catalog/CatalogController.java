package com.pixelflow.catalog;

import java.util.List;

import com.pixelflow.asset.ImageInspector;
import com.pixelflow.common.Uuids;
import com.pixelflow.storage.StorageService;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalogService;
    private final StorageService storageService;

    public CatalogController(CatalogService catalogService, StorageService storageService) {
        this.catalogService = catalogService;
        this.storageService = storageService;
    }

    public record CategoryList(List<CatalogService.CategoryView> items) {
    }

    public record TemplateList(List<CatalogService.TemplateView> items) {
    }

    @GetMapping("/categories")
    public CategoryList categories() {
        return new CategoryList(catalogService.categories());
    }

    @GetMapping("/templates")
    public TemplateList templates(@RequestParam(name = "categoryId", required = false) String categoryId) {
        String normalized = categoryId == null || categoryId.isBlank() ? null : Uuids.require(categoryId, "categoryId");
        return new TemplateList(catalogService.templates(normalized));
    }

    @GetMapping("/templates/{templateId}")
    public CatalogService.TemplateView template(@PathVariable("templateId") String templateId) {
        return catalogService.template(Uuids.require(templateId, "templateId"));
    }

    /** Public cover bytes; demo artwork is packaged read-only with the application. */
    @GetMapping("/templates/{templateId}/cover")
    public ResponseEntity<byte[]> cover(@PathVariable("templateId") String templateId) {
        Template template = catalogService.requireTemplate(Uuids.require(templateId, "templateId"));
        byte[] bytes = storageService.readDemoResource(template.getCoverStorageKey());
        String mime = ImageInspector.detectMimeType(bytes);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mime == null ? MediaType.IMAGE_PNG_VALUE : mime))
                .contentLength(bytes.length)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(bytes);
    }
}
