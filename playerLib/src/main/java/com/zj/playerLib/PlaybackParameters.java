//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;

public final class PlaybackParameters {
    public static final PlaybackParameters DEFAULT = new PlaybackParameters(1.0F);
    public final float speed;
    public final float pitch;
    public final boolean skipSilence;
    private final int scaledUsPerMs;

    public PlaybackParameters(float speed) {
        this(speed, 1.0F, false);
    }

    public PlaybackParameters(float speed, float pitch) {
        this(speed, pitch, false);
    }

    public PlaybackParameters(float speed, float pitch, boolean skipSilence) {
        Assertions.checkArgument(speed > 0.0F);
        Assertions.checkArgument(pitch > 0.0F);
        this.speed = speed;
        this.pitch = pitch;
        this.skipSilence = skipSilence;
        this.scaledUsPerMs = Math.round(speed * 1000.0F);
    }

    public long getMediaTimeUsForPlayOutTimeMs(long timeMs) {
        return timeMs * (long) this.scaledUsPerMs;
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            PlaybackParameters other = (PlaybackParameters) obj;
            return this.speed == other.speed && this.pitch == other.pitch && this.skipSilence == other.skipSilence;
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 17;
        result = 31 * result + Float.floatToRawIntBits(this.speed);
        result = 31 * result + Float.floatToRawIntBits(this.pitch);
        result = 31 * result + (this.skipSilence ? 1 : 0);
        return result;
    }
}
