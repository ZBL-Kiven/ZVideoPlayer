package com.zj.playerLib.source.chunk;

public final class ChunkHolder {
    public Chunk chunk;
    public boolean endOfStream;

    public ChunkHolder() {
    }

    public void clear() {
        this.chunk = null;
        this.endOfStream = false;
    }
}
