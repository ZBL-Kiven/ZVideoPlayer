package com.zj.playerLib.trackselection;

import androidx.annotation.Nullable;
import com.zj.playerLib.Format;
import com.zj.playerLib.source.TrackGroup;
import com.zj.playerLib.source.chunk.MediaChunk;
import com.zj.playerLib.source.chunk.MediaChunkIterator;
import com.zj.playerLib.upstream.BandwidthMeter;
import java.util.List;

public interface TrackSelection {
    void enable();

    void disable();

    TrackGroup getTrackGroup();

    int length();

    Format getFormat(int var1);

    int getIndexInTrackGroup(int var1);

    int indexOf(Format var1);

    int indexOf(int var1);

    Format getSelectedFormat();

    int getSelectedIndexInTrackGroup();

    int getSelectedIndex();

    int getSelectionReason();

    @Nullable
    Object getSelectionData();

    void onPlaybackSpeed(float var1);

    /** @deprecated */
    @Deprecated
    default void updateSelectedTrack(long playbackPositionUs, long bufferedDurationUs, long availableDurationUs) {
        throw new UnsupportedOperationException();
    }

    default void updateSelectedTrack(long playbackPositionUs, long bufferedDurationUs, long availableDurationUs, List<? extends MediaChunk> queue, MediaChunkIterator[] mediaChunkIterators) {
        this.updateSelectedTrack(playbackPositionUs, bufferedDurationUs, availableDurationUs);
    }

    int evaluateQueueSize(long var1, List<? extends MediaChunk> var3);

    boolean blacklist(int var1, long var2);

    interface Factory {
        TrackSelection createTrackSelection(TrackGroup var1, BandwidthMeter var2, int... var3);
    }
}
