package com.zj.playerLib.audio;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

final class ChannelMappingAudioProcessor implements AudioProcessor {
    private int channelCount;
    private int sampleRateHz;
    @Nullable
    private int[] pendingOutputChannels;
    private boolean active;
    @Nullable
    private int[] outputChannels;
    private ByteBuffer buffer;
    private ByteBuffer outputBuffer;
    private boolean inputEnded;

    public ChannelMappingAudioProcessor() {
        this.buffer = EMPTY_BUFFER;
        this.outputBuffer = EMPTY_BUFFER;
        this.channelCount = -1;
        this.sampleRateHz = -1;
    }

    public void setChannelMap(@Nullable int[] outputChannels) {
        this.pendingOutputChannels = outputChannels;
    }

    public boolean configure(int sampleRateHz, int channelCount, int encoding) throws UnhandledFormatException {
        boolean outputChannelsChanged = !Arrays.equals(this.pendingOutputChannels, this.outputChannels);
        this.outputChannels = this.pendingOutputChannels;
        if (this.outputChannels == null) {
            this.active = false;
            return outputChannelsChanged;
        } else if (encoding != 2) {
            throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
        } else if (!outputChannelsChanged && this.sampleRateHz == sampleRateHz && this.channelCount == channelCount) {
            return false;
        } else {
            this.sampleRateHz = sampleRateHz;
            this.channelCount = channelCount;
            this.active = channelCount != this.outputChannels.length;

            for(int i = 0; i < this.outputChannels.length; ++i) {
                int channelIndex = this.outputChannels[i];
                if (channelIndex >= channelCount) {
                    throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
                }

                this.active |= channelIndex != i;
            }

            return true;
        }
    }

    public boolean isActive() {
        return this.active;
    }

    public int getOutputChannelCount() {
        return this.outputChannels == null ? this.channelCount : this.outputChannels.length;
    }

    public int getOutputEncoding() {
        return 2;
    }

    public int getOutputSampleRateHz() {
        return this.sampleRateHz;
    }

    public void queueInput(ByteBuffer inputBuffer) {
        Assertions.checkState(this.outputChannels != null);
        int position = inputBuffer.position();
        int limit = inputBuffer.limit();
        int frameCount = (limit - position) / (2 * this.channelCount);
        int outputSize = frameCount * this.outputChannels.length * 2;
        if (this.buffer.capacity() < outputSize) {
            this.buffer = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder());
        } else {
            this.buffer.clear();
        }

        while(position < limit) {
            int[] var6 = this.outputChannels;
            int var7 = var6.length;

            for(int var8 = 0; var8 < var7; ++var8) {
                int channelIndex = var6[var8];
                this.buffer.putShort(inputBuffer.getShort(position + 2 * channelIndex));
            }

            position += this.channelCount * 2;
        }

        inputBuffer.position(limit);
        this.buffer.flip();
        this.outputBuffer = this.buffer;
    }

    public void queueEndOfStream() {
        this.inputEnded = true;
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
        this.outputBuffer = EMPTY_BUFFER;
        this.inputEnded = false;
    }

    public void reset() {
        this.flush();
        this.buffer = EMPTY_BUFFER;
        this.channelCount = -1;
        this.sampleRateHz = -1;
        this.outputChannels = null;
        this.pendingOutputChannels = null;
        this.active = false;
    }
}
