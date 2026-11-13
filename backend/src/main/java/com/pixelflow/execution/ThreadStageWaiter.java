package com.pixelflow.execution;

import org.springframework.stereotype.Component;

@Component
public class ThreadStageWaiter implements StageWaiter {

    @Override
    public void sleep(long millis) throws InterruptedException {
        if (millis > 0) {
            Thread.sleep(millis);
        }
    }
}
