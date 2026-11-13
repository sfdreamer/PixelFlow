package com.pixelflow.common;

import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pixelflow")
public class PixelFlowProperties {

    private String storageRoot = "./.data/storage";
    private boolean cookieSecure = false;
    private String cookieName = "pf_session";
    private int sessionDays = 30;
    private List<String> allowedOrigins = List.of("http://localhost:3000");
    private boolean demoControlsEnabled = false;
    private StageDelay stageDelayMs = new StageDelay();
    private Worker worker = new Worker();

    public Path storageRootPath() {
        return Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public int getSessionDays() {
        return sessionDays;
    }

    public void setSessionDays(int sessionDays) {
        this.sessionDays = sessionDays;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public boolean isDemoControlsEnabled() {
        return demoControlsEnabled;
    }

    public void setDemoControlsEnabled(boolean demoControlsEnabled) {
        this.demoControlsEnabled = demoControlsEnabled;
    }

    public StageDelay getStageDelayMs() {
        return stageDelayMs;
    }

    public void setStageDelayMs(StageDelay stageDelayMs) {
        this.stageDelayMs = stageDelayMs;
    }

    public Worker getWorker() {
        return worker;
    }

    public void setWorker(Worker worker) {
        this.worker = worker;
    }

    public static class StageDelay {
        private int assetCheck = 2000;
        private int generate = 2000;
        private int finalizeStage = 2000;

        public int getAssetCheck() {
            return assetCheck;
        }

        public void setAssetCheck(int assetCheck) {
            this.assetCheck = assetCheck;
        }

        public int getGenerate() {
            return generate;
        }

        public void setGenerate(int generate) {
            this.generate = generate;
        }

        public int getFinalizeStage() {
            return finalizeStage;
        }

        public void setFinalizeStage(int finalizeStage) {
            this.finalizeStage = finalizeStage;
        }
    }

    public static class Worker {
        private long fixedDelayMs = 500;
        private boolean autoStart = true;

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }

        /** Opens the scheduler gate after startup recovery. Disabled in tests that drive execution. */
        public boolean isAutoStart() {
            return autoStart;
        }

        public void setAutoStart(boolean autoStart) {
            this.autoStart = autoStart;
        }
    }
}
