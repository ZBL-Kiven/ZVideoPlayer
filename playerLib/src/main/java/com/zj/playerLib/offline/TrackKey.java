package com.zj.playerLib.offline;

public final class TrackKey {
    public final int periodIndex;
    public final int groupIndex;
    public final int trackIndex;

    public TrackKey(int periodIndex, int groupIndex, int trackIndex) {
        this.periodIndex = periodIndex;
        this.groupIndex = groupIndex;
        this.trackIndex = trackIndex;
    }
}
