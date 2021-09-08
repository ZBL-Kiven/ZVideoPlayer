//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.util;

public final class ParsableBitArray {
    public byte[] data;
    private int byteOffset;
    private int bitOffset;
    private int byteLimit;

    public ParsableBitArray() {
        this.data = Util.EMPTY_BYTE_ARRAY;
    }

    public ParsableBitArray(byte[] data) {
        this(data, data.length);
    }

    public ParsableBitArray(byte[] data, int limit) {
        this.data = data;
        this.byteLimit = limit;
    }

    public void reset(byte[] data) {
        this.reset(data, data.length);
    }

    public void reset(ParsableByteArray parsableByteArray) {
        this.reset(parsableByteArray.data, parsableByteArray.limit());
        this.setPosition(parsableByteArray.getPosition() * 8);
    }

    public void reset(byte[] data, int limit) {
        this.data = data;
        this.byteOffset = 0;
        this.bitOffset = 0;
        this.byteLimit = limit;
    }

    public int bitsLeft() {
        return (this.byteLimit - this.byteOffset) * 8 - this.bitOffset;
    }

    public int getPosition() {
        return this.byteOffset * 8 + this.bitOffset;
    }

    public int getBytePosition() {
        Assertions.checkState(this.bitOffset == 0);
        return this.byteOffset;
    }

    public void setPosition(int position) {
        this.byteOffset = position / 8;
        this.bitOffset = position - this.byteOffset * 8;
        this.assertValidOffset();
    }

    public void skipBit() {
        if (++this.bitOffset == 8) {
            this.bitOffset = 0;
            ++this.byteOffset;
        }

        this.assertValidOffset();
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

    public boolean readBit() {
        boolean returnValue = (this.data[this.byteOffset] & 128 >> this.bitOffset) != 0;
        this.skipBit();
        return returnValue;
    }

    public int readBits(int numBits) {
        if (numBits == 0) {
            return 0;
        } else {
            int returnValue = 0;

            for(this.bitOffset += numBits; this.bitOffset > 8; returnValue |= (this.data[this.byteOffset++] & 255) << this.bitOffset) {
                this.bitOffset -= 8;
            }

            returnValue |= (this.data[this.byteOffset] & 255) >> 8 - this.bitOffset;
            returnValue &= -1 >>> 32 - numBits;
            if (this.bitOffset == 8) {
                this.bitOffset = 0;
                ++this.byteOffset;
            }

            this.assertValidOffset();
            return returnValue;
        }
    }

    public void readBits(byte[] buffer, int offset, int numBits) {
        int to = offset + (numBits >> 3);

        int bitsLeft;
        for(bitsLeft = offset; bitsLeft < to; ++bitsLeft) {
            buffer[bitsLeft] = (byte)(this.data[this.byteOffset++] << this.bitOffset);
            buffer[bitsLeft] = (byte)(buffer[bitsLeft] | (this.data[this.byteOffset] & 255) >> 8 - this.bitOffset);
        }

        bitsLeft = numBits & 7;
        if (bitsLeft != 0) {
            buffer[to] = (byte)(buffer[to] & 255 >> bitsLeft);
            if (this.bitOffset + bitsLeft > 8) {
                buffer[to] = (byte)(buffer[to] | (this.data[this.byteOffset++] & 255) << this.bitOffset);
                this.bitOffset -= 8;
            }

            this.bitOffset += bitsLeft;
            int lastDataByteTrailingBits = (this.data[this.byteOffset] & 255) >> 8 - this.bitOffset;
            buffer[to] |= (byte)(lastDataByteTrailingBits << 8 - bitsLeft);
            if (this.bitOffset == 8) {
                this.bitOffset = 0;
                ++this.byteOffset;
            }

            this.assertValidOffset();
        }
    }

    public void byteAlign() {
        if (this.bitOffset != 0) {
            this.bitOffset = 0;
            ++this.byteOffset;
            this.assertValidOffset();
        }
    }

    public void readBytes(byte[] buffer, int offset, int length) {
        Assertions.checkState(this.bitOffset == 0);
        System.arraycopy(this.data, this.byteOffset, buffer, offset, length);
        this.byteOffset += length;
        this.assertValidOffset();
    }

    public void skipBytes(int length) {
        Assertions.checkState(this.bitOffset == 0);
        this.byteOffset += length;
        this.assertValidOffset();
    }

    public void putInt(int value, int numBits) {
        if (numBits < 32) {
            value &= (1 << numBits) - 1;
        }

        int firstByteReadSize = Math.min(8 - this.bitOffset, numBits);
        int firstByteRightPaddingSize = 8 - this.bitOffset - firstByteReadSize;
        int firstByteBitmask = '\uff00' >> this.bitOffset | (1 << firstByteRightPaddingSize) - 1;
        this.data[this.byteOffset] = (byte)(this.data[this.byteOffset] & firstByteBitmask);
        int firstByteInputBits = value >>> numBits - firstByteReadSize;
        this.data[this.byteOffset] = (byte)(this.data[this.byteOffset] | firstByteInputBits << firstByteRightPaddingSize);
        int remainingBitsToRead = numBits - firstByteReadSize;

        int currentByteIndex;
        for(currentByteIndex = this.byteOffset + 1; remainingBitsToRead > 8; remainingBitsToRead -= 8) {
            this.data[currentByteIndex++] = (byte)(value >>> remainingBitsToRead - 8);
        }

        int lastByteRightPaddingSize = 8 - remainingBitsToRead;
        this.data[currentByteIndex] = (byte)(this.data[currentByteIndex] & (1 << lastByteRightPaddingSize) - 1);
        int lastByteInput = value & (1 << remainingBitsToRead) - 1;
        this.data[currentByteIndex] = (byte)(this.data[currentByteIndex] | lastByteInput << lastByteRightPaddingSize);
        this.skipBits(numBits);
        this.assertValidOffset();
    }

    private void assertValidOffset() {
        Assertions.checkState(this.byteOffset >= 0 && (this.byteOffset < this.byteLimit || this.byteOffset == this.byteLimit && this.bitOffset == 0));
    }
}
