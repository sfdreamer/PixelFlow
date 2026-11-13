package com.pixelflow.task;

import java.util.List;

/** Frozen template rules captured at acceptance so history keeps its original meaning. */
public record TemplateSnapshot(
        String title,
        int requiredImageCount,
        List<String> acceptedMimeTypes,
        long maxFileSizeBytes,
        String demoResultKey) {
}
