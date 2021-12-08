package com.zj.playerLib.util;

import androidx.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class ParsableByteArray {
    public byte[] data;
    private int position;
    private int limit;

    public ParsableByteArray() {
        this.data = Util.EMPTY_BYTE_ARRAY;
    }

    public ParsableByteArray(int limit) {
        this.data = new byte[limit];
        this.limit = limit;
    }

    public ParsableByteArray(byte[] data) {
        this.data = data;
        this.limit = data.length;
    }

    public ParsableByteArray(byte[] data, int limit) {
        this.data = data;
        this.limit = limit;
    }

    public void reset() {
        this.position = 0;
        this.limit = 0;
    }

    public void reset(int limit) {
        this.reset(this.capacity() < limit ? new byte[limit] : this.data, limit);
    }

    public void reset(byte[] data) {
        this.reset(data, data.length);
    }

    public void reset(byte[] data, int limit) {
        this.data = data;
        this.limit = limit;
        this.position = 0;
    }

    public int bytesLeft() {
        return this.limit - this.position;
    }

    public int limit() {
        return this.limit;
    }

    public void setLimit(int limit) {
        Assertions.checkArgument(limit >= 0 && limit <= this.data.length);
        this.limit = limit;
    }

    public int getPosition() {
        return this.position;
    }

    public int capacity() {
        return this.data.length;
    }

    public void setPosition(int position) {
        Assertions.checkArgument(position >= 0 && position <= this.limit);
        this.position = position;
    }

    public void skipBytes(int bytes) {
        this.setPosition(this.position + bytes);
    }

    public void readBytes(ParsableBitArray bitArray, int length) {
        this.readBytes(bitArray.data, 0, length);
        bitArray.setPosition(0);
    }

    public void readBytes(byte[] buffer, int offset, int length) {
        System.arraycopy(this.data, this.position, buffer, offset, length);
        this.position += length;
    }

    public void readBytes(ByteBuffer buffer, int length) {
        buffer.put(this.data, this.position, length);
        this.position += length;
    }

    public int peekUnsignedByte() {
        return this.data[this.position] & 255;
    }

    public char peekChar() {
        return (char)((this.data[this.position] & 255) << 8 | this.data[this.position + 1] & 255);
    }

    public int readUnsignedByte() {
        return this.data[this.position++] & 255;
    }

    public int readUnsignedShort() {
        return (this.data[this.position++] & 255) << 8 | this.data[this.position++] & 255;
    }

    public int readLittleEndianUnsignedShort() {
        return this.data[this.position++] & 255 | (this.data[this.position++] & 255) << 8;
    }

    public short readShort() {
        return (short)((this.data[this.position++] & 255) << 8 | this.data[this.position++] & 255);
    }

    public short readLittleEndianShort() {
        return (short)(this.data[this.position++] & 255 | (this.data[this.position++] & 255) << 8);
    }

    public int readUnsignedInt24() {
        return (this.data[this.position++] & 255) << 16 | (this.data[this.position++] & 255) << 8 | this.data[this.position++] & 255;
    }

    public int readInt24() {
        return (this.data[this.position++] & 255) << 24 >> 8 | (this.data[this.position++] & 255) << 8 | this.data[this.position++] & 255;
    }

    public int readLittleEndianInt24() {
        return this.data[this.position++] & 255 | (this.data[this.position++] & 255) << 8 | (this.data[this.position++] & 255) << 16;
    }

    public int readLittleEndianUnsignedInt24() {
        return this.data[this.position++] & 255 | (this.data[this.position++] & 255) << 8 | (this.data[this.position++] & 255) << 16;
    }

    public long readUnsignedInt() {
        return ((long)this.data[this.position++] & 255L) << 24 | ((long)this.data[this.position++] & 255L) << 16 | ((long)this.data[this.position++] & 255L) << 8 | (long)this.data[this.position++] & 255L;
    }

    public long readLittleEndianUnsignedInt() {
        return (long)this.data[this.position++] & 255L | ((long)this.data[this.position++] & 255L) << 8 | ((long)this.data[this.position++] & 255L) << 16 | ((long)this.data[this.position++] & 255L) << 24;
    }

    public int readInt() {
        return (this.data[this.position++] & 255) << 24 | (this.data[this.position++] & 255) << 16 | (this.data[this.position++] & 255) << 8 | this.data[this.position++] & 255;
    }

    public int readLittleEndianInt() {
        return this.data[this.position++] & 255 | (this.data[this.position++] & 255) << 8 | (this.data[this.position++] & 255) << 16 | (this.data[this.position++] & 255) << 24;
    }

    public long readLong() {
        return ((long)this.data[this.position++] & 255L) << 56 | ((long)this.data[this.position++] & 255L) << 48 | ((long)this.data[this.position++] & 255L) << 40 | ((long)this.data[this.position++] & 255L) << 32 | ((long)this.data[this.position++] & 255L) << 24 | ((long)this.data[this.position++] & 255L) << 16 | ((long)this.data[this.position++] & 255L) << 8 | (long)this.data[this.position++] & 255L;
    }

    public long readLittleEndianLong() {
        return (long)this.data[this.position++] & 255L | ((long)this.data[this.position++] & 255L) << 8 | ((long)this.data[this.position++] & 255L) << 16 | ((long)this.data[this.position++] & 255L) << 24 | ((long)this.data[this.position++] & 255L) << 32 | ((long)this.data[this.position++] & 255L) << 40 | ((long)this.data[this.position++] & 255L) << 48 | ((long)this.data[this.position++] & 255L) << 56;
    }

    public int readUnsignedFixedPoint1616() {
        int result = (this.data[this.position++] & 255) << 8 | this.data[this.position++] & 255;
        this.position += 2;
        return result;
    }

    public int readSynchSafeInt() {
        int b1 = this.readUnsignedByte();
        int b2 = this.readUnsignedByte();
        int b3 = this.readUnsignedByte();
        int b4 = this.readUnsignedByte();
        return b1 << 21 | b2 << 14 | b3 << 7 | b4;
    }

    public int readUnsignedIntToInt() {
        int result = this.readInt();
        if (result < 0) {
            throw new IllegalStateException("Top bit not zero: " + result);
        } else {
            return result;
        }
    }

    public int readLittleEndianUnsignedIntToInt() {
        int result = this.readLittleEndianInt();
        if (result < 0) {
            throw new IllegalStateException("Top bit not zero: " + result);
        } else {
            return result;
        }
    }

    public long readUnsignedLongToLong() {
        long result = this.readLong();
        if (result < 0L) {
            throw new IllegalStateException("Top bit not zero: " + result);
        } else {
            return result;
        }
    }

    public float readFloat() {
        return Float.intBitsToFloat(this.readInt());
    }

    public double readDouble() {
        return Double.longBitsToDouble(this.readLong());
    }

    public String readString(int length) {
        return this.readString(length, StandardCharsets.UTF_8);
    }

    public String readString(int length, Charset charset) {
        String result = new String(this.data, this.position, length, charset);
        this.position += length;
        return result;
    }

    public String readNullTerminatedString(int length) {
        if (length == 0) {
            return "";
        } else {
            int stringLength = length;
            int lastIndex = this.position + length - 1;
            if (lastIndex < this.limit && this.data[lastIndex] == 0) {
                stringLength = length - 1;
            }

            String result = Util.fromUtf8Bytes(this.data, this.position, stringLength);
            this.position += length;
            return result;
        }
    }

    @Nullable
    public String readNullTerminatedString() {
        if (this.bytesLeft() == 0) {
            return null;
        } else {
            int stringLimit;
            for(stringLimit = this.position; stringLimit < this.limit && this.data[stringLimit] != 0; ++stringLimit) {
            }

            String string = Util.fromUtf8Bytes(this.data, this.position, stringLimit - this.position);
            this.position = stringLimit;
            if (this.position < this.limit) {
                ++this.position;
            }

            return string;
        }
    }

    @Nullable
    public String readLine() {
        if (this.bytesLeft() == 0) {
            return null;
        } else {
            int lineLimit;
            for(lineLimit = this.position; lineLimit < this.limit && !Util.isLinebreak(this.data[lineLimit]); ++lineLimit) {
            }

            if (lineLimit - this.position >= 3 && this.data[this.position] == -17 && this.data[this.position + 1] == -69 && this.data[this.position + 2] == -65) {
                this.position += 3;
            }

            String line = Util.fromUtf8Bytes(this.data, this.position, lineLimit - this.position);
            this.position = lineLimit;
            if (this.position == this.limit) {
                return line;
            } else {
                if (this.data[this.position] == 13) {
                    ++this.position;
                    if (this.position == this.limit) {
                        return line;
                    }
                }

                if (this.data[this.position] == 10) {
                    ++this.position;
                }

                return line;
            }
        }
    }

    public long readUtf8EncodedLong() {
        int length = 0;
        long value = this.data[this.position];

        int j;
        for(j = 7; j >= 0; --j) {
            if ((value & (long)(1 << j)) == 0L) {
                if (j < 6) {
                    value &= (1 << j) - 1;
                    length = 7 - j;
                } else if (j == 7) {
                    length = 1;
                }
                break;
            }
        }

        if (length == 0) {
            throw new NumberFormatException("Invalid UTF-8 sequence first byte: " + value);
        } else {
            for(j = 1; j < length; ++j) {
                int x = this.data[this.position + j];
                if ((x & 192) != 128) {
                    throw new NumberFormatException("Invalid UTF-8 sequence continuation byte: " + value);
                }

                value = value << 6 | (long)(x & 63);
            }

            this.position += length;
            return value;
        }
    }
}
