package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.TimestampAdjuster;

public interface SectionPayloadReader {
    void init(TimestampAdjuster var1, ExtractorOutput var2, TrackIdGenerator var3);

    void consume(ParsableByteArray var1);
}
