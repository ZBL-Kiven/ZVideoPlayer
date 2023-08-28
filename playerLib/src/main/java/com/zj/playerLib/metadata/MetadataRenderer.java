package com.zj.playerLib.metadata;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.Nullable;

import com.zj.playerLib.BaseRenderer;
import com.zj.playerLib.Format;
import com.zj.playerLib.FormatHolder;
import com.zj.playerLib.PlaybackException;
import com.zj.playerLib.drm.DrmSessionManager;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

import java.util.Arrays;

public final class MetadataRenderer extends BaseRenderer implements Callback {
    private static final int MSG_INVOKE_RENDERER = 0;
    private static final int MAX_PENDING_METADATA_COUNT = 5;
    private final MetadataDecoderFactory decoderFactory;
    private final MetadataOutput output;
    @Nullable
    private final Handler outputHandler;
    private final FormatHolder formatHolder;
    private final MetadataInputBuffer buffer;
    private final Metadata[] pendingMetadata;
    private final long[] pendingMetadataTimestamps;
    private int pendingMetadataIndex;
    private int pendingMetadataCount;
    private MetadataDecoder decoder;
    private boolean inputStreamEnded;

    public MetadataRenderer(MetadataOutput output, @Nullable Looper outputLooper) {
        this(output, outputLooper, MetadataDecoderFactory.DEFAULT);
    }

    public MetadataRenderer(MetadataOutput output, @Nullable Looper outputLooper, MetadataDecoderFactory decoderFactory) {
        super(4);
        this.output = Assertions.checkNotNull(output);
        this.outputHandler = outputLooper == null ? null : Util.createHandler(outputLooper, this);
        this.decoderFactory = Assertions.checkNotNull(decoderFactory);
        this.formatHolder = new FormatHolder();
        this.buffer = new MetadataInputBuffer();
        this.pendingMetadata = new Metadata[5];
        this.pendingMetadataTimestamps = new long[5];
    }

    public int supportsFormat(Format format) {
        if (this.decoderFactory.supportsFormat(format)) {
            return supportsFormatDrm(null, format.drmInitData) ? 4 : 2;
        } else {
            return 0;
        }
    }

    protected void onStreamChanged(Format[] formats, long offsetUs) throws PlaybackException {
        this.decoder = this.decoderFactory.createDecoder(formats[0]);
    }

    protected void onPositionReset(long positionUs, boolean joining) {
        this.flushPendingMetadata();
        this.inputStreamEnded = false;
    }

    public void render(long positionUs, long elapsedRealtimeUs) throws PlaybackException {
        if (!this.inputStreamEnded && this.pendingMetadataCount < 5) {
            this.buffer.clear();
            int result = this.readSource(this.formatHolder, this.buffer, false);
            if (result == -4) {
                if (this.buffer.isEndOfStream()) {
                    this.inputStreamEnded = true;
                } else if (!this.buffer.isDecodeOnly()) {
                    this.buffer.subSampleOffsetUs = this.formatHolder.format.subSampleOffsetUs;
                    this.buffer.flip();
                    int index = (this.pendingMetadataIndex + this.pendingMetadataCount) % 5;
                    Metadata metadata = this.decoder.decode(this.buffer);
                    if (metadata != null) {
                        this.pendingMetadata[index] = metadata;
                        this.pendingMetadataTimestamps[index] = this.buffer.timeUs;
                        ++this.pendingMetadataCount;
                    }
                }
            }
        }

        if (this.pendingMetadataCount > 0 && this.pendingMetadataTimestamps[this.pendingMetadataIndex] <= positionUs) {
            this.invokeRenderer(this.pendingMetadata[this.pendingMetadataIndex]);
            this.pendingMetadata[this.pendingMetadataIndex] = null;
            this.pendingMetadataIndex = (this.pendingMetadataIndex + 1) % 5;
            --this.pendingMetadataCount;
        }

    }

    protected void onDisabled() {
        this.flushPendingMetadata();
        this.decoder = null;
    }

    public boolean isEnded() {
        return this.inputStreamEnded;
    }

    public boolean isReady() {
        return true;
    }

    private void invokeRenderer(Metadata metadata) {
        if (this.outputHandler != null) {
            this.outputHandler.obtainMessage(0, metadata).sendToTarget();
        } else {
            this.invokeRendererInternal(metadata);
        }

    }

    private void flushPendingMetadata() {
        Arrays.fill(this.pendingMetadata, null);
        this.pendingMetadataIndex = 0;
        this.pendingMetadataCount = 0;
    }

    public boolean handleMessage(Message msg) {
        switch(msg.what) {
        case 0:
            this.invokeRendererInternal((Metadata)msg.obj);
            return true;
        default:
            throw new IllegalStateException();
        }
    }

    private void invokeRendererInternal(Metadata metadata) {
        this.output.onMetadata(metadata);
    }

    /** @deprecated */
    @Deprecated
    public interface Output extends MetadataOutput {
    }
}
