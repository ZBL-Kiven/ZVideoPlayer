package com.zj.playerLib.decoder;

public abstract class OutputBuffer extends Buffer {
    public long timeUs;
    public int skippedOutputBufferCount;

    public OutputBuffer() {
    }

    public abstract void release();
}
