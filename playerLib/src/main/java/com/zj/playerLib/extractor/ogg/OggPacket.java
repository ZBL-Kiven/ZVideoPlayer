//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.ogg;

import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.ParsableByteArray;

import java.io.IOException;
import java.util.Arrays;

final class OggPacket {
    private final OggPageHeader pageHeader = new OggPageHeader();
    private final ParsableByteArray packetArray = new ParsableByteArray(new byte['︁'], 0);
    private int currentSegmentIndex = -1;
    private int segmentCount;
    private boolean populated;

    OggPacket() {
    }

    public void reset() {
        this.pageHeader.reset();
        this.packetArray.reset();
        this.currentSegmentIndex = -1;
        this.populated = false;
    }

    public boolean populate(ExtractorInput input) throws IOException, InterruptedException {
        Assertions.checkState(input != null);
        if (this.populated) {
            this.populated = false;
            this.packetArray.reset();
        }

        int segmentIndex;
        for(; !this.populated; this.currentSegmentIndex = segmentIndex == this.pageHeader.pageSegmentCount ? -1 : segmentIndex) {
            int size;
            if (this.currentSegmentIndex < 0) {
                if (!this.pageHeader.populate(input, true)) {
                    return false;
                }

                size = 0;
                segmentIndex = this.pageHeader.headerSize;
                if ((this.pageHeader.type & 1) == 1 && this.packetArray.limit() == 0) {
                    segmentIndex += this.calculatePacketSize(size);
                    size += this.segmentCount;
                }

                input.skipFully(segmentIndex);
                this.currentSegmentIndex = size;
            }

            size = this.calculatePacketSize(this.currentSegmentIndex);
            segmentIndex = this.currentSegmentIndex + this.segmentCount;
            if (size > 0) {
                if (this.packetArray.capacity() < this.packetArray.limit() + size) {
                    this.packetArray.data = Arrays.copyOf(this.packetArray.data, this.packetArray.limit() + size);
                }

                input.readFully(this.packetArray.data, this.packetArray.limit(), size);
                this.packetArray.setLimit(this.packetArray.limit() + size);
                this.populated = this.pageHeader.laces[segmentIndex - 1] != 255;
            }
        }

        return true;
    }

    public OggPageHeader getPageHeader() {
        return this.pageHeader;
    }

    public ParsableByteArray getPayload() {
        return this.packetArray;
    }

    public void trimPayload() {
        if (this.packetArray.data.length != 65025) {
            this.packetArray.data = Arrays.copyOf(this.packetArray.data, Math.max(65025, this.packetArray.limit()));
        }
    }

    private int calculatePacketSize(int startSegmentIndex) {
        this.segmentCount = 0;
        int size = 0;

        while(startSegmentIndex + this.segmentCount < this.pageHeader.pageSegmentCount) {
            int segmentLength = this.pageHeader.laces[startSegmentIndex + this.segmentCount++];
            size += segmentLength;
            if (segmentLength != 255) {
                break;
            }
        }

        return size;
    }
}
