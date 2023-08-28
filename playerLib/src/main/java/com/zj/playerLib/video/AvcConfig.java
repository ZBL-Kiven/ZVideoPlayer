package com.zj.playerLib.video;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.util.CodecSpecificDataUtil;
import com.zj.playerLib.util.NalUnitUtil;
import com.zj.playerLib.util.NalUnitUtil.SpsData;
import com.zj.playerLib.util.ParsableByteArray;

import java.util.ArrayList;
import java.util.List;

public final class AvcConfig {
    public final List<byte[]> initializationData;
    public final int nalUnitLengthFieldLength;
    public final int width;
    public final int height;
    public final float pixelWidthAspectRatio;

    public static AvcConfig parse(ParsableByteArray data) throws ParserException {
        try {
            data.skipBytes(4);
            int nalUnitLengthFieldLength = (data.readUnsignedByte() & 3) + 1;
            if (nalUnitLengthFieldLength == 3) {
                throw new IllegalStateException();
            } else {
                List<byte[]> initializationData = new ArrayList();
                int numSequenceParameterSets = data.readUnsignedByte() & 31;

                int numPictureParameterSets;
                for(numPictureParameterSets = 0; numPictureParameterSets < numSequenceParameterSets; ++numPictureParameterSets) {
                    initializationData.add(buildNalUnitForChild(data));
                }

                numPictureParameterSets = data.readUnsignedByte();

                int width;
                for(width = 0; width < numPictureParameterSets; ++width) {
                    initializationData.add(buildNalUnitForChild(data));
                }

                width = -1;
                int height = -1;
                float pixelWidthAspectRatio = 1.0F;
                if (numSequenceParameterSets > 0) {
                    byte[] sps = initializationData.get(0);
                    SpsData spsData = NalUnitUtil.parseSpsNalUnit(initializationData.get(0), nalUnitLengthFieldLength, sps.length);
                    width = spsData.width;
                    height = spsData.height;
                    pixelWidthAspectRatio = spsData.pixelWidthAspectRatio;
                }

                return new AvcConfig(initializationData, nalUnitLengthFieldLength, width, height, pixelWidthAspectRatio);
            }
        } catch (ArrayIndexOutOfBoundsException var10) {
            throw new ParserException("Error parsing AVC config", var10);
        }
    }

    private AvcConfig(List<byte[]> initializationData, int nalUnitLengthFieldLength, int width, int height, float pixelWidthAspectRatio) {
        this.initializationData = initializationData;
        this.nalUnitLengthFieldLength = nalUnitLengthFieldLength;
        this.width = width;
        this.height = height;
        this.pixelWidthAspectRatio = pixelWidthAspectRatio;
    }

    private static byte[] buildNalUnitForChild(ParsableByteArray data) {
        int length = data.readUnsignedShort();
        int offset = data.getPosition();
        data.skipBytes(length);
        return CodecSpecificDataUtil.buildNalUnit(data.data, offset, length);
    }
}
