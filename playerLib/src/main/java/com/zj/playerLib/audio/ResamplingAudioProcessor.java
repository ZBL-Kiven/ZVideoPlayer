//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class ResamplingAudioProcessor implements AudioProcessor {
    private int sampleRateHz = -1;
    private int channelCount = -1;
    private int encoding = 0;
    private ByteBuffer buffer;
    private ByteBuffer outputBuffer;
    private boolean inputEnded;

    public ResamplingAudioProcessor() {
        this.buffer = EMPTY_BUFFER;
        this.outputBuffer = EMPTY_BUFFER;
    }

    public boolean configure(int sampleRateHz, int channelCount, int encoding) throws UnhandledFormatException {
        if (encoding != 3 && encoding != 2 && encoding != -2147483648 && encoding != 1073741824) {
            throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
        } else if (this.sampleRateHz == sampleRateHz && this.channelCount == channelCount && this.encoding == encoding) {
            return false;
        } else {
            this.sampleRateHz = sampleRateHz;
            this.channelCount = channelCount;
            this.encoding = encoding;
            return true;
        }
    }

    public boolean isActive() {
        return this.encoding != 0 && this.encoding != 2;
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
        int size = limit - position;
        int resampledSize;
        switch(this.encoding) {
        case -2147483648:
            resampledSize = size / 3 * 2;
            break;
        case -1:
        case 0:
        case 2:
        case 4:
        case 268435456:
        case 536870912:
        default:
            throw new IllegalStateException();
        case 3:
            resampledSize = size * 2;
            break;
        case 1073741824:
            resampledSize = size / 2;
        }

        if (this.buffer.capacity() < resampledSize) {
            this.buffer = ByteBuffer.allocateDirect(resampledSize).order(ByteOrder.nativeOrder());
        } else {
            this.buffer.clear();
        }

        int i;
        label45:
        switch(this.encoding) {
        case -2147483648:
            i = position;

            while(true) {
                if (i >= limit) {
                    break label45;
                }

                this.buffer.put(inputBuffer.get(i + 1));
                this.buffer.put(inputBuffer.get(i + 2));
                i += 3;
            }
        case -1:
        case 0:
        case 2:
        case 4:
        case 268435456:
        case 536870912:
        default:
            throw new IllegalStateException();
        case 3:
            i = position;

            while(true) {
                if (i >= limit) {
                    break label45;
                }

                this.buffer.put((byte)0);
                this.buffer.put((byte)((inputBuffer.get(i) & 255) - 128));
                ++i;
            }
        case 1073741824:
            for(i = position; i < limit; i += 4) {
                this.buffer.put(inputBuffer.get(i + 2));
                this.buffer.put(inputBuffer.get(i + 3));
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
        this.encoding = 0;
        this.buffer = EMPTY_BUFFER;
    }
}
