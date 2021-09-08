//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.flv;

import android.util.Pair;

import com.zj.playerLib.Format;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.util.CodecSpecificDataUtil;
import com.zj.playerLib.util.ParsableByteArray;

import java.util.Collections;
import java.util.List;

final class AudioTagPayloadReader extends TagPayloadReader {
    private static final int AUDIO_FORMAT_MP3 = 2;
    private static final int AUDIO_FORMAT_ALAW = 7;
    private static final int AUDIO_FORMAT_ULAW = 8;
    private static final int AUDIO_FORMAT_AAC = 10;
    private static final int AAC_PACKET_TYPE_SEQUENCE_HEADER = 0;
    private static final int AAC_PACKET_TYPE_AAC_RAW = 1;
    private static final int[] AUDIO_SAMPLING_RATE_TABLE = new int[]{5512, 11025, 22050, 44100};
    private boolean hasParsedAudioDataHeader;
    private boolean hasOutputFormat;
    private int audioFormat;

    public AudioTagPayloadReader(TrackOutput output) {
        super(output);
    }

    public void seek() {
    }

    protected boolean parseHeader(ParsableByteArray data) throws UnsupportedFormatException {
        if (!this.hasParsedAudioDataHeader) {
            int header = data.readUnsignedByte();
            this.audioFormat = header >> 4 & 15;
            int pcmEncoding;
            Format format;
            if (this.audioFormat == 2) {
                int sampleRateIndex = header >> 2 & 3;
                pcmEncoding = AUDIO_SAMPLING_RATE_TABLE[sampleRateIndex];
                format = Format.createAudioSampleFormat((String)null, "audio/mpeg", (String)null, -1, -1, 1, pcmEncoding, (List)null, (DrmInitData)null, 0, (String)null);
                this.output.format(format);
                this.hasOutputFormat = true;
            } else if (this.audioFormat != 7 && this.audioFormat != 8) {
                if (this.audioFormat != 10) {
                    throw new UnsupportedFormatException("Audio format not supported: " + this.audioFormat);
                }
            } else {
                String type = this.audioFormat == 7 ? "audio/g711-alaw" : "audio/g711-mlaw";
                pcmEncoding = (header & 1) == 1 ? 2 : 3;
                format = Format.createAudioSampleFormat((String)null, type, (String)null, -1, -1, 1, 8000, pcmEncoding, (List)null, (DrmInitData)null, 0, (String)null);
                this.output.format(format);
                this.hasOutputFormat = true;
            }

            this.hasParsedAudioDataHeader = true;
        } else {
            data.skipBytes(1);
        }

        return true;
    }

    protected void parsePayload(ParsableByteArray data, long timeUs) throws ParserException {
        int packetType;
        if (this.audioFormat == 2) {
            packetType = data.bytesLeft();
            this.output.sampleData(data, packetType);
            this.output.sampleMetadata(timeUs, 1, packetType, 0, (CryptoData)null);
        } else {
            packetType = data.readUnsignedByte();
            if (packetType == 0 && !this.hasOutputFormat) {
                byte[] audioSpecificConfig = new byte[data.bytesLeft()];
                data.readBytes(audioSpecificConfig, 0, audioSpecificConfig.length);
                Pair<Integer, Integer> audioParams = CodecSpecificDataUtil.parseAacAudioSpecificConfig(audioSpecificConfig);
                Format format = Format.createAudioSampleFormat((String)null, "audio/mp4a-latm", (String)null, -1, -1, (Integer)audioParams.second, (Integer)audioParams.first, Collections.singletonList(audioSpecificConfig), (DrmInitData)null, 0, (String)null);
                this.output.format(format);
                this.hasOutputFormat = true;
            } else if (this.audioFormat != 10 || packetType == 1) {
                int sampleSize = data.bytesLeft();
                this.output.sampleData(data, sampleSize);
                this.output.sampleMetadata(timeUs, 1, sampleSize, 0, (CryptoData)null);
            }
        }

    }
}
