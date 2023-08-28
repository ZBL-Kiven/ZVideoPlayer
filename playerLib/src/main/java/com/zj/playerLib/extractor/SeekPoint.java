package com.zj.playerLib.extractor;

import androidx.annotation.Nullable;

public final class SeekPoint {
    public static final SeekPoint START = new SeekPoint(0L, 0L);
    public final long timeUs;
    public final long position;

    public SeekPoint(long timeUs, long position) {
        this.timeUs = timeUs;
        this.position = position;
    }

    public String toString() {
        return "[timeUs=" + this.timeUs + ", position=" + this.position + "]";
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            SeekPoint other = (SeekPoint)obj;
            return this.timeUs == other.timeUs && this.position == other.position;
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = (int)this.timeUs;
        result = 31 * result + (int)this.position;
        return result;
    }
}
