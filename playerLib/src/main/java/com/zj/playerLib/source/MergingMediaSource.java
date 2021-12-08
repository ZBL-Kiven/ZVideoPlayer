package com.zj.playerLib.source;

import androidx.annotation.Nullable;
import com.zj.playerLib.InlinePlayer;
import com.zj.playerLib.Timeline;
import com.zj.playerLib.upstream.Allocator;
import com.zj.playerLib.upstream.TransferListener;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public final class MergingMediaSource extends CompositeMediaSource<Integer> {
    private static final int PERIOD_COUNT_UNSET = -1;
    private final MediaSource[] mediaSources;
    private final Timeline[] timelines;
    private final ArrayList<MediaSource> pendingTimelineSources;
    private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
    private Object primaryManifest;
    private int periodCount;
    private IllegalMergeException mergeError;

    public MergingMediaSource(MediaSource... mediaSources) {
        this(new DefaultCompositeSequenceableLoaderFactory(), mediaSources);
    }

    public MergingMediaSource(CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory, MediaSource... mediaSources) {
        this.mediaSources = mediaSources;
        this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
        this.pendingTimelineSources = new ArrayList(Arrays.asList(mediaSources));
        this.periodCount = -1;
        this.timelines = new Timeline[mediaSources.length];
    }

    @Nullable
    public Object getTag() {
        return this.mediaSources.length > 0 ? this.mediaSources[0].getTag() : null;
    }

    public void prepareSourceInternal(InlinePlayer player, boolean isTopLevelSource, @Nullable TransferListener mediaTransferListener) {
        super.prepareSourceInternal(player, isTopLevelSource, mediaTransferListener);

        for(int i = 0; i < this.mediaSources.length; ++i) {
            this.prepareChildSource(i, this.mediaSources[i]);
        }

    }

    public void maybeThrowSourceInfoRefreshError() throws IOException {
        if (this.mergeError != null) {
            throw this.mergeError;
        } else {
            super.maybeThrowSourceInfoRefreshError();
        }
    }

    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
        MediaPeriod[] periods = new MediaPeriod[this.mediaSources.length];
        int periodIndex = this.timelines[0].getIndexOfPeriod(id.periodUid);

        for(int i = 0; i < periods.length; ++i) {
            MediaPeriodId childMediaPeriodId = id.copyWithPeriodUid(this.timelines[i].getUidOfPeriod(periodIndex));
            periods[i] = this.mediaSources[i].createPeriod(childMediaPeriodId, allocator);
        }

        return new MergingMediaPeriod(this.compositeSequenceableLoaderFactory, periods);
    }

    public void releasePeriod(MediaPeriod mediaPeriod) {
        MergingMediaPeriod mergingPeriod = (MergingMediaPeriod)mediaPeriod;

        for(int i = 0; i < this.mediaSources.length; ++i) {
            this.mediaSources[i].releasePeriod(mergingPeriod.periods[i]);
        }

    }

    public void releaseSourceInternal() {
        super.releaseSourceInternal();
        Arrays.fill(this.timelines, null);
        this.primaryManifest = null;
        this.periodCount = -1;
        this.mergeError = null;
        this.pendingTimelineSources.clear();
        Collections.addAll(this.pendingTimelineSources, this.mediaSources);
    }

    protected void onChildSourceInfoRefreshed(Integer id, MediaSource mediaSource, Timeline timeline, @Nullable Object manifest) {
        if (this.mergeError == null) {
            this.mergeError = this.checkTimelineMerges(timeline);
        }

        if (this.mergeError == null) {
            this.pendingTimelineSources.remove(mediaSource);
            this.timelines[id] = timeline;
            if (mediaSource == this.mediaSources[0]) {
                this.primaryManifest = manifest;
            }

            if (this.pendingTimelineSources.isEmpty()) {
                this.refreshSourceInfo(this.timelines[0], this.primaryManifest);
            }

        }
    }

    @Nullable
    protected MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(Integer id, MediaPeriodId mediaPeriodId) {
        return id == 0 ? mediaPeriodId : null;
    }

    private IllegalMergeException checkTimelineMerges(Timeline timeline) {
        if (this.periodCount == -1) {
            this.periodCount = timeline.getPeriodCount();
        } else if (timeline.getPeriodCount() != this.periodCount) {
            return new IllegalMergeException(0);
        }

        return null;
    }

    public static final class IllegalMergeException extends IOException {
        public static final int REASON_PERIOD_COUNT_MISMATCH = 0;
        public final int reason;

        public IllegalMergeException(int reason) {
            this.reason = reason;
        }

        @Documented
        @Retention(RetentionPolicy.SOURCE)
        public @interface Reason {
        }
    }
}
