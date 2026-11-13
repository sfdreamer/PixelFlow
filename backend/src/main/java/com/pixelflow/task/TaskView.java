package com.pixelflow.task;

import java.time.Instant;
import java.util.List;

/** Single Task DTO used by creation, detail and history responses. */
public record TaskView(
        String id,
        String templateId,
        String templateTitle,
        String status,
        String currentStage,
        List<StepView> steps,
        ResultView result,
        TaskErrorView error,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant finishedAt,
        long revision) {

    public record StepView(String stage, String status, Instant startedAt, Instant finishedAt,
                           String errorCode, String errorMessage) {
    }

    public record ResultView(String previewUrl, String mimeType, int width, int height) {
    }

    public record TaskErrorView(String code, String message, String failedStage) {
    }
}
