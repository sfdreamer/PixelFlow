package com.pixelflow.asset;

import com.pixelflow.support.TestImages;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageInspectorTest {

    @Test
    void detectsPngAndJpegByMagicBytes() {
        assertThat(ImageInspector.detectMimeType(TestImages.png())).isEqualTo("image/png");
        assertThat(ImageInspector.detectMimeType("<svg></svg>".getBytes())).isNull();
    }

    @Test
    void readsDimensionsWithoutFullDecode() throws Exception {
        ImageInspector.ImageInfo info = ImageInspector.inspect(TestImages.png(800, 600, 3));
        assertThat(info.mimeType()).isEqualTo("image/png");
        assertThat(info.width()).isEqualTo(800);
        assertThat(info.height()).isEqualTo(600);
    }

    @Test
    void rejectsBytesThatOnlyLookLikePng() {
        assertThatThrownBy(() -> ImageInspector.inspect(TestImages.fakePng()))
                .isInstanceOf(ImageInspector.InvalidImageException.class);
    }

    @Test
    void rejectsImagesAboveTwentyMillionPixels() {
        assertThatThrownBy(() -> ImageInspector.inspect(TestImages.png(5000, 4001, 1)))
                .isInstanceOf(ImageInspector.DimensionsExceededException.class);
    }

    @Test
    void rejectsUnsupportedFormats() {
        assertThatThrownBy(() -> ImageInspector.inspect("GIF89a-not-really".getBytes()))
                .isInstanceOf(ImageInspector.InvalidImageException.class);
    }
}
