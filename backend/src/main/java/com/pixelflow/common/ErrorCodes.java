package com.pixelflow.common;

/** Canonical error codes shared by HTTP responses and task execution failures. */
public final class ErrorCodes {

    // Request/validation
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String INVALID_CURSOR = "INVALID_CURSOR";
    public static final String TEMPLATE_NOT_FOUND = "TEMPLATE_NOT_FOUND";
    public static final String ASSET_NOT_FOUND = "ASSET_NOT_FOUND";
    public static final String ASSET_COUNT_MISMATCH = "ASSET_COUNT_MISMATCH";
    public static final String ASSET_DUPLICATED = "ASSET_DUPLICATED";
    public static final String ASSET_TEMPLATE_MISMATCH = "ASSET_TEMPLATE_MISMATCH";
    public static final String ASSET_UNAVAILABLE = "ASSET_UNAVAILABLE";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    public static final String INVALID_IMAGE = "INVALID_IMAGE";
    public static final String IMAGE_DIMENSIONS_EXCEEDED = "IMAGE_DIMENSIONS_EXCEEDED";
    public static final String TASK_NOT_FOUND = "TASK_NOT_FOUND";
    public static final String RESULT_FILE_MISSING = "RESULT_FILE_MISSING";

    // Auth / origin / capability
    public static final String SESSION_REQUIRED = "SESSION_REQUIRED";
    public static final String ORIGIN_NOT_ALLOWED = "ORIGIN_NOT_ALLOWED";
    public static final String SIMULATION_DISABLED = "SIMULATION_DISABLED";

    // Execution
    public static final String SIMULATED_FAILURE = "SIMULATED_FAILURE";
    public static final String GENERATION_FAILED = "GENERATION_FAILED";
    public static final String RESULT_WRITE_FAILED = "RESULT_WRITE_FAILED";
    public static final String EXECUTION_INTERRUPTED = "EXECUTION_INTERRUPTED";
    public static final String INTERNAL_EXECUTION_ERROR = "INTERNAL_EXECUTION_ERROR";

    // Infrastructure
    public static final String DEPENDENCY_UNAVAILABLE = "DEPENDENCY_UNAVAILABLE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCodes() {
    }
}
