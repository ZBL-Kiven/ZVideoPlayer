package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.Format;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.extractor.ts.TsPayloadReader.DvbSubtitleInfo;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.util.ParsableByteArray;

import java.util.Collections;
import java.util.List;

public final class DvbSubtitleReader implements ElementaryStreamReader {
    private final List<DvbSubtitleInfo> subtitleInfos;
    private final TrackOutput[] outputs;
    private boolean writingSample;
    private int bytesToCheck;
    private int sampleBytesWritten;
    private long sampleTimeUs;

    public DvbSubtitleReader(List<DvbSubtitleInfo> subtitleInfos) {
        this.subtitleInfos = subtitleInfos;
        this.outputs = new TrackOutput[subtitleInfos.size()];
    }

    public void seek() {
        this.writingSample = false;
    }

    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
        for(int i = 0; i < this.outputs.length; ++i) {
            DvbSubtitleInfo subtitleInfo = this.subtitleInfos.get(i);
            idGenerator.generateNewId();
            TrackOutput output = extractorOutput.track(idGenerator.getTrackId(), 3);
            output.format(Format.createImageSampleFormat(idGenerator.getFormatId(), "application/dvbsubs", null, -1, 0, Collections.singletonList(subtitleInfo.initializationData), subtitleInfo.language, null));
            this.outputs[i] = output;
        }

    }

    public void packetStarted(long pesTimeUs, int flags) {
        if ((flags & 4) != 0) {
            this.writingSample = true;
            this.sampleTimeUs = pesTimeUs;
            this.sampleBytesWritten = 0;
            this.bytesToCheck = 2;
        }
    }

    public void packetFinished() {
        if (this.writingSample) {
            TrackOutput[] var1 = this.outputs;
            int var2 = var1.length;

            for(int var3 = 0; var3 < var2; ++var3) {
                TrackOutput output = var1[var3];
                output.sampleMetadata(this.sampleTimeUs, 1, this.sampleBytesWritten, 0, null);
            }

            this.writingSample = false;
        }

    }

    public void consume(ParsableByteArray data) {
        if (this.writingSample) {
            if (this.bytesToCheck == 2 && !this.checkNextByte(data, 32)) {
                return;
            }

            if (this.bytesToCheck == 1 && !this.checkNextByte(data, 0)) {
                return;
            }

            int dataPosition = data.getPosition();
            int bytesAvailable = data.bytesLeft();
            TrackOutput[] var4 = this.outputs;
            int var5 = var4.length;

            for(int var6 = 0; var6 < var5; ++var6) {
                TrackOutput output = var4[var6];
                data.setPosition(dataPosition);
                output.sampleData(data, bytesAvailable);
            }

            this.sampleBytesWritten += bytesAvailable;
        }

    }

    private boolean checkNextByte(ParsableByteArray data, int expectedValue) {
        if (data.bytesLeft() == 0) {
            return false;
        } else {
            if (data.readUnsignedByte() != expectedValue) {
                this.writingSample = false;
            }

            --this.bytesToCheck;
            return this.writingSample;
        }
    }
}
