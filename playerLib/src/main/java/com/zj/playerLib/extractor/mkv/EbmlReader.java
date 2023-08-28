package com.zj.playerLib.extractor.mkv;

import com.zj.playerLib.extractor.ExtractorInput;

import java.io.IOException;

interface EbmlReader {
    void init(EbmlReaderOutput var1);

    void reset();

    boolean read(ExtractorInput var1) throws IOException, InterruptedException;
}
