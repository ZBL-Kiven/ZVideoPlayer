//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib;

import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;

import com.zj.playerLib.Timeline.Window;
import com.zj.playerLib.source.MediaSource.MediaPeriodId;
import com.zj.playerLib.source.TrackGroupArray;
import com.zj.playerLib.trackselection.TrackSelectorResult;

final class PlaybackInfo {
    private static final MediaPeriodId DUMMY_MEDIA_PERIOD_ID = new MediaPeriodId(new Object());
    public final Timeline timeline;
    @Nullable
    public final Object manifest;
    public final MediaPeriodId periodId;
    public final long startPositionUs;
    public final long contentPositionUs;
    public final int playbackState;
    public final boolean isLoading;
    public final TrackGroupArray trackGroups;
    public final TrackSelectorResult trackSelectorResult;
    public final MediaPeriodId loadingMediaPeriodId;
    public volatile long bufferedPositionUs;
    public volatile long totalBufferedDurationUs;
    public volatile long positionUs;

    public static PlaybackInfo createDummy(long startPositionUs, TrackSelectorResult emptyTrackSelectorResult) {
        return new PlaybackInfo(Timeline.EMPTY, (Object)null, DUMMY_MEDIA_PERIOD_ID, startPositionUs, -9223372036854775807L, 1, false, TrackGroupArray.EMPTY, emptyTrackSelectorResult, DUMMY_MEDIA_PERIOD_ID, startPositionUs, 0L, startPositionUs);
    }

    public PlaybackInfo(Timeline timeline, @Nullable Object manifest, MediaPeriodId periodId, long startPositionUs, long contentPositionUs, int playbackState, boolean isLoading, TrackGroupArray trackGroups, TrackSelectorResult trackSelectorResult, MediaPeriodId loadingMediaPeriodId, long bufferedPositionUs, long totalBufferedDurationUs, long positionUs) {
        this.timeline = timeline;
        this.manifest = manifest;
        this.periodId = periodId;
        this.startPositionUs = startPositionUs;
        this.contentPositionUs = contentPositionUs;
        this.playbackState = playbackState;
        this.isLoading = isLoading;
        this.trackGroups = trackGroups;
        this.trackSelectorResult = trackSelectorResult;
        this.loadingMediaPeriodId = loadingMediaPeriodId;
        this.bufferedPositionUs = bufferedPositionUs;
        this.totalBufferedDurationUs = totalBufferedDurationUs;
        this.positionUs = positionUs;
    }

    public MediaPeriodId getDummyFirstMediaPeriodId(boolean shuffleModeEnabled, Window window) {
        if (this.timeline.isEmpty()) {
            return DUMMY_MEDIA_PERIOD_ID;
        } else {
            int firstPeriodIndex = this.timeline.getWindow(this.timeline.getFirstWindowIndex(shuffleModeEnabled), window).firstPeriodIndex;
            return new MediaPeriodId(this.timeline.getUidOfPeriod(firstPeriodIndex));
        }
    }

    @CheckResult
    public PlaybackInfo resetToNewPosition(MediaPeriodId periodId, long startPositionUs, long contentPositionUs) {
        return new PlaybackInfo(this.timeline, this.manifest, periodId, startPositionUs, periodId.isAd() ? contentPositionUs : -9223372036854775807L, this.playbackState, this.isLoading, this.trackGroups, this.trackSelectorResult, periodId, startPositionUs, 0L, startPositionUs);
    }

    @CheckResult
    public PlaybackInfo copyWithNewPosition(MediaPeriodId periodId, long positionUs, long contentPositionUs, long totalBufferedDurationUs) {
        return new PlaybackInfo(this.timeline, this.manifest, periodId, positionUs, periodId.isAd() ? contentPositionUs : -9223372036854775807L, this.playbackState, this.isLoading, this.trackGroups, this.trackSelectorResult, this.loadingMediaPeriodId, this.bufferedPositionUs, totalBufferedDurationUs, positionUs);
    }

    @CheckResult
    public PlaybackInfo copyWithTimeline(Timeline timeline, Object manifest) {
        return new PlaybackInfo(timeline, manifest, this.periodId, this.startPositionUs, this.contentPositionUs, this.playbackState, this.isLoading, this.trackGroups, this.trackSelectorResult, this.loadingMediaPeriodId, this.bufferedPositionUs, this.totalBufferedDurationUs, this.positionUs);
    }

    @CheckResult
    public PlaybackInfo copyWithPlaybackState(int playbackState) {
        return new PlaybackInfo(this.timeline, this.manifest, this.periodId, this.startPositionUs, this.contentPositionUs, playbackState, this.isLoading, this.trackGroups, this.trackSelectorResult, this.loadingMediaPeriodId, this.bufferedPositionUs, this.totalBufferedDurationUs, this.positionUs);
    }

    @CheckResult
    public PlaybackInfo copyWithIsLoading(boolean isLoading) {
        return new PlaybackInfo(this.timeline, this.manifest, this.periodId, this.startPositionUs, this.contentPositionUs, this.playbackState, isLoading, this.trackGroups, this.trackSelectorResult, this.loadingMediaPeriodId, this.bufferedPositionUs, this.totalBufferedDurationUs, this.positionUs);
    }

    @CheckResult
    public PlaybackInfo copyWithTrackInfo(TrackGroupArray trackGroups, TrackSelectorResult trackSelectorResult) {
        return new PlaybackInfo(this.timeline, this.manifest, this.periodId, this.startPositionUs, this.contentPositionUs, this.playbackState, this.isLoading, trackGroups, trackSelectorResult, this.loadingMediaPeriodId, this.bufferedPositionUs, this.totalBufferedDurationUs, this.positionUs);
    }

    @CheckResult
    public PlaybackInfo copyWithLoadingMediaPeriodId(MediaPeriodId loadingMediaPeriodId) {
        return new PlaybackInfo(this.timeline, this.manifest, this.periodId, this.startPositionUs, this.contentPositionUs, this.playbackState, this.isLoading, this.trackGroups, this.trackSelectorResult, loadingMediaPeriodId, this.bufferedPositionUs, this.totalBufferedDurationUs, this.positionUs);
    }
}
