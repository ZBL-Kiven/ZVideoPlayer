package com.zj.playerLib.audio;

import com.zj.playerLib.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class SilenceSkippingAudioProcessor implements AudioProcessor {
    private static final long MINIMUM_SILENCE_DURATION_US = 150000L;
    private static final long PADDING_SILENCE_US = 20000L;
    private static final short SILENCE_THRESHOLD_LEVEL = 1024;
    private static final byte SILENCE_THRESHOLD_LEVEL_MSB = 4;
    private static final int STATE_NOISY = 0;
    private static final int STATE_MAYBE_SILENT = 1;
    private static final int STATE_SILENT = 2;
    private int channelCount;
    private int sampleRateHz;
    private int bytesPerFrame;
    private boolean enabled;
    private ByteBuffer buffer;
    private ByteBuffer outputBuffer;
    private boolean inputEnded;
    private byte[] maybeSilenceBuffer;
    private byte[] paddingBuffer;
    private int state;
    private int maybeSilenceBufferSize;
    private int paddingSize;
    private boolean hasOutputNoise;
    private long skippedFrames;

    public SilenceSkippingAudioProcessor() {
        this.buffer = EMPTY_BUFFER;
        this.outputBuffer = EMPTY_BUFFER;
        this.channelCount = -1;
        this.sampleRateHz = -1;
        this.maybeSilenceBuffer = Util.EMPTY_BYTE_ARRAY;
        this.paddingBuffer = Util.EMPTY_BYTE_ARRAY;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.flush();
    }

    public long getSkippedFrames() {
        return this.skippedFrames;
    }

    public boolean configure(int sampleRateHz, int channelCount, int encoding) throws UnhandledFormatException {
        if (encoding != 2) {
            throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
        } else if (this.sampleRateHz == sampleRateHz && this.channelCount == channelCount) {
            return false;
        } else {
            this.sampleRateHz = sampleRateHz;
            this.channelCount = channelCount;
            this.bytesPerFrame = channelCount * 2;
            return true;
        }
    }

    public boolean isActive() {
        return this.sampleRateHz != -1 && this.enabled;
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
        while(inputBuffer.hasRemaining() && !this.outputBuffer.hasRemaining()) {
            switch(this.state) {
            case 0:
                this.processNoisy(inputBuffer);
                break;
            case 1:
                this.processMaybeSilence(inputBuffer);
                break;
            case 2:
                this.processSilence(inputBuffer);
                break;
            default:
                throw new IllegalStateException();
            }
        }

    }

    public void queueEndOfStream() {
        this.inputEnded = true;
        if (this.maybeSilenceBufferSize > 0) {
            this.output(this.maybeSilenceBuffer, this.maybeSilenceBufferSize);
        }

        if (!this.hasOutputNoise) {
            this.skippedFrames += this.paddingSize / this.bytesPerFrame;
        }

    }

    public ByteBuffer getOutput() {
        ByteBuffer outputBuffer = this.outputBuffer;
        this.outputBuffer = EMPTY_BUFFER;
        return outputBuffer;
    }

    public boolean isEnded() {
        return this.inputEnded && this.outputBuffer == EMPTY_BUFFER;
    }

    public void flush() {
        if (this.isActive()) {
            int maybeSilenceBufferSize = this.durationUsToFrames(150000L) * this.bytesPerFrame;
            if (this.maybeSilenceBuffer.length != maybeSilenceBufferSize) {
                this.maybeSilenceBuffer = new byte[maybeSilenceBufferSize];
            }

            this.paddingSize = this.durationUsToFrames(20000L) * this.bytesPerFrame;
            if (this.paddingBuffer.length != this.paddingSize) {
                this.paddingBuffer = new byte[this.paddingSize];
            }
        }

        this.state = 0;
        this.outputBuffer = EMPTY_BUFFER;
        this.inputEnded = false;
        this.skippedFrames = 0L;
        this.maybeSilenceBufferSize = 0;
        this.hasOutputNoise = false;
    }

    public void reset() {
        this.enabled = false;
        this.flush();
        this.buffer = EMPTY_BUFFER;
        this.channelCount = -1;
        this.sampleRateHz = -1;
        this.paddingSize = 0;
        this.maybeSilenceBuffer = Util.EMPTY_BYTE_ARRAY;
        this.paddingBuffer = Util.EMPTY_BYTE_ARRAY;
    }

    private void processNoisy(ByteBuffer inputBuffer) {
        int limit = inputBuffer.limit();
        inputBuffer.limit(Math.min(limit, inputBuffer.position() + this.maybeSilenceBuffer.length));
        int noiseLimit = this.findNoiseLimit(inputBuffer);
        if (noiseLimit == inputBuffer.position()) {
            this.state = 1;
        } else {
            inputBuffer.limit(noiseLimit);
            this.output(inputBuffer);
        }

        inputBuffer.limit(limit);
    }

    private void processMaybeSilence(ByteBuffer inputBuffer) {
        int limit = inputBuffer.limit();
        int noisePosition = this.findNoisePosition(inputBuffer);
        int maybeSilenceInputSize = noisePosition - inputBuffer.position();
        int maybeSilenceBufferRemaining = this.maybeSilenceBuffer.length - this.maybeSilenceBufferSize;
        if (noisePosition < limit && maybeSilenceInputSize < maybeSilenceBufferRemaining) {
            this.output(this.maybeSilenceBuffer, this.maybeSilenceBufferSize);
            this.maybeSilenceBufferSize = 0;
            this.state = 0;
        } else {
            int bytesToWrite = Math.min(maybeSilenceInputSize, maybeSilenceBufferRemaining);
            inputBuffer.limit(inputBuffer.position() + bytesToWrite);
            inputBuffer.get(this.maybeSilenceBuffer, this.maybeSilenceBufferSize, bytesToWrite);
            this.maybeSilenceBufferSize += bytesToWrite;
            if (this.maybeSilenceBufferSize == this.maybeSilenceBuffer.length) {
                if (this.hasOutputNoise) {
                    this.output(this.maybeSilenceBuffer, this.paddingSize);
                    this.skippedFrames += (this.maybeSilenceBufferSize - this.paddingSize * 2) / this.bytesPerFrame;
                } else {
                    this.skippedFrames += (this.maybeSilenceBufferSize - this.paddingSize) / this.bytesPerFrame;
                }

                this.updatePaddingBuffer(inputBuffer, this.maybeSilenceBuffer, this.maybeSilenceBufferSize);
                this.maybeSilenceBufferSize = 0;
                this.state = 2;
            }

            inputBuffer.limit(limit);
        }

    }

    private void processSilence(ByteBuffer inputBuffer) {
        int limit = inputBuffer.limit();
        int noisyPosition = this.findNoisePosition(inputBuffer);
        inputBuffer.limit(noisyPosition);
        this.skippedFrames += inputBuffer.remaining() / this.bytesPerFrame;
        this.updatePaddingBuffer(inputBuffer, this.paddingBuffer, this.paddingSize);
        if (noisyPosition < limit) {
            this.output(this.paddingBuffer, this.paddingSize);
            this.state = 0;
            inputBuffer.limit(limit);
        }

    }

    private void output(byte[] data, int length) {
        this.prepareForOutput(length);
        this.buffer.put(data, 0, length);
        this.buffer.flip();
        this.outputBuffer = this.buffer;
    }

    private void output(ByteBuffer data) {
        this.prepareForOutput(data.remaining());
        this.buffer.put(data);
        this.buffer.flip();
        this.outputBuffer = this.buffer;
    }

    private void prepareForOutput(int size) {
        if (this.buffer.capacity() < size) {
            this.buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        } else {
            this.buffer.clear();
        }

        if (size > 0) {
            this.hasOutputNoise = true;
        }

    }

    private void updatePaddingBuffer(ByteBuffer input, byte[] buffer, int size) {
        int fromInputSize = Math.min(input.remaining(), this.paddingSize);
        int fromBufferSize = this.paddingSize - fromInputSize;
        System.arraycopy(buffer, size - fromBufferSize, this.paddingBuffer, 0, fromBufferSize);
        input.position(input.limit() - fromInputSize);
        input.get(this.paddingBuffer, fromBufferSize, fromInputSize);
    }

    private int durationUsToFrames(long durationUs) {
        return (int)(durationUs * (long)this.sampleRateHz / 1000000L);
    }

    private int findNoisePosition(ByteBuffer buffer) {
        for(int i = buffer.position() + 1; i < buffer.limit(); i += 2) {
            if (Math.abs(buffer.get(i)) > 4) {
                return this.bytesPerFrame * (i / this.bytesPerFrame);
            }
        }

        return buffer.limit();
    }

    private int findNoiseLimit(ByteBuffer buffer) {
        for(int i = buffer.limit() - 1; i >= buffer.position(); i -= 2) {
            if (Math.abs(buffer.get(i)) > 4) {
                return this.bytesPerFrame * (i / this.bytesPerFrame) + this.bytesPerFrame;
            }
        }

        return buffer.position();
    }
}
