//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.metadata.scte35;

import com.zj.playerLib.metadata.Metadata;
import com.zj.playerLib.metadata.Metadata.Entry;
import com.zj.playerLib.metadata.MetadataDecoder;
import com.zj.playerLib.metadata.MetadataInputBuffer;
import com.zj.playerLib.util.ParsableBitArray;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.TimestampAdjuster;

import java.nio.ByteBuffer;

public final class SpliceInfoDecoder implements MetadataDecoder {
    private static final int TYPE_SPLICE_NULL = 0;
    private static final int TYPE_SPLICE_SCHEDULE = 4;
    private static final int TYPE_SPLICE_INSERT = 5;
    private static final int TYPE_TIME_SIGNAL = 6;
    private static final int TYPE_PRIVATE_COMMAND = 255;
    private final ParsableByteArray sectionData = new ParsableByteArray();
    private final ParsableBitArray sectionHeader = new ParsableBitArray();
    private TimestampAdjuster timestampAdjuster;

    public SpliceInfoDecoder() {
    }

    public Metadata decode(MetadataInputBuffer inputBuffer) {
        if (this.timestampAdjuster == null || inputBuffer.subSampleOffsetUs != this.timestampAdjuster.getTimestampOffsetUs()) {
            this.timestampAdjuster = new TimestampAdjuster(inputBuffer.timeUs);
            this.timestampAdjuster.adjustSampleTimestamp(inputBuffer.timeUs - inputBuffer.subSampleOffsetUs);
        }

        ByteBuffer buffer = inputBuffer.data;
        byte[] data = buffer.array();
        int size = buffer.limit();
        this.sectionData.reset(data, size);
        this.sectionHeader.reset(data, size);
        this.sectionHeader.skipBits(39);
        long ptsAdjustment = (long)this.sectionHeader.readBits(1);
        ptsAdjustment = ptsAdjustment << 32 | (long)this.sectionHeader.readBits(32);
        this.sectionHeader.skipBits(20);
        int spliceCommandLength = this.sectionHeader.readBits(12);
        int spliceCommandType = this.sectionHeader.readBits(8);
        SpliceCommand command = null;
        this.sectionData.skipBytes(14);
        switch(spliceCommandType) {
        case 0:
            command = new SpliceNullCommand();
            break;
        case 4:
            command = SpliceScheduleCommand.parseFromSection(this.sectionData);
            break;
        case 5:
            command = SpliceInsertCommand.parseFromSection(this.sectionData, ptsAdjustment, this.timestampAdjuster);
            break;
        case 6:
            command = TimeSignalCommand.parseFromSection(this.sectionData, ptsAdjustment, this.timestampAdjuster);
            break;
        case 255:
            command = PrivateCommand.parseFromSection(this.sectionData, spliceCommandLength, ptsAdjustment);
        }

        return command == null ? new Metadata(new Entry[0]) : new Metadata(new Entry[]{(Entry)command});
    }
}
