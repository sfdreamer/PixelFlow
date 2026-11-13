package com.pixelflow.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;

/** Deterministic in-memory test images; no external fixtures needed. */
public final class TestImages {

    public static byte[] png() {
        return png(240, 180, 7);
    }

    public static byte[] png(int width, int height, long seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(38, 52, 66));
        graphics.fillRect(0, 0, width, height);
        Random random = new Random(seed);
        for (int i = 0; i < 12; i++) {
            graphics.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
            graphics.fillOval(random.nextInt(width), random.nextInt(height), width / 4, height / 4);
        }
        graphics.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Bytes that declare PNG but cannot be decoded. */
    public static byte[] fakePng() {
        byte[] bytes = new byte[512];
        bytes[0] = (byte) 0x89;
        bytes[1] = 'P';
        bytes[2] = 'N';
        bytes[3] = 'G';
        bytes[4] = 0x0D;
        bytes[5] = 0x0A;
        bytes[6] = 0x1A;
        bytes[7] = 0x0A;
        return bytes;
    }

    private TestImages() {
    }
}
