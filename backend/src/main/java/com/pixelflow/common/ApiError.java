package com.pixelflow.common;

import java.util.List;

/** Contract error payload: code, message, traceId, details[]. */
public record ApiError(String code, String message, String traceId, List<Detail> details) {

    public record Detail(String field, String message) {
    }

    public static ApiError of(String code, String message, String traceId, List<Detail> details) {
        return new ApiError(code, message, traceId, details == null ? List.of() : List.copyOf(details));
    }
}
