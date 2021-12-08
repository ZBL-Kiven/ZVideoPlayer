package com.zj.playerLib;

import com.zj.playerLib.source.TrackGroupArray;
import com.zj.playerLib.trackselection.TrackSelectionArray;
import com.zj.playerLib.upstream.Allocator;

public interface LoadControl {
    void onPrepared();

    void onTracksSelected(Renderer[] var1, TrackGroupArray var2, TrackSelectionArray var3);

    void onStopped();

    void onReleased();

    Allocator getAllocator();

    long getBackBufferDurationUs();

    boolean retainBackBufferFromKeyframe();

    boolean shouldContinueLoading(long var1, float var3);

    boolean shouldStartPlayback(long var1, float var3, boolean var4);
}
