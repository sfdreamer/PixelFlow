package com.pixelflow.asset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssetServiceTest {

    @Test
    void stripsClientPathSegmentsFromDisplayName() {
        assertThat(AssetService.sanitizeName("../../etc/passwd.png")).isEqualTo("passwd.png");
        assertThat(AssetService.sanitizeName("C:\\temp\\photo.jpg")).isEqualTo("photo.jpg");
        assertThat(AssetService.sanitizeName("   ")).isEqualTo("image");
        assertThat(AssetService.sanitizeName(null)).isEqualTo("image");
    }

    @Test
    void truncatesLongNames() {
        String longName = "a".repeat(400) + ".png";
        assertThat(AssetService.sanitizeName(longName)).hasSize(255);
    }

    @Test
    void normalizesDeclaredContentType() {
        assertThat(AssetService.normalizeContentType("image/png; charset=binary")).isEqualTo("image/png");
        assertThat(AssetService.normalizeContentType(" IMAGE/JPEG ")).isEqualTo("image/jpeg");
        assertThat(AssetService.normalizeContentType(null)).isNull();
    }
}
