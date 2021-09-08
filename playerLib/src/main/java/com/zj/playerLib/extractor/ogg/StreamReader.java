//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.ogg;

import com.zj.playerLib.Format;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.extractor.SeekMap;
import com.zj.playerLib.extractor.SeekMap.Unseekable;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.util.ParsableByteArray;

import java.io.IOException;

abstract class StreamReader {
    private static final int STATE_READ_HEADERS = 0;
    private static final int STATE_SKIP_HEADERS = 1;
    private static final int STATE_READ_PAYLOAD = 2;
    private static final int STATE_END_OF_INPUT = 3;
    private final OggPacket oggPacket = new OggPacket();
    private TrackOutput trackOutput;
    private ExtractorOutput extractorOutput;
    private OggSeeker oggSeeker;
    private long targetGranule;
    private long payloadStartPosition;
    private long currentGranule;
    private int state;
    private int sampleRate;
    private SetupData setupData;
    private long lengthOfReadPacket;
    private boolean seekMapSet;
    private boolean formatSet;

    public StreamReader() {
    }

    void init(ExtractorOutput output, TrackOutput trackOutput) {
        this.extractorOutput = output;
        this.trackOutput = trackOutput;
        this.reset(true);
    }

    protected void reset(boolean headerData) {
        if (headerData) {
            this.setupData = new SetupData();
            this.payloadStartPosition = 0L;
            this.state = 0;
        } else {
            this.state = 1;
        }

        this.targetGranule = -1L;
        this.currentGranule = 0L;
    }

    final void seek(long position, long timeUs) {
        this.oggPacket.reset();
        if (position == 0L) {
            this.reset(!this.seekMapSet);
        } else if (this.state != 0) {
            this.targetGranule = this.oggSeeker.startSeek(timeUs);
            this.state = 2;
        }

    }

    final int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        switch(this.state) {
        case 0:
            return this.readHeaders(input);
        case 1:
            input.skipFully((int)this.payloadStartPosition);
            this.state = 2;
            return 0;
        case 2:
            return this.readPayload(input, seekPosition);
        default:
            throw new IllegalStateException();
        }
    }

    private int readHeaders(ExtractorInput input) throws IOException, InterruptedException {
        boolean readingHeaders = true;

        while(readingHeaders) {
            if (!this.oggPacket.populate(input)) {
                this.state = 3;
                return -1;
            }

            this.lengthOfReadPacket = input.getPosition() - this.payloadStartPosition;
            readingHeaders = this.readHeaders(this.oggPacket.getPayload(), this.payloadStartPosition, this.setupData);
            if (readingHeaders) {
                this.payloadStartPosition = input.getPosition();
            }
        }

        this.sampleRate = this.setupData.format.sampleRate;
        if (!this.formatSet) {
            this.trackOutput.format(this.setupData.format);
            this.formatSet = true;
        }

        if (this.setupData.oggSeeker != null) {
            this.oggSeeker = this.setupData.oggSeeker;
        } else if (input.getLength() == -1L) {
            this.oggSeeker = new UnseekableOggSeeker();
        } else {
            OggPageHeader firstPayloadPageHeader = this.oggPacket.getPageHeader();
            boolean isLastPage = (firstPayloadPageHeader.type & 4) != 0;
            this.oggSeeker = new DefaultOggSeeker(this.payloadStartPosition, input.getLength(), this, (long)(firstPayloadPageHeader.headerSize + firstPayloadPageHeader.bodySize), firstPayloadPageHeader.granulePosition, isLastPage);
        }

        this.setupData = null;
        this.state = 2;
        this.oggPacket.trimPayload();
        return 0;
    }

    private int readPayload(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        long position = this.oggSeeker.read(input);
        if (position >= 0L) {
            seekPosition.position = position;
            return 1;
        } else {
            if (position < -1L) {
                this.onSeekEnd(-(position + 2L));
            }

            if (!this.seekMapSet) {
                SeekMap seekMap = this.oggSeeker.createSeekMap();
                this.extractorOutput.seekMap(seekMap);
                this.seekMapSet = true;
            }

            if (this.lengthOfReadPacket <= 0L && !this.oggPacket.populate(input)) {
                this.state = 3;
                return -1;
            } else {
                this.lengthOfReadPacket = 0L;
                ParsableByteArray payload = this.oggPacket.getPayload();
                long granulesInPacket = this.preparePayload(payload);
                if (granulesInPacket >= 0L && this.currentGranule + granulesInPacket >= this.targetGranule) {
                    long timeUs = this.convertGranuleToTime(this.currentGranule);
                    this.trackOutput.sampleData(payload, payload.limit());
                    this.trackOutput.sampleMetadata(timeUs, 1, payload.limit(), 0, (CryptoData)null);
                    this.targetGranule = -1L;
                }

                this.currentGranule += granulesInPacket;
                return 0;
            }
        }
    }

    protected long convertGranuleToTime(long granule) {
        return granule * 1000000L / (long)this.sampleRate;
    }

    protected long convertTimeToGranule(long timeUs) {
        return (long)this.sampleRate * timeUs / 1000000L;
    }

    protected abstract long preparePayload(ParsableByteArray var1);

    protected abstract boolean readHeaders(ParsableByteArray var1, long var2, SetupData var4) throws IOException, InterruptedException;

    protected void onSeekEnd(long currentGranule) {
        this.currentGranule = currentGranule;
    }

    private static final class UnseekableOggSeeker implements OggSeeker {
        private UnseekableOggSeeker() {
        }

        public long read(ExtractorInput input) throws IOException, InterruptedException {
            return -1L;
        }

        public long startSeek(long timeUs) {
            return 0L;
        }

        public SeekMap createSeekMap() {
            return new Unseekable(-9223372036854775807L);
        }
    }

    static class SetupData {
        Format format;
        OggSeeker oggSeeker;

        SetupData() {
        }
    }
}
