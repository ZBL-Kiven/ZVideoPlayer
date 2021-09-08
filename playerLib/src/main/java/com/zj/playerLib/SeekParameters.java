//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib;

import androidx.annotation.Nullable;
import com.zj.playerLib.util.Assertions;

public final class SeekParameters {
    public static final SeekParameters EXACT = new SeekParameters(0L, 0L);
    public static final SeekParameters CLOSEST_SYNC = new SeekParameters(9223372036854775807L, 9223372036854775807L);
    public static final SeekParameters PREVIOUS_SYNC = new SeekParameters(9223372036854775807L, 0L);
    public static final SeekParameters NEXT_SYNC = new SeekParameters(0L, 9223372036854775807L);
    public static final SeekParameters DEFAULT;
    public final long toleranceBeforeUs;
    public final long toleranceAfterUs;

    public SeekParameters(long toleranceBeforeUs, long toleranceAfterUs) {
        Assertions.checkArgument(toleranceBeforeUs >= 0L);
        Assertions.checkArgument(toleranceAfterUs >= 0L);
        this.toleranceBeforeUs = toleranceBeforeUs;
        this.toleranceAfterUs = toleranceAfterUs;
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            SeekParameters other = (SeekParameters)obj;
            return this.toleranceBeforeUs == other.toleranceBeforeUs && this.toleranceAfterUs == other.toleranceAfterUs;
        } else {
            return false;
        }
    }

    public int hashCode() {
        return 31 * (int)this.toleranceBeforeUs + (int)this.toleranceAfterUs;
    }

    static {
        DEFAULT = EXACT;
    }
}
