package com.zj.playerLib.analytics;

import android.view.Surface;

import androidx.annotation.Nullable;

import com.zj.playerLib.Format;
import com.zj.playerLib.PlaybackException;
import com.zj.playerLib.PlaybackParameters;
import com.zj.playerLib.Player;
import com.zj.playerLib.Player.EventListener;
import com.zj.playerLib.Timeline;
import com.zj.playerLib.Timeline.Period;
import com.zj.playerLib.Timeline.Window;
import com.zj.playerLib.analytics.PlayerStateListener.EventTime;
import com.zj.playerLib.audio.AudioAttributes;
import com.zj.playerLib.audio.AudioListener;
import com.zj.playerLib.audio.AudioRendererEventListener;
import com.zj.playerLib.decoder.DecoderCounters;
import com.zj.playerLib.drm.DefaultDrmSessionEventListener;
import com.zj.playerLib.metadata.Metadata;
import com.zj.playerLib.metadata.MetadataOutput;
import com.zj.playerLib.source.MediaSource.MediaPeriodId;
import com.zj.playerLib.source.MediaSourceEventListener;
import com.zj.playerLib.source.TrackGroupArray;
import com.zj.playerLib.trackselection.TrackSelectionArray;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Clock;
import com.zj.playerLib.video.VideoListener;
import com.zj.playerLib.video.VideoRendererEventListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class PlayerStateCollector implements EventListener, MetadataOutput, AudioRendererEventListener, VideoRendererEventListener, MediaSourceEventListener, com.zj.playerLib.upstream.BandwidthMeter.EventListener, DefaultDrmSessionEventListener, VideoListener, AudioListener {
    private final CopyOnWriteArraySet<PlayerStateListener> listeners;
    private final Clock clock;
    private final Window window;
    private final MediaPeriodQueueTracker mediaPeriodQueueTracker;

    private Player player;

    protected PlayerStateCollector(@Nullable Player player, Clock clock) {
        if (player != null) {
            this.player = player;
        }
        this.clock = Assertions.checkNotNull(clock);
        this.listeners = new CopyOnWriteArraySet<>();
        this.mediaPeriodQueueTracker = new MediaPeriodQueueTracker();
        this.window = new Window();
    }

    public void addListener(PlayerStateListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(PlayerStateListener listener) {
        this.listeners.remove(listener);
    }

    public void setPlayer(Player player) {
        Assertions.checkState(this.player == null);
        this.player = Assertions.checkNotNull(player);
    }

    public final void notifySeekStarted() {
        if (!this.mediaPeriodQueueTracker.isSeeking()) {
            EventTime eventTime = this.generatePlayingMediaPeriodEventTime();
            this.mediaPeriodQueueTracker.onSeekStarted();
            for (PlayerStateListener listener : this.listeners) {
                listener.onSeekStarted(eventTime);
            }
        }
    }

    public final void resetForNewMediaSource() {
        List<MediaPeriodInfo> mediaPeriodInfos = new ArrayList<>(this.mediaPeriodQueueTracker.mediaPeriodInfoQueue);
        for (MediaPeriodInfo mediaPeriodInfo : mediaPeriodInfos) {
            this.onMediaPeriodReleased(mediaPeriodInfo.windowIndex, mediaPeriodInfo.mediaPeriodId);
        }
    }

    public final void onMetadata(Metadata metadata) {
        EventTime eventTime = this.generatePlayingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onMetadata(eventTime, metadata);
        }

    }

    public final void onAudioEnabled(DecoderCounters counters) {
        EventTime eventTime = this.generatePlayingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onDecoderEnabled(eventTime, 1, counters);
        }

    }

    public final void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
        EventTime eventTime = this.generateReadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onDecoderInitialized(eventTime, 1, decoderName, initializationDurationMs);
        }

    }

    public final void onAudioInputFormatChanged(Format format) {
        EventTime eventTime = this.generateReadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onDecoderInputFormatChanged(eventTime, 1, format);
        }

    }

    public final void onAudioSinkUnderRun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
        EventTime eventTime = this.generateReadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onAudioUnderrun(eventTime, bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
        }

    }

    public final void onAudioDisabled(DecoderCounters counters) {
        EventTime eventTime = this.generateLastReportedPlayingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onDecoderDisabled(eventTime, 1, counters);
        }

    }

    public final void onAudioSessionId(int audioSessionId) {
        EventTime eventTime = this.generateReadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onAudioSessionId(eventTime, audioSessionId);
        }

    }

    public void onAudioAttributesChanged(AudioAttributes audioAttributes) {
        EventTime eventTime = this.generateReadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onAudioAttributesChanged(eventTime, audioAttributes);
        }

    }

    public void onVolumeChanged(float audioVolume) {
        EventTime eventTime = this.generateReadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onVolumeChanged(eventTime, audioVolume);
        }

    }

    public final void onVideoEnabled(DecoderCounters counters) {
        EventTime eventTime = this.generatePlayingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onDecoderEnabled(eventTime, 2, counters);
        }

    }

    public final void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
        EventTime eventTime = this.generateReadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onDecoderInitialized(eventTime, 2, decoderName, initializationDurationMs);
        }

    }

    public final void onVideoInputFormatChanged(Format format) {
        EventTime eventTime = this.generateReadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onDecoderInputFormatChanged(eventTime, 2, format);
        }

    }

    public final void onDroppedFrames(int count, long elapsedMs) {
        EventTime eventTime = this.generateLastReportedPlayingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onDroppedVideoFrames(eventTime, count, elapsedMs);
        }

    }

    public final void onVideoDisabled(DecoderCounters counters) {
        EventTime eventTime = this.generateLastReportedPlayingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onDecoderDisabled(eventTime, 2, counters);
        }

    }

    public final void onRenderedFirstFrame(@Nullable Surface surface) {
        EventTime eventTime = this.generateReadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onRenderedFirstFrame(eventTime, surface);
        }

    }

    public final void onRenderedFirstFrame() {
    }

    public final void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        EventTime eventTime = this.generateReadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onVideoSizeChanged(eventTime, width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
        }

    }

    public void onSurfaceSizeChanged(int width, int height) {
        EventTime eventTime = this.generateReadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onSurfaceSizeChanged(eventTime, width, height);
        }

    }

    public final void onMediaPeriodCreated(int windowIndex, MediaPeriodId mediaPeriodId) {
        this.mediaPeriodQueueTracker.onMediaPeriodCreated(windowIndex, mediaPeriodId);
        EventTime eventTime = this.generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
        for (PlayerStateListener listener : this.listeners) {
            listener.onMediaPeriodCreated(eventTime);
        }

    }

    public final void onMediaPeriodReleased(int windowIndex, MediaPeriodId mediaPeriodId) {
        EventTime eventTime = this.generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
        if (this.mediaPeriodQueueTracker.onMediaPeriodReleased(mediaPeriodId)) {
            for (PlayerStateListener listener : this.listeners) {
                listener.onMediaPeriodReleased(eventTime);
            }
        }

    }

    public final void onLoadStarted(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
        EventTime eventTime = this.generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
        for (PlayerStateListener listener : this.listeners) {
            listener.onLoadStarted(eventTime, loadEventInfo, mediaLoadData);
        }

    }

    public final void onLoadCompleted(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
        EventTime eventTime = this.generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
        for (PlayerStateListener listener : this.listeners) {
            listener.onLoadCompleted(eventTime, loadEventInfo, mediaLoadData);
        }

    }

    public final void onLoadCanceled(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
        EventTime eventTime = this.generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
        for (PlayerStateListener listener : this.listeners) {
            listener.onLoadCanceled(eventTime, loadEventInfo, mediaLoadData);
        }

    }

    public final void onLoadError(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
        EventTime eventTime = this.generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
        for (PlayerStateListener listener : this.listeners) {
            listener.onLoadError(eventTime, loadEventInfo, mediaLoadData, error, wasCanceled);
        }

    }

    public final void onReadingStarted(int windowIndex, MediaPeriodId mediaPeriodId) {
        this.mediaPeriodQueueTracker.onReadingStarted(mediaPeriodId);
        EventTime eventTime = this.generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
        for (PlayerStateListener listener : this.listeners) {
            listener.onReadingStarted(eventTime);
        }

    }

    public final void onUpstreamDiscarded(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
        EventTime eventTime = this.generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
        for (PlayerStateListener listener : this.listeners) {
            listener.onUpstreamDiscarded(eventTime, mediaLoadData);
        }

    }

    public final void onDownstreamFormatChanged(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
        EventTime eventTime = this.generateMediaPeriodEventTime(windowIndex, mediaPeriodId);
        for (PlayerStateListener listener : this.listeners) {
            listener.onDownstreamFormatChanged(eventTime, mediaLoadData);
        }

    }

    public final void onTimelineChanged(Timeline timeline, @Nullable Object manifest, int reason) {
        this.mediaPeriodQueueTracker.onTimelineChanged(timeline);
        EventTime eventTime = this.generatePlayingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onTimelineChanged(eventTime, reason);
        }

    }

    public final void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        EventTime eventTime = this.generatePlayingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onTracksChanged(eventTime, trackGroups, trackSelections);
        }

    }

    public final void onLoadingChanged(boolean isLoading) {
        EventTime eventTime = this.generatePlayingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onLoadingChanged(eventTime, isLoading);
        }

    }

    public final void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        EventTime eventTime = this.generatePlayingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onPlayerStateChanged(eventTime, playWhenReady, playbackState);
        }

    }

    public final void onRepeatModeChanged(int repeatMode) {
        EventTime eventTime = this.generatePlayingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onRepeatModeChanged(eventTime, repeatMode);
        }

    }

    public final void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
        EventTime eventTime = this.generatePlayingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onShuffleModeChanged(eventTime, shuffleModeEnabled);
        }

    }

    public final void onPlayerError(PlaybackException error) {
        EventTime eventTime = this.generatePlayingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onPlayerError(eventTime, error);
        }

    }

    public final void onPositionDiscontinuity(int reason) {
        this.mediaPeriodQueueTracker.onPositionDiscontinuity(reason);
        EventTime eventTime = this.generatePlayingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onPositionDiscontinuity(eventTime, reason);
        }

    }

    public final void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        EventTime eventTime = this.generatePlayingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onPlaybackParametersChanged(eventTime, playbackParameters);
        }

    }

    public final void onSeekProcessed() {
        if (this.mediaPeriodQueueTracker.isSeeking()) {
            this.mediaPeriodQueueTracker.onSeekProcessed();
            EventTime eventTime = this.generatePlayingMediaPeriodEventTime();
            for (PlayerStateListener listener : this.listeners) {
                listener.onSeekProcessed(eventTime);
            }
        }

    }

    public final void onBandwidthSample(int elapsedMs, long bytes, long bitrate) {
        EventTime eventTime = this.generateLoadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onBandwidthEstimate(eventTime, elapsedMs, bytes, bitrate);
        }

    }

    public final void onDrmSessionAcquired() {
        EventTime eventTime = this.generateReadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onDrmSessionAcquired(eventTime);
        }

    }

    public final void onDrmKeysLoaded() {
        EventTime eventTime = this.generateReadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onDrmKeysLoaded(eventTime);
        }

    }

    public final void onDrmSessionManagerError(Exception error) {
        EventTime eventTime = this.generateReadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onDrmSessionManagerError(eventTime, error);
        }

    }

    public final void onDrmKeysRestored() {
        EventTime eventTime = this.generateReadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onDrmKeysRestored(eventTime);
        }

    }

    public final void onDrmKeysRemoved() {
        EventTime eventTime = this.generateReadingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onDrmKeysRemoved(eventTime);
        }

    }

    public final void onDrmSessionReleased() {
        EventTime eventTime = this.generateLastReportedPlayingMediaPeriodEventTime();
        for (PlayerStateListener listener : this.listeners) {
            listener.onDrmSessionReleased(eventTime);
        }

    }

    protected Set<PlayerStateListener> getListeners() {
        return Collections.unmodifiableSet(this.listeners);
    }


    protected EventTime generateEventTime(Timeline timeline, int windowIndex, @Nullable MediaPeriodId mediaPeriodId) {
        if (timeline.isEmpty()) {
            mediaPeriodId = null;
        }

        long realtimeMs = this.clock.elapsedRealtime();
        boolean isInCurrentWindow = timeline == this.player.getCurrentTimeline() && windowIndex == this.player.getCurrentWindowIndex();
        long eventPositionMs;
        if (mediaPeriodId != null && mediaPeriodId.isAd()) {
            boolean isCurrentAd = isInCurrentWindow && this.player.getCurrentAdGroupIndex() == mediaPeriodId.adGroupIndex && this.player.getCurrentAdIndexInAdGroup() == mediaPeriodId.adIndexInAdGroup;
            eventPositionMs = isCurrentAd ? this.player.getCurrentPosition() : 0L;
        } else if (isInCurrentWindow) {
            eventPositionMs = this.player.getContentPosition();
        } else {
            eventPositionMs = timeline.isEmpty() ? 0L : timeline.getWindow(windowIndex, this.window).getDefaultPositionMs();
        }

        return new EventTime(realtimeMs, timeline, windowIndex, mediaPeriodId, eventPositionMs, this.player.getCurrentPosition(), this.player.getTotalBufferedDuration());
    }

    private EventTime generateEventTime(@Nullable PlayerStateCollector.MediaPeriodInfo mediaPeriodInfo) {
        Assertions.checkNotNull(this.player);
        if (mediaPeriodInfo == null) {
            int windowIndex = this.player.getCurrentWindowIndex();
            mediaPeriodInfo = this.mediaPeriodQueueTracker.tryResolveWindowIndex(windowIndex);
            if (mediaPeriodInfo == null) {
                Timeline timeline = this.player.getCurrentTimeline();
                boolean windowIsInTimeline = windowIndex < timeline.getWindowCount();
                return this.generateEventTime(windowIsInTimeline ? timeline : Timeline.EMPTY, windowIndex, null);
            }
        }

        return this.generateEventTime(mediaPeriodInfo.timeline, mediaPeriodInfo.windowIndex, mediaPeriodInfo.mediaPeriodId);
    }

    private EventTime generateLastReportedPlayingMediaPeriodEventTime() {
        return this.generateEventTime(this.mediaPeriodQueueTracker.getLastReportedPlayingMediaPeriod());
    }

    private EventTime generatePlayingMediaPeriodEventTime() {
        return this.generateEventTime(this.mediaPeriodQueueTracker.getPlayingMediaPeriod());
    }

    private EventTime generateReadingMediaPeriodEventTime() {
        return this.generateEventTime(this.mediaPeriodQueueTracker.getReadingMediaPeriod());
    }

    private EventTime generateLoadingMediaPeriodEventTime() {
        return this.generateEventTime(this.mediaPeriodQueueTracker.getLoadingMediaPeriod());
    }

    private EventTime generateMediaPeriodEventTime(int windowIndex, @Nullable MediaPeriodId mediaPeriodId) {
        Assertions.checkNotNull(this.player);
        if (mediaPeriodId != null) {
            MediaPeriodInfo mediaPeriodInfo = this.mediaPeriodQueueTracker.getMediaPeriodInfo(mediaPeriodId);
            return mediaPeriodInfo != null ? this.generateEventTime(mediaPeriodInfo) : this.generateEventTime(Timeline.EMPTY, windowIndex, mediaPeriodId);
        } else {
            Timeline timeline = this.player.getCurrentTimeline();
            boolean windowIsInTimeline = windowIndex < timeline.getWindowCount();
            return this.generateEventTime(windowIsInTimeline ? timeline : Timeline.EMPTY, windowIndex, null);
        }
    }

    private static final class MediaPeriodInfo {
        public final MediaPeriodId mediaPeriodId;
        public final Timeline timeline;
        public final int windowIndex;

        public MediaPeriodInfo(MediaPeriodId mediaPeriodId, Timeline timeline, int windowIndex) {
            this.mediaPeriodId = mediaPeriodId;
            this.timeline = timeline;
            this.windowIndex = windowIndex;
        }
    }

    @SuppressWarnings("unused")
    private static final class MediaPeriodQueueTracker {
        private final ArrayList<MediaPeriodInfo> mediaPeriodInfoQueue = new ArrayList<>();
        private final HashMap<MediaPeriodId, MediaPeriodInfo> mediaPeriodIdToInfo = new HashMap<>();
        private final Period period = new Period();
        @Nullable
        private PlayerStateCollector.MediaPeriodInfo lastReportedPlayingMediaPeriod;
        @Nullable
        private PlayerStateCollector.MediaPeriodInfo readingMediaPeriod;
        private Timeline timeline;
        private boolean isSeeking;

        public MediaPeriodQueueTracker() {
            this.timeline = Timeline.EMPTY;
        }

        @Nullable
        public PlayerStateCollector.MediaPeriodInfo getPlayingMediaPeriod() {
            return !this.mediaPeriodInfoQueue.isEmpty() && !this.timeline.isEmpty() && !this.isSeeking ? this.mediaPeriodInfoQueue.get(0) : null;
        }

        @Nullable
        public PlayerStateCollector.MediaPeriodInfo getLastReportedPlayingMediaPeriod() {
            return this.lastReportedPlayingMediaPeriod;
        }

        @Nullable
        public PlayerStateCollector.MediaPeriodInfo getReadingMediaPeriod() {
            return this.readingMediaPeriod;
        }

        @Nullable
        public PlayerStateCollector.MediaPeriodInfo getLoadingMediaPeriod() {
            return this.mediaPeriodInfoQueue.isEmpty() ? null : this.mediaPeriodInfoQueue.get(this.mediaPeriodInfoQueue.size() - 1);
        }

        @Nullable
        public PlayerStateCollector.MediaPeriodInfo getMediaPeriodInfo(MediaPeriodId mediaPeriodId) {
            return this.mediaPeriodIdToInfo.get(mediaPeriodId);
        }

        public boolean isSeeking() {
            return this.isSeeking;
        }

        @Nullable
        public PlayerStateCollector.MediaPeriodInfo tryResolveWindowIndex(int windowIndex) {
            MediaPeriodInfo match = null;

            for (int i = 0; i < this.mediaPeriodInfoQueue.size(); ++i) {
                MediaPeriodInfo info = this.mediaPeriodInfoQueue.get(i);
                int periodIndex = this.timeline.getIndexOfPeriod(info.mediaPeriodId.periodUid);
                if (periodIndex != -1 && this.timeline.getPeriod(periodIndex, this.period).windowIndex == windowIndex) {
                    if (match != null) {
                        return null;
                    }

                    match = info;
                }
            }

            return match;
        }

        public void onPositionDiscontinuity(int reason) {
            this.updateLastReportedPlayingMediaPeriod();
        }

        public void onTimelineChanged(Timeline timeline) {
            for (int i = 0; i < this.mediaPeriodInfoQueue.size(); ++i) {
                MediaPeriodInfo newMediaPeriodInfo = this.updateMediaPeriodInfoToNewTimeline(this.mediaPeriodInfoQueue.get(i), timeline);
                this.mediaPeriodInfoQueue.set(i, newMediaPeriodInfo);
                this.mediaPeriodIdToInfo.put(newMediaPeriodInfo.mediaPeriodId, newMediaPeriodInfo);
            }

            if (this.readingMediaPeriod != null) {
                this.readingMediaPeriod = this.updateMediaPeriodInfoToNewTimeline(this.readingMediaPeriod, timeline);
            }

            this.timeline = timeline;
            this.updateLastReportedPlayingMediaPeriod();
        }

        public void onSeekStarted() {
            this.isSeeking = true;
        }

        public void onSeekProcessed() {
            this.isSeeking = false;
            this.updateLastReportedPlayingMediaPeriod();
        }

        public void onMediaPeriodCreated(int windowIndex, MediaPeriodId mediaPeriodId) {
            boolean isInTimeline = this.timeline.getIndexOfPeriod(mediaPeriodId.periodUid) != -1;
            MediaPeriodInfo mediaPeriodInfo = new MediaPeriodInfo(mediaPeriodId, isInTimeline ? this.timeline : Timeline.EMPTY, windowIndex);
            this.mediaPeriodInfoQueue.add(mediaPeriodInfo);
            this.mediaPeriodIdToInfo.put(mediaPeriodId, mediaPeriodInfo);
            if (this.mediaPeriodInfoQueue.size() == 1 && !this.timeline.isEmpty()) {
                this.updateLastReportedPlayingMediaPeriod();
            }

        }

        public boolean onMediaPeriodReleased(MediaPeriodId mediaPeriodId) {
            MediaPeriodInfo mediaPeriodInfo = this.mediaPeriodIdToInfo.remove(mediaPeriodId);
            if (mediaPeriodInfo == null) {
                return false;
            } else {
                this.mediaPeriodInfoQueue.remove(mediaPeriodInfo);
                if (this.readingMediaPeriod != null && mediaPeriodId.equals(this.readingMediaPeriod.mediaPeriodId)) {
                    this.readingMediaPeriod = this.mediaPeriodInfoQueue.isEmpty() ? null : this.mediaPeriodInfoQueue.get(0);
                }

                return true;
            }
        }

        public void onReadingStarted(MediaPeriodId mediaPeriodId) {
            this.readingMediaPeriod = this.mediaPeriodIdToInfo.get(mediaPeriodId);
        }

        private void updateLastReportedPlayingMediaPeriod() {
            if (!this.mediaPeriodInfoQueue.isEmpty()) {
                this.lastReportedPlayingMediaPeriod = this.mediaPeriodInfoQueue.get(0);
            }

        }

        private MediaPeriodInfo updateMediaPeriodInfoToNewTimeline(MediaPeriodInfo info, Timeline newTimeline) {
            int newPeriodIndex = newTimeline.getIndexOfPeriod(info.mediaPeriodId.periodUid);
            if (newPeriodIndex == -1) {
                return info;
            } else {
                int newWindowIndex = newTimeline.getPeriod(newPeriodIndex, this.period).windowIndex;
                return new MediaPeriodInfo(info.mediaPeriodId, newTimeline, newWindowIndex);
            }
        }
    }

    public static class Factory {
        public Factory() {
        }

        public PlayerStateCollector createAnalyticsCollector(@Nullable Player player, Clock clock) {
            return new PlayerStateCollector(player, clock);
        }
    }
}
