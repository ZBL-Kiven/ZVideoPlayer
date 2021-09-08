//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source.chunk;

import com.zj.playerLib.Format;
import com.zj.playerLib.extractor.DefaultExtractorInput;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.upstream.DataSource;
import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.util.Util;

import java.io.IOException;

public final class SingleSampleMediaChunk extends BaseMediaChunk {
    private final int trackType;
    private final Format sampleFormat;
    private long nextLoadPosition;
    private boolean loadCompleted;

    public SingleSampleMediaChunk(DataSource dataSource, DataSpec dataSpec, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long startTimeUs, long endTimeUs, long chunkIndex, int trackType, Format sampleFormat) {
        super(dataSource, dataSpec, trackFormat, trackSelectionReason, trackSelectionData, startTimeUs, endTimeUs, -9223372036854775807L, -9223372036854775807L, chunkIndex);
        this.trackType = trackType;
        this.sampleFormat = sampleFormat;
    }

    public boolean isLoadCompleted() {
        return this.loadCompleted;
    }

    public void cancelLoad() {
    }

    public void load() throws IOException, InterruptedException {
        DataSpec loadDataSpec = this.dataSpec.subrange(this.nextLoadPosition);

        try {
            long length = this.dataSource.open(loadDataSpec);
            if (length != -1L) {
                length += this.nextLoadPosition;
            }

            ExtractorInput extractorInput = new DefaultExtractorInput(this.dataSource, this.nextLoadPosition, length);
            BaseMediaChunkOutput output = this.getOutput();
            output.setSampleOffsetUs(0L);
            TrackOutput trackOutput = output.track(0, this.trackType);
            trackOutput.format(this.sampleFormat);
            int result = 0;

            while(true) {
                if (result == -1) {
                    int sampleSize = (int)this.nextLoadPosition;
                    trackOutput.sampleMetadata(this.startTimeUs, 1, sampleSize, 0, (CryptoData)null);
                    break;
                }

                this.nextLoadPosition += (long)result;
                result = trackOutput.sampleData(extractorInput, 2147483647, true);
            }
        } finally {
            Util.closeQuietly(this.dataSource);
        }

        this.loadCompleted = true;
    }
}
