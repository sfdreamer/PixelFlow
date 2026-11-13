package com.pixelflow.task;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Normalized, complete simulation parameters snapshotted into the task at creation. */
public record SimulationSnapshot(String failAt, StageDelays stageDelayMs) {

    public record StageDelays(
            @JsonProperty("ASSET_CHECK") int assetCheck,
            @JsonProperty("GENERATE") int generate,
            @JsonProperty("FINALIZE") int finalizeStage) {

        public int delayFor(Stage stage) {
            return switch (stage) {
                case ASSET_CHECK -> assetCheck;
                case GENERATE -> generate;
                case FINALIZE -> finalizeStage;
            };
        }
    }

    public boolean failsAt(Stage stage) {
        return stage != null && stage.name().equals(failAt);
    }
}
