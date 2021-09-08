//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib;

import androidx.annotation.Nullable;

import com.zj.playerLib.decoder.DecoderInputBuffer;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.drm.DrmSessionManager;
import com.zj.playerLib.source.SampleStream;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.MediaClock;

import java.io.IOException;

public abstract class BaseRenderer implements Renderer, RendererCapabilities {
    private final int trackType;
    private RendererConfiguration configuration;
    private int index;
    private int state;
    private SampleStream stream;
    private Format[] streamFormats;
    private long streamOffsetUs;
    private boolean readEndOfStream;
    private boolean streamIsFinal;

    public BaseRenderer(int trackType) {
        this.trackType = trackType;
        this.readEndOfStream = true;
    }

    public final int getTrackType() {
        return this.trackType;
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
        this.readEndOfStream = false;
        this.streamFormats = formats;
        this.streamOffsetUs = offsetUs;
        this.onStreamChanged(formats, offsetUs);
    }

    public final SampleStream getStream() {
        return this.stream;
    }

    public final boolean hasReadStreamToEnd() {
        return this.readEndOfStream;
    }

    public final void setCurrentStreamFinal() {
        this.streamIsFinal = true;
    }

    public final boolean isCurrentStreamFinal() {
        return this.streamIsFinal;
    }

    public final void maybeThrowStreamError() throws IOException {
        this.stream.maybeThrowError();
    }

    public final void resetPosition(long positionUs) throws PlaybackException {
        this.streamIsFinal = false;
        this.readEndOfStream = false;
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
        this.streamFormats = null;
        this.streamIsFinal = false;
        this.onDisabled();
    }

    public int supportsMixedMimeTypeAdaptation() throws PlaybackException {
        return 0;
    }

    public void handleMessage(int what, @Nullable Object object) throws PlaybackException {
    }

    protected void onEnabled(boolean joining) throws PlaybackException {
    }

    protected void onStreamChanged(Format[] formats, long offsetUs) throws PlaybackException {
    }

    protected void onPositionReset(long positionUs, boolean joining) throws PlaybackException {
    }

    protected void onStarted() throws PlaybackException {
    }

    protected void onStopped() throws PlaybackException {
    }

    protected void onDisabled() {
    }

    protected final Format[] getStreamFormats() {
        return this.streamFormats;
    }

    protected final RendererConfiguration getConfiguration() {
        return this.configuration;
    }

    protected final int getIndex() {
        return this.index;
    }

    protected final int readSource(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired) {
        int result = this.stream.readData(formatHolder, buffer, formatRequired);
        if (result == -4) {
            if (buffer.isEndOfStream()) {
                this.readEndOfStream = true;
                return this.streamIsFinal ? -4 : -3;
            }

            buffer.timeUs += this.streamOffsetUs;
        } else if (result == -5) {
            Format format = formatHolder.format;
            if (format.subSampleOffsetUs != 9223372036854775807L) {
                format = format.copyWithSubSampleOffsetUs(format.subSampleOffsetUs + this.streamOffsetUs);
                formatHolder.format = format;
            }
        }

        return result;
    }

    protected int skipSource(long positionUs) {
        return this.stream.skipData(positionUs - this.streamOffsetUs);
    }

    protected final boolean isSourceReady() {
        return this.readEndOfStream ? this.streamIsFinal : this.stream.isReady();
    }

    protected static boolean supportsFormatDrm(@Nullable DrmSessionManager<?> drmSessionManager, @Nullable DrmInitData drmInitData) {
        if (drmInitData == null) {
            return true;
        } else {
            return drmSessionManager == null ? false : drmSessionManager.canAcquireSession(drmInitData);
        }
    }
}
