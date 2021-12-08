package com.zj.playerLib.source.chunk;

import com.zj.playerLib.Format;
import com.zj.playerLib.upstream.DataSource;
import com.zj.playerLib.upstream.DataSpec;

public abstract class BaseMediaChunk extends MediaChunk {
    public final long clippedStartTimeUs;
    public final long clippedEndTimeUs;
    private BaseMediaChunkOutput output;
    private int[] firstSampleIndices;

    public BaseMediaChunk(DataSource dataSource, DataSpec dataSpec, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long startTimeUs, long endTimeUs, long clippedStartTimeUs, long clippedEndTimeUs, long chunkIndex) {
        super(dataSource, dataSpec, trackFormat, trackSelectionReason, trackSelectionData, startTimeUs, endTimeUs, chunkIndex);
        this.clippedStartTimeUs = clippedStartTimeUs;
        this.clippedEndTimeUs = clippedEndTimeUs;
    }

    public void init(BaseMediaChunkOutput output) {
        this.output = output;
        this.firstSampleIndices = output.getWriteIndices();
    }

    public final int getFirstSampleIndex(int trackIndex) {
        return this.firstSampleIndices[trackIndex];
    }

    protected final BaseMediaChunkOutput getOutput() {
        return this.output;
    }
}
