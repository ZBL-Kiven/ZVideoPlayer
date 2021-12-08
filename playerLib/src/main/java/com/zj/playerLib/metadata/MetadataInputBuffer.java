package com.zj.playerLib.metadata;

import com.zj.playerLib.decoder.DecoderInputBuffer;

public final class MetadataInputBuffer extends DecoderInputBuffer {
    public long subSampleOffsetUs;

    public MetadataInputBuffer() {
        super(1);
    }
}
