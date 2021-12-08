package com.zj.playerLib.source;

import androidx.annotation.Nullable;

import com.zj.playerLib.InlinePlayer;
import com.zj.playerLib.Timeline;
import com.zj.playerLib.source.ShuffleOrder.UnshuffledShuffleOrder;
import com.zj.playerLib.upstream.Allocator;
import com.zj.playerLib.upstream.TransferListener;
import com.zj.playerLib.util.Assertions;

import java.util.HashMap;
import java.util.Map;

public final class LoopingMediaSource extends CompositeMediaSource<Void> {
    private final MediaSource childSource;
    private final int loopCount;
    private final Map<MediaPeriodId, MediaPeriodId> childMediaPeriodIdToMediaPeriodId;
    private final Map<MediaPeriod, MediaPeriodId> mediaPeriodToChildMediaPeriodId;

    public LoopingMediaSource(MediaSource childSource) {
        this(childSource, 2147483647);
    }

    public LoopingMediaSource(MediaSource childSource, int loopCount) {
        Assertions.checkArgument(loopCount > 0);
        this.childSource = childSource;
        this.loopCount = loopCount;
        this.childMediaPeriodIdToMediaPeriodId = new HashMap<>();
        this.mediaPeriodToChildMediaPeriodId = new HashMap<>();
    }

    @Nullable
    public Object getTag() {
        return this.childSource.getTag();
    }

    public void prepareSourceInternal(InlinePlayer player, boolean isTopLevelSource, @Nullable TransferListener mediaTransferListener) {
        super.prepareSourceInternal(player, isTopLevelSource, mediaTransferListener);
        this.prepareChildSource(null, this.childSource);
    }

    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
        if (this.loopCount == 2147483647) {
            return this.childSource.createPeriod(id, allocator);
        } else {
            Object childPeriodUid = LoopingTimeline.getChildPeriodUidFromConcatenatedUid(id.periodUid);
            MediaPeriodId childMediaPeriodId = id.copyWithPeriodUid(childPeriodUid);
            this.childMediaPeriodIdToMediaPeriodId.put(childMediaPeriodId, id);
            MediaPeriod mediaPeriod = this.childSource.createPeriod(childMediaPeriodId, allocator);
            this.mediaPeriodToChildMediaPeriodId.put(mediaPeriod, childMediaPeriodId);
            return mediaPeriod;
        }
    }

    public void releasePeriod(MediaPeriod mediaPeriod) {
        this.childSource.releasePeriod(mediaPeriod);
        MediaPeriodId childMediaPeriodId = this.mediaPeriodToChildMediaPeriodId.remove(mediaPeriod);
        if (childMediaPeriodId != null) {
            this.childMediaPeriodIdToMediaPeriodId.remove(childMediaPeriodId);
        }

    }

    protected void onChildSourceInfoRefreshed(Void id, MediaSource mediaSource, Timeline timeline, @Nullable Object manifest) {
        Timeline loopingTimeline = this.loopCount != 2147483647 ? new LoopingTimeline(timeline, this.loopCount) : new InfinitelyLoopingTimeline(timeline);
        this.refreshSourceInfo(loopingTimeline, manifest);
    }

    @Nullable
    protected MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(Void id, MediaPeriodId mediaPeriodId) {
        return this.loopCount != 2147483647 ? this.childMediaPeriodIdToMediaPeriodId.get(mediaPeriodId) : mediaPeriodId;
    }

    private static final class InfinitelyLoopingTimeline extends ForwardingTimeline {
        public InfinitelyLoopingTimeline(Timeline timeline) {
            super(timeline);
        }

        public int getNextWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
            int childNextWindowIndex = this.timeline.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled);
            return childNextWindowIndex == -1 ? this.getFirstWindowIndex(shuffleModeEnabled) : childNextWindowIndex;
        }

        public int getPreviousWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
            int childPreviousWindowIndex = this.timeline.getPreviousWindowIndex(windowIndex, repeatMode, shuffleModeEnabled);
            return childPreviousWindowIndex == -1 ? this.getLastWindowIndex(shuffleModeEnabled) : childPreviousWindowIndex;
        }
    }

    private static final class LoopingTimeline extends AbstractConcatenatedTimeline {
        private final Timeline childTimeline;
        private final int childPeriodCount;
        private final int childWindowCount;
        private final int loopCount;

        public LoopingTimeline(Timeline childTimeline, int loopCount) {
            super(false, new UnshuffledShuffleOrder(loopCount));
            this.childTimeline = childTimeline;
            this.childPeriodCount = childTimeline.getPeriodCount();
            this.childWindowCount = childTimeline.getWindowCount();
            this.loopCount = loopCount;
            if (this.childPeriodCount > 0) {
                Assertions.checkState(loopCount <= 2147483647 / this.childPeriodCount, "LoopingMediaSource contains too many periods");
            }

        }

        public int getWindowCount() {
            return this.childWindowCount * this.loopCount;
        }

        public int getPeriodCount() {
            return this.childPeriodCount * this.loopCount;
        }

        protected int getChildIndexByPeriodIndex(int periodIndex) {
            return periodIndex / this.childPeriodCount;
        }

        protected int getChildIndexByWindowIndex(int windowIndex) {
            return windowIndex / this.childWindowCount;
        }

        protected int getChildIndexByChildUid(Object childUid) {
            return !(childUid instanceof Integer) ? -1 : (Integer)childUid;
        }

        protected Timeline getTimelineByChildIndex(int childIndex) {
            return this.childTimeline;
        }

        protected int getFirstPeriodIndexByChildIndex(int childIndex) {
            return childIndex * this.childPeriodCount;
        }

        protected int getFirstWindowIndexByChildIndex(int childIndex) {
            return childIndex * this.childWindowCount;
        }

        protected Object getChildUidByChildIndex(int childIndex) {
            return childIndex;
        }
    }
}
