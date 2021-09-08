//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;

public interface SeekMap {
    boolean isSeekable();

    long getDurationUs();

    SeekPoints getSeekPoints(long var1);

    public static final class SeekPoints {
        public final SeekPoint first;
        public final SeekPoint second;

        public SeekPoints(SeekPoint point) {
            this(point, point);
        }

        public SeekPoints(SeekPoint first, SeekPoint second) {
            this.first = (SeekPoint)Assertions.checkNotNull(first);
            this.second = (SeekPoint)Assertions.checkNotNull(second);
        }

        public String toString() {
            return "[" + this.first + (this.first.equals(this.second) ? "" : ", " + this.second) + "]";
        }

        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            } else if (obj != null && this.getClass() == obj.getClass()) {
                SeekPoints other = (SeekPoints)obj;
                return this.first.equals(other.first) && this.second.equals(other.second);
            } else {
                return false;
            }
        }

        public int hashCode() {
            return 31 * this.first.hashCode() + this.second.hashCode();
        }
    }

    public static final class Unseekable implements SeekMap {
        private final long durationUs;
        private final SeekPoints startSeekPoints;

        public Unseekable(long durationUs) {
            this(durationUs, 0L);
        }

        public Unseekable(long durationUs, long startPosition) {
            this.durationUs = durationUs;
            this.startSeekPoints = new SeekPoints(startPosition == 0L ? SeekPoint.START : new SeekPoint(0L, startPosition));
        }

        public boolean isSeekable() {
            return false;
        }

        public long getDurationUs() {
            return this.durationUs;
        }

        public SeekPoints getSeekPoints(long timeUs) {
            return this.startSeekPoints;
        }
    }
}
