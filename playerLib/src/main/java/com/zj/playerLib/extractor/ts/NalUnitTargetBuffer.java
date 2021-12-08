package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.util.Assertions;

import java.util.Arrays;

final class NalUnitTargetBuffer {
    private final int targetType;
    private boolean isFilling;
    private boolean isCompleted;
    public byte[] nalData;
    public int nalLength;

    public NalUnitTargetBuffer(int targetType, int initialCapacity) {
        this.targetType = targetType;
        this.nalData = new byte[3 + initialCapacity];
        this.nalData[2] = 1;
    }

    public void reset() {
        this.isFilling = false;
        this.isCompleted = false;
    }

    public boolean isCompleted() {
        return this.isCompleted;
    }

    public void startNalUnit(int type) {
        Assertions.checkState(!this.isFilling);
        this.isFilling = type == this.targetType;
        if (this.isFilling) {
            this.nalLength = 3;
            this.isCompleted = false;
        }

    }

    public void appendToNalUnit(byte[] data, int offset, int limit) {
        if (this.isFilling) {
            int readLength = limit - offset;
            if (this.nalData.length < this.nalLength + readLength) {
                this.nalData = Arrays.copyOf(this.nalData, (this.nalLength + readLength) * 2);
            }

            System.arraycopy(data, offset, this.nalData, this.nalLength, readLength);
            this.nalLength += readLength;
        }
    }

    public boolean endNalUnit(int discardPadding) {
        if (!this.isFilling) {
            return false;
        } else {
            this.nalLength -= discardPadding;
            this.isFilling = false;
            this.isCompleted = true;
            return true;
        }
    }
}
