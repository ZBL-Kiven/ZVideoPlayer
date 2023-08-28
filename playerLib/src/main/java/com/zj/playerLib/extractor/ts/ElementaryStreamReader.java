package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.util.ParsableByteArray;

public interface ElementaryStreamReader {
    void seek();

    void createTracks(ExtractorOutput var1, TrackIdGenerator var2);

    void packetStarted(long var1, int var3);

    void consume(ParsableByteArray var1) throws ParserException;

    void packetFinished();
}
