package com.zj.playerLib.source;

import android.os.Handler;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zj.playerLib.InlinePlayer;
import com.zj.playerLib.PlaybackException;
import com.zj.playerLib.PlayerMessage.Target;
import com.zj.playerLib.Timeline;
import com.zj.playerLib.Timeline.Period;
import com.zj.playerLib.Timeline.Window;
import com.zj.playerLib.source.ShuffleOrder.DefaultShuffleOrder;
import com.zj.playerLib.upstream.Allocator;
import com.zj.playerLib.upstream.TransferListener;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ConcatenatingMediaSource extends CompositeMediaSource<ConcatenatingMediaSource.MediaSourceHolder> implements Target {
    private static final int MSG_ADD = 0;
    private static final int MSG_REMOVE = 1;
    private static final int MSG_MOVE = 2;
    private static final int MSG_SET_SHUFFLE_ORDER = 3;
    private static final int MSG_NOTIFY_LISTENER = 4;
    private static final int MSG_ON_COMPLETION = 5;
    private final List<MediaSourceHolder> mediaSourcesPublic;
    private final List<MediaSourceHolder> mediaSourceHolders;
    private final Map<MediaPeriod, MediaSourceHolder> mediaSourceByMediaPeriod;
    private final Map<Object, MediaSourceHolder> mediaSourceByUid;
    private final List<Runnable> pendingOnCompletionActions;
    private final boolean isAtomic;
    private final boolean useLazyPreparation;
    private final Window window;
    private final Period period;
    @Nullable
    private InlinePlayer player;
    @Nullable
    private Handler playerApplicationHandler;
    private boolean listenerNotificationScheduled;
    private ShuffleOrder shuffleOrder;
    private int windowCount;
    private int periodCount;

    public ConcatenatingMediaSource(MediaSource... mediaSources) {
        this(false, mediaSources);
    }

    public ConcatenatingMediaSource(boolean isAtomic, MediaSource... mediaSources) {
        this(isAtomic, new DefaultShuffleOrder(0), mediaSources);
    }

    public ConcatenatingMediaSource(boolean isAtomic, ShuffleOrder shuffleOrder, MediaSource... mediaSources) {
        this(isAtomic, false, shuffleOrder, mediaSources);
    }

    public ConcatenatingMediaSource(boolean isAtomic, boolean useLazyPreparation, ShuffleOrder shuffleOrder, MediaSource... mediaSources) {
        MediaSource[] var5 = mediaSources;
        int var6 = mediaSources.length;

        for(int var7 = 0; var7 < var6; ++var7) {
            MediaSource mediaSource = var5[var7];
            Assertions.checkNotNull(mediaSource);
        }

        this.shuffleOrder = shuffleOrder.getLength() > 0 ? shuffleOrder.cloneAndClear() : shuffleOrder;
        this.mediaSourceByMediaPeriod = new IdentityHashMap();
        this.mediaSourceByUid = new HashMap();
        this.mediaSourcesPublic = new ArrayList();
        this.mediaSourceHolders = new ArrayList();
        this.pendingOnCompletionActions = new ArrayList();
        this.isAtomic = isAtomic;
        this.useLazyPreparation = useLazyPreparation;
        this.window = new Window();
        this.period = new Period();
        this.addMediaSources(Arrays.asList(mediaSources));
    }

    public final synchronized void addMediaSource(MediaSource mediaSource) {
        this.addMediaSource(this.mediaSourcesPublic.size(), mediaSource, null);
    }

    public final synchronized void addMediaSource(MediaSource mediaSource, @Nullable Runnable actionOnCompletion) {
        this.addMediaSource(this.mediaSourcesPublic.size(), mediaSource, actionOnCompletion);
    }

    public final synchronized void addMediaSource(int index, MediaSource mediaSource) {
        this.addMediaSource(index, mediaSource, null);
    }

    public final synchronized void addMediaSource(int index, MediaSource mediaSource, @Nullable Runnable actionOnCompletion) {
        this.addMediaSources(index, Collections.singletonList(mediaSource), actionOnCompletion);
    }

    public final synchronized void addMediaSources(Collection<MediaSource> mediaSources) {
        this.addMediaSources(this.mediaSourcesPublic.size(), mediaSources, null);
    }

    public final synchronized void addMediaSources(Collection<MediaSource> mediaSources, @Nullable Runnable actionOnCompletion) {
        this.addMediaSources(this.mediaSourcesPublic.size(), mediaSources, actionOnCompletion);
    }

    public final synchronized void addMediaSources(int index, Collection<MediaSource> mediaSources) {
        this.addMediaSources(index, mediaSources, null);
    }

    public final synchronized void addMediaSources(int index, Collection<MediaSource> mediaSources, @Nullable Runnable actionOnCompletion) {
        Iterator var4 = mediaSources.iterator();

        while(var4.hasNext()) {
            MediaSource mediaSource = (MediaSource)var4.next();
            Assertions.checkNotNull(mediaSource);
        }

        List<MediaSourceHolder> mediaSourceHolders = new ArrayList(mediaSources.size());
        Iterator var8 = mediaSources.iterator();

        while(var8.hasNext()) {
            MediaSource mediaSource = (MediaSource)var8.next();
            mediaSourceHolders.add(new MediaSourceHolder(mediaSource));
        }

        this.mediaSourcesPublic.addAll(index, mediaSourceHolders);
        if (this.player != null && !mediaSources.isEmpty()) {
            this.player.createMessage(this).setType(0).setPayload(new MessageData(index, mediaSourceHolders, actionOnCompletion)).send();
        } else if (actionOnCompletion != null) {
            actionOnCompletion.run();
        }

    }

    public final synchronized void removeMediaSource(int index) {
        this.removeMediaSource(index, null);
    }

    public final synchronized void removeMediaSource(int index, @Nullable Runnable actionOnCompletion) {
        this.removeMediaSourceRange(index, index + 1, actionOnCompletion);
    }

    public final synchronized void removeMediaSourceRange(int fromIndex, int toIndex) {
        this.removeMediaSourceRange(fromIndex, toIndex, null);
    }

    public final synchronized void removeMediaSourceRange(int fromIndex, int toIndex, @Nullable Runnable actionOnCompletion) {
        Util.removeRange(this.mediaSourcesPublic, fromIndex, toIndex);
        if (fromIndex == toIndex) {
            if (actionOnCompletion != null) {
                actionOnCompletion.run();
            }

        } else {
            if (this.player != null) {
                this.player.createMessage(this).setType(1).setPayload(new MessageData(fromIndex, toIndex, actionOnCompletion)).send();
            } else if (actionOnCompletion != null) {
                actionOnCompletion.run();
            }

        }
    }

    public final synchronized void moveMediaSource(int currentIndex, int newIndex) {
        this.moveMediaSource(currentIndex, newIndex, null);
    }

    public final synchronized void moveMediaSource(int currentIndex, int newIndex, @Nullable Runnable actionOnCompletion) {
        if (currentIndex == newIndex) {
            if (actionOnCompletion != null) {
                actionOnCompletion.run();
            }

        } else {
            this.mediaSourcesPublic.add(newIndex, this.mediaSourcesPublic.remove(currentIndex));
            if (this.player != null) {
                this.player.createMessage(this).setType(2).setPayload(new MessageData(currentIndex, newIndex, actionOnCompletion)).send();
            } else if (actionOnCompletion != null) {
                actionOnCompletion.run();
            }

        }
    }

    public final synchronized void clear() {
        this.clear(null);
    }

    public final synchronized void clear(@Nullable Runnable actionOnCompletion) {
        this.removeMediaSourceRange(0, this.getSize(), actionOnCompletion);
    }

    public final synchronized int getSize() {
        return this.mediaSourcesPublic.size();
    }

    public final synchronized MediaSource getMediaSource(int index) {
        return this.mediaSourcesPublic.get(index).mediaSource;
    }

    public final synchronized void setShuffleOrder(ShuffleOrder shuffleOrder) {
        this.setShuffleOrder(shuffleOrder, null);
    }

    public final synchronized void setShuffleOrder(ShuffleOrder shuffleOrder, @Nullable Runnable actionOnCompletion) {
        InlinePlayer player = this.player;
        if (player != null) {
            int size = this.getSize();
            if (shuffleOrder.getLength() != size) {
                shuffleOrder = shuffleOrder.cloneAndClear().cloneAndInsert(0, size);
            }

            player.createMessage(this).setType(3).setPayload(new MessageData(0, shuffleOrder, actionOnCompletion)).send();
        } else {
            this.shuffleOrder = shuffleOrder.getLength() > 0 ? shuffleOrder.cloneAndClear() : shuffleOrder;
            if (actionOnCompletion != null) {
                actionOnCompletion.run();
            }
        }

    }

    @Nullable
    public Object getTag() {
        return null;
    }

    public final synchronized void prepareSourceInternal(InlinePlayer player, boolean isTopLevelSource, @Nullable TransferListener mediaTransferListener) {
        super.prepareSourceInternal(player, isTopLevelSource, mediaTransferListener);
        this.player = player;
        this.playerApplicationHandler = new Handler(player.getApplicationLooper());
        if (this.mediaSourcesPublic.isEmpty()) {
            this.notifyListener();
        } else {
            this.shuffleOrder = this.shuffleOrder.cloneAndInsert(0, this.mediaSourcesPublic.size());
            this.addMediaSourcesInternal(0, this.mediaSourcesPublic);
            this.scheduleListenerNotification(null);
        }

    }

    public void maybeThrowSourceInfoRefreshError() throws IOException {
    }

    public final MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
        Object mediaSourceHolderUid = getMediaSourceHolderUid(id.periodUid);
        MediaSourceHolder holder = this.mediaSourceByUid.get(mediaSourceHolderUid);
        if (holder == null) {
            holder = new MediaSourceHolder(new DummyMediaSource());
            holder.hasStartedPreparing = true;
        }

        DeferredMediaPeriod mediaPeriod = new DeferredMediaPeriod(holder.mediaSource, id, allocator);
        this.mediaSourceByMediaPeriod.put(mediaPeriod, holder);
        holder.activeMediaPeriods.add(mediaPeriod);
        if (!holder.hasStartedPreparing) {
            holder.hasStartedPreparing = true;
            this.prepareChildSource(holder, holder.mediaSource);
        } else if (holder.isPrepared) {
            MediaPeriodId idInSource = id.copyWithPeriodUid(getChildPeriodUid(holder, id.periodUid));
            mediaPeriod.createPeriod(idInSource);
        }

        return mediaPeriod;
    }

    public final void releasePeriod(MediaPeriod mediaPeriod) {
        MediaSourceHolder holder = Assertions.checkNotNull(this.mediaSourceByMediaPeriod.remove(mediaPeriod));
        ((DeferredMediaPeriod)mediaPeriod).releasePeriod();
        holder.activeMediaPeriods.remove(mediaPeriod);
        this.maybeReleaseChildSource(holder);
    }

    public final void releaseSourceInternal() {
        super.releaseSourceInternal();
        this.mediaSourceHolders.clear();
        this.mediaSourceByUid.clear();
        this.player = null;
        this.playerApplicationHandler = null;
        this.shuffleOrder = this.shuffleOrder.cloneAndClear();
        this.windowCount = 0;
        this.periodCount = 0;
    }

    protected final void onChildSourceInfoRefreshed(MediaSourceHolder mediaSourceHolder, MediaSource mediaSource, Timeline timeline, @Nullable Object manifest) {
        this.updateMediaSourceInternal(mediaSourceHolder, timeline);
    }

    @Nullable
    protected MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(MediaSourceHolder mediaSourceHolder, MediaPeriodId mediaPeriodId) {
        for(int i = 0; i < mediaSourceHolder.activeMediaPeriods.size(); ++i) {
            if (mediaSourceHolder.activeMediaPeriods.get(i).id.windowSequenceNumber == mediaPeriodId.windowSequenceNumber) {
                Object periodUid = getPeriodUid(mediaSourceHolder, mediaPeriodId.periodUid);
                return mediaPeriodId.copyWithPeriodUid(periodUid);
            }
        }

        return null;
    }

    protected int getWindowIndexForChildWindowIndex(MediaSourceHolder mediaSourceHolder, int windowIndex) {
        return windowIndex + mediaSourceHolder.firstWindowIndexInChild;
    }

    public final void handleMessage(int messageType, @Nullable Object message) throws PlaybackException {
        if (this.player != null) {
            switch(messageType) {
            case 0:
                MessageData<Collection<MediaSourceHolder>> addMessage = (MessageData)Util.castNonNull(message);
                this.shuffleOrder = this.shuffleOrder.cloneAndInsert(addMessage.index, addMessage.customData.size());
                this.addMediaSourcesInternal(addMessage.index, addMessage.customData);
                this.scheduleListenerNotification(addMessage.actionOnCompletion);
                break;
            case 1:
                MessageData<Integer> removeMessage = (MessageData)Util.castNonNull(message);
                int fromIndex = removeMessage.index;
                int toIndex = removeMessage.customData;
                if (fromIndex == 0 && toIndex == this.shuffleOrder.getLength()) {
                    this.shuffleOrder = this.shuffleOrder.cloneAndClear();
                } else {
                    this.shuffleOrder = this.shuffleOrder.cloneAndRemove(fromIndex, toIndex);
                }

                for(int index = toIndex - 1; index >= fromIndex; --index) {
                    this.removeMediaSourceInternal(index);
                }

                this.scheduleListenerNotification(removeMessage.actionOnCompletion);
                break;
            case 2:
                MessageData<Integer> moveMessage = (MessageData)Util.castNonNull(message);
                this.shuffleOrder = this.shuffleOrder.cloneAndRemove(moveMessage.index, moveMessage.index + 1);
                this.shuffleOrder = this.shuffleOrder.cloneAndInsert(moveMessage.customData, 1);
                this.moveMediaSourceInternal(moveMessage.index, moveMessage.customData);
                this.scheduleListenerNotification(moveMessage.actionOnCompletion);
                break;
            case 3:
                MessageData<ShuffleOrder> shuffleOrderMessage = (MessageData)Util.castNonNull(message);
                this.shuffleOrder = shuffleOrderMessage.customData;
                this.scheduleListenerNotification(shuffleOrderMessage.actionOnCompletion);
                break;
            case 4:
                this.notifyListener();
                break;
            case 5:
                List<Runnable> actionsOnCompletion = (List)Util.castNonNull(message);
                Handler handler = Assertions.checkNotNull(this.playerApplicationHandler);

                for(int i = 0; i < actionsOnCompletion.size(); ++i) {
                    handler.post(actionsOnCompletion.get(i));
                }

                return;
            default:
                throw new IllegalStateException();
            }

        }
    }

    private void scheduleListenerNotification(@Nullable Runnable actionOnCompletion) {
        if (!this.listenerNotificationScheduled) {
            Assertions.checkNotNull(this.player).createMessage(this).setType(4).send();
            this.listenerNotificationScheduled = true;
        }

        if (actionOnCompletion != null) {
            this.pendingOnCompletionActions.add(actionOnCompletion);
        }

    }

    private void notifyListener() {
        this.listenerNotificationScheduled = false;
        List<Runnable> actionsOnCompletion = this.pendingOnCompletionActions.isEmpty() ? Collections.emptyList() : new ArrayList(this.pendingOnCompletionActions);
        this.pendingOnCompletionActions.clear();
        this.refreshSourceInfo(new ConcatenatedTimeline(this.mediaSourceHolders, this.windowCount, this.periodCount, this.shuffleOrder, this.isAtomic), null);
        if (!actionsOnCompletion.isEmpty()) {
            Assertions.checkNotNull(this.player).createMessage(this).setType(5).setPayload(actionsOnCompletion).send();
        }

    }

    private void addMediaSourcesInternal(int index, Collection<MediaSourceHolder> mediaSourceHolders) {
        Iterator var3 = mediaSourceHolders.iterator();

        while(var3.hasNext()) {
            MediaSourceHolder mediaSourceHolder = (MediaSourceHolder)var3.next();
            this.addMediaSourceInternal(index++, mediaSourceHolder);
        }

    }

    private void addMediaSourceInternal(int newIndex, MediaSourceHolder newMediaSourceHolder) {
        if (newIndex > 0) {
            MediaSourceHolder previousHolder = this.mediaSourceHolders.get(newIndex - 1);
            newMediaSourceHolder.reset(newIndex, previousHolder.firstWindowIndexInChild + previousHolder.timeline.getWindowCount(), previousHolder.firstPeriodIndexInChild + previousHolder.timeline.getPeriodCount());
        } else {
            newMediaSourceHolder.reset(newIndex, 0, 0);
        }

        this.correctOffsets(newIndex, 1, newMediaSourceHolder.timeline.getWindowCount(), newMediaSourceHolder.timeline.getPeriodCount());
        this.mediaSourceHolders.add(newIndex, newMediaSourceHolder);
        this.mediaSourceByUid.put(newMediaSourceHolder.uid, newMediaSourceHolder);
        if (!this.useLazyPreparation) {
            newMediaSourceHolder.hasStartedPreparing = true;
            this.prepareChildSource(newMediaSourceHolder, newMediaSourceHolder.mediaSource);
        }

    }

    private void updateMediaSourceInternal(MediaSourceHolder mediaSourceHolder, Timeline timeline) {
        if (mediaSourceHolder == null) {
            throw new IllegalArgumentException();
        } else {
            DeferredTimeline deferredTimeline = mediaSourceHolder.timeline;
            if (deferredTimeline.getTimeline() != timeline) {
                int windowOffsetUpdate = timeline.getWindowCount() - deferredTimeline.getWindowCount();
                int periodOffsetUpdate = timeline.getPeriodCount() - deferredTimeline.getPeriodCount();
                if (windowOffsetUpdate != 0 || periodOffsetUpdate != 0) {
                    this.correctOffsets(mediaSourceHolder.childIndex + 1, 0, windowOffsetUpdate, periodOffsetUpdate);
                }

                if (mediaSourceHolder.isPrepared) {
                    mediaSourceHolder.timeline = deferredTimeline.cloneWithUpdatedTimeline(timeline);
                } else if (timeline.isEmpty()) {
                    mediaSourceHolder.timeline = DeferredTimeline.createWithRealTimeline(timeline, DeferredTimeline.DUMMY_ID);
                } else {
                    Assertions.checkState(mediaSourceHolder.activeMediaPeriods.size() <= 1);
                    DeferredMediaPeriod deferredMediaPeriod = mediaSourceHolder.activeMediaPeriods.isEmpty() ? null : mediaSourceHolder.activeMediaPeriods.get(0);
                    long windowStartPositionUs = this.window.getDefaultPositionUs();
                    if (deferredMediaPeriod != null) {
                        long periodPreparePositionUs = deferredMediaPeriod.getPreparePositionUs();
                        if (periodPreparePositionUs != 0L) {
                            windowStartPositionUs = periodPreparePositionUs;
                        }
                    }

                    Pair<Object, Long> periodPosition = timeline.getPeriodPosition(this.window, this.period, 0, windowStartPositionUs);
                    Object periodUid = periodPosition.first;
                    long periodPositionUs = periodPosition.second;
                    mediaSourceHolder.timeline = DeferredTimeline.createWithRealTimeline(timeline, periodUid);
                    if (deferredMediaPeriod != null) {
                        deferredMediaPeriod.overridePreparePositionUs(periodPositionUs);
                        MediaPeriodId idInSource = deferredMediaPeriod.id.copyWithPeriodUid(getChildPeriodUid(mediaSourceHolder, deferredMediaPeriod.id.periodUid));
                        deferredMediaPeriod.createPeriod(idInSource);
                    }
                }

                mediaSourceHolder.isPrepared = true;
                this.scheduleListenerNotification(null);
            }
        }
    }

    private void removeMediaSourceInternal(int index) {
        MediaSourceHolder holder = this.mediaSourceHolders.remove(index);
        this.mediaSourceByUid.remove(holder.uid);
        Timeline oldTimeline = holder.timeline;
        this.correctOffsets(index, -1, -oldTimeline.getWindowCount(), -oldTimeline.getPeriodCount());
        holder.isRemoved = true;
        this.maybeReleaseChildSource(holder);
    }

    private void moveMediaSourceInternal(int currentIndex, int newIndex) {
        int startIndex = Math.min(currentIndex, newIndex);
        int endIndex = Math.max(currentIndex, newIndex);
        int windowOffset = this.mediaSourceHolders.get(startIndex).firstWindowIndexInChild;
        int periodOffset = this.mediaSourceHolders.get(startIndex).firstPeriodIndexInChild;
        this.mediaSourceHolders.add(newIndex, this.mediaSourceHolders.remove(currentIndex));

        for(int i = startIndex; i <= endIndex; ++i) {
            MediaSourceHolder holder = this.mediaSourceHolders.get(i);
            holder.firstWindowIndexInChild = windowOffset;
            holder.firstPeriodIndexInChild = periodOffset;
            windowOffset += holder.timeline.getWindowCount();
            periodOffset += holder.timeline.getPeriodCount();
        }

    }

    private void correctOffsets(int startIndex, int childIndexUpdate, int windowOffsetUpdate, int periodOffsetUpdate) {
        this.windowCount += windowOffsetUpdate;
        this.periodCount += periodOffsetUpdate;

        for(int i = startIndex; i < this.mediaSourceHolders.size(); ++i) {
            MediaSourceHolder var10000 = this.mediaSourceHolders.get(i);
            var10000.childIndex += childIndexUpdate;
            var10000 = this.mediaSourceHolders.get(i);
            var10000.firstWindowIndexInChild += windowOffsetUpdate;
            var10000 = this.mediaSourceHolders.get(i);
            var10000.firstPeriodIndexInChild += periodOffsetUpdate;
        }

    }

    private void maybeReleaseChildSource(MediaSourceHolder mediaSourceHolder) {
        if (mediaSourceHolder.isRemoved && mediaSourceHolder.hasStartedPreparing && mediaSourceHolder.activeMediaPeriods.isEmpty()) {
            this.releaseChildSource(mediaSourceHolder);
        }

    }

    private static Object getMediaSourceHolderUid(Object periodUid) {
        return ConcatenatedTimeline.getChildTimelineUidFromConcatenatedUid(periodUid);
    }

    private static Object getChildPeriodUid(MediaSourceHolder holder, Object periodUid) {
        Object childUid = ConcatenatedTimeline.getChildPeriodUidFromConcatenatedUid(periodUid);
        return childUid.equals(DeferredTimeline.DUMMY_ID) ? holder.timeline.replacedId : childUid;
    }

    private static Object getPeriodUid(MediaSourceHolder holder, Object childPeriodUid) {
        if (holder.timeline.replacedId.equals(childPeriodUid)) {
            childPeriodUid = DeferredTimeline.DUMMY_ID;
        }

        return ConcatenatedTimeline.getConcatenatedUid(holder.uid, childPeriodUid);
    }

    private static final class DummyMediaSource extends BaseMediaSource {
        private DummyMediaSource() {
        }

        protected void prepareSourceInternal(InlinePlayer player, boolean isTopLevelSource, @Nullable TransferListener mediaTransferListener) {
        }

        @Nullable
        public Object getTag() {
            return null;
        }

        protected void releaseSourceInternal() {
        }

        public void maybeThrowSourceInfoRefreshError() throws IOException {
        }

        public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
            throw new UnsupportedOperationException();
        }

        public void releasePeriod(MediaPeriod mediaPeriod) {
        }
    }

    private static final class DummyTimeline extends Timeline {
        @Nullable
        private final Object tag;

        public DummyTimeline(@Nullable Object tag) {
            this.tag = tag;
        }

        public int getWindowCount() {
            return 1;
        }

        public Window getWindow(int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
            return window.set(this.tag, -Long.MAX_VALUE, -Long.MAX_VALUE, false, true, 0L, -Long.MAX_VALUE, 0, 0, 0L);
        }

        public int getPeriodCount() {
            return 1;
        }

        public Period getPeriod(int periodIndex, Period period, boolean setIds) {
            return period.set(0, DeferredTimeline.DUMMY_ID, 0, -Long.MAX_VALUE, 0L);
        }

        public int getIndexOfPeriod(Object uid) {
            return uid == DeferredTimeline.DUMMY_ID ? 0 : -1;
        }

        public Object getUidOfPeriod(int periodIndex) {
            return DeferredTimeline.DUMMY_ID;
        }
    }

    private static final class DeferredTimeline extends ForwardingTimeline {
        private static final Object DUMMY_ID = new Object();
        private final Object replacedId;

        public static DeferredTimeline createWithDummyTimeline(@Nullable Object windowTag) {
            return new DeferredTimeline(new DummyTimeline(windowTag), DUMMY_ID);
        }

        public static DeferredTimeline createWithRealTimeline(Timeline timeline, Object firstPeriodUid) {
            return new DeferredTimeline(timeline, firstPeriodUid);
        }

        private DeferredTimeline(Timeline timeline, Object replacedId) {
            super(timeline);
            this.replacedId = replacedId;
        }

        public DeferredTimeline cloneWithUpdatedTimeline(Timeline timeline) {
            return new DeferredTimeline(timeline, this.replacedId);
        }

        public Timeline getTimeline() {
            return this.timeline;
        }

        public Period getPeriod(int periodIndex, Period period, boolean setIds) {
            this.timeline.getPeriod(periodIndex, period, setIds);
            if (Util.areEqual(period.uid, this.replacedId)) {
                period.uid = DUMMY_ID;
            }

            return period;
        }

        public int getIndexOfPeriod(Object uid) {
            return this.timeline.getIndexOfPeriod(DUMMY_ID.equals(uid) ? this.replacedId : uid);
        }

        public Object getUidOfPeriod(int periodIndex) {
            Object uid = this.timeline.getUidOfPeriod(periodIndex);
            return Util.areEqual(uid, this.replacedId) ? DUMMY_ID : uid;
        }
    }

    private static final class ConcatenatedTimeline extends AbstractConcatenatedTimeline {
        private final int windowCount;
        private final int periodCount;
        private final int[] firstPeriodInChildIndices;
        private final int[] firstWindowInChildIndices;
        private final Timeline[] timelines;
        private final Object[] uids;
        private final HashMap<Object, Integer> childIndexByUid;

        public ConcatenatedTimeline(Collection<MediaSourceHolder> mediaSourceHolders, int windowCount, int periodCount, ShuffleOrder shuffleOrder, boolean isAtomic) {
            super(isAtomic, shuffleOrder);
            this.windowCount = windowCount;
            this.periodCount = periodCount;
            int childCount = mediaSourceHolders.size();
            this.firstPeriodInChildIndices = new int[childCount];
            this.firstWindowInChildIndices = new int[childCount];
            this.timelines = new Timeline[childCount];
            this.uids = new Object[childCount];
            this.childIndexByUid = new HashMap();
            int index = 0;
            Iterator var8 = mediaSourceHolders.iterator();

            while(var8.hasNext()) {
                MediaSourceHolder mediaSourceHolder = (MediaSourceHolder)var8.next();
                this.timelines[index] = mediaSourceHolder.timeline;
                this.firstPeriodInChildIndices[index] = mediaSourceHolder.firstPeriodIndexInChild;
                this.firstWindowInChildIndices[index] = mediaSourceHolder.firstWindowIndexInChild;
                this.uids[index] = mediaSourceHolder.uid;
                this.childIndexByUid.put(this.uids[index], index++);
            }

        }

        protected int getChildIndexByPeriodIndex(int periodIndex) {
            return Util.binarySearchFloor(this.firstPeriodInChildIndices, periodIndex + 1, false, false);
        }

        protected int getChildIndexByWindowIndex(int windowIndex) {
            return Util.binarySearchFloor(this.firstWindowInChildIndices, windowIndex + 1, false, false);
        }

        protected int getChildIndexByChildUid(Object childUid) {
            Integer index = this.childIndexByUid.get(childUid);
            return index == null ? -1 : index;
        }

        protected Timeline getTimelineByChildIndex(int childIndex) {
            return this.timelines[childIndex];
        }

        protected int getFirstPeriodIndexByChildIndex(int childIndex) {
            return this.firstPeriodInChildIndices[childIndex];
        }

        protected int getFirstWindowIndexByChildIndex(int childIndex) {
            return this.firstWindowInChildIndices[childIndex];
        }

        protected Object getChildUidByChildIndex(int childIndex) {
            return this.uids[childIndex];
        }

        public int getWindowCount() {
            return this.windowCount;
        }

        public int getPeriodCount() {
            return this.periodCount;
        }
    }

    private static final class MessageData<T> {
        public final int index;
        public final T customData;
        @Nullable
        public final Runnable actionOnCompletion;

        public MessageData(int index, T customData, @Nullable Runnable actionOnCompletion) {
            this.index = index;
            this.actionOnCompletion = actionOnCompletion;
            this.customData = customData;
        }
    }

    static final class MediaSourceHolder implements Comparable<MediaSourceHolder> {
        public final MediaSource mediaSource;
        public final Object uid;
        public DeferredTimeline timeline;
        public int childIndex;
        public int firstWindowIndexInChild;
        public int firstPeriodIndexInChild;
        public boolean hasStartedPreparing;
        public boolean isPrepared;
        public boolean isRemoved;
        public List<DeferredMediaPeriod> activeMediaPeriods;

        public MediaSourceHolder(MediaSource mediaSource) {
            this.mediaSource = mediaSource;
            this.timeline = DeferredTimeline.createWithDummyTimeline(mediaSource.getTag());
            this.activeMediaPeriods = new ArrayList();
            this.uid = new Object();
        }

        public void reset(int childIndex, int firstWindowIndexInChild, int firstPeriodIndexInChild) {
            this.childIndex = childIndex;
            this.firstWindowIndexInChild = firstWindowIndexInChild;
            this.firstPeriodIndexInChild = firstPeriodIndexInChild;
            this.hasStartedPreparing = false;
            this.isPrepared = false;
            this.isRemoved = false;
            this.activeMediaPeriods.clear();
        }

        public int compareTo(@NonNull ConcatenatingMediaSource.MediaSourceHolder other) {
            return this.firstPeriodIndexInChild - other.firstPeriodIndexInChild;
        }
    }
}
