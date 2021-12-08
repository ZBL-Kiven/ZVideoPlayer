package com.zj.playerLib.text;

import com.zj.playerLib.decoder.DecoderInputBuffer;

public class SubtitleInputBuffer extends DecoderInputBuffer {
    public long subSampleOffsetUs;

    public SubtitleInputBuffer() {
        super(1);
    }
}
