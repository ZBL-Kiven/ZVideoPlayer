package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.Format;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.ParsableByteArray;

public final class Id3Reader implements ElementaryStreamReader {
    private static final String TAG = "Id3Reader";
    private static final int ID3_HEADER_SIZE = 10;
    private final ParsableByteArray id3Header = new ParsableByteArray(10);
    private TrackOutput output;
    private boolean writingSample;
    private long sampleTimeUs;
    private int sampleSize;
    private int sampleBytesRead;

    public Id3Reader() {
    }

    public void seek() {
        this.writingSample = false;
    }

    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
        idGenerator.generateNewId();
        this.output = extractorOutput.track(idGenerator.getTrackId(), 4);
        this.output.format(Format.createSampleFormat(idGenerator.getFormatId(), "application/id3", null, -1, null));
    }

    public void packetStarted(long pesTimeUs, int flags) {
        if ((flags & 4) != 0) {
            this.writingSample = true;
            this.sampleTimeUs = pesTimeUs;
            this.sampleSize = 0;
            this.sampleBytesRead = 0;
        }
    }

    public void consume(ParsableByteArray data) {
        if (this.writingSample) {
            int bytesAvailable = data.bytesLeft();
            int headerBytesAvailable;
            if (this.sampleBytesRead < 10) {
                headerBytesAvailable = Math.min(bytesAvailable, 10 - this.sampleBytesRead);
                System.arraycopy(data.data, data.getPosition(), this.id3Header.data, this.sampleBytesRead, headerBytesAvailable);
                if (this.sampleBytesRead + headerBytesAvailable == 10) {
                    this.id3Header.setPosition(0);
                    if (73 != this.id3Header.readUnsignedByte() || 68 != this.id3Header.readUnsignedByte() || 51 != this.id3Header.readUnsignedByte()) {
                        Log.w("Id3Reader", "Discarding invalid ID3 tag");
                        this.writingSample = false;
                        return;
                    }

                    this.id3Header.skipBytes(3);
                    this.sampleSize = 10 + this.id3Header.readSynchSafeInt();
                }
            }

            headerBytesAvailable = Math.min(bytesAvailable, this.sampleSize - this.sampleBytesRead);
            this.output.sampleData(data, headerBytesAvailable);
            this.sampleBytesRead += headerBytesAvailable;
        }
    }

    public void packetFinished() {
        if (this.writingSample && this.sampleSize != 0 && this.sampleBytesRead == this.sampleSize) {
            this.output.sampleMetadata(this.sampleTimeUs, 1, this.sampleSize, 0, null);
            this.writingSample = false;
        }
    }
}
