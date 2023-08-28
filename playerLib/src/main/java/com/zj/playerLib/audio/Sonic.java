package com.zj.playerLib.audio;

import com.zj.playerLib.util.Assertions;
import java.nio.ShortBuffer;
import java.util.Arrays;

final class Sonic {
    private static final int MINIMUM_PITCH = 65;
    private static final int MAXIMUM_PITCH = 400;
    private static final int AMDF_FREQUENCY = 4000;
    private final int inputSampleRateHz;
    private final int channelCount;
    private final float speed;
    private final float pitch;
    private final float rate;
    private final int minPeriod;
    private final int maxPeriod;
    private final int maxRequiredFrameCount;
    private final short[] downSampleBuffer;
    private short[] inputBuffer;
    private int inputFrameCount;
    private short[] outputBuffer;
    private int outputFrameCount;
    private short[] pitchBuffer;
    private int pitchFrameCount;
    private int oldRatePosition;
    private int newRatePosition;
    private int remainingInputToCopyFrameCount;
    private int prevPeriod;
    private int prevMinDiff;
    private int minDiff;
    private int maxDiff;

    public Sonic(int inputSampleRateHz, int channelCount, float speed, float pitch, int outputSampleRateHz) {
        this.inputSampleRateHz = inputSampleRateHz;
        this.channelCount = channelCount;
        this.speed = speed;
        this.pitch = pitch;
        this.rate = (float)inputSampleRateHz / (float)outputSampleRateHz;
        this.minPeriod = inputSampleRateHz / 400;
        this.maxPeriod = inputSampleRateHz / 65;
        this.maxRequiredFrameCount = 2 * this.maxPeriod;
        this.downSampleBuffer = new short[this.maxRequiredFrameCount];
        this.inputBuffer = new short[this.maxRequiredFrameCount * channelCount];
        this.outputBuffer = new short[this.maxRequiredFrameCount * channelCount];
        this.pitchBuffer = new short[this.maxRequiredFrameCount * channelCount];
    }

    public void queueInput(ShortBuffer buffer) {
        int framesToWrite = buffer.remaining() / this.channelCount;
        int bytesToWrite = framesToWrite * this.channelCount * 2;
        this.inputBuffer = this.ensureSpaceForAdditionalFrames(this.inputBuffer, this.inputFrameCount, framesToWrite);
        buffer.get(this.inputBuffer, this.inputFrameCount * this.channelCount, bytesToWrite / 2);
        this.inputFrameCount += framesToWrite;
        this.processStreamInput();
    }

    public void getOutput(ShortBuffer buffer) {
        int framesToRead = Math.min(buffer.remaining() / this.channelCount, this.outputFrameCount);
        buffer.put(this.outputBuffer, 0, framesToRead * this.channelCount);
        this.outputFrameCount -= framesToRead;
        System.arraycopy(this.outputBuffer, framesToRead * this.channelCount, this.outputBuffer, 0, this.outputFrameCount * this.channelCount);
    }

    public void queueEndOfStream() {
        int remainingFrameCount = this.inputFrameCount;
        float s = this.speed / this.pitch;
        float r = this.rate * this.pitch;
        int expectedOutputFrames = this.outputFrameCount + (int)(((float)remainingFrameCount / s + (float)this.pitchFrameCount) / r + 0.5F);
        this.inputBuffer = this.ensureSpaceForAdditionalFrames(this.inputBuffer, this.inputFrameCount, remainingFrameCount + 2 * this.maxRequiredFrameCount);

        for(int xSample = 0; xSample < 2 * this.maxRequiredFrameCount * this.channelCount; ++xSample) {
            this.inputBuffer[remainingFrameCount * this.channelCount + xSample] = 0;
        }

        this.inputFrameCount += 2 * this.maxRequiredFrameCount;
        this.processStreamInput();
        if (this.outputFrameCount > expectedOutputFrames) {
            this.outputFrameCount = expectedOutputFrames;
        }

        this.inputFrameCount = 0;
        this.remainingInputToCopyFrameCount = 0;
        this.pitchFrameCount = 0;
    }

    public void flush() {
        this.inputFrameCount = 0;
        this.outputFrameCount = 0;
        this.pitchFrameCount = 0;
        this.oldRatePosition = 0;
        this.newRatePosition = 0;
        this.remainingInputToCopyFrameCount = 0;
        this.prevPeriod = 0;
        this.prevMinDiff = 0;
        this.minDiff = 0;
        this.maxDiff = 0;
    }

    public int getFramesAvailable() {
        return this.outputFrameCount;
    }

    private short[] ensureSpaceForAdditionalFrames(short[] buffer, int frameCount, int additionalFrameCount) {
        int currentCapacityFrames = buffer.length / this.channelCount;
        if (frameCount + additionalFrameCount <= currentCapacityFrames) {
            return buffer;
        } else {
            int newCapacityFrames = 3 * currentCapacityFrames / 2 + additionalFrameCount;
            return Arrays.copyOf(buffer, newCapacityFrames * this.channelCount);
        }
    }

    private void removeProcessedInputFrames(int positionFrames) {
        int remainingFrames = this.inputFrameCount - positionFrames;
        System.arraycopy(this.inputBuffer, positionFrames * this.channelCount, this.inputBuffer, 0, remainingFrames * this.channelCount);
        this.inputFrameCount = remainingFrames;
    }

    private void copyToOutput(short[] samples, int positionFrames, int frameCount) {
        this.outputBuffer = this.ensureSpaceForAdditionalFrames(this.outputBuffer, this.outputFrameCount, frameCount);
        System.arraycopy(samples, positionFrames * this.channelCount, this.outputBuffer, this.outputFrameCount * this.channelCount, frameCount * this.channelCount);
        this.outputFrameCount += frameCount;
    }

    private int copyInputToOutput(int positionFrames) {
        int frameCount = Math.min(this.maxRequiredFrameCount, this.remainingInputToCopyFrameCount);
        this.copyToOutput(this.inputBuffer, positionFrames, frameCount);
        this.remainingInputToCopyFrameCount -= frameCount;
        return frameCount;
    }

    private void downSampleInput(short[] samples, int position, int skip) {
        int frameCount = this.maxRequiredFrameCount / skip;
        int samplesPerValue = this.channelCount * skip;
        position *= this.channelCount;

        for(int i = 0; i < frameCount; ++i) {
            int value = 0;

            for(int j = 0; j < samplesPerValue; ++j) {
                value += samples[position + i * samplesPerValue + j];
            }

            value /= samplesPerValue;
            this.downSampleBuffer[i] = (short)value;
        }

    }

    private int findPitchPeriodInRange(short[] samples, int position, int minPeriod, int maxPeriod) {
        int bestPeriod = 0;
        int worstPeriod = 255;
        int minDiff = 1;
        int maxDiff = 0;
        position *= this.channelCount;

        for(int period = minPeriod; period <= maxPeriod; ++period) {
            int diff = 0;

            for(int i = 0; i < period; ++i) {
                short sVal = samples[position + i];
                short pVal = samples[position + period + i];
                diff += Math.abs(sVal - pVal);
            }

            if (diff * bestPeriod < minDiff * period) {
                minDiff = diff;
                bestPeriod = period;
            }

            if (diff * worstPeriod > maxDiff * period) {
                maxDiff = diff;
                worstPeriod = period;
            }
        }

        this.minDiff = minDiff / bestPeriod;
        this.maxDiff = maxDiff / worstPeriod;
        return bestPeriod;
    }

    private boolean previousPeriodBetter(int minDiff, int maxDiff) {
        if (minDiff != 0 && this.prevPeriod != 0) {
            if (maxDiff > minDiff * 3) {
                return false;
            } else {
                return minDiff * 2 > this.prevMinDiff * 3;
            }
        } else {
            return false;
        }
    }

    private int findPitchPeriod(short[] samples, int position) {
        int skip = this.inputSampleRateHz > 4000 ? this.inputSampleRateHz / 4000 : 1;
        int period;
        if (this.channelCount == 1 && skip == 1) {
            period = this.findPitchPeriodInRange(samples, position, this.minPeriod, this.maxPeriod);
        } else {
            this.downSampleInput(samples, position, skip);
            period = this.findPitchPeriodInRange(this.downSampleBuffer, 0, this.minPeriod / skip, this.maxPeriod / skip);
            if (skip != 1) {
                period *= skip;
                int minP = period - skip * 4;
                int maxP = period + skip * 4;
                if (minP < this.minPeriod) {
                    minP = this.minPeriod;
                }

                if (maxP > this.maxPeriod) {
                    maxP = this.maxPeriod;
                }

                if (this.channelCount == 1) {
                    period = this.findPitchPeriodInRange(samples, position, minP, maxP);
                } else {
                    this.downSampleInput(samples, position, 1);
                    period = this.findPitchPeriodInRange(this.downSampleBuffer, 0, minP, maxP);
                }
            }
        }

        int retPeriod;
        if (this.previousPeriodBetter(this.minDiff, this.maxDiff)) {
            retPeriod = this.prevPeriod;
        } else {
            retPeriod = period;
        }

        this.prevMinDiff = this.minDiff;
        this.prevPeriod = period;
        return retPeriod;
    }

    private void moveNewSamplesToPitchBuffer(int originalOutputFrameCount) {
        int frameCount = this.outputFrameCount - originalOutputFrameCount;
        this.pitchBuffer = this.ensureSpaceForAdditionalFrames(this.pitchBuffer, this.pitchFrameCount, frameCount);
        System.arraycopy(this.outputBuffer, originalOutputFrameCount * this.channelCount, this.pitchBuffer, this.pitchFrameCount * this.channelCount, frameCount * this.channelCount);
        this.outputFrameCount = originalOutputFrameCount;
        this.pitchFrameCount += frameCount;
    }

    private void removePitchFrames(int frameCount) {
        if (frameCount != 0) {
            System.arraycopy(this.pitchBuffer, frameCount * this.channelCount, this.pitchBuffer, 0, (this.pitchFrameCount - frameCount) * this.channelCount);
            this.pitchFrameCount -= frameCount;
        }
    }

    private short interpolate(short[] in, int inPos, int oldSampleRate, int newSampleRate) {
        short left = in[inPos];
        short right = in[inPos + this.channelCount];
        int position = this.newRatePosition * oldSampleRate;
        int leftPosition = this.oldRatePosition * newSampleRate;
        int rightPosition = (this.oldRatePosition + 1) * newSampleRate;
        int ratio = rightPosition - position;
        int width = rightPosition - leftPosition;
        return (short)((ratio * left + (width - ratio) * right) / width);
    }

    private void adjustRate(float rate, int originalOutputFrameCount) {
        if (this.outputFrameCount != originalOutputFrameCount) {
            int newSampleRate = (int)((float)this.inputSampleRateHz / rate);

            int oldSampleRate;
            for(oldSampleRate = this.inputSampleRateHz; newSampleRate > 16384 || oldSampleRate > 16384; oldSampleRate /= 2) {
                newSampleRate /= 2;
            }

            this.moveNewSamplesToPitchBuffer(originalOutputFrameCount);

            for(int position = 0; position < this.pitchFrameCount - 1; ++position) {
                while((this.oldRatePosition + 1) * newSampleRate > this.newRatePosition * oldSampleRate) {
                    this.outputBuffer = this.ensureSpaceForAdditionalFrames(this.outputBuffer, this.outputFrameCount, 1);

                    for(int i = 0; i < this.channelCount; ++i) {
                        this.outputBuffer[this.outputFrameCount * this.channelCount + i] = this.interpolate(this.pitchBuffer, position * this.channelCount + i, oldSampleRate, newSampleRate);
                    }

                    ++this.newRatePosition;
                    ++this.outputFrameCount;
                }

                ++this.oldRatePosition;
                if (this.oldRatePosition == oldSampleRate) {
                    this.oldRatePosition = 0;
                    Assertions.checkState(this.newRatePosition == newSampleRate);
                    this.newRatePosition = 0;
                }
            }

            this.removePitchFrames(this.pitchFrameCount - 1);
        }
    }

    private int skipPitchPeriod(short[] samples, int position, float speed, int period) {
        int newFrameCount;
        if (speed >= 2.0F) {
            newFrameCount = (int)((float)period / (speed - 1.0F));
        } else {
            newFrameCount = period;
            this.remainingInputToCopyFrameCount = (int)((float)period * (2.0F - speed) / (speed - 1.0F));
        }

        this.outputBuffer = this.ensureSpaceForAdditionalFrames(this.outputBuffer, this.outputFrameCount, newFrameCount);
        overlapAdd(newFrameCount, this.channelCount, this.outputBuffer, this.outputFrameCount, samples, position, samples, position + period);
        this.outputFrameCount += newFrameCount;
        return newFrameCount;
    }

    private int insertPitchPeriod(short[] samples, int position, float speed, int period) {
        int newFrameCount;
        if (speed < 0.5F) {
            newFrameCount = (int)((float)period * speed / (1.0F - speed));
        } else {
            newFrameCount = period;
            this.remainingInputToCopyFrameCount = (int)((float)period * (2.0F * speed - 1.0F) / (1.0F - speed));
        }

        this.outputBuffer = this.ensureSpaceForAdditionalFrames(this.outputBuffer, this.outputFrameCount, period + newFrameCount);
        System.arraycopy(samples, position * this.channelCount, this.outputBuffer, this.outputFrameCount * this.channelCount, period * this.channelCount);
        overlapAdd(newFrameCount, this.channelCount, this.outputBuffer, this.outputFrameCount + period, samples, position + period, samples, position);
        this.outputFrameCount += period + newFrameCount;
        return newFrameCount;
    }

    private void changeSpeed(float speed) {
        if (this.inputFrameCount >= this.maxRequiredFrameCount) {
            int frameCount = this.inputFrameCount;
            int positionFrames = 0;

            do {
                if (this.remainingInputToCopyFrameCount > 0) {
                    positionFrames += this.copyInputToOutput(positionFrames);
                } else {
                    int period = this.findPitchPeriod(this.inputBuffer, positionFrames);
                    if ((double)speed > 1.0D) {
                        positionFrames += period + this.skipPitchPeriod(this.inputBuffer, positionFrames, speed, period);
                    } else {
                        positionFrames += this.insertPitchPeriod(this.inputBuffer, positionFrames, speed, period);
                    }
                }
            } while(positionFrames + this.maxRequiredFrameCount <= frameCount);

            this.removeProcessedInputFrames(positionFrames);
        }
    }

    private void processStreamInput() {
        int originalOutputFrameCount = this.outputFrameCount;
        float s = this.speed / this.pitch;
        float r = this.rate * this.pitch;
        if ((double)s <= 1.00001D && (double)s >= 0.99999D) {
            this.copyToOutput(this.inputBuffer, 0, this.inputFrameCount);
            this.inputFrameCount = 0;
        } else {
            this.changeSpeed(s);
        }

        if (r != 1.0F) {
            this.adjustRate(r, originalOutputFrameCount);
        }

    }

    private static void overlapAdd(int frameCount, int channelCount, short[] out, int outPosition, short[] rampDown, int rampDownPosition, short[] rampUp, int rampUpPosition) {
        for(int i = 0; i < channelCount; ++i) {
            int o = outPosition * channelCount + i;
            int u = rampUpPosition * channelCount + i;
            int d = rampDownPosition * channelCount + i;

            for(int t = 0; t < frameCount; ++t) {
                out[o] = (short)((rampDown[d] * (frameCount - t) + rampUp[u] * t) / frameCount);
                o += channelCount;
                d += channelCount;
                u += channelCount;
            }
        }

    }
}
