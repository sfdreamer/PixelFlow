package com.pixelflow.execution;

/** Injectable stage delay so tests can run without real sleeps. */
public interface StageWaiter {

    void sleep(long millis) throws InterruptedException;
}
