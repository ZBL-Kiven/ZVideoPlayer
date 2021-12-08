package com.zj.playerLib.source;

import com.zj.playerLib.Format;
import com.zj.playerLib.FormatHolder;
import com.zj.playerLib.SeekParameters;
import com.zj.playerLib.decoder.DecoderInputBuffer;
import com.zj.playerLib.source.MediaPeriod.Callback;
import com.zj.playerLib.trackselection.TrackSelection;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.MimeTypes;
import com.zj.playerLib.util.Util;
import java.io.IOException;

public final class ClippingMediaPeriod implements MediaPeriod, Callback {
    public final MediaPeriod mediaPeriod;
    private Callback callback;
    private ClippingSampleStream[] sampleStreams;
    private long pendingInitialDiscontinuityPositionUs;
    long startUs;
    long endUs;

    public ClippingMediaPeriod(MediaPeriod mediaPeriod, boolean enableInitialDiscontinuity, long startUs, long endUs) {
        this.mediaPeriod = mediaPeriod;
        this.sampleStreams = new ClippingSampleStream[0];
        this.pendingInitialDiscontinuityPositionUs = enableInitialDiscontinuity ? startUs : -Long.MAX_VALUE;
        this.startUs = startUs;
        this.endUs = endUs;
    }

    public void updateClipping(long startUs, long endUs) {
        this.startUs = startUs;
        this.endUs = endUs;
    }

    public void prepare(Callback callback, long positionUs) {
        this.callback = callback;
        this.mediaPeriod.prepare(this, positionUs);
    }

    public void maybeThrowPrepareError() throws IOException {
        this.mediaPeriod.maybeThrowPrepareError();
    }

    public TrackGroupArray getTrackGroups() {
        return this.mediaPeriod.getTrackGroups();
    }

    public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags, SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
        this.sampleStreams = new ClippingSampleStream[streams.length];
        SampleStream[] childStreams = new SampleStream[streams.length];

        for(int i = 0; i < streams.length; ++i) {
            this.sampleStreams[i] = (ClippingSampleStream)streams[i];
            childStreams[i] = this.sampleStreams[i] != null ? this.sampleStreams[i].childStream : null;
        }

        long enablePositionUs = this.mediaPeriod.selectTracks(selections, mayRetainStreamFlags, childStreams, streamResetFlags, positionUs);
        this.pendingInitialDiscontinuityPositionUs = this.isPendingInitialDiscontinuity() && positionUs == this.startUs && shouldKeepInitialDiscontinuity(this.startUs, selections) ? enablePositionUs : -Long.MAX_VALUE;
        Assertions.checkState(enablePositionUs == positionUs || enablePositionUs >= this.startUs && (this.endUs == -9223372036854775808L || enablePositionUs <= this.endUs));

        for(int i = 0; i < streams.length; ++i) {
            if (childStreams[i] == null) {
                this.sampleStreams[i] = null;
            } else if (streams[i] == null || this.sampleStreams[i].childStream != childStreams[i]) {
                this.sampleStreams[i] = new ClippingSampleStream(childStreams[i]);
            }

            streams[i] = this.sampleStreams[i];
        }

        return enablePositionUs;
    }

    public void discardBuffer(long positionUs, boolean toKeyframe) {
        this.mediaPeriod.discardBuffer(positionUs, toKeyframe);
    }

    public void reevaluateBuffer(long positionUs) {
        this.mediaPeriod.reevaluateBuffer(positionUs);
    }

    public long readDiscontinuity() {
        long discontinuityUs;
        if (this.isPendingInitialDiscontinuity()) {
            discontinuityUs = this.pendingInitialDiscontinuityPositionUs;
            this.pendingInitialDiscontinuityPositionUs = -Long.MAX_VALUE;
            long childDiscontinuityUs = this.readDiscontinuity();
            return childDiscontinuityUs != -Long.MAX_VALUE ? childDiscontinuityUs : discontinuityUs;
        } else {
            discontinuityUs = this.mediaPeriod.readDiscontinuity();
            if (discontinuityUs == -Long.MAX_VALUE) {
                return -Long.MAX_VALUE;
            } else {
                Assertions.checkState(discontinuityUs >= this.startUs);
                Assertions.checkState(this.endUs == -9223372036854775808L || discontinuityUs <= this.endUs);
                return discontinuityUs;
            }
        }
    }

    public long getBufferedPositionUs() {
        long bufferedPositionUs = this.mediaPeriod.getBufferedPositionUs();
        return bufferedPositionUs != -9223372036854775808L && (this.endUs == -9223372036854775808L || bufferedPositionUs < this.endUs) ? bufferedPositionUs : -9223372036854775808L;
    }

    public long seekToUs(long positionUs) {
        this.pendingInitialDiscontinuityPositionUs = -Long.MAX_VALUE;
        ClippingSampleStream[] var3 = this.sampleStreams;
        int var4 = var3.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            ClippingSampleStream sampleStream = var3[var5];
            if (sampleStream != null) {
                sampleStream.clearSentEos();
            }
        }

        long seekUs = this.mediaPeriod.seekToUs(positionUs);
        Assertions.checkState(seekUs == positionUs || seekUs >= this.startUs && (this.endUs == -9223372036854775808L || seekUs <= this.endUs));
        return seekUs;
    }

    public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
        if (positionUs == this.startUs) {
            return this.startUs;
        } else {
            SeekParameters clippedSeekParameters = this.clipSeekParameters(positionUs, seekParameters);
            return this.mediaPeriod.getAdjustedSeekPositionUs(positionUs, clippedSeekParameters);
        }
    }

    public long getNextLoadPositionUs() {
        long nextLoadPositionUs = this.mediaPeriod.getNextLoadPositionUs();
        return nextLoadPositionUs != -9223372036854775808L && (this.endUs == -9223372036854775808L || nextLoadPositionUs < this.endUs) ? nextLoadPositionUs : -9223372036854775808L;
    }

    public boolean continueLoading(long positionUs) {
        return this.mediaPeriod.continueLoading(positionUs);
    }

    public void onPrepared(MediaPeriod mediaPeriod) {
        this.callback.onPrepared(this);
    }

    public void onContinueLoadingRequested(MediaPeriod source) {
        this.callback.onContinueLoadingRequested(this);
    }

    boolean isPendingInitialDiscontinuity() {
        return this.pendingInitialDiscontinuityPositionUs != -Long.MAX_VALUE;
    }

    private SeekParameters clipSeekParameters(long positionUs, SeekParameters seekParameters) {
        long toleranceBeforeUs = Util.constrainValue(seekParameters.toleranceBeforeUs, 0L, positionUs - this.startUs);
        long toleranceAfterUs = Util.constrainValue(seekParameters.toleranceAfterUs, 0L, this.endUs == -9223372036854775808L ? Long.MAX_VALUE : this.endUs - positionUs);
        return toleranceBeforeUs == seekParameters.toleranceBeforeUs && toleranceAfterUs == seekParameters.toleranceAfterUs ? seekParameters : new SeekParameters(toleranceBeforeUs, toleranceAfterUs);
    }

    private static boolean shouldKeepInitialDiscontinuity(long startUs, TrackSelection[] selections) {
        if (startUs != 0L) {
            TrackSelection[] var3 = selections;
            int var4 = selections.length;

            for(int var5 = 0; var5 < var4; ++var5) {
                TrackSelection trackSelection = var3[var5];
                if (trackSelection != null) {
                    Format selectedFormat = trackSelection.getSelectedFormat();
                    if (!MimeTypes.isAudio(selectedFormat.sampleMimeType)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private final class ClippingSampleStream implements SampleStream {
        public final SampleStream childStream;
        private boolean sentEos;

        public ClippingSampleStream(SampleStream childStream) {
            this.childStream = childStream;
        }

        public void clearSentEos() {
            this.sentEos = false;
        }

        public boolean isReady() {
            return !ClippingMediaPeriod.this.isPendingInitialDiscontinuity() && this.childStream.isReady();
        }

        public void maybeThrowError() throws IOException {
            this.childStream.maybeThrowError();
        }

        public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean requireFormat) {
            if (ClippingMediaPeriod.this.isPendingInitialDiscontinuity()) {
                return -3;
            } else if (this.sentEos) {
                buffer.setFlags(4);
                return -4;
            } else {
                int result = this.childStream.readData(formatHolder, buffer, requireFormat);
                if (result == -5) {
                    Format format = formatHolder.format;
                    if (format.encoderDelay != 0 || format.encoderPadding != 0) {
                        int encoderDelay = ClippingMediaPeriod.this.startUs != 0L ? 0 : format.encoderDelay;
                        int encoderPadding = ClippingMediaPeriod.this.endUs != -9223372036854775808L ? 0 : format.encoderPadding;
                        formatHolder.format = format.copyWithGapLessInfo(encoderDelay, encoderPadding);
                    }

                    return -5;
                } else if (ClippingMediaPeriod.this.endUs == -9223372036854775808L || (result != -4 || buffer.timeUs < ClippingMediaPeriod.this.endUs) && (result != -3 || ClippingMediaPeriod.this.getBufferedPositionUs() != -9223372036854775808L)) {
                    return result;
                } else {
                    buffer.clear();
                    buffer.setFlags(4);
                    this.sentEos = true;
                    return -4;
                }
            }
        }

        public int skipData(long positionUs) {
            return ClippingMediaPeriod.this.isPendingInitialDiscontinuity() ? -3 : this.childStream.skipData(positionUs);
        }
    }
}
