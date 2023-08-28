package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.TimestampAdjuster;
import com.zj.playerLib.util.Util;

public final class SectionReader implements TsPayloadReader {
    private static final int SECTION_HEADER_LENGTH = 3;
    private static final int DEFAULT_SECTION_BUFFER_LENGTH = 32;
    private static final int MAX_SECTION_LENGTH = 4098;
    private final SectionPayloadReader reader;
    private final ParsableByteArray sectionData;
    private int totalSectionLength;
    private int bytesRead;
    private boolean sectionSyntaxIndicator;
    private boolean waitingForPayloadStart;

    public SectionReader(SectionPayloadReader reader) {
        this.reader = reader;
        this.sectionData = new ParsableByteArray(32);
    }

    public void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
        this.reader.init(timestampAdjuster, extractorOutput, idGenerator);
        this.waitingForPayloadStart = true;
    }

    public void seek() {
        this.waitingForPayloadStart = true;
    }

    public void consume(ParsableByteArray data, int flags) {
        boolean payloadUnitStartIndicator = (flags & 1) != 0;
        int payloadStartPosition = -1;
        int tableId;
        if (payloadUnitStartIndicator) {
            tableId = data.readUnsignedByte();
            payloadStartPosition = data.getPosition() + tableId;
        }

        if (this.waitingForPayloadStart) {
            if (!payloadUnitStartIndicator) {
                return;
            }

            this.waitingForPayloadStart = false;
            data.setPosition(payloadStartPosition);
            this.bytesRead = 0;
        }

        while(data.bytesLeft() > 0) {
            if (this.bytesRead < 3) {
                if (this.bytesRead == 0) {
                    tableId = data.readUnsignedByte();
                    data.setPosition(data.getPosition() - 1);
                    if (tableId == 255) {
                        this.waitingForPayloadStart = true;
                        return;
                    }
                }

                tableId = Math.min(data.bytesLeft(), 3 - this.bytesRead);
                data.readBytes(this.sectionData.data, this.bytesRead, tableId);
                this.bytesRead += tableId;
                if (this.bytesRead == 3) {
                    this.sectionData.reset(3);
                    this.sectionData.skipBytes(1);
                    int secondHeaderByte = this.sectionData.readUnsignedByte();
                    int thirdHeaderByte = this.sectionData.readUnsignedByte();
                    this.sectionSyntaxIndicator = (secondHeaderByte & 128) != 0;
                    this.totalSectionLength = ((secondHeaderByte & 15) << 8 | thirdHeaderByte) + 3;
                    if (this.sectionData.capacity() < this.totalSectionLength) {
                        byte[] bytes = this.sectionData.data;
                        this.sectionData.reset(Math.min(4098, Math.max(this.totalSectionLength, bytes.length * 2)));
                        System.arraycopy(bytes, 0, this.sectionData.data, 0, 3);
                    }
                }
            } else {
                tableId = Math.min(data.bytesLeft(), this.totalSectionLength - this.bytesRead);
                data.readBytes(this.sectionData.data, this.bytesRead, tableId);
                this.bytesRead += tableId;
                if (this.bytesRead == this.totalSectionLength) {
                    if (this.sectionSyntaxIndicator) {
                        if (Util.crc(this.sectionData.data, 0, this.totalSectionLength, -1) != 0) {
                            this.waitingForPayloadStart = true;
                            return;
                        }

                        this.sectionData.reset(this.totalSectionLength - 4);
                    } else {
                        this.sectionData.reset(this.totalSectionLength);
                    }

                    this.reader.consume(this.sectionData);
                    this.bytesRead = 0;
                }
            }
        }

    }
}
