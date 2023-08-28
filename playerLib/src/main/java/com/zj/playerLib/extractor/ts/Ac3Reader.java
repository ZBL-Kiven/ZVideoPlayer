package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.Format;
import com.zj.playerLib.audio.Ac3Util;
import com.zj.playerLib.audio.Ac3Util.SyncFrameInfo;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.util.ParsableBitArray;
import com.zj.playerLib.util.ParsableByteArray;

import java.util.List;

public final class Ac3Reader implements ElementaryStreamReader {
    private static final int STATE_FINDING_SYNC = 0;
    private static final int STATE_READING_HEADER = 1;
    private static final int STATE_READING_SAMPLE = 2;
    private static final int HEADER_SIZE = 128;
    private final ParsableBitArray headerScratchBits;
    private final ParsableByteArray headerScratchBytes;
    private final String language;
    private String trackFormatId;
    private TrackOutput output;
    private int state;
    private int bytesRead;
    private boolean lastByteWas0B;
    private long sampleDurationUs;
    private Format format;
    private int sampleSize;
    private long timeUs;

    public Ac3Reader() {
        this(null);
    }

    public Ac3Reader(String language) {
        this.headerScratchBits = new ParsableBitArray(new byte[128]);
        this.headerScratchBytes = new ParsableByteArray(this.headerScratchBits.data);
        this.state = 0;
        this.language = language;
    }

    public void seek() {
        this.state = 0;
        this.bytesRead = 0;
        this.lastByteWas0B = false;
    }

    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator generator) {
        generator.generateNewId();
        this.trackFormatId = generator.getFormatId();
        this.output = extractorOutput.track(generator.getTrackId(), 1);
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
                    this.headerScratchBytes.data[0] = 11;
                    this.headerScratchBytes.data[1] = 119;
                    this.bytesRead = 2;
                }
                break;
            case 1:
                if (this.continueRead(data, this.headerScratchBytes.data, 128)) {
                    this.parseHeader();
                    this.headerScratchBytes.setPosition(0);
                    this.output.sampleData(this.headerScratchBytes, 128);
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
        while(pesBuffer.bytesLeft() > 0) {
            if (!this.lastByteWas0B) {
                this.lastByteWas0B = pesBuffer.readUnsignedByte() == 11;
            } else {
                int secondByte = pesBuffer.readUnsignedByte();
                if (secondByte == 119) {
                    this.lastByteWas0B = false;
                    return true;
                }

                this.lastByteWas0B = secondByte == 11;
            }
        }

        return false;
    }

    private void parseHeader() {
        this.headerScratchBits.setPosition(0);
        SyncFrameInfo frameInfo = Ac3Util.parseAc3SyncframeInfo(this.headerScratchBits);
        if (this.format == null || frameInfo.channelCount != this.format.channelCount || frameInfo.sampleRate != this.format.sampleRate || frameInfo.mimeType != this.format.sampleMimeType) {
            this.format = Format.createAudioSampleFormat(this.trackFormatId, frameInfo.mimeType, null, -1, -1, frameInfo.channelCount, frameInfo.sampleRate, null, null, 0, this.language);
            this.output.format(this.format);
        }

        this.sampleSize = frameInfo.frameSize;
        this.sampleDurationUs = 1000000L * (long)frameInfo.sampleCount / (long)this.format.sampleRate;
    }
}
