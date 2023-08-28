package com.zj.playerLib;

import androidx.annotation.Nullable;

import com.zj.playerLib.Timeline.Window;
import com.zj.playerLib.util.Util;

public abstract class BasePlayer implements Player {
    protected final Window window = new Window();

    public BasePlayer() {
    }

    public final void seekToDefaultPosition() {
        this.seekToDefaultPosition(this.getCurrentWindowIndex());
    }

    public final void seekToDefaultPosition(int windowIndex) {
        this.seekTo(windowIndex, -Long.MAX_VALUE);
    }

    public final void seekTo(long positionMs) {
        this.seekTo(this.getCurrentWindowIndex(), positionMs);
    }

    public final boolean hasPrevious() {
        return this.getPreviousWindowIndex() != -1;
    }

    public final void previous() {
        int previousWindowIndex = this.getPreviousWindowIndex();
        if (previousWindowIndex != -1) {
            this.seekToDefaultPosition(previousWindowIndex);
        }

    }

    public final boolean hasNext() {
        return this.getNextWindowIndex() != -1;
    }

    public final void next() {
        int nextWindowIndex = this.getNextWindowIndex();
        if (nextWindowIndex != -1) {
            this.seekToDefaultPosition(nextWindowIndex);
        }

    }

    public final void stop() {
        this.stop(false);
    }

    public final int getNextWindowIndex() {
        Timeline timeline = this.getCurrentTimeline();
        return timeline.isEmpty() ? -1 : timeline.getNextWindowIndex(this.getCurrentWindowIndex(), this.getRepeatModeForNavigation(), this.getShuffleModeEnabled());
    }

    public final int getPreviousWindowIndex() {
        Timeline timeline = this.getCurrentTimeline();
        return timeline.isEmpty() ? -1 : timeline.getPreviousWindowIndex(this.getCurrentWindowIndex(), this.getRepeatModeForNavigation(), this.getShuffleModeEnabled());
    }

    @Nullable
    public final Object getCurrentTag() {
        int windowIndex = this.getCurrentWindowIndex();
        Timeline timeline = this.getCurrentTimeline();
        return windowIndex >= timeline.getWindowCount() ? null : timeline.getWindow(windowIndex, this.window, true).tag;
    }

    public final int getBufferedPercentage() {
        long position = this.getBufferedPosition();
        long duration = this.getDuration();
        return position != -Long.MAX_VALUE && duration != -Long.MAX_VALUE ? (duration == 0L ? 100 : Util.constrainValue((int)(position * 100L / duration), 0, 100)) : 0;
    }

    public final boolean isCurrentWindowDynamic() {
        Timeline timeline = this.getCurrentTimeline();
        return !timeline.isEmpty() && timeline.getWindow(this.getCurrentWindowIndex(), this.window).isDynamic;
    }

    public final boolean isCurrentWindowSeekable() {
        Timeline timeline = this.getCurrentTimeline();
        return !timeline.isEmpty() && timeline.getWindow(this.getCurrentWindowIndex(), this.window).isSeekable;
    }

    public final long getContentDuration() {
        Timeline timeline = this.getCurrentTimeline();
        return timeline.isEmpty() ? -Long.MAX_VALUE : timeline.getWindow(this.getCurrentWindowIndex(), this.window).getDurationMs();
    }

    private int getRepeatModeForNavigation() {
        int repeatMode = this.getRepeatMode();
        return repeatMode == 1 ? 0 : repeatMode;
    }
}
