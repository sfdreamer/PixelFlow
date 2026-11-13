package com.pixelflow.execution;

import com.pixelflow.task.Stage;

/** Internal control flow for a stage that failed with a user-visible error code. */
public class ExecutionFailure extends RuntimeException {

    private final Stage stage;
    private final String code;

    public ExecutionFailure(Stage stage, String code, String message) {
        super(message);
        this.stage = stage;
        this.code = code;
    }

    public Stage getStage() {
        return stage;
    }

    public String getCode() {
        return code;
    }
}
