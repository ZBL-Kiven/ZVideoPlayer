package com.zj.playerLib.audio;

import android.annotation.TargetApi;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import androidx.annotation.Nullable;
import com.zj.playerLib.util.Util;

@SuppressWarnings("unused")
final class AudioTimestampPoller {
    private static final int STATE_INITIALIZING = 0;
    private static final int STATE_TIMESTAMP = 1;
    private static final int STATE_TIMESTAMP_ADVANCING = 2;
    private static final int STATE_NO_TIMESTAMP = 3;
    private static final int STATE_ERROR = 4;
    private static final int FAST_POLL_INTERVAL_US = 5000;
    private static final int SLOW_POLL_INTERVAL_US = 10000000;
    private static final int ERROR_POLL_INTERVAL_US = 500000;
    private static final int INITIALIZING_DURATION_US = 500000;
    @Nullable
    private final AudioTimestampPoller.AudioTimestampV19 audioTimestamp;
    private int state;
    private long initializeSystemTimeUs;
    private long sampleIntervalUs;
    private long lastTimestampSampleTimeUs;
    private long initialTimestampPositionFrames;

    public AudioTimestampPoller(AudioTrack audioTrack) {
        if (Util.SDK_INT >= 19) {
            this.audioTimestamp = new AudioTimestampV19(audioTrack);
            this.reset();
        } else {
            this.audioTimestamp = null;
            this.updateState(3);
        }

    }

    public boolean maybePollTimestamp(long systemTimeUs) {
        if (this.audioTimestamp != null && systemTimeUs - this.lastTimestampSampleTimeUs >= this.sampleIntervalUs) {
            this.lastTimestampSampleTimeUs = systemTimeUs;
            boolean updatedTimestamp = this.audioTimestamp.maybeUpdateTimestamp();
            switch(this.state) {
            case 0:
                if (updatedTimestamp) {
                    if (this.audioTimestamp.getTimestampSystemTimeUs() >= this.initializeSystemTimeUs) {
                        this.initialTimestampPositionFrames = this.audioTimestamp.getTimestampPositionFrames();
                        this.updateState(1);
                    } else {
                        updatedTimestamp = false;
                    }
                } else if (systemTimeUs - this.initializeSystemTimeUs > 500000L) {
                    this.updateState(3);
                }
                break;
            case 1:
                if (updatedTimestamp) {
                    long timestampPositionFrames = this.audioTimestamp.getTimestampPositionFrames();
                    if (timestampPositionFrames > this.initialTimestampPositionFrames) {
                        this.updateState(2);
                    }
                } else {
                    this.reset();
                }
                break;
            case 2:
                if (!updatedTimestamp) {
                    this.reset();
                }
                break;
            case 3:
                if (updatedTimestamp) {
                    this.reset();
                }
            case 4:
                break;
            default:
                throw new IllegalStateException();
            }

            return updatedTimestamp;
        } else {
            return false;
        }
    }

    public void rejectTimestamp() {
        this.updateState(4);
    }

    public void acceptTimestamp() {
        if (this.state == 4) {
            this.reset();
        }

    }

    public boolean hasTimestamp() {
        return this.state == 1 || this.state == 2;
    }

    public boolean isTimestampAdvancing() {
        return this.state == 2;
    }

    public void reset() {
        if (this.audioTimestamp != null) {
            this.updateState(0);
        }

    }

    public long getTimestampSystemTimeUs() {
        return this.audioTimestamp != null ? this.audioTimestamp.getTimestampSystemTimeUs() : -Long.MAX_VALUE;
    }

    public long getTimestampPositionFrames() {
        return this.audioTimestamp != null ? this.audioTimestamp.getTimestampPositionFrames() : -1L;
    }

    private void updateState(int state) {
        this.state = state;
        switch(state) {
        case 0:
            this.lastTimestampSampleTimeUs = 0L;
            this.initialTimestampPositionFrames = -1L;
            this.initializeSystemTimeUs = System.nanoTime() / 1000L;
            this.sampleIntervalUs = 5000L;
            break;
        case 1:
            this.sampleIntervalUs = 5000L;
            break;
        case 2:
        case 3:
            this.sampleIntervalUs = 10000000L;
            break;
        case 4:
            this.sampleIntervalUs = 500000L;
            break;
        default:
            throw new IllegalStateException();
        }

    }

    @TargetApi(19)
    private static final class AudioTimestampV19 {
        private final AudioTrack audioTrack;
        private final AudioTimestamp audioTimestamp;
        private long rawTimestampFramePositionWrapCount;
        private long lastTimestampRawPositionFrames;
        private long lastTimestampPositionFrames;

        public AudioTimestampV19(AudioTrack audioTrack) {
            this.audioTrack = audioTrack;
            this.audioTimestamp = new AudioTimestamp();
        }

        public boolean maybeUpdateTimestamp() {
            boolean updated = this.audioTrack.getTimestamp(this.audioTimestamp);
            if (updated) {
                long rawPositionFrames = this.audioTimestamp.framePosition;
                if (this.lastTimestampRawPositionFrames > rawPositionFrames) {
                    ++this.rawTimestampFramePositionWrapCount;
                }

                this.lastTimestampRawPositionFrames = rawPositionFrames;
                this.lastTimestampPositionFrames = rawPositionFrames + (this.rawTimestampFramePositionWrapCount << 32);
            }

            return updated;
        }

        public long getTimestampSystemTimeUs() {
            return this.audioTimestamp.nanoTime / 1000L;
        }

        public long getTimestampPositionFrames() {
            return this.lastTimestampPositionFrames;
        }
    }
}
