package com.zj.playerLib.trackselection;

import androidx.annotation.Nullable;

import com.zj.playerLib.Format;
import com.zj.playerLib.source.TrackGroup;
import com.zj.playerLib.source.chunk.MediaChunk;
import com.zj.playerLib.source.chunk.MediaChunkIterator;
import com.zj.playerLib.upstream.BandwidthMeter;
import com.zj.playerLib.util.Clock;
import com.zj.playerLib.util.Util;

import java.util.List;

public class AdaptiveTrackSelection extends BaseTrackSelection {
    public static final int DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS = 10000;
    public static final int DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS = 25000;
    public static final int DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS = 25000;
    public static final float DEFAULT_BANDWIDTH_FRACTION = 0.75F;
    public static final float DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE = 0.75F;
    public static final long DEFAULT_MIN_TIME_BETWEEN_BUFFER_REEVALUTATION_MS = 2000L;
    private final BandwidthMeter bandwidthMeter;
    private final long minDurationForQualityIncreaseUs;
    private final long maxDurationForQualityDecreaseUs;
    private final long minDurationToRetainAfterDiscardUs;
    private final float bandwidthFraction;
    private final float bufferedFractionToLiveEdgeForQualityIncrease;
    private final long minTimeBetweenBufferReevaluationMs;
    private final Clock clock;
    private float playbackSpeed;
    private int selectedIndex;
    private int reason;
    private long lastBufferEvaluationMs;

    public AdaptiveTrackSelection(TrackGroup group, int[] tracks, BandwidthMeter bandwidthMeter) {
        this(group, tracks, bandwidthMeter, 10000L, 25000L, 25000L, 0.75F, 0.75F, 2000L, Clock.DEFAULT);
    }

    public AdaptiveTrackSelection(TrackGroup group, int[] tracks, BandwidthMeter bandwidthMeter, long minDurationForQualityIncreaseMs, long maxDurationForQualityDecreaseMs, long minDurationToRetainAfterDiscardMs, float bandwidthFraction, float bufferedFractionToLiveEdgeForQualityIncrease, long minTimeBetweenBufferReevaluationMs, Clock clock) {
        super(group, tracks);
        this.bandwidthMeter = bandwidthMeter;
        this.minDurationForQualityIncreaseUs = minDurationForQualityIncreaseMs * 1000L;
        this.maxDurationForQualityDecreaseUs = maxDurationForQualityDecreaseMs * 1000L;
        this.minDurationToRetainAfterDiscardUs = minDurationToRetainAfterDiscardMs * 1000L;
        this.bandwidthFraction = bandwidthFraction;
        this.bufferedFractionToLiveEdgeForQualityIncrease = bufferedFractionToLiveEdgeForQualityIncrease;
        this.minTimeBetweenBufferReevaluationMs = minTimeBetweenBufferReevaluationMs;
        this.clock = clock;
        this.playbackSpeed = 1.0F;
        this.reason = 1;
        this.lastBufferEvaluationMs = -Long.MAX_VALUE;
        int selectedIndex = this.determineIdealSelectedIndex(-9223372036854775808L);
        this.selectedIndex = selectedIndex;
    }

    public void enable() {
        this.lastBufferEvaluationMs = -Long.MAX_VALUE;
    }

    public void onPlaybackSpeed(float playbackSpeed) {
        this.playbackSpeed = playbackSpeed;
    }

    public void updateSelectedTrack(long playbackPositionUs, long bufferedDurationUs, long availableDurationUs, List<? extends MediaChunk> queue, MediaChunkIterator[] mediaChunkIterators) {
        long nowMs = this.clock.elapsedRealtime();
        int currentSelectedIndex = this.selectedIndex;
        this.selectedIndex = this.determineIdealSelectedIndex(nowMs);
        if (this.selectedIndex != currentSelectedIndex) {
            if (!this.isBlacklisted(currentSelectedIndex, nowMs)) {
                Format currentFormat = this.getFormat(currentSelectedIndex);
                Format selectedFormat = this.getFormat(this.selectedIndex);
                if (selectedFormat.bitrate > currentFormat.bitrate && bufferedDurationUs < this.minDurationForQualityIncreaseUs(availableDurationUs)) {
                    this.selectedIndex = currentSelectedIndex;
                } else if (selectedFormat.bitrate < currentFormat.bitrate && bufferedDurationUs >= this.maxDurationForQualityDecreaseUs) {
                    this.selectedIndex = currentSelectedIndex;
                }
            }

            if (this.selectedIndex != currentSelectedIndex) {
                this.reason = 3;
            }

        }
    }

    public int getSelectedIndex() {
        return this.selectedIndex;
    }

    public int getSelectionReason() {
        return this.reason;
    }

    @Nullable
    public Object getSelectionData() {
        return null;
    }

    public int evaluateQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
        long nowMs = this.clock.elapsedRealtime();
        if (this.lastBufferEvaluationMs != -Long.MAX_VALUE && nowMs - this.lastBufferEvaluationMs < this.minTimeBetweenBufferReevaluationMs) {
            return queue.size();
        } else {
            this.lastBufferEvaluationMs = nowMs;
            if (queue.isEmpty()) {
                return 0;
            } else {
                int queueSize = queue.size();
                MediaChunk lastChunk = queue.get(queueSize - 1);
                long playoutBufferedDurationBeforeLastChunkUs = Util.getPlayoutDurationForMediaDuration(lastChunk.startTimeUs - playbackPositionUs, this.playbackSpeed);
                if (playoutBufferedDurationBeforeLastChunkUs < this.minDurationToRetainAfterDiscardUs) {
                    return queueSize;
                } else {
                    int idealSelectedIndex = this.determineIdealSelectedIndex(nowMs);
                    Format idealFormat = this.getFormat(idealSelectedIndex);

                    for(int i = 0; i < queueSize; ++i) {
                        MediaChunk chunk = queue.get(i);
                        Format format = chunk.trackFormat;
                        long mediaDurationBeforeThisChunkUs = chunk.startTimeUs - playbackPositionUs;
                        long playoutDurationBeforeThisChunkUs = Util.getPlayoutDurationForMediaDuration(mediaDurationBeforeThisChunkUs, this.playbackSpeed);
                        if (playoutDurationBeforeThisChunkUs >= this.minDurationToRetainAfterDiscardUs && format.bitrate < idealFormat.bitrate && format.height != -1 && format.height < 720 && format.width != -1 && format.width < 1280 && format.height < idealFormat.height) {
                            return i;
                        }
                    }

                    return queueSize;
                }
            }
        }
    }

    private int determineIdealSelectedIndex(long nowMs) {
        long effectiveBitrate = (long)((float)this.bandwidthMeter.getBitrateEstimate() * this.bandwidthFraction);
        int lowestBitrateNonBlacklistedIndex = 0;

        for(int i = 0; i < this.length; ++i) {
            if (nowMs == -9223372036854775808L || !this.isBlacklisted(i, nowMs)) {
                Format format = this.getFormat(i);
                if ((long)Math.round((float)format.bitrate * this.playbackSpeed) <= effectiveBitrate) {
                    return i;
                }

                lowestBitrateNonBlacklistedIndex = i;
            }
        }

        return lowestBitrateNonBlacklistedIndex;
    }

    private long minDurationForQualityIncreaseUs(long availableDurationUs) {
        boolean isAvailableDurationTooShort = availableDurationUs != -Long.MAX_VALUE && availableDurationUs <= this.minDurationForQualityIncreaseUs;
        return isAvailableDurationTooShort ? (long)((float)availableDurationUs * this.bufferedFractionToLiveEdgeForQualityIncrease) : this.minDurationForQualityIncreaseUs;
    }

    public static final class Factory implements TrackSelection.Factory {
        @Nullable
        private final BandwidthMeter bandwidthMeter;
        private final int minDurationForQualityIncreaseMs;
        private final int maxDurationForQualityDecreaseMs;
        private final int minDurationToRetainAfterDiscardMs;
        private final float bandwidthFraction;
        private final float bufferedFractionToLiveEdgeForQualityIncrease;
        private final long minTimeBetweenBufferReevaluationMs;
        private final Clock clock;

        public Factory() {
            this(10000, 25000, 25000, 0.75F, 0.75F, 2000L, Clock.DEFAULT);
        }

        /** @deprecated */
        @Deprecated
        public Factory(BandwidthMeter bandwidthMeter) {
            this(bandwidthMeter, 10000, 25000, 25000, 0.75F, 0.75F, 2000L, Clock.DEFAULT);
        }

        public Factory(int minDurationForQualityIncreaseMs, int maxDurationForQualityDecreaseMs, int minDurationToRetainAfterDiscardMs, float bandwidthFraction) {
            this(minDurationForQualityIncreaseMs, maxDurationForQualityDecreaseMs, minDurationToRetainAfterDiscardMs, bandwidthFraction, 0.75F, 2000L, Clock.DEFAULT);
        }

        /** @deprecated */
        @Deprecated
        public Factory(BandwidthMeter bandwidthMeter, int minDurationForQualityIncreaseMs, int maxDurationForQualityDecreaseMs, int minDurationToRetainAfterDiscardMs, float bandwidthFraction) {
            this(bandwidthMeter, minDurationForQualityIncreaseMs, maxDurationForQualityDecreaseMs, minDurationToRetainAfterDiscardMs, bandwidthFraction, 0.75F, 2000L, Clock.DEFAULT);
        }

        public Factory(int minDurationForQualityIncreaseMs, int maxDurationForQualityDecreaseMs, int minDurationToRetainAfterDiscardMs, float bandwidthFraction, float bufferedFractionToLiveEdgeForQualityIncrease, long minTimeBetweenBufferReevaluationMs, Clock clock) {
            this(null, minDurationForQualityIncreaseMs, maxDurationForQualityDecreaseMs, minDurationToRetainAfterDiscardMs, bandwidthFraction, bufferedFractionToLiveEdgeForQualityIncrease, minTimeBetweenBufferReevaluationMs, clock);
        }

        /** @deprecated */
        @Deprecated
        public Factory(@Nullable BandwidthMeter bandwidthMeter, int minDurationForQualityIncreaseMs, int maxDurationForQualityDecreaseMs, int minDurationToRetainAfterDiscardMs, float bandwidthFraction, float bufferedFractionToLiveEdgeForQualityIncrease, long minTimeBetweenBufferReevaluationMs, Clock clock) {
            this.bandwidthMeter = bandwidthMeter;
            this.minDurationForQualityIncreaseMs = minDurationForQualityIncreaseMs;
            this.maxDurationForQualityDecreaseMs = maxDurationForQualityDecreaseMs;
            this.minDurationToRetainAfterDiscardMs = minDurationToRetainAfterDiscardMs;
            this.bandwidthFraction = bandwidthFraction;
            this.bufferedFractionToLiveEdgeForQualityIncrease = bufferedFractionToLiveEdgeForQualityIncrease;
            this.minTimeBetweenBufferReevaluationMs = minTimeBetweenBufferReevaluationMs;
            this.clock = clock;
        }

        public AdaptiveTrackSelection createTrackSelection(TrackGroup group, BandwidthMeter bandwidthMeter, int... tracks) {
            if (this.bandwidthMeter != null) {
                bandwidthMeter = this.bandwidthMeter;
            }

            return new AdaptiveTrackSelection(group, tracks, bandwidthMeter, this.minDurationForQualityIncreaseMs, this.maxDurationForQualityDecreaseMs, this.minDurationToRetainAfterDiscardMs, this.bandwidthFraction, this.bufferedFractionToLiveEdgeForQualityIncrease, this.minTimeBetweenBufferReevaluationMs, this.clock);
        }
    }
}
