package com.pixelflow.task;

import java.time.Instant;

import com.pixelflow.common.ApiException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorCodecTest {

    @Test
    void roundTripsTimestampAndId() {
        Instant createdAt = Instant.parse("2026-11-13T02:30:00.123Z");
        String encoded = CursorCodec.encode(createdAt, "00000000-0000-4000-8000-000000000101");
        CursorCodec.Cursor decoded = CursorCodec.decode(encoded);
        assertThat(decoded.createdAt()).isEqualTo(createdAt);
        assertThat(decoded.id()).isEqualTo("00000000-0000-4000-8000-000000000101");
    }

    @Test
    void isUrlSafeAndOpaque() {
        String encoded = CursorCodec.encode(Instant.now(), "abc");
        assertThat(encoded).doesNotContain("+").doesNotContain("/").doesNotContain("=");
    }

    @Test
    void rejectsMalformedCursorsWith400() {
        assertThatThrownBy(() -> CursorCodec.decode("not-a-cursor"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("INVALID_CURSOR"));
    }
}
