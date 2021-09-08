//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.audio;

import com.zj.playerLib.util.Util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class TrimmingAudioProcessor implements AudioProcessor {
    private static final int OUTPUT_ENCODING = 2;
    private boolean isActive;
    private int trimStartFrames;
    private int trimEndFrames;
    private int channelCount;
    private int sampleRateHz;
    private int bytesPerFrame;
    private boolean receivedInputSinceConfigure;
    private int pendingTrimStartBytes;
    private ByteBuffer buffer;
    private ByteBuffer outputBuffer;
    private byte[] endBuffer;
    private int endBufferSize;
    private boolean inputEnded;
    private long trimmedFrameCount;

    public TrimmingAudioProcessor() {
        this.buffer = EMPTY_BUFFER;
        this.outputBuffer = EMPTY_BUFFER;
        this.channelCount = -1;
        this.sampleRateHz = -1;
        this.endBuffer = Util.EMPTY_BYTE_ARRAY;
    }

    public void setTrimFrameCount(int trimStartFrames, int trimEndFrames) {
        this.trimStartFrames = trimStartFrames;
        this.trimEndFrames = trimEndFrames;
    }

    public void resetTrimmedFrameCount() {
        this.trimmedFrameCount = 0L;
    }

    public long getTrimmedFrameCount() {
        return this.trimmedFrameCount;
    }

    public boolean configure(int sampleRateHz, int channelCount, int encoding) throws UnhandledFormatException {
        if (encoding != 2) {
            throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
        } else {
            if (this.endBufferSize > 0) {
                this.trimmedFrameCount += (long)(this.endBufferSize / this.bytesPerFrame);
            }

            this.channelCount = channelCount;
            this.sampleRateHz = sampleRateHz;
            this.bytesPerFrame = Util.getPcmFrameSize(2, channelCount);
            this.endBuffer = new byte[this.trimEndFrames * this.bytesPerFrame];
            this.endBufferSize = 0;
            this.pendingTrimStartBytes = this.trimStartFrames * this.bytesPerFrame;
            boolean wasActive = this.isActive;
            this.isActive = this.trimStartFrames != 0 || this.trimEndFrames != 0;
            this.receivedInputSinceConfigure = false;
            return wasActive != this.isActive;
        }
    }

    public boolean isActive() {
        return this.isActive;
    }

    public int getOutputChannelCount() {
        return this.channelCount;
    }

    public int getOutputEncoding() {
        return 2;
    }

    public int getOutputSampleRateHz() {
        return this.sampleRateHz;
    }

    public void queueInput(ByteBuffer inputBuffer) {
        int position = inputBuffer.position();
        int limit = inputBuffer.limit();
        int remaining = limit - position;
        if (remaining != 0) {
            this.receivedInputSinceConfigure = true;
            int trimBytes = Math.min(remaining, this.pendingTrimStartBytes);
            this.trimmedFrameCount += (long)(trimBytes / this.bytesPerFrame);
            this.pendingTrimStartBytes -= trimBytes;
            inputBuffer.position(position + trimBytes);
            if (this.pendingTrimStartBytes <= 0) {
                remaining -= trimBytes;
                int remainingBytesToOutput = this.endBufferSize + remaining - this.endBuffer.length;
                if (this.buffer.capacity() < remainingBytesToOutput) {
                    this.buffer = ByteBuffer.allocateDirect(remainingBytesToOutput).order(ByteOrder.nativeOrder());
                } else {
                    this.buffer.clear();
                }

                int endBufferBytesToOutput = Util.constrainValue(remainingBytesToOutput, 0, this.endBufferSize);
                this.buffer.put(this.endBuffer, 0, endBufferBytesToOutput);
                remainingBytesToOutput -= endBufferBytesToOutput;
                int inputBufferBytesToOutput = Util.constrainValue(remainingBytesToOutput, 0, remaining);
                inputBuffer.limit(inputBuffer.position() + inputBufferBytesToOutput);
                this.buffer.put(inputBuffer);
                inputBuffer.limit(limit);
                remaining -= inputBufferBytesToOutput;
                this.endBufferSize -= endBufferBytesToOutput;
                System.arraycopy(this.endBuffer, endBufferBytesToOutput, this.endBuffer, 0, this.endBufferSize);
                inputBuffer.get(this.endBuffer, this.endBufferSize, remaining);
                this.endBufferSize += remaining;
                this.buffer.flip();
                this.outputBuffer = this.buffer;
            }
        }
    }

    public void queueEndOfStream() {
        this.inputEnded = true;
    }

    public ByteBuffer getOutput() {
        ByteBuffer outputBuffer = this.outputBuffer;
        if (this.inputEnded && this.endBufferSize > 0 && outputBuffer == EMPTY_BUFFER) {
            if (this.buffer.capacity() < this.endBufferSize) {
                this.buffer = ByteBuffer.allocateDirect(this.endBufferSize).order(ByteOrder.nativeOrder());
            } else {
                this.buffer.clear();
            }

            this.buffer.put(this.endBuffer, 0, this.endBufferSize);
            this.endBufferSize = 0;
            this.buffer.flip();
            outputBuffer = this.buffer;
        }

        this.outputBuffer = EMPTY_BUFFER;
        return outputBuffer;
    }

    public boolean isEnded() {
        return this.inputEnded && this.endBufferSize == 0 && this.outputBuffer == EMPTY_BUFFER;
    }

    public void flush() {
        this.outputBuffer = EMPTY_BUFFER;
        this.inputEnded = false;
        if (this.receivedInputSinceConfigure) {
            this.pendingTrimStartBytes = 0;
        }

        this.endBufferSize = 0;
    }

    public void reset() {
        this.flush();
        this.buffer = EMPTY_BUFFER;
        this.channelCount = -1;
        this.sampleRateHz = -1;
        this.endBuffer = Util.EMPTY_BYTE_ARRAY;
    }
}
