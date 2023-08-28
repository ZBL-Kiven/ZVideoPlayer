package com.zj.playerLib.util;

public final class ParsableNalUnitBitArray {
    private byte[] data;
    private int byteLimit;
    private int byteOffset;
    private int bitOffset;

    public ParsableNalUnitBitArray(byte[] data, int offset, int limit) {
        this.reset(data, offset, limit);
    }

    public void reset(byte[] data, int offset, int limit) {
        this.data = data;
        this.byteOffset = offset;
        this.byteLimit = limit;
        this.bitOffset = 0;
        this.assertValidOffset();
    }

    public void skipBit() {
        if (++this.bitOffset == 8) {
            this.bitOffset = 0;
            this.byteOffset += this.shouldSkipByte(this.byteOffset + 1) ? 2 : 1;
        }

        this.assertValidOffset();
    }

    public void skipBits(int numBits) {
        int oldByteOffset = this.byteOffset;
        int numBytes = numBits / 8;
        this.byteOffset += numBytes;
        this.bitOffset += numBits - numBytes * 8;
        if (this.bitOffset > 7) {
            ++this.byteOffset;
            this.bitOffset -= 8;
        }

        for(int i = oldByteOffset + 1; i <= this.byteOffset; ++i) {
            if (this.shouldSkipByte(i)) {
                ++this.byteOffset;
                i += 2;
            }
        }

        this.assertValidOffset();
    }

    public boolean canReadBits(int numBits) {
        int oldByteOffset = this.byteOffset;
        int numBytes = numBits / 8;
        int newByteOffset = this.byteOffset + numBytes;
        int newBitOffset = this.bitOffset + numBits - numBytes * 8;
        if (newBitOffset > 7) {
            ++newByteOffset;
            newBitOffset -= 8;
        }

        for(int i = oldByteOffset + 1; i <= newByteOffset && newByteOffset < this.byteLimit; ++i) {
            if (this.shouldSkipByte(i)) {
                ++newByteOffset;
                i += 2;
            }
        }

        return newByteOffset < this.byteLimit || newByteOffset == this.byteLimit && newBitOffset == 0;
    }

    public boolean readBit() {
        boolean returnValue = (this.data[this.byteOffset] & 128 >> this.bitOffset) != 0;
        this.skipBit();
        return returnValue;
    }

    public int readBits(int numBits) {
        int returnValue = 0;

        for(this.bitOffset += numBits; this.bitOffset > 8; this.byteOffset += this.shouldSkipByte(this.byteOffset + 1) ? 2 : 1) {
            this.bitOffset -= 8;
            returnValue |= (this.data[this.byteOffset] & 255) << this.bitOffset;
        }

        returnValue |= (this.data[this.byteOffset] & 255) >> 8 - this.bitOffset;
        returnValue &= -1 >>> 32 - numBits;
        if (this.bitOffset == 8) {
            this.bitOffset = 0;
            this.byteOffset += this.shouldSkipByte(this.byteOffset + 1) ? 2 : 1;
        }

        this.assertValidOffset();
        return returnValue;
    }

    public boolean canReadExpGolombCodedNum() {
        int initialByteOffset = this.byteOffset;
        int initialBitOffset = this.bitOffset;

        int leadingZeros;
        for(leadingZeros = 0; this.byteOffset < this.byteLimit && !this.readBit(); ++leadingZeros) {
        }

        boolean hitLimit = this.byteOffset == this.byteLimit;
        this.byteOffset = initialByteOffset;
        this.bitOffset = initialBitOffset;
        return !hitLimit && this.canReadBits(leadingZeros * 2 + 1);
    }

    public int readUnsignedExpGolombCodedInt() {
        return this.readExpGolombCodeNum();
    }

    public int readSignedExpGolombCodedInt() {
        int codeNum = this.readExpGolombCodeNum();
        return (codeNum % 2 == 0 ? -1 : 1) * ((codeNum + 1) / 2);
    }

    private int readExpGolombCodeNum() {
        int leadingZeros;
        for(leadingZeros = 0; !this.readBit(); ++leadingZeros) {
        }

        return (1 << leadingZeros) - 1 + (leadingZeros > 0 ? this.readBits(leadingZeros) : 0);
    }

    private boolean shouldSkipByte(int offset) {
        return 2 <= offset && offset < this.byteLimit && this.data[offset] == 3 && this.data[offset - 2] == 0 && this.data[offset - 1] == 0;
    }

    private void assertValidOffset() {
        Assertions.checkState(this.byteOffset >= 0 && (this.byteOffset < this.byteLimit || this.byteOffset == this.byteLimit && this.bitOffset == 0));
    }
}
