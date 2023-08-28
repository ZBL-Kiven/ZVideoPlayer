package com.zj.playerLib.source.chunk;

import com.zj.playerLib.SeekParameters;

import java.io.IOException;
import java.util.List;

public interface ChunkSource {
    long getAdjustedSeekPositionUs(long var1, SeekParameters var3);

    void maybeThrowError() throws IOException;

    int getPreferredQueueSize(long var1, List<? extends MediaChunk> var3);

    void getNextChunk(long var1, long var3, List<? extends MediaChunk> var5, ChunkHolder var6);

    void onChunkLoadCompleted(Chunk var1);

    boolean onChunkLoadError(Chunk var1, boolean var2, Exception var3, long var4);
}
