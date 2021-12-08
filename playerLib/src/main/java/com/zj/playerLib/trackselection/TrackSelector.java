package com.zj.playerLib.trackselection;

import androidx.annotation.Nullable;

import com.zj.playerLib.PlaybackException;
import com.zj.playerLib.RendererCapabilities;
import com.zj.playerLib.source.TrackGroupArray;
import com.zj.playerLib.upstream.BandwidthMeter;
import com.zj.playerLib.util.Assertions;

public abstract class TrackSelector {
    @Nullable
    private TrackSelector.InvalidationListener listener;
    @Nullable
    private BandwidthMeter bandwidthMeter;

    public TrackSelector() {
    }

    public final void init(InvalidationListener listener, BandwidthMeter bandwidthMeter) {
        this.listener = listener;
        this.bandwidthMeter = bandwidthMeter;
    }

    public abstract TrackSelectorResult selectTracks(RendererCapabilities[] var1, TrackGroupArray var2) throws PlaybackException;

    public abstract void onSelectionActivated(Object var1);

    protected final void invalidate() {
        if (this.listener != null) {
            this.listener.onTrackSelectionsInvalidated();
        }

    }

    protected final BandwidthMeter getBandwidthMeter() {
        return Assertions.checkNotNull(this.bandwidthMeter);
    }

    public interface InvalidationListener {
        void onTrackSelectionsInvalidated();
    }
}
