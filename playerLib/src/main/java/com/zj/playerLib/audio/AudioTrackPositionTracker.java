package com.zj.playerLib.audio;

import android.media.AudioTrack;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.zj.playerLib.C;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

import java.lang.reflect.Method;


@SuppressWarnings("unused")
final class AudioTrackPositionTracker {
    private static final int PLAYS_TATE_STOPPED = 1;
    private static final int PLAY_STATE_PAUSED = 2;
    private static final int PLAY_STATE_PLAYING = 3;
    private static final long MAX_AUDIO_TIMESTAMP_OFFSET_US = 5000000L;
    private static final long MAX_LATENCY_US = 5000000L;
    private static final long FORCE_RESET_WORKAROUND_TIMEOUT_MS = 200L;
    private static final int MAX_PLAY_HEAD_OFFSET_COUNT = 10;
    private static final int MIN_PLAY_HEAD_OFFSET_SAMPLE_INTERVAL_US = 30000;
    private static final int MIN_LATENCY_SAMPLE_INTERVAL_US = 500000;
    private final Listener listener;
    private final long[] playHeadOffsets;
    @Nullable
    private AudioTrack audioTrack;
    private int outputPcmFrameSize;
    private int bufferSize;
    @Nullable
    private AudioTimestampPoller audioTimestampPoller;
    private int outputSampleRate;
    private boolean needsPassthroughWorkarounds;
    private long bufferSizeUs;
    private long smoothedPlayheadOffsetUs;
    private long lastPlayheadSampleTimeUs;
    @Nullable
    private Method getLatencyMethod;
    private long latencyUs;
    private boolean hasData;
    private boolean isOutputPcm;
    private long lastLatencySampleTimeUs;
    private long lastRawPlaybackHeadPosition;
    private long rawPlaybackHeadWrapCount;
    private long passThroughWorkaroundPauseOffset;
    private int nextPlayHeadOffsetIndex;
    private int playHeadOffsetCount;
    private long stopTimestampUs;
    private long forceResetWorkaroundTimeMs;
    private long stopPlaybackHeadPosition;
    private long endPlaybackHeadPosition;

    public AudioTrackPositionTracker(Listener listener) {
        this.listener = Assertions.checkNotNull(listener);
        if (Util.SDK_INT >= 18) {
            try {
                this.getLatencyMethod = AudioTrack.class.getMethod("getLatency", (Class<?>[]) null);
            } catch (NoSuchMethodException ignored) {
            }
        }

        this.playHeadOffsets = new long[10];
    }

    public void setAudioTrack(AudioTrack audioTrack, int outputEncoding, int outputPcmFrameSize, int bufferSize) {
        this.audioTrack = audioTrack;
        this.outputPcmFrameSize = outputPcmFrameSize;
        this.bufferSize = bufferSize;
        this.audioTimestampPoller = new AudioTimestampPoller(audioTrack);
        this.outputSampleRate = audioTrack.getSampleRate();
        this.needsPassthroughWorkarounds = needsPassThroughWorkarounds(outputEncoding);
        this.isOutputPcm = Util.isEncodingLinearPcm(outputEncoding);
        this.bufferSizeUs = this.isOutputPcm ? this.framesToDurationUs(bufferSize / outputPcmFrameSize) : -9223372036854775807L;
        this.lastRawPlaybackHeadPosition = 0L;
        this.rawPlaybackHeadWrapCount = 0L;
        this.passThroughWorkaroundPauseOffset = 0L;
        this.hasData = false;
        this.stopTimestampUs = -9223372036854775807L;
        this.forceResetWorkaroundTimeMs = -9223372036854775807L;
        this.latencyUs = 0L;
    }

    public long getCurrentPositionUs(boolean sourceEnded) {
        if (Assertions.checkNotNull(this.audioTrack).getPlayState() == 3) {
            this.maybeSampleSyncParams();
        }

        long systemTimeUs = System.nanoTime() / 1000L;
        AudioTimestampPoller audioTimestampPoller = Assertions.checkNotNull(this.audioTimestampPoller);
        long positionUs;
        if (audioTimestampPoller.hasTimestamp()) {
            positionUs = audioTimestampPoller.getTimestampPositionFrames();
            long timestampPositionUs = this.framesToDurationUs(positionUs);
            if (!audioTimestampPoller.isTimestampAdvancing()) {
                return timestampPositionUs;
            } else {
                long elapsedSinceTimestampUs = systemTimeUs - audioTimestampPoller.getTimestampSystemTimeUs();
                return timestampPositionUs + elapsedSinceTimestampUs;
            }
        } else {
            if (this.playHeadOffsetCount == 0) {
                positionUs = this.getPlaybackHeadPositionUs();
            } else {
                positionUs = systemTimeUs + this.smoothedPlayheadOffsetUs;
            }

            if (!sourceEnded) {
                positionUs -= this.latencyUs;
            }

            return positionUs;
        }
    }

    public void start() {
        Assertions.checkNotNull(this.audioTimestampPoller).reset();
    }

    public boolean isPlaying() {
        return Assertions.checkNotNull(this.audioTrack).getPlayState() == 3;
    }

    public boolean mayHandleBuffer(long writtenFrames) {
        int playState = Assertions.checkNotNull(this.audioTrack).getPlayState();
        if (this.needsPassthroughWorkarounds) {
            if (playState == 2) {
                this.hasData = false;
                return false;
            }

            if (playState == 1 && this.getPlaybackHeadPosition() == 0L) {
                return false;
            }
        }

        boolean hadData = this.hasData;
        this.hasData = this.hasPendingData(writtenFrames);
        if (hadData && !this.hasData && playState != 1 && this.listener != null) {
            this.listener.onUnderrun(this.bufferSize, C.usToMs(this.bufferSizeUs));
        }

        return true;
    }

    public int getAvailableBufferSize(long writtenBytes) {
        int bytesPending = (int) (writtenBytes - this.getPlaybackHeadPosition() * (long) this.outputPcmFrameSize);
        return this.bufferSize - bytesPending;
    }

    public boolean isStalled(long writtenFrames) {
        return this.forceResetWorkaroundTimeMs != -9223372036854775807L && writtenFrames > 0L && SystemClock.elapsedRealtime() - this.forceResetWorkaroundTimeMs >= 200L;
    }

    public void handleEndOfStream(long writtenFrames) {
        this.stopPlaybackHeadPosition = this.getPlaybackHeadPosition();
        this.stopTimestampUs = SystemClock.elapsedRealtime() * 1000L;
        this.endPlaybackHeadPosition = writtenFrames;
    }

    public boolean hasPendingData(long writtenFrames) {
        return writtenFrames > this.getPlaybackHeadPosition() || this.forceHasPendingData();
    }

    public boolean pause() {
        this.resetSyncParams();
        if (this.stopTimestampUs == -9223372036854775807L) {
            Assertions.checkNotNull(this.audioTimestampPoller).reset();
            return true;
        } else {
            return false;
        }
    }

    public void reset() {
        this.resetSyncParams();
        this.audioTrack = null;
        this.audioTimestampPoller = null;
    }

    private void maybeSampleSyncParams() {
        long playbackPositionUs = this.getPlaybackHeadPositionUs();
        if (playbackPositionUs != 0L) {
            long systemTimeUs = System.nanoTime() / 1000L;
            if (systemTimeUs - this.lastPlayheadSampleTimeUs >= 30000L) {
                this.playHeadOffsets[this.nextPlayHeadOffsetIndex] = playbackPositionUs - systemTimeUs;
                this.nextPlayHeadOffsetIndex = (this.nextPlayHeadOffsetIndex + 1) % 10;
                if (this.playHeadOffsetCount < 10) {
                    ++this.playHeadOffsetCount;
                }

                this.lastPlayheadSampleTimeUs = systemTimeUs;
                this.smoothedPlayheadOffsetUs = 0L;

                for (int i = 0; i < this.playHeadOffsetCount; ++i) {
                    this.smoothedPlayheadOffsetUs += this.playHeadOffsets[i] / (long) this.playHeadOffsetCount;
                }
            }

            if (!this.needsPassthroughWorkarounds) {
                this.maybePollAndCheckTimestamp(systemTimeUs, playbackPositionUs);
                this.maybeUpdateLatency(systemTimeUs);
            }
        }
    }

    private void maybePollAndCheckTimestamp(long systemTimeUs, long playbackPositionUs) {
        AudioTimestampPoller audioTimestampPoller = Assertions.checkNotNull(this.audioTimestampPoller);
        if (audioTimestampPoller.maybePollTimestamp(systemTimeUs)) {
            long audioTimestampSystemTimeUs = audioTimestampPoller.getTimestampSystemTimeUs();
            long audioTimestampPositionFrames = audioTimestampPoller.getTimestampPositionFrames();
            if (Math.abs(audioTimestampSystemTimeUs - systemTimeUs) > 5000000L) {
                this.listener.onSystemTimeUsMismatch(audioTimestampPositionFrames, audioTimestampSystemTimeUs, systemTimeUs, playbackPositionUs);
                audioTimestampPoller.rejectTimestamp();
            } else if (Math.abs(this.framesToDurationUs(audioTimestampPositionFrames) - playbackPositionUs) > 5000000L) {
                this.listener.onPositionFramesMismatch(audioTimestampPositionFrames, audioTimestampSystemTimeUs, systemTimeUs, playbackPositionUs);
                audioTimestampPoller.rejectTimestamp();
            } else {
                audioTimestampPoller.acceptTimestamp();
            }

        }
    }

    private void maybeUpdateLatency(long systemTimeUs) {
        if (this.isOutputPcm && this.getLatencyMethod != null && systemTimeUs - this.lastLatencySampleTimeUs >= 500000L) {
            try {
                Object o = this.getLatencyMethod.invoke(Assertions.checkNotNull(this.audioTrack));
                if (o != null) this.latencyUs = (long) Util.castNonNull((Integer) o) * 1000L - this.bufferSizeUs;
                this.latencyUs = Math.max(this.latencyUs, 0L);
                if (this.latencyUs > 5000000L) {
                    this.listener.onInvalidLatency(this.latencyUs);
                    this.latencyUs = 0L;
                }
            } catch (Exception var4) {
                this.getLatencyMethod = null;
            }

            this.lastLatencySampleTimeUs = systemTimeUs;
        }

    }

    private long framesToDurationUs(long frameCount) {
        return frameCount * 1000000L / (long) this.outputSampleRate;
    }

    private void resetSyncParams() {
        this.smoothedPlayheadOffsetUs = 0L;
        this.playHeadOffsetCount = 0;
        this.nextPlayHeadOffsetIndex = 0;
        this.lastPlayheadSampleTimeUs = 0L;
    }

    private boolean forceHasPendingData() {
        return this.needsPassthroughWorkarounds && Assertions.checkNotNull(this.audioTrack).getPlayState() == 2 && this.getPlaybackHeadPosition() == 0L;
    }

    private static boolean needsPassThroughWorkarounds(int outputEncoding) {
        return Util.SDK_INT < 23 && (outputEncoding == 5 || outputEncoding == 6);
    }

    private long getPlaybackHeadPositionUs() {
        return this.framesToDurationUs(this.getPlaybackHeadPosition());
    }

    private long getPlaybackHeadPosition() {
        AudioTrack audioTrack = Assertions.checkNotNull(this.audioTrack);
        if (this.stopTimestampUs != -9223372036854775807L) {
            long elapsedTimeSinceStopUs = SystemClock.elapsedRealtime() * 1000L - this.stopTimestampUs;
            long framesSinceStop = elapsedTimeSinceStopUs * (long) this.outputSampleRate / 1000000L;
            return Math.min(this.endPlaybackHeadPosition, this.stopPlaybackHeadPosition + framesSinceStop);
        } else {
            int state = audioTrack.getPlayState();
            if (state == 1) {
                return 0L;
            } else {
                long rawPlaybackHeadPosition = 4294967295L & (long) audioTrack.getPlaybackHeadPosition();
                if (this.needsPassthroughWorkarounds) {
                    if (state == 2 && rawPlaybackHeadPosition == 0L) {
                        this.passThroughWorkaroundPauseOffset = this.lastRawPlaybackHeadPosition;
                    }

                    rawPlaybackHeadPosition += this.passThroughWorkaroundPauseOffset;
                }

                if (Util.SDK_INT <= 28) {
                    if (rawPlaybackHeadPosition == 0L && this.lastRawPlaybackHeadPosition > 0L && state == 3) {
                        if (this.forceResetWorkaroundTimeMs == -9223372036854775807L) {
                            this.forceResetWorkaroundTimeMs = SystemClock.elapsedRealtime();
                        }

                        return this.lastRawPlaybackHeadPosition;
                    }

                    this.forceResetWorkaroundTimeMs = -9223372036854775807L;
                }

                if (this.lastRawPlaybackHeadPosition > rawPlaybackHeadPosition) {
                    ++this.rawPlaybackHeadWrapCount;
                }

                this.lastRawPlaybackHeadPosition = rawPlaybackHeadPosition;
                return rawPlaybackHeadPosition + (this.rawPlaybackHeadWrapCount << 32);
            }
        }
    }

    public interface Listener {
        void onPositionFramesMismatch(long var1, long var3, long var5, long var7);

        void onSystemTimeUsMismatch(long var1, long var3, long var5, long var7);

        void onInvalidLatency(long var1);

        void onUnderrun(int var1, long var2);
    }
}
