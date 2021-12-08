package com.zj.playerLib.trackselection;

import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.zj.playerLib.Format;
import com.zj.playerLib.source.TrackGroup;
import com.zj.playerLib.source.chunk.MediaChunk;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public abstract class BaseTrackSelection implements TrackSelection {
    protected final TrackGroup group;
    protected final int length;
    protected final int[] tracks;
    private final Format[] formats;
    private final long[] blacklistUntilTimes;
    private int hashCode;

    public BaseTrackSelection(TrackGroup group, int... tracks) {
        Assertions.checkState(tracks.length > 0);
        this.group = Assertions.checkNotNull(group);
        this.length = tracks.length;
        this.formats = new Format[this.length];

        int i;
        for(i = 0; i < tracks.length; ++i) {
            this.formats[i] = group.getFormat(tracks[i]);
        }

        Arrays.sort(this.formats, new DecreasingBandwidthComparator());
        this.tracks = new int[this.length];

        for(i = 0; i < this.length; ++i) {
            this.tracks[i] = group.indexOf(this.formats[i]);
        }

        this.blacklistUntilTimes = new long[this.length];
    }

    public void enable() {
    }

    public void disable() {
    }

    public final TrackGroup getTrackGroup() {
        return this.group;
    }

    public final int length() {
        return this.tracks.length;
    }

    public final Format getFormat(int index) {
        return this.formats[index];
    }

    public final int getIndexInTrackGroup(int index) {
        return this.tracks[index];
    }

    public final int indexOf(Format format) {
        for(int i = 0; i < this.length; ++i) {
            if (this.formats[i] == format) {
                return i;
            }
        }

        return -1;
    }

    public final int indexOf(int indexInTrackGroup) {
        for(int i = 0; i < this.length; ++i) {
            if (this.tracks[i] == indexInTrackGroup) {
                return i;
            }
        }

        return -1;
    }

    public final Format getSelectedFormat() {
        return this.formats[this.getSelectedIndex()];
    }

    public final int getSelectedIndexInTrackGroup() {
        return this.tracks[this.getSelectedIndex()];
    }

    public void onPlaybackSpeed(float playbackSpeed) {
    }

    public int evaluateQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
        return queue.size();
    }

    public final boolean blacklist(int index, long blacklistDurationMs) {
        long nowMs = SystemClock.elapsedRealtime();
        boolean canBlacklist = this.isBlacklisted(index, nowMs);

        for(int i = 0; i < this.length && !canBlacklist; ++i) {
            canBlacklist = i != index && !this.isBlacklisted(i, nowMs);
        }

        if (!canBlacklist) {
            return false;
        } else {
            this.blacklistUntilTimes[index] = Math.max(this.blacklistUntilTimes[index], Util.addWithOverflowDefault(nowMs, blacklistDurationMs, Long.MAX_VALUE));
            return true;
        }
    }

    protected final boolean isBlacklisted(int index, long nowMs) {
        return this.blacklistUntilTimes[index] > nowMs;
    }

    public int hashCode() {
        if (this.hashCode == 0) {
            this.hashCode = 31 * System.identityHashCode(this.group) + Arrays.hashCode(this.tracks);
        }

        return this.hashCode;
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            BaseTrackSelection other = (BaseTrackSelection)obj;
            return this.group == other.group && Arrays.equals(this.tracks, other.tracks);
        } else {
            return false;
        }
    }

    private static final class DecreasingBandwidthComparator implements Comparator<Format> {
        private DecreasingBandwidthComparator() {
        }

        public int compare(Format a, Format b) {
            return b.bitrate - a.bitrate;
        }
    }
}
