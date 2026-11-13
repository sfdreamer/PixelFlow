package com.pixelflow.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** Single clock source so tests can substitute a fixed clock. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
