package com.zj.playerLib.extractor.mp4;

import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

final class TrackSampleTable {
    public final Track track;
    public final int sampleCount;
    public final long[] offsets;
    public final int[] sizes;
    public final int maximumSize;
    public final long[] timestampsUs;
    public final int[] flags;
    public final long durationUs;

    public TrackSampleTable(Track track, long[] offsets, int[] sizes, int maximumSize, long[] timestampsUs, int[] flags, long durationUs) {
        Assertions.checkArgument(sizes.length == timestampsUs.length);
        Assertions.checkArgument(offsets.length == timestampsUs.length);
        Assertions.checkArgument(flags.length == timestampsUs.length);
        this.track = track;
        this.offsets = offsets;
        this.sizes = sizes;
        this.maximumSize = maximumSize;
        this.timestampsUs = timestampsUs;
        this.flags = flags;
        this.durationUs = durationUs;
        this.sampleCount = offsets.length;
    }

    public int getIndexOfEarlierOrEqualSynchronizationSample(long timeUs) {
        int startIndex = Util.binarySearchFloor(this.timestampsUs, timeUs, true, false);

        for(int i = startIndex; i >= 0; --i) {
            if ((this.flags[i] & 1) != 0) {
                return i;
            }
        }

        return -1;
    }

    public int getIndexOfLaterOrEqualSynchronizationSample(long timeUs) {
        int startIndex = Util.binarySearchCeil(this.timestampsUs, timeUs, true, false);

        for(int i = startIndex; i < this.timestampsUs.length; ++i) {
            if ((this.flags[i] & 1) != 0) {
                return i;
            }
        }

        return -1;
    }
}
