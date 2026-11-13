package com.pixelflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PixelFlow API entry point.
 *
 * <p>Every mapper interface is annotated with {@code @Mapper}; no broad package scan is used so
 * that service interfaces (for example the injectable stage waiter) are never mistaken for mappers.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class PixelFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(PixelFlowApplication.class, args);
    }
}
