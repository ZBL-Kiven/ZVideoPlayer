package com.zj.playerLib.source.chunk;

import com.zj.playerLib.Format;
import com.zj.playerLib.extractor.DefaultExtractorInput;
import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.upstream.DataSource;
import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;
import java.io.IOException;

public class ContainerMediaChunk extends BaseMediaChunk {
    private static final PositionHolder DUMMY_POSITION_HOLDER = new PositionHolder();
    private final int chunkCount;
    private final long sampleOffsetUs;
    private final ChunkExtractorWrapper extractorWrapper;
    private long nextLoadPosition;
    private volatile boolean loadCanceled;
    private boolean loadCompleted;

    public ContainerMediaChunk(DataSource dataSource, DataSpec dataSpec, Format trackFormat, int trackSelectionReason, Object trackSelectionData, long startTimeUs, long endTimeUs, long clippedStartTimeUs, long clippedEndTimeUs, long chunkIndex, int chunkCount, long sampleOffsetUs, ChunkExtractorWrapper extractorWrapper) {
        super(dataSource, dataSpec, trackFormat, trackSelectionReason, trackSelectionData, startTimeUs, endTimeUs, clippedStartTimeUs, clippedEndTimeUs, chunkIndex);
        this.chunkCount = chunkCount;
        this.sampleOffsetUs = sampleOffsetUs;
        this.extractorWrapper = extractorWrapper;
    }

    public long getNextChunkIndex() {
        return this.chunkIndex + (long)this.chunkCount;
    }

    public boolean isLoadCompleted() {
        return this.loadCompleted;
    }

    public final void cancelLoad() {
        this.loadCanceled = true;
    }

    public final void load() throws IOException, InterruptedException {
        DataSpec loadDataSpec = this.dataSpec.subrange(this.nextLoadPosition);

        try {
            ExtractorInput input = new DefaultExtractorInput(this.dataSource, loadDataSpec.absoluteStreamPosition, this.dataSource.open(loadDataSpec));
            if (this.nextLoadPosition == 0L) {
                BaseMediaChunkOutput output = this.getOutput();
                output.setSampleOffsetUs(this.sampleOffsetUs);
                this.extractorWrapper.init(output, this.clippedStartTimeUs == -Long.MAX_VALUE ? -Long.MAX_VALUE : this.clippedStartTimeUs - this.sampleOffsetUs, this.clippedEndTimeUs == -Long.MAX_VALUE ? -Long.MAX_VALUE : this.clippedEndTimeUs - this.sampleOffsetUs);
            }

            try {
                Extractor extractor = this.extractorWrapper.extractor;

                int result;
                for(result = 0; result == 0 && !this.loadCanceled; result = extractor.read(input, DUMMY_POSITION_HOLDER)) {
                }

                Assertions.checkState(result != 1);
            } finally {
                this.nextLoadPosition = input.getPosition() - this.dataSpec.absoluteStreamPosition;
            }
        } finally {
            Util.closeQuietly(this.dataSource);
        }

        this.loadCompleted = true;
    }
}
