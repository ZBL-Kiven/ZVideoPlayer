//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.ParsableBitArray;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.TimestampAdjuster;

public final class PesReader implements TsPayloadReader {
    private static final String TAG = "PesReader";
    private static final int STATE_FINDING_HEADER = 0;
    private static final int STATE_READING_HEADER = 1;
    private static final int STATE_READING_HEADER_EXTENSION = 2;
    private static final int STATE_READING_BODY = 3;
    private static final int HEADER_SIZE = 9;
    private static final int MAX_HEADER_EXTENSION_SIZE = 10;
    private static final int PES_SCRATCH_SIZE = 10;
    private final ElementaryStreamReader reader;
    private final ParsableBitArray pesScratch;
    private int state;
    private int bytesRead;
    private TimestampAdjuster timestampAdjuster;
    private boolean ptsFlag;
    private boolean dtsFlag;
    private boolean seenFirstDts;
    private int extendedHeaderLength;
    private int payloadSize;
    private boolean dataAlignmentIndicator;
    private long timeUs;

    public PesReader(ElementaryStreamReader reader) {
        this.reader = reader;
        this.pesScratch = new ParsableBitArray(new byte[10]);
        this.state = 0;
    }

    public void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
        this.timestampAdjuster = timestampAdjuster;
        this.reader.createTracks(extractorOutput, idGenerator);
    }

    public final void seek() {
        this.state = 0;
        this.bytesRead = 0;
        this.seenFirstDts = false;
        this.reader.seek();
    }

    public final void consume(ParsableByteArray data, int flags) throws ParserException {
        if ((flags & 1) != 0) {
            switch(this.state) {
            case 0:
            case 1:
                break;
            case 2:
                Log.w("PesReader", "Unexpected start indicator reading extended header");
                break;
            case 3:
                if (this.payloadSize != -1) {
                    Log.w("PesReader", "Unexpected start indicator: expected " + this.payloadSize + " more bytes");
                }

                this.reader.packetFinished();
                break;
            default:
                throw new IllegalStateException();
            }

            this.setState(1);
        }

        while(data.bytesLeft() > 0) {
            int readLength;
            switch(this.state) {
            case 0:
                data.skipBytes(data.bytesLeft());
                break;
            case 1:
                if (this.continueRead(data, this.pesScratch.data, 9)) {
                    this.setState(this.parseHeader() ? 2 : 0);
                }
                break;
            case 2:
                readLength = Math.min(10, this.extendedHeaderLength);
                if (this.continueRead(data, this.pesScratch.data, readLength) && this.continueRead(data, (byte[])null, this.extendedHeaderLength)) {
                    this.parseHeaderExtension();
                    flags |= this.dataAlignmentIndicator ? 4 : 0;
                    this.reader.packetStarted(this.timeUs, flags);
                    this.setState(3);
                }
                break;
            case 3:
                readLength = data.bytesLeft();
                int padding = this.payloadSize == -1 ? 0 : readLength - this.payloadSize;
                if (padding > 0) {
                    readLength -= padding;
                    data.setLimit(data.getPosition() + readLength);
                }

                this.reader.consume(data);
                if (this.payloadSize != -1) {
                    this.payloadSize -= readLength;
                    if (this.payloadSize == 0) {
                        this.reader.packetFinished();
                        this.setState(1);
                    }
                }
                break;
            default:
                throw new IllegalStateException();
            }
        }

    }

    private void setState(int state) {
        this.state = state;
        this.bytesRead = 0;
    }

    private boolean continueRead(ParsableByteArray source, byte[] target, int targetLength) {
        int bytesToRead = Math.min(source.bytesLeft(), targetLength - this.bytesRead);
        if (bytesToRead <= 0) {
            return true;
        } else {
            if (target == null) {
                source.skipBytes(bytesToRead);
            } else {
                source.readBytes(target, this.bytesRead, bytesToRead);
            }

            this.bytesRead += bytesToRead;
            return this.bytesRead == targetLength;
        }
    }

    private boolean parseHeader() {
        this.pesScratch.setPosition(0);
        int startCodePrefix = this.pesScratch.readBits(24);
        if (startCodePrefix != 1) {
            Log.w("PesReader", "Unexpected start code prefix: " + startCodePrefix);
            this.payloadSize = -1;
            return false;
        } else {
            this.pesScratch.skipBits(8);
            int packetLength = this.pesScratch.readBits(16);
            this.pesScratch.skipBits(5);
            this.dataAlignmentIndicator = this.pesScratch.readBit();
            this.pesScratch.skipBits(2);
            this.ptsFlag = this.pesScratch.readBit();
            this.dtsFlag = this.pesScratch.readBit();
            this.pesScratch.skipBits(6);
            this.extendedHeaderLength = this.pesScratch.readBits(8);
            if (packetLength == 0) {
                this.payloadSize = -1;
            } else {
                this.payloadSize = packetLength + 6 - 9 - this.extendedHeaderLength;
            }

            return true;
        }
    }

    private void parseHeaderExtension() {
        this.pesScratch.setPosition(0);
        this.timeUs = -9223372036854775807L;
        if (this.ptsFlag) {
            this.pesScratch.skipBits(4);
            long pts = (long)this.pesScratch.readBits(3) << 30;
            this.pesScratch.skipBits(1);
            pts |= (long)(this.pesScratch.readBits(15) << 15);
            this.pesScratch.skipBits(1);
            pts |= (long)this.pesScratch.readBits(15);
            this.pesScratch.skipBits(1);
            if (!this.seenFirstDts && this.dtsFlag) {
                this.pesScratch.skipBits(4);
                long dts = (long)this.pesScratch.readBits(3) << 30;
                this.pesScratch.skipBits(1);
                dts |= (long)(this.pesScratch.readBits(15) << 15);
                this.pesScratch.skipBits(1);
                dts |= (long)this.pesScratch.readBits(15);
                this.pesScratch.skipBits(1);
                this.timestampAdjuster.adjustTsTimestamp(dts);
                this.seenFirstDts = true;
            }

            this.timeUs = this.timestampAdjuster.adjustTsTimestamp(pts);
        }

    }
}
