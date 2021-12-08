package com.zj.playerLib.extractor.ogg;

import com.zj.playerLib.util.Assertions;

final class VorbisBitArray {
    private final byte[] data;
    private final int byteLimit;
    private int byteOffset;
    private int bitOffset;

    public VorbisBitArray(byte[] data) {
        this.data = data;
        this.byteLimit = data.length;
    }

    public void reset() {
        this.byteOffset = 0;
        this.bitOffset = 0;
    }

    public boolean readBit() {
        boolean returnValue = ((this.data[this.byteOffset] & 255) >> this.bitOffset & 1) == 1;
        this.skipBits(1);
        return returnValue;
    }

    public int readBits(int numBits) {
        int tempByteOffset = this.byteOffset;
        int bitsRead = Math.min(numBits, 8 - this.bitOffset);

        int returnValue;
        for(returnValue = (this.data[tempByteOffset++] & 255) >> this.bitOffset & 255 >> 8 - bitsRead; bitsRead < numBits; bitsRead += 8) {
            returnValue |= (this.data[tempByteOffset++] & 255) << bitsRead;
        }

        returnValue &= -1 >>> 32 - numBits;
        this.skipBits(numBits);
        return returnValue;
    }

    public void skipBits(int numBits) {
        int numBytes = numBits / 8;
        this.byteOffset += numBytes;
        this.bitOffset += numBits - numBytes * 8;
        if (this.bitOffset > 7) {
            ++this.byteOffset;
            this.bitOffset -= 8;
        }

        this.assertValidOffset();
    }

    public int getPosition() {
        return this.byteOffset * 8 + this.bitOffset;
    }

    public void setPosition(int position) {
        this.byteOffset = position / 8;
        this.bitOffset = position - this.byteOffset * 8;
        this.assertValidOffset();
    }

    public int bitsLeft() {
        return (this.byteLimit - this.byteOffset) * 8 - this.bitOffset;
    }

    private void assertValidOffset() {
        Assertions.checkState(this.byteOffset >= 0 && (this.byteOffset < this.byteLimit || this.byteOffset == this.byteLimit && this.bitOffset == 0));
    }
}
