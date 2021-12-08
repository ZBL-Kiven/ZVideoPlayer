package com.zj.playerLib.util;

import com.zj.playerLib.C;
import com.zj.playerLib.PlaybackParameters;

public final class StandaloneMediaClock implements MediaClock {
    private final Clock clock;
    private boolean started;
    private long baseUs;
    private long baseElapsedMs;
    private PlaybackParameters playbackParameters;

    public StandaloneMediaClock(Clock clock) {
        this.clock = clock;
        this.playbackParameters = PlaybackParameters.DEFAULT;
    }

    public void start() {
        if (!this.started) {
            this.baseElapsedMs = this.clock.elapsedRealtime();
            this.started = true;
        }

    }

    public void stop() {
        if (this.started) {
            this.resetPosition(this.getPositionUs());
            this.started = false;
        }

    }

    public void resetPosition(long positionUs) {
        this.baseUs = positionUs;
        if (this.started) {
            this.baseElapsedMs = this.clock.elapsedRealtime();
        }

    }

    public long getPositionUs() {
        long positionUs = this.baseUs;
        if (this.started) {
            long elapsedSinceBaseMs = this.clock.elapsedRealtime() - this.baseElapsedMs;
            if (this.playbackParameters.speed == 1.0F) {
                positionUs += C.msToUs(elapsedSinceBaseMs);
            } else {
                positionUs += this.playbackParameters.getMediaTimeUsForPlayOutTimeMs(elapsedSinceBaseMs);
            }
        }

        return positionUs;
    }

    public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
        if (this.started) {
            this.resetPosition(this.getPositionUs());
        }

        this.playbackParameters = playbackParameters;
        return playbackParameters;
    }

    public PlaybackParameters getPlaybackParameters() {
        return this.playbackParameters;
    }
}
