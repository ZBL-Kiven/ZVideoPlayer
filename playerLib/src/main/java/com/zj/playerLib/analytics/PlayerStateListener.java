package com.zj.playerLib.analytics;

import android.view.Surface;

import androidx.annotation.Nullable;

import com.zj.playerLib.PlaybackException;
import com.zj.playerLib.Format;
import com.zj.playerLib.PlaybackParameters;
import com.zj.playerLib.Timeline;
import com.zj.playerLib.audio.AudioAttributes;
import com.zj.playerLib.decoder.DecoderCounters;
import com.zj.playerLib.metadata.Metadata;
import com.zj.playerLib.source.TrackGroupArray;
import com.zj.playerLib.source.MediaSource.MediaPeriodId;
import com.zj.playerLib.source.MediaSourceEventListener.LoadEventInfo;
import com.zj.playerLib.source.MediaSourceEventListener.MediaLoadData;
import com.zj.playerLib.trackselection.TrackSelectionArray;

import java.io.IOException;

@SuppressWarnings("unused,UnnecessaryInterfaceModifier")
public interface PlayerStateListener {
    default void onPlayerStateChanged(EventTime eventTime, boolean playWhenReady, int playbackState) {
    }

    default void onTimelineChanged(EventTime eventTime, int reason) {
    }

    default void onPositionDiscontinuity(EventTime eventTime, int reason) {
    }

    default void onSeekStarted(EventTime eventTime) {
    }

    default void onSeekProcessed(EventTime eventTime) {
    }

    default void onPlaybackParametersChanged(EventTime eventTime, PlaybackParameters playbackParameters) {
    }

    default void onRepeatModeChanged(EventTime eventTime, int repeatMode) {
    }

    default void onShuffleModeChanged(EventTime eventTime, boolean shuffleModeEnabled) {
    }

    default void onLoadingChanged(EventTime eventTime, boolean isLoading) {
    }

    default void onPlayerError(EventTime eventTime, PlaybackException error) {
    }

    default void onTracksChanged(EventTime eventTime, TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    }

    default void onLoadStarted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    }

    default void onLoadCompleted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    }

    default void onLoadCanceled(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    }

    default void onLoadError(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
    }

    default void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData mediaLoadData) {
    }

    default void onUpstreamDiscarded(EventTime eventTime, MediaLoadData mediaLoadData) {
    }

    default void onMediaPeriodCreated(EventTime eventTime) {
    }

    default void onMediaPeriodReleased(EventTime eventTime) {
    }

    default void onReadingStarted(EventTime eventTime) {
    }

    default void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
    }

    default void onSurfaceSizeChanged(EventTime eventTime, int width, int height) {
    }

    default void onMetadata(EventTime eventTime, Metadata metadata) {
    }

    default void onDecoderEnabled(EventTime eventTime, int trackType, DecoderCounters decoderCounters) {
    }

    default void onDecoderInitialized(EventTime eventTime, int trackType, String decoderName, long initializationDurationMs) {
    }

    default void onDecoderInputFormatChanged(EventTime eventTime, int trackType, Format format) {
    }

    default void onDecoderDisabled(EventTime eventTime, int trackType, DecoderCounters decoderCounters) {
    }

    default void onAudioSessionId(EventTime eventTime, int audioSessionId) {
    }

    default void onAudioAttributesChanged(EventTime eventTime, AudioAttributes audioAttributes) {
    }

    default void onVolumeChanged(EventTime eventTime, float volume) {
    }

    default void onAudioUnderrun(EventTime eventTime, int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    }

    default void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
    }

    default void onVideoSizeChanged(EventTime eventTime, int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
    }

    default void onRenderedFirstFrame(EventTime eventTime, @Nullable Surface surface) {
    }

    default void onDrmSessionAcquired(EventTime eventTime) {
    }

    default void onDrmKeysLoaded(EventTime eventTime) {
    }

    default void onDrmSessionManagerError(EventTime eventTime, Exception error) {
    }

    default void onDrmKeysRestored(EventTime eventTime) {
    }

    default void onDrmKeysRemoved(EventTime eventTime) {
    }

    default void onDrmSessionReleased(EventTime eventTime) {
    }

    public static final class EventTime {
        public final long realtimeMs;
        public final Timeline timeline;
        public final int windowIndex;
        @Nullable
        public final MediaPeriodId mediaPeriodId;
        public final long eventPlaybackPositionMs;
        public final long currentPlaybackPositionMs;
        public final long totalBufferedDurationMs;

        public EventTime(long realtimeMs, Timeline timeline, int windowIndex, @Nullable MediaPeriodId mediaPeriodId, long eventPlaybackPositionMs, long currentPlaybackPositionMs, long totalBufferedDurationMs) {
            this.realtimeMs = realtimeMs;
            this.timeline = timeline;
            this.windowIndex = windowIndex;
            this.mediaPeriodId = mediaPeriodId;
            this.eventPlaybackPositionMs = eventPlaybackPositionMs;
            this.currentPlaybackPositionMs = currentPlaybackPositionMs;
            this.totalBufferedDurationMs = totalBufferedDurationMs;
        }
    }
}
