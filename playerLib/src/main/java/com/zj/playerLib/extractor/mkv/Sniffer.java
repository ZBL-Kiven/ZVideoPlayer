//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.mkv;

import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.util.ParsableByteArray;

import java.io.IOException;

final class Sniffer {
    private static final int SEARCH_LENGTH = 1024;
    private static final int ID_EBML = 440786851;
    private final ParsableByteArray scratch = new ParsableByteArray(8);
    private int peekLength;

    public Sniffer() {
    }

    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        long inputLength = input.getLength();
        int bytesToSearch = (int)(inputLength != -1L && inputLength <= 1024L ? inputLength : 1024L);
        input.peekFully(this.scratch.data, 0, 4);
        long tag = this.scratch.readUnsignedInt();

        for(this.peekLength = 4; tag != 440786851L; tag |= (long)(this.scratch.data[0] & 255)) {
            if (++this.peekLength == bytesToSearch) {
                return false;
            }

            input.peekFully(this.scratch.data, 0, 1);
            tag = tag << 8 & -256L;
        }

        long headerSize = this.readUint(input);
        long headerStart = (long)this.peekLength;
        if (headerSize != -9223372036854775808L && (inputLength == -1L || headerStart + headerSize < inputLength)) {
            while((long)this.peekLength < headerStart + headerSize) {
                long id = this.readUint(input);
                if (id == -9223372036854775808L) {
                    return false;
                }

                long size = this.readUint(input);
                if (size < 0L || size > 2147483647L) {
                    return false;
                }

                if (size != 0L) {
                    int sizeInt = (int)size;
                    input.advancePeekPosition(sizeInt);
                    this.peekLength += sizeInt;
                }
            }

            return (long)this.peekLength == headerStart + headerSize;
        } else {
            return false;
        }
    }

    private long readUint(ExtractorInput input) throws IOException, InterruptedException {
        input.peekFully(this.scratch.data, 0, 1);
        int value = this.scratch.data[0] & 255;
        if (value == 0) {
            return -9223372036854775808L;
        } else {
            int mask = 128;

            int length;
            for(length = 0; (value & mask) == 0; ++length) {
                mask >>= 1;
            }

            value &= ~mask;
            input.peekFully(this.scratch.data, 1, length);

            for(int i = 0; i < length; ++i) {
                value <<= 8;
                value += this.scratch.data[i + 1] & 255;
            }

            this.peekLength += length + 1;
            return (long)value;
        }
    }
}
