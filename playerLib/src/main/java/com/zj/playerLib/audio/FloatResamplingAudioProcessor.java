//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.audio;

import com.zj.playerLib.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class FloatResamplingAudioProcessor implements AudioProcessor {
    private static final int FLOAT_NAN_AS_INT = Float.floatToIntBits(0.0f);
    private static final double PCM_32_BIT_INT_TO_PCM_32_BIT_FLOAT_FACTOR = 4.656612875245797E-10D;
    private int sampleRateHz = -1;
    private int channelCount = -1;
    private int sourceEncoding = 0;
    private ByteBuffer buffer;
    private ByteBuffer outputBuffer;
    private boolean inputEnded;

    public FloatResamplingAudioProcessor() {
        this.buffer = EMPTY_BUFFER;
        this.outputBuffer = EMPTY_BUFFER;
    }

    public boolean configure(int sampleRateHz, int channelCount, int encoding) throws UnhandledFormatException {
        if (!Util.isEncodingHighResolutionIntegerPcm(encoding)) {
            throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
        } else if (this.sampleRateHz == sampleRateHz && this.channelCount == channelCount && this.sourceEncoding == encoding) {
            return false;
        } else {
            this.sampleRateHz = sampleRateHz;
            this.channelCount = channelCount;
            this.sourceEncoding = encoding;
            return true;
        }
    }

    public boolean isActive() {
        return Util.isEncodingHighResolutionIntegerPcm(this.sourceEncoding);
    }

    public int getOutputChannelCount() {
        return this.channelCount;
    }

    public int getOutputEncoding() {
        return 4;
    }

    public int getOutputSampleRateHz() {
        return this.sampleRateHz;
    }

    public void queueInput(ByteBuffer inputBuffer) {
        boolean isInput32Bit = this.sourceEncoding == 1073741824;
        int position = inputBuffer.position();
        int limit = inputBuffer.limit();
        int size = limit - position;
        int resampledSize = isInput32Bit ? size : size / 3 * 4;
        if (this.buffer.capacity() < resampledSize) {
            this.buffer = ByteBuffer.allocateDirect(resampledSize).order(ByteOrder.nativeOrder());
        } else {
            this.buffer.clear();
        }

        int i;
        int pcm32BitInteger;
        if (isInput32Bit) {
            for(i = position; i < limit; i += 4) {
                pcm32BitInteger = inputBuffer.get(i) & 255 | (inputBuffer.get(i + 1) & 255) << 8 | (inputBuffer.get(i + 2) & 255) << 16 | (inputBuffer.get(i + 3) & 255) << 24;
                writePcm32BitFloat(pcm32BitInteger, this.buffer);
            }
        } else {
            for(i = position; i < limit; i += 3) {
                pcm32BitInteger = (inputBuffer.get(i) & 255) << 8 | (inputBuffer.get(i + 1) & 255) << 16 | (inputBuffer.get(i + 2) & 255) << 24;
                writePcm32BitFloat(pcm32BitInteger, this.buffer);
            }
        }

        inputBuffer.position(inputBuffer.limit());
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
        this.sampleRateHz = -1;
        this.channelCount = -1;
        this.sourceEncoding = 0;
        this.buffer = EMPTY_BUFFER;
    }

    private static void writePcm32BitFloat(int pcm32BitInt, ByteBuffer buffer) {
        float pcm32BitFloat = (float)(4.656612875245797E-10D * (double)pcm32BitInt);
        int floatBits = Float.floatToIntBits(pcm32BitFloat);
        if (floatBits == FLOAT_NAN_AS_INT) {
            floatBits = Float.floatToIntBits(0.0F);
        }

        buffer.putInt(floatBits);
    }
}
