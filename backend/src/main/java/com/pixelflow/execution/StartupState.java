package com.pixelflow.execution;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

/**
 * Readiness gate. The scheduler does not claim tasks and readiness does not report UP until the
 * startup recovery of interrupted tasks has completed.
 */
@Component
public class StartupState {

    private final AtomicBoolean ready = new AtomicBoolean(false);

    public boolean isReady() {
        return ready.get();
    }

    public void markReady() {
        ready.set(true);
    }
}
