package com.zj.playerLib.trackselection;

import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.zj.playerLib.source.TrackGroup;
import com.zj.playerLib.source.chunk.MediaChunk;
import com.zj.playerLib.source.chunk.MediaChunkIterator;
import com.zj.playerLib.upstream.BandwidthMeter;

import java.util.List;
import java.util.Random;

public final class RandomTrackSelection extends BaseTrackSelection {
    private final Random random;
    private int selectedIndex;

    public RandomTrackSelection(TrackGroup group, int... tracks) {
        super(group, tracks);
        this.random = new Random();
        this.selectedIndex = this.random.nextInt(this.length);
    }

    public RandomTrackSelection(TrackGroup group, int[] tracks, long seed) {
        this(group, tracks, new Random(seed));
    }

    public RandomTrackSelection(TrackGroup group, int[] tracks, Random random) {
        super(group, tracks);
        this.random = random;
        this.selectedIndex = random.nextInt(this.length);
    }

    public void updateSelectedTrack(long playbackPositionUs, long bufferedDurationUs, long availableDurationUs, List<? extends MediaChunk> queue, MediaChunkIterator[] mediaChunkIterators) {
        long nowMs = SystemClock.elapsedRealtime();
        int nonBlacklistedFormatCount = 0;

        int i;
        for(i = 0; i < this.length; ++i) {
            if (!this.isBlacklisted(i, nowMs)) {
                ++nonBlacklistedFormatCount;
            }
        }

        this.selectedIndex = this.random.nextInt(nonBlacklistedFormatCount);
        if (nonBlacklistedFormatCount != this.length) {
            nonBlacklistedFormatCount = 0;

            for(i = 0; i < this.length; ++i) {
                if (!this.isBlacklisted(i, nowMs) && this.selectedIndex == nonBlacklistedFormatCount++) {
                    this.selectedIndex = i;
                    return;
                }
            }
        }

    }

    public int getSelectedIndex() {
        return this.selectedIndex;
    }

    public int getSelectionReason() {
        return 3;
    }

    @Nullable
    public Object getSelectionData() {
        return null;
    }

    public static final class Factory implements TrackSelection.Factory {
        private final Random random;

        public Factory() {
            this.random = new Random();
        }

        public Factory(int seed) {
            this.random = new Random(seed);
        }

        public RandomTrackSelection createTrackSelection(TrackGroup group, BandwidthMeter bandwidthMeter, int... tracks) {
            return new RandomTrackSelection(group, tracks, this.random);
        }
    }
}
