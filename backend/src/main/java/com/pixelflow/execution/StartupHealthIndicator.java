package com.pixelflow.execution;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Exposed as actuator health component {@code startupRecoveryState} inside the readiness group. */
@Component("startupRecoveryState")
public class StartupHealthIndicator implements HealthIndicator {

    private final StartupState startupState;

    public StartupHealthIndicator(StartupState startupState) {
        this.startupState = startupState;
    }

    @Override
    public Health health() {
        return startupState.isReady()
                ? Health.up().withDetail("recovery", "completed").build()
                : Health.down().withDetail("recovery", "pending").build();
    }
}
