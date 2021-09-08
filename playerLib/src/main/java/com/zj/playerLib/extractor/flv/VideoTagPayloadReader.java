//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.flv;

import com.zj.playerLib.Format;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.util.NalUnitUtil;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.video.AvcConfig;

final class VideoTagPayloadReader extends TagPayloadReader {
    private static final int VIDEO_CODEC_AVC = 7;
    private static final int VIDEO_FRAME_KEYFRAME = 1;
    private static final int VIDEO_FRAME_VIDEO_INFO = 5;
    private static final int AVC_PACKET_TYPE_SEQUENCE_HEADER = 0;
    private static final int AVC_PACKET_TYPE_AVC_NALU = 1;
    private final ParsableByteArray nalStartCode;
    private final ParsableByteArray nalLength;
    private int nalUnitLengthFieldLength;
    private boolean hasOutputFormat;
    private int frameType;

    public VideoTagPayloadReader(TrackOutput output) {
        super(output);
        this.nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
        this.nalLength = new ParsableByteArray(4);
    }

    public void seek() {
    }

    protected boolean parseHeader(ParsableByteArray data) throws UnsupportedFormatException {
        int header = data.readUnsignedByte();
        int frameType = header >> 4 & 15;
        int videoCodec = header & 15;
        if (videoCodec != 7) {
            throw new UnsupportedFormatException("Video format not supported: " + videoCodec);
        } else {
            this.frameType = frameType;
            return frameType != 5;
        }
    }

    protected void parsePayload(ParsableByteArray data, long timeUs) throws ParserException {
        int packetType = data.readUnsignedByte();
        int compositionTimeMs = data.readInt24();
        timeUs += (long)compositionTimeMs * 1000L;
        if (packetType == 0 && !this.hasOutputFormat) {
            ParsableByteArray videoSequence = new ParsableByteArray(new byte[data.bytesLeft()]);
            data.readBytes(videoSequence.data, 0, data.bytesLeft());
            AvcConfig avcConfig = AvcConfig.parse(videoSequence);
            this.nalUnitLengthFieldLength = avcConfig.nalUnitLengthFieldLength;
            Format format = Format.createVideoSampleFormat((String)null, "video/avc", (String)null, -1, -1, avcConfig.width, avcConfig.height, -1.0F, avcConfig.initializationData, -1, avcConfig.pixelWidthAspectRatio, (DrmInitData)null);
            this.output.format(format);
            this.hasOutputFormat = true;
        } else if (packetType == 1 && this.hasOutputFormat) {
            byte[] nalLengthData = this.nalLength.data;
            nalLengthData[0] = 0;
            nalLengthData[1] = 0;
            nalLengthData[2] = 0;
            int nalUnitLengthFieldLengthDiff = 4 - this.nalUnitLengthFieldLength;

            int bytesWritten;
            int bytesToWrite;
            for(bytesWritten = 0; data.bytesLeft() > 0; bytesWritten += bytesToWrite) {
                data.readBytes(this.nalLength.data, nalUnitLengthFieldLengthDiff, this.nalUnitLengthFieldLength);
                this.nalLength.setPosition(0);
                bytesToWrite = this.nalLength.readUnsignedIntToInt();
                this.nalStartCode.setPosition(0);
                this.output.sampleData(this.nalStartCode, 4);
                bytesWritten += 4;
                this.output.sampleData(data, bytesToWrite);
            }

            this.output.sampleMetadata(timeUs, this.frameType == 1 ? 1 : 0, bytesWritten, 0, (CryptoData)null);
        }

    }
}
