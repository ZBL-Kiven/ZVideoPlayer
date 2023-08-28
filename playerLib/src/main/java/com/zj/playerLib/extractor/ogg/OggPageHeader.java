package com.zj.playerLib.extractor.ogg;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;

import java.io.EOFException;
import java.io.IOException;

final class OggPageHeader {
    public static final int EMPTY_PAGE_HEADER_SIZE = 27;
    public static final int MAX_SEGMENT_COUNT = 255;
    public static final int MAX_PAGE_PAYLOAD = 65025;
    public static final int MAX_PAGE_SIZE = 65307;
    private static final int TYPE_OGGS = Util.getIntegerCodeForString("OggS");
    public int revision;
    public int type;
    public long granulePosition;
    public long streamSerialNumber;
    public long pageSequenceNumber;
    public long pageChecksum;
    public int pageSegmentCount;
    public int headerSize;
    public int bodySize;
    public final int[] laces = new int[255];
    private final ParsableByteArray scratch = new ParsableByteArray(255);

    OggPageHeader() {
    }

    public void reset() {
        this.revision = 0;
        this.type = 0;
        this.granulePosition = 0L;
        this.streamSerialNumber = 0L;
        this.pageSequenceNumber = 0L;
        this.pageChecksum = 0L;
        this.pageSegmentCount = 0;
        this.headerSize = 0;
        this.bodySize = 0;
    }

    public boolean populate(ExtractorInput input, boolean quiet) throws IOException, InterruptedException {
        this.scratch.reset();
        this.reset();
        boolean hasEnoughBytes = input.getLength() == -1L || input.getLength() - input.getPeekPosition() >= 27L;
        if (hasEnoughBytes && input.peekFully(this.scratch.data, 0, 27, true)) {
            if (this.scratch.readUnsignedInt() != (long)TYPE_OGGS) {
                if (quiet) {
                    return false;
                } else {
                    throw new ParserException("expected OggS capture pattern at begin of page");
                }
            } else {
                this.revision = this.scratch.readUnsignedByte();
                if (this.revision != 0) {
                    if (quiet) {
                        return false;
                    } else {
                        throw new ParserException("unsupported bit stream revision");
                    }
                } else {
                    this.type = this.scratch.readUnsignedByte();
                    this.granulePosition = this.scratch.readLittleEndianLong();
                    this.streamSerialNumber = this.scratch.readLittleEndianUnsignedInt();
                    this.pageSequenceNumber = this.scratch.readLittleEndianUnsignedInt();
                    this.pageChecksum = this.scratch.readLittleEndianUnsignedInt();
                    this.pageSegmentCount = this.scratch.readUnsignedByte();
                    this.headerSize = 27 + this.pageSegmentCount;
                    this.scratch.reset();
                    input.peekFully(this.scratch.data, 0, this.pageSegmentCount);

                    for(int i = 0; i < this.pageSegmentCount; ++i) {
                        this.laces[i] = this.scratch.readUnsignedByte();
                        this.bodySize += this.laces[i];
                    }

                    return true;
                }
            }
        } else if (quiet) {
            return false;
        } else {
            throw new EOFException();
        }
    }
}
