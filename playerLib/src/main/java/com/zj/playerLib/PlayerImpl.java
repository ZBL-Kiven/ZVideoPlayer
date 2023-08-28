package com.zj.playerLib;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.zj.playerLib.PlayerMessage.Target;
import com.zj.playerLib.Timeline.Period;
import com.zj.playerLib.source.MediaSource;
import com.zj.playerLib.source.MediaSource.MediaPeriodId;
import com.zj.playerLib.source.TrackGroupArray;
import com.zj.playerLib.trackselection.TrackSelection;
import com.zj.playerLib.trackselection.TrackSelectionArray;
import com.zj.playerLib.trackselection.TrackSelector;
import com.zj.playerLib.trackselection.TrackSelectorResult;
import com.zj.playerLib.upstream.BandwidthMeter;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Clock;
import com.zj.playerLib.util.Log;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

final class PlayerImpl extends BasePlayer implements InlinePlayer {
    final TrackSelectorResult emptyTrackSelectorResult;
    private final Renderer[] renderers;
    private final TrackSelector trackSelector;
    private final Handler eventHandler;
    private final PlayerImplInternal internalPlayer;
    private final Handler internalPlayerHandler;
    private final CopyOnWriteArraySet<EventListener> listeners;
    private final Period period;
    private final ArrayDeque<PlaybackInfoUpdate> pendingPlaybackInfoUpdates;
    private MediaSource mediaSource;
    private boolean playWhenReady;
    private boolean internalPlayWhenReady;
    private int repeatMode;
    private boolean shuffleModeEnabled;
    private int pendingOperationAcks;
    private boolean hasPendingPrepare;
    private boolean hasPendingSeek;
    private PlaybackParameters playbackParameters;
    private SeekParameters seekParameters;
    @Nullable
    private PlaybackException playbackError;
    private PlaybackInfo playbackInfo;
    private int maskingWindowIndex;
    private int maskingPeriodIndex;
    private long maskingWindowPositionMs;

    @SuppressLint({"HandlerLeak"})
    public PlayerImpl(Renderer[] renderers, TrackSelector trackSelector, LoadControl loadControl, BandwidthMeter bandwidthMeter, Clock clock, Looper looper) {
        Assertions.checkState(renderers.length > 0);
        this.renderers = Assertions.checkNotNull(renderers);
        this.trackSelector = Assertions.checkNotNull(trackSelector);
        this.playWhenReady = false;
        this.repeatMode = 0;
        this.shuffleModeEnabled = false;
        this.listeners = new CopyOnWriteArraySet<>();
        this.emptyTrackSelectorResult = new TrackSelectorResult(new RendererConfiguration[renderers.length], new TrackSelection[renderers.length], null);
        this.period = new Period();
        this.playbackParameters = PlaybackParameters.DEFAULT;
        this.seekParameters = SeekParameters.DEFAULT;
        this.eventHandler = new Handler(looper) {
            public void handleMessage(Message msg) {
                PlayerImpl.this.handleEvent(msg);
            }
        };
        this.playbackInfo = PlaybackInfo.createDummy(0L, this.emptyTrackSelectorResult);
        this.pendingPlaybackInfoUpdates = new ArrayDeque<>();
        this.internalPlayer = new PlayerImplInternal(renderers, trackSelector, this.emptyTrackSelectorResult, loadControl, bandwidthMeter, this.playWhenReady, this.repeatMode, this.shuffleModeEnabled, this.eventHandler, this, clock);
        this.internalPlayerHandler = new Handler(this.internalPlayer.getPlaybackLooper());
    }

    @Nullable
    public AudioComponent getAudioComponent() {
        return null;
    }

    @Nullable
    public VideoComponent getVideoComponent() {
        return null;
    }

    @Nullable
    public TextComponent getTextComponent() {
        return null;
    }

    @Nullable
    public MetadataComponent getMetadataComponent() {
        return null;
    }

    public Looper getPlaybackLooper() {
        return this.internalPlayer.getPlaybackLooper();
    }

    public Looper getApplicationLooper() {
        return this.eventHandler.getLooper();
    }

    public void addListener(EventListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(EventListener listener) {
        this.listeners.remove(listener);
    }

    public int getPlaybackState() {
        return this.playbackInfo.playbackState;
    }

    @Nullable
    public PlaybackException getPlaybackError() {
        return this.playbackError;
    }

    public void retry() {
        if (this.mediaSource != null && (this.playbackError != null || this.playbackInfo.playbackState == 1)) {
            this.prepare(this.mediaSource, false, false);
        }

    }

    public void prepare(MediaSource mediaSource) {
        this.prepare(mediaSource, true, true);
    }

    public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
        this.playbackError = null;
        this.mediaSource = mediaSource;
        PlaybackInfo playbackInfo = this.getResetPlaybackInfo(resetPosition, resetState, 2);
        this.hasPendingPrepare = true;
        ++this.pendingOperationAcks;
        this.internalPlayer.prepare(mediaSource, resetPosition, resetState);
        this.updatePlaybackInfo(playbackInfo, false, 4, 1, false, false);
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        this.setPlayWhenReady(playWhenReady, false);
    }

    public void setPlayWhenReady(boolean playWhenReady, boolean suppressPlayback) {
        boolean internalPlayWhenReady = playWhenReady && !suppressPlayback;
        if (this.internalPlayWhenReady != internalPlayWhenReady) {
            this.internalPlayWhenReady = internalPlayWhenReady;
            this.internalPlayer.setPlayWhenReady(internalPlayWhenReady);
        }

        if (this.playWhenReady != playWhenReady) {
            this.playWhenReady = playWhenReady;
            this.updatePlaybackInfo(this.playbackInfo, false, 4, 1, false, true);
        }

    }

    public boolean getPlayWhenReady() {
        return this.playWhenReady;
    }

    public void setRepeatMode(int repeatMode) {
        if (this.repeatMode != repeatMode) {
            this.repeatMode = repeatMode;
            this.internalPlayer.setRepeatMode(repeatMode);
            for (EventListener listener : this.listeners) {
                listener.onRepeatModeChanged(repeatMode);
            }
        }

    }

    public int getRepeatMode() {
        return this.repeatMode;
    }

    public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
        if (this.shuffleModeEnabled != shuffleModeEnabled) {
            this.shuffleModeEnabled = shuffleModeEnabled;
            this.internalPlayer.setShuffleModeEnabled(shuffleModeEnabled);
            for (EventListener listener : this.listeners) {
                listener.onShuffleModeEnabledChanged(shuffleModeEnabled);
            }
        }

    }

    public boolean getShuffleModeEnabled() {
        return this.shuffleModeEnabled;
    }

    public boolean isLoading() {
        return this.playbackInfo.isLoading;
    }

    public void seekTo(int windowIndex, long positionMs) {
        Timeline timeline = this.playbackInfo.timeline;
        if (windowIndex >= 0 && (timeline.isEmpty() || windowIndex < timeline.getWindowCount())) {
            this.hasPendingSeek = true;
            ++this.pendingOperationAcks;
            if (this.isPlayingAd()) {
                Log.w("PlayerImpl", "seekTo ignored because an ad is playing");
                this.eventHandler.obtainMessage(0, 1, -1, this.playbackInfo).sendToTarget();
            } else {
                this.maskingWindowIndex = windowIndex;
                if (timeline.isEmpty()) {
                    this.maskingWindowPositionMs = positionMs == -Long.MAX_VALUE ? 0L : positionMs;
                    this.maskingPeriodIndex = 0;
                } else {
                    long windowPositionUs = positionMs == -Long.MAX_VALUE ? timeline.getWindow(windowIndex, this.window).getDefaultPositionUs() : C.msToUs(positionMs);
                    Pair<Object, Long> periodUidAndPosition = timeline.getPeriodPosition(this.window, this.period, windowIndex, windowPositionUs);
                    this.maskingWindowPositionMs = C.usToMs(windowPositionUs);
                    this.maskingPeriodIndex = timeline.getIndexOfPeriod(periodUidAndPosition.first);
                }
                this.internalPlayer.seekTo(timeline, windowIndex, C.msToUs(positionMs));
                for (EventListener listener : this.listeners) {
                    listener.onPositionDiscontinuity(1);
                }

            }
        } else {
            throw new IllegalSeekPositionException(timeline, windowIndex, positionMs);
        }
    }

    public void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters) {
        if (playbackParameters == null) {
            playbackParameters = PlaybackParameters.DEFAULT;
        }

        this.internalPlayer.setPlaybackParameters(playbackParameters);
    }

    public PlaybackParameters getPlaybackParameters() {
        return this.playbackParameters;
    }

    public void setSeekParameters(@Nullable SeekParameters seekParameters) {
        if (seekParameters == null) {
            seekParameters = SeekParameters.DEFAULT;
        }

        if (!this.seekParameters.equals(seekParameters)) {
            this.seekParameters = seekParameters;
            this.internalPlayer.setSeekParameters(seekParameters);
        }

    }

    public SeekParameters getSeekParameters() {
        return this.seekParameters;
    }

    public void stop(boolean reset) {
        if (reset) {
            this.playbackError = null;
            this.mediaSource = null;
        }

        PlaybackInfo playbackInfo = this.getResetPlaybackInfo(reset, reset, 1);
        ++this.pendingOperationAcks;
        this.internalPlayer.stop(reset);
        this.updatePlaybackInfo(playbackInfo, false, 4, 1, false, false);
    }

    public void release() {
        this.mediaSource = null;
        this.internalPlayer.release();
        this.eventHandler.removeCallbacksAndMessages(null);
    }

    public PlayerMessage createMessage(Target target) {
        return new PlayerMessage(this.internalPlayer, target, this.playbackInfo.timeline, this.getCurrentWindowIndex(), this.internalPlayerHandler);
    }

    public int getCurrentPeriodIndex() {
        return this.shouldMaskPosition() ? this.maskingPeriodIndex : this.playbackInfo.timeline.getIndexOfPeriod(this.playbackInfo.periodId.periodUid);
    }

    public int getCurrentWindowIndex() {
        return this.shouldMaskPosition() ? this.maskingWindowIndex : this.playbackInfo.timeline.getPeriodByUid(this.playbackInfo.periodId.periodUid, this.period).windowIndex;
    }

    public long getDuration() {
        if (this.isPlayingAd()) {
            MediaPeriodId periodId = this.playbackInfo.periodId;
            this.playbackInfo.timeline.getPeriodByUid(periodId.periodUid, this.period);
            long adDurationUs = this.period.getAdDurationUs(periodId.adGroupIndex, periodId.adIndexInAdGroup);
            return C.usToMs(adDurationUs);
        } else {
            return this.getContentDuration();
        }
    }

    public long getCurrentPosition() {
        if (this.shouldMaskPosition()) {
            return this.maskingWindowPositionMs;
        } else {
            return this.playbackInfo.periodId.isAd() ? C.usToMs(this.playbackInfo.positionUs) : this.periodPositionUsToWindowPositionMs(this.playbackInfo.periodId, this.playbackInfo.positionUs);
        }
    }

    public long getBufferedPosition() {
        if (this.isPlayingAd()) {
            return this.playbackInfo.loadingMediaPeriodId.equals(this.playbackInfo.periodId) ? C.usToMs(this.playbackInfo.bufferedPositionUs) : this.getDuration();
        } else {
            return this.getContentBufferedPosition();
        }
    }

    public long getTotalBufferedDuration() {
        return Math.max(0L, C.usToMs(this.playbackInfo.totalBufferedDurationUs));
    }

    public boolean isPlayingAd() {
        return !this.shouldMaskPosition() && this.playbackInfo.periodId.isAd();
    }

    public int getCurrentAdGroupIndex() {
        return this.isPlayingAd() ? this.playbackInfo.periodId.adGroupIndex : -1;
    }

    public int getCurrentAdIndexInAdGroup() {
        return this.isPlayingAd() ? this.playbackInfo.periodId.adIndexInAdGroup : -1;
    }

    public long getContentPosition() {
        if (this.isPlayingAd()) {
            this.playbackInfo.timeline.getPeriodByUid(this.playbackInfo.periodId.periodUid, this.period);
            return this.period.getPositionInWindowMs() + C.usToMs(this.playbackInfo.contentPositionUs);
        } else {
            return this.getCurrentPosition();
        }
    }

    public long getContentBufferedPosition() {
        if (this.shouldMaskPosition()) {
            return this.maskingWindowPositionMs;
        } else if (this.playbackInfo.loadingMediaPeriodId.windowSequenceNumber != this.playbackInfo.periodId.windowSequenceNumber) {
            return this.playbackInfo.timeline.getWindow(this.getCurrentWindowIndex(), this.window).getDurationMs();
        } else {
            long contentBufferedPositionUs = this.playbackInfo.bufferedPositionUs;
            if (this.playbackInfo.loadingMediaPeriodId.isAd()) {
                Period loadingPeriod = this.playbackInfo.timeline.getPeriodByUid(this.playbackInfo.loadingMediaPeriodId.periodUid, this.period);
                contentBufferedPositionUs = loadingPeriod.getAdGroupTimeUs(this.playbackInfo.loadingMediaPeriodId.adGroupIndex);
                if (contentBufferedPositionUs == -9223372036854775808L) {
                    contentBufferedPositionUs = loadingPeriod.durationUs;
                }
            }

            return this.periodPositionUsToWindowPositionMs(this.playbackInfo.loadingMediaPeriodId, contentBufferedPositionUs);
        }
    }

    public int getRendererCount() {
        return this.renderers.length;
    }

    public int getRendererType(int index) {
        return this.renderers[index].getTrackType();
    }

    public TrackGroupArray getCurrentTrackGroups() {
        return this.playbackInfo.trackGroups;
    }

    public TrackSelectionArray getCurrentTrackSelections() {
        return this.playbackInfo.trackSelectorResult.selections;
    }

    public Timeline getCurrentTimeline() {
        return this.playbackInfo.timeline;
    }

    public Object getCurrentManifest() {
        return this.playbackInfo.manifest;
    }

    void handleEvent(Message msg) {
        switch (msg.what) {
            case 0:
                this.handlePlaybackInfo((PlaybackInfo) msg.obj, msg.arg1, msg.arg2 != -1, msg.arg2);
                break;
            case 1:
                PlaybackParameters playbackParameters = (PlaybackParameters) msg.obj;
                if (!this.playbackParameters.equals(playbackParameters)) {
                    this.playbackParameters = playbackParameters;
                    for (EventListener listener : this.listeners) {
                        listener.onPlaybackParametersChanged(playbackParameters);
                    }
                }
                break;
            case 2:
                PlaybackException playbackError = (PlaybackException) msg.obj;
                this.playbackError = playbackError;
                for (EventListener listener : this.listeners) {
                    listener.onPlayerError(playbackError);
                }

                return;
            default:
                throw new IllegalStateException();
        }

    }

    private void handlePlaybackInfo(PlaybackInfo playbackInfo, int operationAcks, boolean positionDiscontinuity, int positionDiscontinuityReason) {
        this.pendingOperationAcks -= operationAcks;
        if (this.pendingOperationAcks == 0) {
            if (playbackInfo.startPositionUs == -Long.MAX_VALUE) {
                playbackInfo = playbackInfo.resetToNewPosition(playbackInfo.periodId, 0L, playbackInfo.contentPositionUs);
            }

            if ((!this.playbackInfo.timeline.isEmpty() || this.hasPendingPrepare) && playbackInfo.timeline.isEmpty()) {
                this.maskingPeriodIndex = 0;
                this.maskingWindowIndex = 0;
                this.maskingWindowPositionMs = 0L;
            }

            int timelineChangeReason = this.hasPendingPrepare ? 0 : 2;
            boolean seekProcessed = this.hasPendingSeek;
            this.hasPendingPrepare = false;
            this.hasPendingSeek = false;
            this.updatePlaybackInfo(playbackInfo, positionDiscontinuity, positionDiscontinuityReason, timelineChangeReason, seekProcessed, false);
        }

    }

    private PlaybackInfo getResetPlaybackInfo(boolean resetPosition, boolean resetState, int playbackState) {
        if (resetPosition) {
            this.maskingWindowIndex = 0;
            this.maskingPeriodIndex = 0;
            this.maskingWindowPositionMs = 0L;
        } else {
            this.maskingWindowIndex = this.getCurrentWindowIndex();
            this.maskingPeriodIndex = this.getCurrentPeriodIndex();
            this.maskingWindowPositionMs = this.getCurrentPosition();
        }

        MediaPeriodId mediaPeriodId = resetPosition ? this.playbackInfo.getDummyFirstMediaPeriodId(this.shuffleModeEnabled, this.window) : this.playbackInfo.periodId;
        long startPositionUs = resetPosition ? 0L : this.playbackInfo.positionUs;
        long contentPositionUs = resetPosition ? -Long.MAX_VALUE : this.playbackInfo.contentPositionUs;
        return new PlaybackInfo(resetState ? Timeline.EMPTY : this.playbackInfo.timeline, resetState ? null : this.playbackInfo.manifest, mediaPeriodId, startPositionUs, contentPositionUs, playbackState, false, resetState ? TrackGroupArray.EMPTY : this.playbackInfo.trackGroups, resetState ? this.emptyTrackSelectorResult : this.playbackInfo.trackSelectorResult, mediaPeriodId, startPositionUs, 0L, startPositionUs);
    }

    private void updatePlaybackInfo(PlaybackInfo playbackInfo, boolean positionDiscontinuity, int positionDiscontinuityReason, int timelineChangeReason, boolean seekProcessed, boolean playWhenReadyChanged) {
        boolean isRunningRecursiveListenerNotification = !this.pendingPlaybackInfoUpdates.isEmpty();
        this.pendingPlaybackInfoUpdates.addLast(new PlaybackInfoUpdate(playbackInfo, this.playbackInfo, this.listeners, this.trackSelector, positionDiscontinuity, positionDiscontinuityReason, timelineChangeReason, seekProcessed, this.playWhenReady, playWhenReadyChanged));
        this.playbackInfo = playbackInfo;
        if (!isRunningRecursiveListenerNotification) {
            while (!this.pendingPlaybackInfoUpdates.isEmpty()) {
                PlaybackInfoUpdate info = this.pendingPlaybackInfoUpdates.peekFirst();
                if (info != null) info.notifyListeners();
                this.pendingPlaybackInfoUpdates.removeFirst();
            }

        }
    }

    private long periodPositionUsToWindowPositionMs(MediaPeriodId periodId, long positionUs) {
        long positionMs = C.usToMs(positionUs);
        this.playbackInfo.timeline.getPeriodByUid(periodId.periodUid, this.period);
        positionMs += this.period.getPositionInWindowMs();
        return positionMs;
    }

    private boolean shouldMaskPosition() {
        return this.playbackInfo.timeline.isEmpty() || this.pendingOperationAcks > 0;
    }

    private static final class PlaybackInfoUpdate {
        private final PlaybackInfo playbackInfo;
        private final Set<EventListener> listeners;
        private final TrackSelector trackSelector;
        private final boolean positionDiscontinuity;
        private final int positionDiscontinuityReason;
        private final int timelineChangeReason;
        private final boolean seekProcessed;
        private final boolean playWhenReady;
        private final boolean playbackStateOrPlayWhenReadyChanged;
        private final boolean timelineOrManifestChanged;
        private final boolean isLoadingChanged;
        private final boolean trackSelectorResultChanged;

        public PlaybackInfoUpdate(PlaybackInfo playbackInfo, PlaybackInfo previousPlaybackInfo, Set<EventListener> listeners, TrackSelector trackSelector, boolean positionDiscontinuity, int positionDiscontinuityReason, int timelineChangeReason, boolean seekProcessed, boolean playWhenReady, boolean playWhenReadyChanged) {
            this.playbackInfo = playbackInfo;
            this.listeners = listeners;
            this.trackSelector = trackSelector;
            this.positionDiscontinuity = positionDiscontinuity;
            this.positionDiscontinuityReason = positionDiscontinuityReason;
            this.timelineChangeReason = timelineChangeReason;
            this.seekProcessed = seekProcessed;
            this.playWhenReady = playWhenReady;
            this.playbackStateOrPlayWhenReadyChanged = playWhenReadyChanged || previousPlaybackInfo.playbackState != playbackInfo.playbackState;
            this.timelineOrManifestChanged = previousPlaybackInfo.timeline != playbackInfo.timeline || previousPlaybackInfo.manifest != playbackInfo.manifest;
            this.isLoadingChanged = previousPlaybackInfo.isLoading != playbackInfo.isLoading;
            this.trackSelectorResultChanged = previousPlaybackInfo.trackSelectorResult != playbackInfo.trackSelectorResult;
        }

        public void notifyListeners() {
            Iterator<?> var1;
            EventListener listener;
            if (this.timelineOrManifestChanged || this.timelineChangeReason == 0) {
                var1 = this.listeners.iterator();

                while (var1.hasNext()) {
                    listener = (EventListener) var1.next();
                    listener.onTimelineChanged(this.playbackInfo.timeline, this.playbackInfo.manifest, this.timelineChangeReason);
                }
            }

            if (this.positionDiscontinuity) {
                var1 = this.listeners.iterator();

                while (var1.hasNext()) {
                    listener = (EventListener) var1.next();
                    listener.onPositionDiscontinuity(this.positionDiscontinuityReason);
                }
            }

            if (this.trackSelectorResultChanged) {
                this.trackSelector.onSelectionActivated(this.playbackInfo.trackSelectorResult.info);
                var1 = this.listeners.iterator();

                while (var1.hasNext()) {
                    listener = (EventListener) var1.next();
                    listener.onTracksChanged(this.playbackInfo.trackGroups, this.playbackInfo.trackSelectorResult.selections);
                }
            }

            if (this.isLoadingChanged) {
                var1 = this.listeners.iterator();

                while (var1.hasNext()) {
                    listener = (EventListener) var1.next();
                    listener.onLoadingChanged(this.playbackInfo.isLoading);
                }
            }

            if (this.playbackStateOrPlayWhenReadyChanged) {
                var1 = this.listeners.iterator();

                while (var1.hasNext()) {
                    listener = (EventListener) var1.next();
                    listener.onPlayerStateChanged(this.playWhenReady, this.playbackInfo.playbackState);
                }
            }

            if (this.seekProcessed) {
                var1 = this.listeners.iterator();

                while (var1.hasNext()) {
                    listener = (EventListener) var1.next();
                    listener.onSeekProcessed();
                }
            }

        }
    }
}
