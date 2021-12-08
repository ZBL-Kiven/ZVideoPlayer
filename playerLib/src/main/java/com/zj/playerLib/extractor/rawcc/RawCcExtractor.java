package com.zj.playerLib.extractor.rawcc;

import com.zj.playerLib.Format;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.extractor.SeekMap.Unseekable;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;

import java.io.IOException;

public final class RawCcExtractor implements Extractor {
    private static final int SCRATCH_SIZE = 9;
    private static final int HEADER_SIZE = 8;
    private static final int HEADER_ID = Util.getIntegerCodeForString("RCC\u0001");
    private static final int TIMESTAMP_SIZE_V0 = 4;
    private static final int TIMESTAMP_SIZE_V1 = 8;
    private static final int STATE_READING_HEADER = 0;
    private static final int STATE_READING_TIMESTAMP_AND_COUNT = 1;
    private static final int STATE_READING_SAMPLES = 2;
    private final Format format;
    private final ParsableByteArray dataScratch;
    private TrackOutput trackOutput;
    private int parserState;
    private int version;
    private long timestampUs;
    private int remainingSampleCount;
    private int sampleBytesWritten;

    public RawCcExtractor(Format format) {
        this.format = format;
        this.dataScratch = new ParsableByteArray(9);
        this.parserState = 0;
    }

    public void init(ExtractorOutput output) {
        output.seekMap(new Unseekable(-Long.MAX_VALUE));
        this.trackOutput = output.track(0, 3);
        output.endTracks();
        this.trackOutput.format(this.format);
    }

    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        this.dataScratch.reset();
        input.peekFully(this.dataScratch.data, 0, 8);
        return this.dataScratch.readInt() == HEADER_ID;
    }

    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        while(true) {
            switch(this.parserState) {
            case 0:
                if (this.parseHeader(input)) {
                    this.parserState = 1;
                    break;
                }

                return -1;
            case 1:
                if (this.parseTimestampAndSampleCount(input)) {
                    this.parserState = 2;
                    break;
                }

                this.parserState = 0;
                return -1;
            case 2:
                this.parseSamples(input);
                this.parserState = 1;
                return 0;
            default:
                throw new IllegalStateException();
            }
        }
    }

    public void seek(long position, long timeUs) {
        this.parserState = 0;
    }

    public void release() {
    }

    private boolean parseHeader(ExtractorInput input) throws IOException, InterruptedException {
        this.dataScratch.reset();
        if (input.readFully(this.dataScratch.data, 0, 8, true)) {
            if (this.dataScratch.readInt() != HEADER_ID) {
                throw new IOException("Input not RawCC");
            } else {
                this.version = this.dataScratch.readUnsignedByte();
                return true;
            }
        } else {
            return false;
        }
    }

    private boolean parseTimestampAndSampleCount(ExtractorInput input) throws IOException, InterruptedException {
        this.dataScratch.reset();
        if (this.version == 0) {
            if (!input.readFully(this.dataScratch.data, 0, 5, true)) {
                return false;
            }

            this.timestampUs = this.dataScratch.readUnsignedInt() * 1000L / 45L;
        } else {
            if (this.version != 1) {
                throw new ParserException("Unsupported version number: " + this.version);
            }

            if (!input.readFully(this.dataScratch.data, 0, 9, true)) {
                return false;
            }

            this.timestampUs = this.dataScratch.readLong();
        }

        this.remainingSampleCount = this.dataScratch.readUnsignedByte();
        this.sampleBytesWritten = 0;
        return true;
    }

    private void parseSamples(ExtractorInput input) throws IOException, InterruptedException {
        while(this.remainingSampleCount > 0) {
            this.dataScratch.reset();
            input.readFully(this.dataScratch.data, 0, 3);
            this.trackOutput.sampleData(this.dataScratch, 3);
            this.sampleBytesWritten += 3;
            --this.remainingSampleCount;
        }

        if (this.sampleBytesWritten > 0) {
            this.trackOutput.sampleMetadata(this.timestampUs, 1, this.sampleBytesWritten, 0, null);
        }

    }
}
