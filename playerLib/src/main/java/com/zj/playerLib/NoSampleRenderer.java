package com.zj.playerLib;

import androidx.annotation.Nullable;

import com.zj.playerLib.source.SampleStream;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.MediaClock;

import java.io.IOException;

public abstract class NoSampleRenderer implements Renderer, RendererCapabilities {
    private RendererConfiguration configuration;
    private int index;
    private int state;
    private SampleStream stream;
    private boolean streamIsFinal;

    public NoSampleRenderer() {
    }

    public final int getTrackType() {
        return 6;
    }

    public final RendererCapabilities getCapabilities() {
        return this;
    }

    public final void setIndex(int index) {
        this.index = index;
    }

    public MediaClock getMediaClock() {
        return null;
    }

    public final int getState() {
        return this.state;
    }

    public final void enable(RendererConfiguration configuration, Format[] formats, SampleStream stream, long positionUs, boolean joining, long offsetUs) throws PlaybackException {
        Assertions.checkState(this.state == 0);
        this.configuration = configuration;
        this.state = 1;
        this.onEnabled(joining);
        this.replaceStream(formats, stream, offsetUs);
        this.onPositionReset(positionUs, joining);
    }

    public final void start() throws PlaybackException {
        Assertions.checkState(this.state == 1);
        this.state = 2;
        this.onStarted();
    }

    public final void replaceStream(Format[] formats, SampleStream stream, long offsetUs) throws PlaybackException {
        Assertions.checkState(!this.streamIsFinal);
        this.stream = stream;
        this.onRendererOffsetChanged(offsetUs);
    }

    public final SampleStream getStream() {
        return this.stream;
    }

    public final boolean hasReadStreamToEnd() {
        return true;
    }

    public final void setCurrentStreamFinal() {
        this.streamIsFinal = true;
    }

    public final boolean isCurrentStreamFinal() {
        return this.streamIsFinal;
    }

    public final void maybeThrowStreamError() throws IOException {
    }

    public final void resetPosition(long positionUs) throws PlaybackException {
        this.streamIsFinal = false;
        this.onPositionReset(positionUs, false);
    }

    public final void stop() throws PlaybackException {
        Assertions.checkState(this.state == 2);
        this.state = 1;
        this.onStopped();
    }

    public final void disable() {
        Assertions.checkState(this.state == 1);
        this.state = 0;
        this.stream = null;
        this.streamIsFinal = false;
        this.onDisabled();
    }

    public boolean isReady() {
        return true;
    }

    public boolean isEnded() {
        return true;
    }

    public int supportsFormat(Format format) throws PlaybackException {
        return 0;
    }

    public int supportsMixedMimeTypeAdaptation() throws PlaybackException {
        return 0;
    }

    public void handleMessage(int what, @Nullable Object object) throws PlaybackException {
    }

    protected void onEnabled(boolean joining) throws PlaybackException {
    }

    protected void onRendererOffsetChanged(long offsetUs) throws PlaybackException {
    }

    protected void onPositionReset(long positionUs, boolean joining) throws PlaybackException {
    }

    protected void onStarted() throws PlaybackException {
    }

    protected void onStopped() throws PlaybackException {
    }

    protected void onDisabled() {
    }

    protected final RendererConfiguration getConfiguration() {
        return this.configuration;
    }

    protected final int getIndex() {
        return this.index;
    }
}
