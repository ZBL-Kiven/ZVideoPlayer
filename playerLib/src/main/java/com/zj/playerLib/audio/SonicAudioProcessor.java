//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.audio;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public final class SonicAudioProcessor implements AudioProcessor {
    public static final float MAXIMUM_SPEED = 8.0F;
    public static final float MINIMUM_SPEED = 0.1F;
    public static final float MAXIMUM_PITCH = 8.0F;
    public static final float MINIMUM_PITCH = 0.1F;
    public static final int SAMPLE_RATE_NO_CHANGE = -1;
    private static final float CLOSE_THRESHOLD = 0.01F;
    private static final int MIN_BYTES_FOR_SPEEDUP_CALCULATION = 1024;
    private int channelCount = -1;
    private int sampleRateHz = -1;
    private float speed = 1.0F;
    private float pitch = 1.0F;
    private int outputSampleRateHz = -1;
    private int pendingOutputSampleRateHz;
    @Nullable
    private Sonic sonic;
    private ByteBuffer buffer;
    private ShortBuffer shortBuffer;
    private ByteBuffer outputBuffer;
    private long inputBytes;
    private long outputBytes;
    private boolean inputEnded;

    public SonicAudioProcessor() {
        this.buffer = EMPTY_BUFFER;
        this.shortBuffer = this.buffer.asShortBuffer();
        this.outputBuffer = EMPTY_BUFFER;
        this.pendingOutputSampleRateHz = -1;
    }

    public float setSpeed(float speed) {
        speed = Util.constrainValue(speed, 0.1F, 8.0F);
        if (this.speed != speed) {
            this.speed = speed;
            this.sonic = null;
        }

        this.flush();
        return speed;
    }

    public float setPitch(float pitch) {
        pitch = Util.constrainValue(pitch, 0.1F, 8.0F);
        if (this.pitch != pitch) {
            this.pitch = pitch;
            this.sonic = null;
        }

        this.flush();
        return pitch;
    }

    public void setOutputSampleRateHz(int sampleRateHz) {
        this.pendingOutputSampleRateHz = sampleRateHz;
    }

    public long scaleDurationForSpeedup(long duration) {
        if (this.outputBytes >= 1024L) {
            return this.outputSampleRateHz == this.sampleRateHz ? Util.scaleLargeTimestamp(duration, this.inputBytes, this.outputBytes) : Util.scaleLargeTimestamp(duration, this.inputBytes * (long)this.outputSampleRateHz, this.outputBytes * (long)this.sampleRateHz);
        } else {
            return (long)((double)this.speed * (double)duration);
        }
    }

    public boolean configure(int sampleRateHz, int channelCount, int encoding) throws UnhandledFormatException {
        if (encoding != 2) {
            throw new UnhandledFormatException(sampleRateHz, channelCount, encoding);
        } else {
            int outputSampleRateHz = this.pendingOutputSampleRateHz == -1 ? sampleRateHz : this.pendingOutputSampleRateHz;
            if (this.sampleRateHz == sampleRateHz && this.channelCount == channelCount && this.outputSampleRateHz == outputSampleRateHz) {
                return false;
            } else {
                this.sampleRateHz = sampleRateHz;
                this.channelCount = channelCount;
                this.outputSampleRateHz = outputSampleRateHz;
                this.sonic = null;
                return true;
            }
        }
    }

    public boolean isActive() {
        return this.sampleRateHz != -1 && (Math.abs(this.speed - 1.0F) >= 0.01F || Math.abs(this.pitch - 1.0F) >= 0.01F || this.outputSampleRateHz != this.sampleRateHz);
    }

    public int getOutputChannelCount() {
        return this.channelCount;
    }

    public int getOutputEncoding() {
        return 2;
    }

    public int getOutputSampleRateHz() {
        return this.outputSampleRateHz;
    }

    public void queueInput(ByteBuffer inputBuffer) {
        Assertions.checkState(this.sonic != null);
        if (inputBuffer.hasRemaining()) {
            ShortBuffer shortBuffer = inputBuffer.asShortBuffer();
            int inputSize = inputBuffer.remaining();
            this.inputBytes += (long)inputSize;
            this.sonic.queueInput(shortBuffer);
            inputBuffer.position(inputBuffer.position() + inputSize);
        }

        int outputSize = this.sonic.getFramesAvailable() * this.channelCount * 2;
        if (outputSize > 0) {
            if (this.buffer.capacity() < outputSize) {
                this.buffer = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.nativeOrder());
                this.shortBuffer = this.buffer.asShortBuffer();
            } else {
                this.buffer.clear();
                this.shortBuffer.clear();
            }

            this.sonic.getOutput(this.shortBuffer);
            this.outputBytes += (long)outputSize;
            this.buffer.limit(outputSize);
            this.outputBuffer = this.buffer;
        }

    }

    public void queueEndOfStream() {
        Assertions.checkState(this.sonic != null);
        this.sonic.queueEndOfStream();
        this.inputEnded = true;
    }

    public ByteBuffer getOutput() {
        ByteBuffer outputBuffer = this.outputBuffer;
        this.outputBuffer = EMPTY_BUFFER;
        return outputBuffer;
    }

    public boolean isEnded() {
        return this.inputEnded && (this.sonic == null || this.sonic.getFramesAvailable() == 0);
    }

    public void flush() {
        if (this.isActive()) {
            if (this.sonic == null) {
                this.sonic = new Sonic(this.sampleRateHz, this.channelCount, this.speed, this.pitch, this.outputSampleRateHz);
            } else {
                this.sonic.flush();
            }
        }

        this.outputBuffer = EMPTY_BUFFER;
        this.inputBytes = 0L;
        this.outputBytes = 0L;
        this.inputEnded = false;
    }

    public void reset() {
        this.speed = 1.0F;
        this.pitch = 1.0F;
        this.channelCount = -1;
        this.sampleRateHz = -1;
        this.outputSampleRateHz = -1;
        this.buffer = EMPTY_BUFFER;
        this.shortBuffer = this.buffer.asShortBuffer();
        this.outputBuffer = EMPTY_BUFFER;
        this.pendingOutputSampleRateHz = -1;
        this.sonic = null;
        this.inputBytes = 0L;
        this.outputBytes = 0L;
        this.inputEnded = false;
    }
}
