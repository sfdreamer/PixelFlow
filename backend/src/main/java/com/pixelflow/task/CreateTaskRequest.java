package com.pixelflow.task;

import java.util.List;
import java.util.Map;

/**
 * Create-task request body. Field-level shape errors are 400; business rule failures are 422.
 * Simulation fields are only accepted when demo controls are enabled.
 */
public record CreateTaskRequest(String templateId, List<String> assetIds, Simulation simulation) {

    public record Simulation(String failAt, Map<String, Integer> stageDelayMs) {
    }
}
