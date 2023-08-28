package com.zj.playerLib.extractor.ogg;

import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.SeekMap;

import java.io.IOException;

interface OggSeeker {
    SeekMap createSeekMap();

    long startSeek(long var1);

    long read(ExtractorInput var1) throws IOException, InterruptedException;
}
