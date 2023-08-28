package com.zj.playerLib.offline;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class StreamKey implements Comparable<StreamKey> {
    public final int periodIndex;
    public final int groupIndex;
    public final int trackIndex;

    public StreamKey(int groupIndex, int trackIndex) {
        this(0, groupIndex, trackIndex);
    }

    public StreamKey(int periodIndex, int groupIndex, int trackIndex) {
        this.periodIndex = periodIndex;
        this.groupIndex = groupIndex;
        this.trackIndex = trackIndex;
    }

    public String toString() {
        return this.periodIndex + "." + this.groupIndex + "." + this.trackIndex;
    }

    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            StreamKey that = (StreamKey)o;
            return this.periodIndex == that.periodIndex && this.groupIndex == that.groupIndex && this.trackIndex == that.trackIndex;
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = this.periodIndex;
        result = 31 * result + this.groupIndex;
        result = 31 * result + this.trackIndex;
        return result;
    }

    public int compareTo(@NonNull StreamKey o) {
        int result = this.periodIndex - o.periodIndex;
        if (result == 0) {
            result = this.groupIndex - o.groupIndex;
            if (result == 0) {
                result = this.trackIndex - o.trackIndex;
            }
        }

        return result;
    }
}
