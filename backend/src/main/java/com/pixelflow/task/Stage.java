package com.pixelflow.task;

/** Execution stages, in fixed order. */
public enum Stage {
    ASSET_CHECK(1),
    GENERATE(2),
    FINALIZE(3);

    private final int order;

    Stage(int order) {
        this.order = order;
    }

    public int order() {
        return order;
    }

    public Stage next() {
        return switch (this) {
            case ASSET_CHECK -> GENERATE;
            case GENERATE -> FINALIZE;
            case FINALIZE -> null;
        };
    }
}
