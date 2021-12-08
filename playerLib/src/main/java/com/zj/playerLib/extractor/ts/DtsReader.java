package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.Format;
import com.zj.playerLib.audio.DtsUtil;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.util.ParsableByteArray;

public final class DtsReader implements ElementaryStreamReader {
    private static final int STATE_FINDING_SYNC = 0;
    private static final int STATE_READING_HEADER = 1;
    private static final int STATE_READING_SAMPLE = 2;
    private static final int HEADER_SIZE = 18;
    private final ParsableByteArray headerScratchBytes = new ParsableByteArray(new byte[18]);
    private final String language;
    private String formatId;
    private TrackOutput output;
    private int state = 0;
    private int bytesRead;
    private int syncBytes;
    private long sampleDurationUs;
    private Format format;
    private int sampleSize;
    private long timeUs;

    public DtsReader(String language) {
        this.language = language;
    }

    public void seek() {
        this.state = 0;
        this.bytesRead = 0;
        this.syncBytes = 0;
    }

    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
        idGenerator.generateNewId();
        this.formatId = idGenerator.getFormatId();
        this.output = extractorOutput.track(idGenerator.getTrackId(), 1);
    }

    public void packetStarted(long pesTimeUs, int flags) {
        this.timeUs = pesTimeUs;
    }

    public void consume(ParsableByteArray data) {
        while(data.bytesLeft() > 0) {
            switch(this.state) {
            case 0:
                if (this.skipToNextSync(data)) {
                    this.state = 1;
                }
                break;
            case 1:
                if (this.continueRead(data, this.headerScratchBytes.data, 18)) {
                    this.parseHeader();
                    this.headerScratchBytes.setPosition(0);
                    this.output.sampleData(this.headerScratchBytes, 18);
                    this.state = 2;
                }
                break;
            case 2:
                int bytesToRead = Math.min(data.bytesLeft(), this.sampleSize - this.bytesRead);
                this.output.sampleData(data, bytesToRead);
                this.bytesRead += bytesToRead;
                if (this.bytesRead == this.sampleSize) {
                    this.output.sampleMetadata(this.timeUs, 1, this.sampleSize, 0, null);
                    this.timeUs += this.sampleDurationUs;
                    this.state = 0;
                }
                break;
            default:
                throw new IllegalStateException();
            }
        }

    }

    public void packetFinished() {
    }

    private boolean continueRead(ParsableByteArray source, byte[] target, int targetLength) {
        int bytesToRead = Math.min(source.bytesLeft(), targetLength - this.bytesRead);
        source.readBytes(target, this.bytesRead, bytesToRead);
        this.bytesRead += bytesToRead;
        return this.bytesRead == targetLength;
    }

    private boolean skipToNextSync(ParsableByteArray pesBuffer) {
        while(true) {
            if (pesBuffer.bytesLeft() > 0) {
                this.syncBytes <<= 8;
                this.syncBytes |= pesBuffer.readUnsignedByte();
                if (!DtsUtil.isSyncWord(this.syncBytes)) {
                    continue;
                }

                this.headerScratchBytes.data[0] = (byte)(this.syncBytes >> 24 & 255);
                this.headerScratchBytes.data[1] = (byte)(this.syncBytes >> 16 & 255);
                this.headerScratchBytes.data[2] = (byte)(this.syncBytes >> 8 & 255);
                this.headerScratchBytes.data[3] = (byte)(this.syncBytes & 255);
                this.bytesRead = 4;
                this.syncBytes = 0;
                return true;
            }

            return false;
        }
    }

    private void parseHeader() {
        byte[] frameData = this.headerScratchBytes.data;
        if (this.format == null) {
            this.format = DtsUtil.parseDtsFormat(frameData, this.formatId, this.language, null);
            this.output.format(this.format);
        }

        this.sampleSize = DtsUtil.getDtsFrameSize(frameData);
        this.sampleDurationUs = (int)(1000000L * (long)DtsUtil.parseDtsAudioSampleCount(frameData) / (long)this.format.sampleRate);
    }
}
