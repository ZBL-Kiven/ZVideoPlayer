package com.zj.playerLib.decoder;

public final class DecoderCounters {
    public int decoderInitCount;
    public int decoderReleaseCount;
    public int inputBufferCount;
    public int skippedInputBufferCount;
    public int renderedOutputBufferCount;
    public int skippedOutputBufferCount;
    public int droppedBufferCount;
    public int maxConsecutiveDroppedBufferCount;
    public int droppedToKeyframeCount;

    public DecoderCounters() {
    }

    public synchronized void ensureUpdated() {
    }

    public void merge(DecoderCounters other) {
        this.decoderInitCount += other.decoderInitCount;
        this.decoderReleaseCount += other.decoderReleaseCount;
        this.inputBufferCount += other.inputBufferCount;
        this.skippedInputBufferCount += other.skippedInputBufferCount;
        this.renderedOutputBufferCount += other.renderedOutputBufferCount;
        this.skippedOutputBufferCount += other.skippedOutputBufferCount;
        this.droppedBufferCount += other.droppedBufferCount;
        this.maxConsecutiveDroppedBufferCount = Math.max(this.maxConsecutiveDroppedBufferCount, other.maxConsecutiveDroppedBufferCount);
        this.droppedToKeyframeCount += other.droppedToKeyframeCount;
    }
}
