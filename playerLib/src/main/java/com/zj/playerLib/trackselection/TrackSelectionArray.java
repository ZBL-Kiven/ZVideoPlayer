//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.trackselection;

import androidx.annotation.Nullable;

import java.util.Arrays;

public final class TrackSelectionArray {
    public final int length;
    private final TrackSelection[] trackSelections;
    private int hashCode;

    public TrackSelectionArray(TrackSelection... trackSelections) {
        this.trackSelections = trackSelections;
        this.length = trackSelections.length;
    }

    @Nullable
    public TrackSelection get(int index) {
        return this.trackSelections[index];
    }

    public TrackSelection[] getAll() {
        return this.trackSelections.clone();
    }

    public int hashCode() {
        if (this.hashCode == 0) {
            int result = 17;
            result = 31 * result + Arrays.hashCode(this.trackSelections);
            this.hashCode = result;
        }

        return this.hashCode;
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            TrackSelectionArray other = (TrackSelectionArray) obj;
            return Arrays.equals(this.trackSelections, other.trackSelections);
        } else {
            return false;
        }
    }
}
