//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib;

import android.util.Pair;

import androidx.annotation.Nullable;

import com.zj.playerLib.Timeline.Period;
import com.zj.playerLib.Timeline.Window;
import com.zj.playerLib.source.MediaPeriod;
import com.zj.playerLib.source.MediaSource;
import com.zj.playerLib.source.MediaSource.MediaPeriodId;
import com.zj.playerLib.trackselection.TrackSelector;
import com.zj.playerLib.upstream.Allocator;
import com.zj.playerLib.util.Assertions;

final class MediaPeriodQueue {
    private static final int MAXIMUM_BUFFER_AHEAD_PERIODS = 100;
    private final Period period = new Period();
    private final Window window = new Window();
    private long nextWindowSequenceNumber;
    private Timeline timeline;
    private int repeatMode;
    private boolean shuffleModeEnabled;
    @Nullable
    private MediaPeriodHolder playing;
    @Nullable
    private MediaPeriodHolder reading;
    @Nullable
    private MediaPeriodHolder loading;
    private int length;
    @Nullable
    private Object oldFrontPeriodUid;
    private long oldFrontPeriodWindowSequenceNumber;

    public MediaPeriodQueue() {
        this.timeline = Timeline.EMPTY;
    }

    public void setTimeline(Timeline timeline) {
        this.timeline = timeline;
    }

    public boolean updateRepeatMode(int repeatMode) {
        this.repeatMode = repeatMode;
        return this.updateForPlaybackModeChange();
    }

    public boolean updateShuffleModeEnabled(boolean shuffleModeEnabled) {
        this.shuffleModeEnabled = shuffleModeEnabled;
        return this.updateForPlaybackModeChange();
    }

    public boolean isLoading(MediaPeriod mediaPeriod) {
        return this.loading != null && this.loading.mediaPeriod == mediaPeriod;
    }

    public void reevaluateBuffer(long rendererPositionUs) {
        if (this.loading != null) {
            this.loading.reevaluateBuffer(rendererPositionUs);
        }

    }

    public boolean shouldLoadNextMediaPeriod() {
        return this.loading == null || !this.loading.info.isFinal && this.loading.isFullyBuffered() && this.loading.info.durationUs != -9223372036854775807L && this.length < 100;
    }

    @Nullable
    public MediaPeriodInfo getNextMediaPeriodInfo(long rendererPositionUs, PlaybackInfo playbackInfo) {
        return this.loading == null ? this.getFirstMediaPeriodInfo(playbackInfo) : this.getFollowingMediaPeriodInfo(this.loading, rendererPositionUs);
    }

    public MediaPeriod enqueueNextMediaPeriod(RendererCapabilities[] rendererCapabilities, TrackSelector trackSelector, Allocator allocator, MediaSource mediaSource, MediaPeriodInfo info) {
        long rendererPositionOffsetUs = this.loading == null ? info.startPositionUs : this.loading.getRendererOffset() + this.loading.info.durationUs;
        MediaPeriodHolder newPeriodHolder = new MediaPeriodHolder(rendererCapabilities, rendererPositionOffsetUs, trackSelector, allocator, mediaSource, info);
        if (this.loading != null) {
            Assertions.checkState(this.hasPlayingPeriod());
            this.loading.next = newPeriodHolder;
        }

        this.oldFrontPeriodUid = null;
        this.loading = newPeriodHolder;
        ++this.length;
        return newPeriodHolder.mediaPeriod;
    }

    public MediaPeriodHolder getLoadingPeriod() {
        return this.loading;
    }

    public MediaPeriodHolder getPlayingPeriod() {
        return this.playing;
    }

    public MediaPeriodHolder getReadingPeriod() {
        return this.reading;
    }

    public MediaPeriodHolder getFrontPeriod() {
        return this.hasPlayingPeriod() ? this.playing : this.loading;
    }

    public boolean hasPlayingPeriod() {
        return this.playing != null;
    }

    public MediaPeriodHolder advanceReadingPeriod() {
        Assertions.checkState(this.reading != null && this.reading.next != null);
        this.reading = this.reading.next;
        return this.reading;
    }

    public MediaPeriodHolder advancePlayingPeriod() {
        if (this.playing != null) {
            if (this.playing == this.reading) {
                this.reading = this.playing.next;
            }

            this.playing.release();
            --this.length;
            if (this.length == 0) {
                this.loading = null;
                this.oldFrontPeriodUid = this.playing.uid;
                this.oldFrontPeriodWindowSequenceNumber = this.playing.info.id.windowSequenceNumber;
            }

            this.playing = this.playing.next;
        } else {
            this.playing = this.loading;
            this.reading = this.loading;
        }

        return this.playing;
    }

    public boolean removeAfter(MediaPeriodHolder mediaPeriodHolder) {
        Assertions.checkState(mediaPeriodHolder != null);
        boolean removedReading = false;

        for(this.loading = mediaPeriodHolder; mediaPeriodHolder.next != null; --this.length) {
            mediaPeriodHolder = mediaPeriodHolder.next;
            if (mediaPeriodHolder == this.reading) {
                this.reading = this.playing;
                removedReading = true;
            }

            mediaPeriodHolder.release();
        }

        this.loading.next = null;
        return removedReading;
    }

    public void clear(boolean keepFrontPeriodUid) {
        MediaPeriodHolder front = this.getFrontPeriod();
        if (front != null) {
            this.oldFrontPeriodUid = keepFrontPeriodUid ? front.uid : null;
            this.oldFrontPeriodWindowSequenceNumber = front.info.id.windowSequenceNumber;
            front.release();
            this.removeAfter(front);
        } else if (!keepFrontPeriodUid) {
            this.oldFrontPeriodUid = null;
        }

        this.playing = null;
        this.loading = null;
        this.reading = null;
        this.length = 0;
    }

    public boolean updateQueuedPeriods(MediaPeriodId playingPeriodId, long rendererPositionUs) {
        int periodIndex = this.timeline.getIndexOfPeriod(playingPeriodId.periodUid);
        MediaPeriodHolder previousPeriodHolder = null;

        for(MediaPeriodHolder periodHolder = this.getFrontPeriod(); periodHolder != null; periodHolder = periodHolder.next) {
            if (previousPeriodHolder == null) {
                periodHolder.info = this.getUpdatedMediaPeriodInfo(periodHolder.info);
            } else {
                if (periodIndex == -1 || !periodHolder.uid.equals(this.timeline.getUidOfPeriod(periodIndex))) {
                    return !this.removeAfter(previousPeriodHolder);
                }

                MediaPeriodInfo periodInfo = this.getFollowingMediaPeriodInfo(previousPeriodHolder, rendererPositionUs);
                if (periodInfo == null) {
                    return !this.removeAfter(previousPeriodHolder);
                }

                periodHolder.info = this.getUpdatedMediaPeriodInfo(periodHolder.info);
                if (!this.canKeepMediaPeriodHolder(periodHolder, periodInfo)) {
                    return !this.removeAfter(previousPeriodHolder);
                }
            }

            if (periodHolder.info.isLastInTimelinePeriod) {
                periodIndex = this.timeline.getNextPeriodIndex(periodIndex, this.period, this.window, this.repeatMode, this.shuffleModeEnabled);
            }

            previousPeriodHolder = periodHolder;
        }

        return true;
    }

    public MediaPeriodInfo getUpdatedMediaPeriodInfo(MediaPeriodInfo info) {
        boolean isLastInPeriod = this.isLastInPeriod(info.id);
        boolean isLastInTimeline = this.isLastInTimeline(info.id, isLastInPeriod);
        this.timeline.getPeriodByUid(info.id.periodUid, this.period);
        long durationUs = info.id.isAd() ? this.period.getAdDurationUs(info.id.adGroupIndex, info.id.adIndexInAdGroup) : (info.id.endPositionUs == -9223372036854775808L ? this.period.getDurationUs() : info.id.endPositionUs);
        return new MediaPeriodInfo(info.id, info.startPositionUs, info.contentPositionUs, durationUs, isLastInPeriod, isLastInTimeline);
    }

    public MediaPeriodId resolveMediaPeriodIdForAds(Object periodUid, long positionUs) {
        long windowSequenceNumber = this.resolvePeriodIndexToWindowSequenceNumber(periodUid);
        return this.resolveMediaPeriodIdForAds(periodUid, positionUs, windowSequenceNumber);
    }

    private MediaPeriodId resolveMediaPeriodIdForAds(Object periodUid, long positionUs, long windowSequenceNumber) {
        this.timeline.getPeriodByUid(periodUid, this.period);
        int adGroupIndex = this.period.getAdGroupIndexForPositionUs(positionUs);
        int adIndexInAdGroup;
        if (adGroupIndex == -1) {
            adIndexInAdGroup = this.period.getAdGroupIndexAfterPositionUs(positionUs);
            long endPositionUs = adIndexInAdGroup == -1 ? -9223372036854775808L : this.period.getAdGroupTimeUs(adIndexInAdGroup);
            return new MediaPeriodId(periodUid, windowSequenceNumber, endPositionUs);
        } else {
            adIndexInAdGroup = this.period.getFirstAdIndexToPlay(adGroupIndex);
            return new MediaPeriodId(periodUid, adGroupIndex, adIndexInAdGroup, windowSequenceNumber);
        }
    }

    private long resolvePeriodIndexToWindowSequenceNumber(Object periodUid) {
        int windowIndex = this.timeline.getPeriodByUid(periodUid, this.period).windowIndex;
        int indexOfHolderInTimeline;
        if (this.oldFrontPeriodUid != null) {
            int oldFrontPeriodIndex = this.timeline.getIndexOfPeriod(this.oldFrontPeriodUid);
            if (oldFrontPeriodIndex != -1) {
                indexOfHolderInTimeline = this.timeline.getPeriod(oldFrontPeriodIndex, this.period).windowIndex;
                if (indexOfHolderInTimeline == windowIndex) {
                    return this.oldFrontPeriodWindowSequenceNumber;
                }
            }
        }

        MediaPeriodHolder mediaPeriodHolder;
        for(mediaPeriodHolder = this.getFrontPeriod(); mediaPeriodHolder != null; mediaPeriodHolder = mediaPeriodHolder.next) {
            if (mediaPeriodHolder.uid.equals(periodUid)) {
                return mediaPeriodHolder.info.id.windowSequenceNumber;
            }
        }

        for(mediaPeriodHolder = this.getFrontPeriod(); mediaPeriodHolder != null; mediaPeriodHolder = mediaPeriodHolder.next) {
            indexOfHolderInTimeline = this.timeline.getIndexOfPeriod(mediaPeriodHolder.uid);
            if (indexOfHolderInTimeline != -1) {
                int holderWindowIndex = this.timeline.getPeriod(indexOfHolderInTimeline, this.period).windowIndex;
                if (holderWindowIndex == windowIndex) {
                    return mediaPeriodHolder.info.id.windowSequenceNumber;
                }
            }
        }

        return (long)(this.nextWindowSequenceNumber++);
    }

    private boolean canKeepMediaPeriodHolder(MediaPeriodHolder periodHolder, MediaPeriodInfo info) {
        MediaPeriodInfo periodHolderInfo = periodHolder.info;
        return periodHolderInfo.startPositionUs == info.startPositionUs && periodHolderInfo.id.equals(info.id);
    }

    private boolean updateForPlaybackModeChange() {
        MediaPeriodHolder lastValidPeriodHolder = this.getFrontPeriod();
        if (lastValidPeriodHolder == null) {
            return true;
        } else {
            int currentPeriodIndex = this.timeline.getIndexOfPeriod(lastValidPeriodHolder.uid);

            while(true) {
                int nextPeriodIndex;
                for(nextPeriodIndex = this.timeline.getNextPeriodIndex(currentPeriodIndex, this.period, this.window, this.repeatMode, this.shuffleModeEnabled); lastValidPeriodHolder.next != null && !lastValidPeriodHolder.info.isLastInTimelinePeriod; lastValidPeriodHolder = lastValidPeriodHolder.next) {
                }

                if (nextPeriodIndex == -1 || lastValidPeriodHolder.next == null) {
                    break;
                }

                int nextPeriodHolderPeriodIndex = this.timeline.getIndexOfPeriod(lastValidPeriodHolder.next.uid);
                if (nextPeriodHolderPeriodIndex != nextPeriodIndex) {
                    break;
                }

                lastValidPeriodHolder = lastValidPeriodHolder.next;
                currentPeriodIndex = nextPeriodIndex;
            }

            boolean readingPeriodRemoved = this.removeAfter(lastValidPeriodHolder);
            lastValidPeriodHolder.info = this.getUpdatedMediaPeriodInfo(lastValidPeriodHolder.info);
            return !readingPeriodRemoved || !this.hasPlayingPeriod();
        }
    }

    private MediaPeriodInfo getFirstMediaPeriodInfo(PlaybackInfo playbackInfo) {
        return this.getMediaPeriodInfo(playbackInfo.periodId, playbackInfo.contentPositionUs, playbackInfo.startPositionUs);
    }

    @Nullable
    private MediaPeriodInfo getFollowingMediaPeriodInfo(MediaPeriodHolder mediaPeriodHolder, long rendererPositionUs) {
        MediaPeriodInfo mediaPeriodInfo = mediaPeriodHolder.info;
        long bufferedDurationUs = mediaPeriodHolder.getRendererOffset() + mediaPeriodInfo.durationUs - rendererPositionUs;
        int adGroupCount;
        if (mediaPeriodInfo.isLastInTimelinePeriod) {
            int currentPeriodIndex = this.timeline.getIndexOfPeriod(mediaPeriodInfo.id.periodUid);
            adGroupCount = this.timeline.getNextPeriodIndex(currentPeriodIndex, this.period, this.window, this.repeatMode, this.shuffleModeEnabled);
            if (adGroupCount == -1) {
                return null;
            } else {
                int nextWindowIndex = this.timeline.getPeriod(adGroupCount, this.period, true).windowIndex;
                Object nextPeriodUid = this.period.uid;
                long windowSequenceNumber = mediaPeriodInfo.id.windowSequenceNumber;
                long startPositionUs;
                if (this.timeline.getWindow(nextWindowIndex, this.window).firstPeriodIndex == adGroupCount) {
                    Pair<Object, Long> defaultPosition = this.timeline.getPeriodPosition(this.window, this.period, nextWindowIndex, -9223372036854775807L, Math.max(0L, bufferedDurationUs));
                    if (defaultPosition == null) {
                        return null;
                    }

                    nextPeriodUid = defaultPosition.first;
                    startPositionUs = (Long)defaultPosition.second;
                    if (mediaPeriodHolder.next != null && mediaPeriodHolder.next.uid.equals(nextPeriodUid)) {
                        windowSequenceNumber = mediaPeriodHolder.next.info.id.windowSequenceNumber;
                    } else {
                        windowSequenceNumber = (long)(this.nextWindowSequenceNumber++);
                    }
                } else {
                    startPositionUs = 0L;
                }

                MediaPeriodId periodId = this.resolveMediaPeriodIdForAds(nextPeriodUid, startPositionUs, windowSequenceNumber);
                return this.getMediaPeriodInfo(periodId, startPositionUs, startPositionUs);
            }
        } else {
            MediaPeriodId currentPeriodId = mediaPeriodInfo.id;
            this.timeline.getPeriodByUid(currentPeriodId.periodUid, this.period);
            int adGroupIndex;
            int adIndexInAdGroup;
            long contentDurationUs;
            if (currentPeriodId.isAd()) {
                adGroupCount = currentPeriodId.adGroupIndex;
                adGroupIndex = this.period.getAdCountInAdGroup(adGroupCount);
                if (adGroupIndex == -1) {
                    return null;
                } else {
                    adIndexInAdGroup = this.period.getNextAdIndexToPlay(adGroupCount, currentPeriodId.adIndexInAdGroup);
                    if (adIndexInAdGroup < adGroupIndex) {
                        return !this.period.isAdAvailable(adGroupCount, adIndexInAdGroup) ? null : this.getMediaPeriodInfoForAd(currentPeriodId.periodUid, adGroupCount, adIndexInAdGroup, mediaPeriodInfo.contentPositionUs, currentPeriodId.windowSequenceNumber);
                    } else {
                        contentDurationUs = mediaPeriodInfo.contentPositionUs;
                        if (this.period.getAdGroupCount() == 1 && this.period.getAdGroupTimeUs(0) == 0L) {
                            Pair<Object, Long> defaultPosition = this.timeline.getPeriodPosition(this.window, this.period, this.period.windowIndex, -9223372036854775807L, Math.max(0L, bufferedDurationUs));
                            if (defaultPosition == null) {
                                return null;
                            }

                            contentDurationUs = (Long)defaultPosition.second;
                        }

                        return this.getMediaPeriodInfoForContent(currentPeriodId.periodUid, contentDurationUs, currentPeriodId.windowSequenceNumber);
                    }
                }
            } else if (mediaPeriodInfo.id.endPositionUs != -9223372036854775808L) {
                adGroupCount = this.period.getAdGroupIndexForPositionUs(mediaPeriodInfo.id.endPositionUs);
                if (adGroupCount == -1) {
                    return this.getMediaPeriodInfoForContent(currentPeriodId.periodUid, mediaPeriodInfo.id.endPositionUs, currentPeriodId.windowSequenceNumber);
                } else {
                    adGroupIndex = this.period.getFirstAdIndexToPlay(adGroupCount);
                    return !this.period.isAdAvailable(adGroupCount, adGroupIndex) ? null : this.getMediaPeriodInfoForAd(currentPeriodId.periodUid, adGroupCount, adGroupIndex, mediaPeriodInfo.id.endPositionUs, currentPeriodId.windowSequenceNumber);
                }
            } else {
                adGroupCount = this.period.getAdGroupCount();
                if (adGroupCount == 0) {
                    return null;
                } else {
                    adGroupIndex = adGroupCount - 1;
                    if (this.period.getAdGroupTimeUs(adGroupIndex) == -9223372036854775808L && !this.period.hasPlayedAdGroup(adGroupIndex)) {
                        adIndexInAdGroup = this.period.getFirstAdIndexToPlay(adGroupIndex);
                        if (!this.period.isAdAvailable(adGroupIndex, adIndexInAdGroup)) {
                            return null;
                        } else {
                            contentDurationUs = this.period.getDurationUs();
                            return this.getMediaPeriodInfoForAd(currentPeriodId.periodUid, adGroupIndex, adIndexInAdGroup, contentDurationUs, currentPeriodId.windowSequenceNumber);
                        }
                    } else {
                        return null;
                    }
                }
            }
        }
    }

    private MediaPeriodInfo getMediaPeriodInfo(MediaPeriodId id, long contentPositionUs, long startPositionUs) {
        this.timeline.getPeriodByUid(id.periodUid, this.period);
        if (id.isAd()) {
            return !this.period.isAdAvailable(id.adGroupIndex, id.adIndexInAdGroup) ? null : this.getMediaPeriodInfoForAd(id.periodUid, id.adGroupIndex, id.adIndexInAdGroup, contentPositionUs, id.windowSequenceNumber);
        } else {
            return this.getMediaPeriodInfoForContent(id.periodUid, startPositionUs, id.windowSequenceNumber);
        }
    }

    private MediaPeriodInfo getMediaPeriodInfoForAd(Object periodUid, int adGroupIndex, int adIndexInAdGroup, long contentPositionUs, long windowSequenceNumber) {
        MediaPeriodId id = new MediaPeriodId(periodUid, adGroupIndex, adIndexInAdGroup, windowSequenceNumber);
        boolean isLastInPeriod = this.isLastInPeriod(id);
        boolean isLastInTimeline = this.isLastInTimeline(id, isLastInPeriod);
        long durationUs = this.timeline.getPeriodByUid(id.periodUid, this.period).getAdDurationUs(id.adGroupIndex, id.adIndexInAdGroup);
        long startPositionUs = adIndexInAdGroup == this.period.getFirstAdIndexToPlay(adGroupIndex) ? this.period.getAdResumePositionUs() : 0L;
        return new MediaPeriodInfo(id, startPositionUs, contentPositionUs, durationUs, isLastInPeriod, isLastInTimeline);
    }

    private MediaPeriodInfo getMediaPeriodInfoForContent(Object periodUid, long startPositionUs, long windowSequenceNumber) {
        int nextAdGroupIndex = this.period.getAdGroupIndexAfterPositionUs(startPositionUs);
        long endPositionUs = nextAdGroupIndex == -1 ? -9223372036854775808L : this.period.getAdGroupTimeUs(nextAdGroupIndex);
        MediaPeriodId id = new MediaPeriodId(periodUid, windowSequenceNumber, endPositionUs);
        this.timeline.getPeriodByUid(id.periodUid, this.period);
        boolean isLastInPeriod = this.isLastInPeriod(id);
        boolean isLastInTimeline = this.isLastInTimeline(id, isLastInPeriod);
        long durationUs = endPositionUs == -9223372036854775808L ? this.period.getDurationUs() : endPositionUs;
        return new MediaPeriodInfo(id, startPositionUs, -9223372036854775807L, durationUs, isLastInPeriod, isLastInTimeline);
    }

    private boolean isLastInPeriod(MediaPeriodId id) {
        int adGroupCount = this.timeline.getPeriodByUid(id.periodUid, this.period).getAdGroupCount();
        if (adGroupCount == 0) {
            return true;
        } else {
            int lastAdGroupIndex = adGroupCount - 1;
            boolean isAd = id.isAd();
            if (this.period.getAdGroupTimeUs(lastAdGroupIndex) != -9223372036854775808L) {
                return !isAd && id.endPositionUs == -9223372036854775808L;
            } else {
                int postrollAdCount = this.period.getAdCountInAdGroup(lastAdGroupIndex);
                if (postrollAdCount == -1) {
                    return false;
                } else {
                    boolean isLastAd = isAd && id.adGroupIndex == lastAdGroupIndex && id.adIndexInAdGroup == postrollAdCount - 1;
                    return isLastAd || !isAd && this.period.getFirstAdIndexToPlay(lastAdGroupIndex) == postrollAdCount;
                }
            }
        }
    }

    private boolean isLastInTimeline(MediaPeriodId id, boolean isLastMediaPeriodInPeriod) {
        int periodIndex = this.timeline.getIndexOfPeriod(id.periodUid);
        int windowIndex = this.timeline.getPeriod(periodIndex, this.period).windowIndex;
        return !this.timeline.getWindow(windowIndex, this.window).isDynamic && this.timeline.isLastPeriod(periodIndex, this.period, this.window, this.repeatMode, this.shuffleModeEnabled) && isLastMediaPeriodInPeriod;
    }
}
