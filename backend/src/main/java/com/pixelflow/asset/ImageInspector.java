package com.pixelflow.asset;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Reads the real image type from magic bytes and the dimensions from the header, before a full
 * decode. This keeps a forged upload from being accepted and avoids decoding huge images in memory.
 */
public final class ImageInspector {

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final long MAX_PIXELS = 20_000_000L;

    public record ImageInfo(String mimeType, int width, int height) {
    }

    /** Returns the detected media type, or null when the bytes are not JPEG/PNG. */
    public static String detectMimeType(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && (bytes[4] & 0xFF) == 0x0D && (bytes[5] & 0xFF) == 0x0A
                && (bytes[6] & 0xFF) == 0x1A && (bytes[7] & 0xFF) == 0x0A) {
            return PNG;
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        return null;
    }

    /**
     * @throws InvalidImageException when the header cannot be read or the pixels exceed the limit
     */
    public static ImageInfo inspect(byte[] bytes) throws InvalidImageException {
        String mime = detectMimeType(bytes);
        if (mime == null) {
            throw new InvalidImageException("文件内容不是有效的 JPEG 或 PNG 图片");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new InvalidImageException("无法读取图片内容");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidImageException("无法识别图片格式");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new InvalidImageException("图片尺寸无效");
                }
                if ((long) width * height > MAX_PIXELS) {
                    throw new DimensionsExceededException("图片像素数超过 2000 万限制");
                }
                return new ImageInfo(mime, width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new InvalidImageException("图片内容无法解析");
        }
    }

    /** Full decode; used once per upload to prove the bytes are a complete, decodable image. */
    public static void requireDecodable(byte[] bytes) throws InvalidImageException {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new InvalidImageException("图片解码失败");
            }
        } catch (IOException | RuntimeException e) {
            throw new InvalidImageException("图片解码失败");
        }
    }

    public static class InvalidImageException extends Exception {
        public InvalidImageException(String message) {
            super(message);
        }
    }

    public static class DimensionsExceededException extends InvalidImageException {
        public DimensionsExceededException(String message) {
            super(message);
        }
    }

    private ImageInspector() {
    }
}
