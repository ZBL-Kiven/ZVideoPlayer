package com.zj.playerLib.source.chunk;

import androidx.annotation.Nullable;

import com.zj.playerLib.Format;
import com.zj.playerLib.extractor.DefaultExtractorInput;
import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.source.chunk.ChunkExtractorWrapper.TrackOutputProvider;
import com.zj.playerLib.upstream.DataSource;
import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

import java.io.IOException;

public final class InitializationChunk extends Chunk {
    private static final PositionHolder DUMMY_POSITION_HOLDER = new PositionHolder();
    private final ChunkExtractorWrapper extractorWrapper;
    private long nextLoadPosition;
    private volatile boolean loadCanceled;

    public InitializationChunk(DataSource dataSource, DataSpec dataSpec, Format trackFormat, int trackSelectionReason, @Nullable Object trackSelectionData, ChunkExtractorWrapper extractorWrapper) {
        super(dataSource, dataSpec, 2, trackFormat, trackSelectionReason, trackSelectionData, -Long.MAX_VALUE, -Long.MAX_VALUE);
        this.extractorWrapper = extractorWrapper;
    }

    public void cancelLoad() {
        this.loadCanceled = true;
    }

    public void load() throws IOException, InterruptedException {
        DataSpec loadDataSpec = this.dataSpec.subrange(this.nextLoadPosition);

        try {
            ExtractorInput input = new DefaultExtractorInput(this.dataSource, loadDataSpec.absoluteStreamPosition, this.dataSource.open(loadDataSpec));
            if (this.nextLoadPosition == 0L) {
                this.extractorWrapper.init(null, -Long.MAX_VALUE, -Long.MAX_VALUE);
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
    }
}
