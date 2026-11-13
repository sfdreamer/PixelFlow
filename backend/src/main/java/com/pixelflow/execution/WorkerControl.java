package com.pixelflow.execution;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

/**
 * Runtime gate for the scheduler. It starts closed and is opened by {@link StartupRecoveryRunner}
 * once recovery finished and {@code pixelflow.worker.auto-start} is enabled, so no task can be
 * claimed before the queue has been reconciled.
 *
 * <p>Operators and tests can close it again to stop new claims without stopping the application,
 * for example before an intentional single-instance maintenance window or when a test wants to
 * place fixtures in the queue itself. Tasks already running are not interrupted.
 */
@Component
public class WorkerControl {

    private final AtomicBoolean paused = new AtomicBoolean(true);

    public boolean isPaused() {
        return paused.get();
    }

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
    }
}
