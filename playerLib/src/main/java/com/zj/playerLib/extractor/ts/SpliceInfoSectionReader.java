package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.Format;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.TimestampAdjuster;

public final class SpliceInfoSectionReader implements SectionPayloadReader {
    private TimestampAdjuster timestampAdjuster;
    private TrackOutput output;
    private boolean formatDeclared;

    public SpliceInfoSectionReader() {
    }

    public void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
        this.timestampAdjuster = timestampAdjuster;
        idGenerator.generateNewId();
        this.output = extractorOutput.track(idGenerator.getTrackId(), 4);
        this.output.format(Format.createSampleFormat(idGenerator.getFormatId(), "application/x-scte35", null, -1, null));
    }

    public void consume(ParsableByteArray sectionData) {
        if (!this.formatDeclared) {
            if (this.timestampAdjuster.getTimestampOffsetUs() == -Long.MAX_VALUE) {
                return;
            }

            this.output.format(Format.createSampleFormat(null, "application/x-scte35", this.timestampAdjuster.getTimestampOffsetUs()));
            this.formatDeclared = true;
        }

        int sampleSize = sectionData.bytesLeft();
        this.output.sampleData(sectionData, sampleSize);
        this.output.sampleMetadata(this.timestampAdjuster.getLastAdjustedTimestampUs(), 1, sampleSize, 0, null);
    }
}
