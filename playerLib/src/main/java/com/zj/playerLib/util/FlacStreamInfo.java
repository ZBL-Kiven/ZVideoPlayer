package com.zj.playerLib.util;

public final class FlacStreamInfo {
    public final int minBlockSize;
    public final int maxBlockSize;
    public final int minFrameSize;
    public final int maxFrameSize;
    public final int sampleRate;
    public final int channels;
    public final int bitsPerSample;
    public final long totalSamples;

    public FlacStreamInfo(byte[] data, int offset) {
        ParsableBitArray scratch = new ParsableBitArray(data);
        scratch.setPosition(offset * 8);
        this.minBlockSize = scratch.readBits(16);
        this.maxBlockSize = scratch.readBits(16);
        this.minFrameSize = scratch.readBits(24);
        this.maxFrameSize = scratch.readBits(24);
        this.sampleRate = scratch.readBits(20);
        this.channels = scratch.readBits(3) + 1;
        this.bitsPerSample = scratch.readBits(5) + 1;
        this.totalSamples = ((long)scratch.readBits(4) & 15L) << 32 | (long)scratch.readBits(32) & 4294967295L;
    }

    public FlacStreamInfo(int minBlockSize, int maxBlockSize, int minFrameSize, int maxFrameSize, int sampleRate, int channels, int bitsPerSample, long totalSamples) {
        this.minBlockSize = minBlockSize;
        this.maxBlockSize = maxBlockSize;
        this.minFrameSize = minFrameSize;
        this.maxFrameSize = maxFrameSize;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitsPerSample = bitsPerSample;
        this.totalSamples = totalSamples;
    }

    public int maxDecodedFrameSize() {
        return this.maxBlockSize * this.channels * (this.bitsPerSample / 8);
    }

    public int bitRate() {
        return this.bitsPerSample * this.sampleRate;
    }

    public long durationUs() {
        return this.totalSamples * 1000000L / (long)this.sampleRate;
    }

    public long getSampleIndex(long timeUs) {
        long sampleIndex = timeUs * (long)this.sampleRate / 1000000L;
        return Util.constrainValue(sampleIndex, 0L, this.totalSamples - 1L);
    }

    public long getApproxBytesPerFrame() {
        long approxBytesPerFrame;
        if (this.maxFrameSize > 0) {
            approxBytesPerFrame = ((long)this.maxFrameSize + (long)this.minFrameSize) / 2L + 1L;
        } else {
            long blockSize = this.minBlockSize == this.maxBlockSize && this.minBlockSize > 0 ? (long)this.minBlockSize : 4096L;
            approxBytesPerFrame = blockSize * (long)this.channels * (long)this.bitsPerSample / 8L + 64L;
        }

        return approxBytesPerFrame;
    }
}
