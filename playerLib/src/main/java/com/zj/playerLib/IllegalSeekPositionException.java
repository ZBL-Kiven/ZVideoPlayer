package com.zj.playerLib;

public final class IllegalSeekPositionException extends IllegalStateException {
    public final Timeline timeline;
    public final int windowIndex;
    public final long positionMs;

    public IllegalSeekPositionException(Timeline timeline, int windowIndex, long positionMs) {
        this.timeline = timeline;
        this.windowIndex = windowIndex;
        this.positionMs = positionMs;
    }
}
