package com.pixelflow.common;

import java.util.UUID;

/** Path/query identifiers are UUID strings; a malformed value is a 400, not a 404. */
public final class Uuids {

    public static String require(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_REQUEST,
                    "参数格式不正确", java.util.List.of(new ApiError.Detail(field, "必须是合法的 UUID")));
        }
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    private Uuids() {
    }
}
