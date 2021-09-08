//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source;

import androidx.annotation.Nullable;

import com.zj.playerLib.SeekParameters;
import com.zj.playerLib.source.MediaPeriod.Callback;
import com.zj.playerLib.source.MediaSource.MediaPeriodId;
import com.zj.playerLib.trackselection.TrackSelection;
import com.zj.playerLib.upstream.Allocator;

import java.io.IOException;

public final class DeferredMediaPeriod implements MediaPeriod, Callback {
    public final MediaSource mediaSource;
    public final MediaPeriodId id;
    private final Allocator allocator;
    private MediaPeriod mediaPeriod;
    private Callback callback;
    private long preparePositionUs;
    @Nullable
    private DeferredMediaPeriod.PrepareErrorListener listener;
    private boolean notifiedPrepareError;
    private long preparePositionOverrideUs;

    public DeferredMediaPeriod(MediaSource mediaSource, MediaPeriodId id, Allocator allocator) {
        this.id = id;
        this.allocator = allocator;
        this.mediaSource = mediaSource;
        this.preparePositionOverrideUs = -9223372036854775807L;
    }

    public void setPrepareErrorListener(PrepareErrorListener listener) {
        this.listener = listener;
    }

    public long getPreparePositionUs() {
        return this.preparePositionUs;
    }

    public void overridePreparePositionUs(long defaultPreparePositionUs) {
        this.preparePositionOverrideUs = defaultPreparePositionUs;
    }

    public void createPeriod(MediaPeriodId id) {
        this.mediaPeriod = this.mediaSource.createPeriod(id, this.allocator);
        if (this.callback != null) {
            long preparePositionUs = this.preparePositionOverrideUs != -9223372036854775807L ? this.preparePositionOverrideUs : this.preparePositionUs;
            this.mediaPeriod.prepare(this, preparePositionUs);
        }

    }

    public void releasePeriod() {
        if (this.mediaPeriod != null) {
            this.mediaSource.releasePeriod(this.mediaPeriod);
        }

    }

    public void prepare(Callback callback, long preparePositionUs) {
        this.callback = callback;
        this.preparePositionUs = preparePositionUs;
        if (this.mediaPeriod != null) {
            this.mediaPeriod.prepare(this, preparePositionUs);
        }

    }

    public void maybeThrowPrepareError() throws IOException {
        try {
            if (this.mediaPeriod != null) {
                this.mediaPeriod.maybeThrowPrepareError();
            } else {
                this.mediaSource.maybeThrowSourceInfoRefreshError();
            }
        } catch (IOException var2) {
            if (this.listener == null) {
                throw var2;
            }

            if (!this.notifiedPrepareError) {
                this.notifiedPrepareError = true;
                this.listener.onPrepareError(this.id, var2);
            }
        }

    }

    public TrackGroupArray getTrackGroups() {
        return this.mediaPeriod.getTrackGroups();
    }

    public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags, SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
        if (this.preparePositionOverrideUs != -9223372036854775807L && positionUs == this.preparePositionUs) {
            positionUs = this.preparePositionOverrideUs;
            this.preparePositionOverrideUs = -9223372036854775807L;
        }

        return this.mediaPeriod.selectTracks(selections, mayRetainStreamFlags, streams, streamResetFlags, positionUs);
    }

    public void discardBuffer(long positionUs, boolean toKeyframe) {
        this.mediaPeriod.discardBuffer(positionUs, toKeyframe);
    }

    public long readDiscontinuity() {
        return this.mediaPeriod.readDiscontinuity();
    }

    public long getBufferedPositionUs() {
        return this.mediaPeriod.getBufferedPositionUs();
    }

    public long seekToUs(long positionUs) {
        return this.mediaPeriod.seekToUs(positionUs);
    }

    public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
        return this.mediaPeriod.getAdjustedSeekPositionUs(positionUs, seekParameters);
    }

    public long getNextLoadPositionUs() {
        return this.mediaPeriod.getNextLoadPositionUs();
    }

    public void reevaluateBuffer(long positionUs) {
        this.mediaPeriod.reevaluateBuffer(positionUs);
    }

    public boolean continueLoading(long positionUs) {
        return this.mediaPeriod != null && this.mediaPeriod.continueLoading(positionUs);
    }

    public void onContinueLoadingRequested(MediaPeriod source) {
        this.callback.onContinueLoadingRequested(this);
    }

    public void onPrepared(MediaPeriod mediaPeriod) {
        this.callback.onPrepared(this);
    }

    public interface PrepareErrorListener {
        void onPrepareError(MediaPeriodId var1, IOException var2);
    }
}
