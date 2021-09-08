//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source;

import android.util.Pair;

import com.zj.playerLib.Timeline;

abstract class AbstractConcatenatedTimeline extends Timeline {
    private final int childCount;
    private final ShuffleOrder shuffleOrder;
    private final boolean isAtomic;

    public static Object getChildTimelineUidFromConcatenatedUid(Object concatenatedUid) {
        return ((Pair)concatenatedUid).first;
    }

    public static Object getChildPeriodUidFromConcatenatedUid(Object concatenatedUid) {
        return ((Pair)concatenatedUid).second;
    }

    public static Object getConcatenatedUid(Object childTimelineUid, Object childPeriodUid) {
        return Pair.create(childTimelineUid, childPeriodUid);
    }

    public AbstractConcatenatedTimeline(boolean isAtomic, ShuffleOrder shuffleOrder) {
        this.isAtomic = isAtomic;
        this.shuffleOrder = shuffleOrder;
        this.childCount = shuffleOrder.getLength();
    }

    public int getNextWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
        if (this.isAtomic) {
            repeatMode = repeatMode == 1 ? 2 : repeatMode;
            shuffleModeEnabled = false;
        }

        int childIndex = this.getChildIndexByWindowIndex(windowIndex);
        int firstWindowIndexInChild = this.getFirstWindowIndexByChildIndex(childIndex);
        int nextWindowIndexInChild = this.getTimelineByChildIndex(childIndex).getNextWindowIndex(windowIndex - firstWindowIndexInChild, repeatMode == 2 ? 0 : repeatMode, shuffleModeEnabled);
        if (nextWindowIndexInChild != -1) {
            return firstWindowIndexInChild + nextWindowIndexInChild;
        } else {
            int nextChildIndex;
            for(nextChildIndex = this.getNextChildIndex(childIndex, shuffleModeEnabled); nextChildIndex != -1 && this.getTimelineByChildIndex(nextChildIndex).isEmpty(); nextChildIndex = this.getNextChildIndex(nextChildIndex, shuffleModeEnabled)) {
            }

            if (nextChildIndex != -1) {
                return this.getFirstWindowIndexByChildIndex(nextChildIndex) + this.getTimelineByChildIndex(nextChildIndex).getFirstWindowIndex(shuffleModeEnabled);
            } else {
                return repeatMode == 2 ? this.getFirstWindowIndex(shuffleModeEnabled) : -1;
            }
        }
    }

    public int getPreviousWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
        if (this.isAtomic) {
            repeatMode = repeatMode == 1 ? 2 : repeatMode;
            shuffleModeEnabled = false;
        }

        int childIndex = this.getChildIndexByWindowIndex(windowIndex);
        int firstWindowIndexInChild = this.getFirstWindowIndexByChildIndex(childIndex);
        int previousWindowIndexInChild = this.getTimelineByChildIndex(childIndex).getPreviousWindowIndex(windowIndex - firstWindowIndexInChild, repeatMode == 2 ? 0 : repeatMode, shuffleModeEnabled);
        if (previousWindowIndexInChild != -1) {
            return firstWindowIndexInChild + previousWindowIndexInChild;
        } else {
            int previousChildIndex;
            for(previousChildIndex = this.getPreviousChildIndex(childIndex, shuffleModeEnabled); previousChildIndex != -1 && this.getTimelineByChildIndex(previousChildIndex).isEmpty(); previousChildIndex = this.getPreviousChildIndex(previousChildIndex, shuffleModeEnabled)) {
            }

            if (previousChildIndex != -1) {
                return this.getFirstWindowIndexByChildIndex(previousChildIndex) + this.getTimelineByChildIndex(previousChildIndex).getLastWindowIndex(shuffleModeEnabled);
            } else {
                return repeatMode == 2 ? this.getLastWindowIndex(shuffleModeEnabled) : -1;
            }
        }
    }

    public int getLastWindowIndex(boolean shuffleModeEnabled) {
        if (this.childCount == 0) {
            return -1;
        } else {
            if (this.isAtomic) {
                shuffleModeEnabled = false;
            }

            int lastChildIndex = shuffleModeEnabled ? this.shuffleOrder.getLastIndex() : this.childCount - 1;

            do {
                if (!this.getTimelineByChildIndex(lastChildIndex).isEmpty()) {
                    return this.getFirstWindowIndexByChildIndex(lastChildIndex) + this.getTimelineByChildIndex(lastChildIndex).getLastWindowIndex(shuffleModeEnabled);
                }

                lastChildIndex = this.getPreviousChildIndex(lastChildIndex, shuffleModeEnabled);
            } while(lastChildIndex != -1);

            return -1;
        }
    }

    public int getFirstWindowIndex(boolean shuffleModeEnabled) {
        if (this.childCount == 0) {
            return -1;
        } else {
            if (this.isAtomic) {
                shuffleModeEnabled = false;
            }

            int firstChildIndex = shuffleModeEnabled ? this.shuffleOrder.getFirstIndex() : 0;

            do {
                if (!this.getTimelineByChildIndex(firstChildIndex).isEmpty()) {
                    return this.getFirstWindowIndexByChildIndex(firstChildIndex) + this.getTimelineByChildIndex(firstChildIndex).getFirstWindowIndex(shuffleModeEnabled);
                }

                firstChildIndex = this.getNextChildIndex(firstChildIndex, shuffleModeEnabled);
            } while(firstChildIndex != -1);

            return -1;
        }
    }

    public final Window getWindow(int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
        int childIndex = this.getChildIndexByWindowIndex(windowIndex);
        int firstWindowIndexInChild = this.getFirstWindowIndexByChildIndex(childIndex);
        int firstPeriodIndexInChild = this.getFirstPeriodIndexByChildIndex(childIndex);
        this.getTimelineByChildIndex(childIndex).getWindow(windowIndex - firstWindowIndexInChild, window, setTag, defaultPositionProjectionUs);
        window.firstPeriodIndex += firstPeriodIndexInChild;
        window.lastPeriodIndex += firstPeriodIndexInChild;
        return window;
    }

    public final Period getPeriodByUid(Object uid, Period period) {
        Object childUid = getChildTimelineUidFromConcatenatedUid(uid);
        Object periodUid = getChildPeriodUidFromConcatenatedUid(uid);
        int childIndex = this.getChildIndexByChildUid(childUid);
        int firstWindowIndexInChild = this.getFirstWindowIndexByChildIndex(childIndex);
        this.getTimelineByChildIndex(childIndex).getPeriodByUid(periodUid, period);
        period.windowIndex += firstWindowIndexInChild;
        period.uid = uid;
        return period;
    }

    public final Period getPeriod(int periodIndex, Period period, boolean setIds) {
        int childIndex = this.getChildIndexByPeriodIndex(periodIndex);
        int firstWindowIndexInChild = this.getFirstWindowIndexByChildIndex(childIndex);
        int firstPeriodIndexInChild = this.getFirstPeriodIndexByChildIndex(childIndex);
        this.getTimelineByChildIndex(childIndex).getPeriod(periodIndex - firstPeriodIndexInChild, period, setIds);
        period.windowIndex += firstWindowIndexInChild;
        if (setIds) {
            period.uid = getConcatenatedUid(this.getChildUidByChildIndex(childIndex), period.uid);
        }

        return period;
    }

    public final int getIndexOfPeriod(Object uid) {
        if (!(uid instanceof Pair)) {
            return -1;
        } else {
            Object childUid = getChildTimelineUidFromConcatenatedUid(uid);
            Object periodUid = getChildPeriodUidFromConcatenatedUid(uid);
            int childIndex = this.getChildIndexByChildUid(childUid);
            if (childIndex == -1) {
                return -1;
            } else {
                int periodIndexInChild = this.getTimelineByChildIndex(childIndex).getIndexOfPeriod(periodUid);
                return periodIndexInChild == -1 ? -1 : this.getFirstPeriodIndexByChildIndex(childIndex) + periodIndexInChild;
            }
        }
    }

    public final Object getUidOfPeriod(int periodIndex) {
        int childIndex = this.getChildIndexByPeriodIndex(periodIndex);
        int firstPeriodIndexInChild = this.getFirstPeriodIndexByChildIndex(childIndex);
        Object periodUidInChild = this.getTimelineByChildIndex(childIndex).getUidOfPeriod(periodIndex - firstPeriodIndexInChild);
        return getConcatenatedUid(this.getChildUidByChildIndex(childIndex), periodUidInChild);
    }

    protected abstract int getChildIndexByPeriodIndex(int var1);

    protected abstract int getChildIndexByWindowIndex(int var1);

    protected abstract int getChildIndexByChildUid(Object var1);

    protected abstract Timeline getTimelineByChildIndex(int var1);

    protected abstract int getFirstPeriodIndexByChildIndex(int var1);

    protected abstract int getFirstWindowIndexByChildIndex(int var1);

    protected abstract Object getChildUidByChildIndex(int var1);

    private int getNextChildIndex(int childIndex, boolean shuffleModeEnabled) {
        return shuffleModeEnabled ? this.shuffleOrder.getNextIndex(childIndex) : (childIndex < this.childCount - 1 ? childIndex + 1 : -1);
    }

    private int getPreviousChildIndex(int childIndex, boolean shuffleModeEnabled) {
        return shuffleModeEnabled ? this.shuffleOrder.getPreviousIndex(childIndex) : (childIndex > 0 ? childIndex - 1 : -1);
    }
}
