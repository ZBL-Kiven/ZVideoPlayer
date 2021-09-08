//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.trackselection;

import androidx.annotation.Nullable;

import com.zj.playerLib.source.TrackGroup;
import com.zj.playerLib.source.chunk.MediaChunk;
import com.zj.playerLib.source.chunk.MediaChunkIterator;
import com.zj.playerLib.upstream.BandwidthMeter;
import com.zj.playerLib.util.Assertions;

import java.util.List;

public final class FixedTrackSelection extends BaseTrackSelection {
    private final int reason;
    @Nullable
    private final Object data;

    public FixedTrackSelection(TrackGroup group, int track) {
        this(group, track, 0, (Object)null);
    }

    public FixedTrackSelection(TrackGroup group, int track, int reason, @Nullable Object data) {
        super(group, new int[]{track});
        this.reason = reason;
        this.data = data;
    }

    public void updateSelectedTrack(long playbackPositionUs, long bufferedDurationUs, long availableDurationUs, List<? extends MediaChunk> queue, MediaChunkIterator[] mediaChunkIterators) {
    }

    public int getSelectedIndex() {
        return 0;
    }

    public int getSelectionReason() {
        return this.reason;
    }

    @Nullable
    public Object getSelectionData() {
        return this.data;
    }

    /** @deprecated */
    @Deprecated
    public static final class Factory implements TrackSelection.Factory {
        private final int reason;
        @Nullable
        private final Object data;

        public Factory() {
            this.reason = 0;
            this.data = null;
        }

        public Factory(int reason, @Nullable Object data) {
            this.reason = reason;
            this.data = data;
        }

        public FixedTrackSelection createTrackSelection(TrackGroup group, BandwidthMeter bandwidthMeter, int... tracks) {
            Assertions.checkArgument(tracks.length == 1);
            return new FixedTrackSelection(group, tracks[0], this.reason, this.data);
        }
    }
}
