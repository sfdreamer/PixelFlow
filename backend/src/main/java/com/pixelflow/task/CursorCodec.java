package com.pixelflow.task;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import com.pixelflow.common.ApiException;
import com.pixelflow.common.ErrorCodes;

/**
 * Opaque cursor of (createdAt, id). It only positions the next page; it never grants access.
 * Page-number offsets are not used because inserts would shift the window.
 */
public final class CursorCodec {

    private static final String SEPARATOR = "|";

    public record Cursor(Instant createdAt, String id) {
    }

    public static String encode(Instant createdAt, String id) {
        String raw = createdAt.toEpochMilli() + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int separator = raw.indexOf(SEPARATOR);
            if (separator <= 0 || separator == raw.length() - 1) {
                throw new IllegalArgumentException("bad separator");
            }
            long millis = Long.parseLong(raw.substring(0, separator));
            String id = raw.substring(separator + 1);
            if (id.length() > 64) {
                throw new IllegalArgumentException("bad id");
            }
            return new Cursor(Instant.ofEpochMilli(millis), id);
        } catch (RuntimeException e) {
            throw ApiException.badRequest(ErrorCodes.INVALID_CURSOR, "分页游标格式不正确");
        }
    }

    private CursorCodec() {
    }
}
