package com.zj.playerLib.source;

import android.os.Handler;

import androidx.annotation.Nullable;

import com.zj.playerLib.InlinePlayer;
import com.zj.playerLib.Timeline;
import com.zj.playerLib.source.MediaSourceEventListener.EventDispatcher;
import com.zj.playerLib.upstream.TransferListener;
import com.zj.playerLib.util.Assertions;

import java.util.ArrayList;

public abstract class BaseMediaSource implements MediaSource {
    private final ArrayList<SourceInfoRefreshListener> sourceInfoListeners = new ArrayList<>(1);
    private final EventDispatcher eventDispatcher = new EventDispatcher();
    @Nullable
    private InlinePlayer player;
    @Nullable
    private Timeline timeline;
    @Nullable
    private Object manifest;

    public BaseMediaSource() {
    }

    protected abstract void prepareSourceInternal(InlinePlayer var1, boolean var2, @Nullable TransferListener var3);

    protected abstract void releaseSourceInternal();

    protected final void refreshSourceInfo(Timeline timeline, @Nullable Object manifest) {
        this.timeline = timeline;
        this.manifest = manifest;
        for (SourceInfoRefreshListener listener : this.sourceInfoListeners) {
            listener.onSourceInfoRefreshed(this, timeline, manifest);
        }

    }

    protected final EventDispatcher createEventDispatcher(@Nullable MediaPeriodId mediaPeriodId) {
        return this.eventDispatcher.withParameters(0, mediaPeriodId, 0L);
    }

    protected final EventDispatcher createEventDispatcher(MediaPeriodId mediaPeriodId, long mediaTimeOffsetMs) {
        Assertions.checkArgument(mediaPeriodId != null);
        return this.eventDispatcher.withParameters(0, mediaPeriodId, mediaTimeOffsetMs);
    }

    protected final EventDispatcher createEventDispatcher(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, long mediaTimeOffsetMs) {
        return this.eventDispatcher.withParameters(windowIndex, mediaPeriodId, mediaTimeOffsetMs);
    }

    public final void addEventListener(Handler handler, MediaSourceEventListener eventListener) {
        this.eventDispatcher.addEventListener(handler, eventListener);
    }

    public final void removeEventListener(MediaSourceEventListener eventListener) {
        this.eventDispatcher.removeEventListener(eventListener);
    }

    public final void prepareSource(InlinePlayer player, boolean isTopLevelSource, SourceInfoRefreshListener listener, @Nullable TransferListener mediaTransferListener) {
        Assertions.checkArgument(this.player == null || this.player == player);
        this.sourceInfoListeners.add(listener);
        if (this.player == null) {
            this.player = player;
            this.prepareSourceInternal(player, isTopLevelSource, mediaTransferListener);
        } else if (this.timeline != null) {
            listener.onSourceInfoRefreshed(this, this.timeline, this.manifest);
        }

    }

    public final void releaseSource(SourceInfoRefreshListener listener) {
        this.sourceInfoListeners.remove(listener);
        if (this.sourceInfoListeners.isEmpty()) {
            this.player = null;
            this.timeline = null;
            this.manifest = null;
            this.releaseSourceInternal();
        }

    }
}
