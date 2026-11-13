package com.pixelflow.execution;

import com.pixelflow.common.PixelFlowProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs before readiness flips UP and before the scheduler may claim work. If recovery fails the
 * application fails to start instead of serving a half-initialized queue.
 *
 * <p>Only after recovery has committed does the scheduler gate open, so a restart never competes
 * with the reconciliation of interrupted work.
 */
@Component
@Order(1)
public class StartupRecoveryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRecoveryRunner.class);

    private final StartupRecoveryService recoveryService;
    private final StartupState startupState;
    private final WorkerControl workerControl;
    private final PixelFlowProperties properties;

    public StartupRecoveryRunner(StartupRecoveryService recoveryService, StartupState startupState,
                                 WorkerControl workerControl, PixelFlowProperties properties) {
        this.recoveryService = recoveryService;
        this.startupState = startupState;
        this.workerControl = workerControl;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            recoveryService.recoverInterruptedTasks();
        } catch (RuntimeException e) {
            log.error("startup recovery failed; the scheduler stays disabled", e);
            throw e;
        }
        startupState.markReady();
        if (properties.getWorker().isAutoStart()) {
            workerControl.resume();
            log.info("startup recovery completed; worker scheduler enabled");
        } else {
            log.info("startup recovery completed; worker scheduler parked by configuration");
        }
    }
}
