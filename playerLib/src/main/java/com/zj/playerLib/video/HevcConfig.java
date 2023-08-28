package com.zj.playerLib.video;

import androidx.annotation.Nullable;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.util.NalUnitUtil;
import com.zj.playerLib.util.ParsableByteArray;
import java.util.Collections;
import java.util.List;

public final class HevcConfig {
    @Nullable
    public final List<byte[]> initializationData;
    public final int nalUnitLengthFieldLength;

    public static HevcConfig parse(ParsableByteArray data) throws ParserException {
        try {
            data.skipBytes(21);
            int lengthSizeMinusOne = data.readUnsignedByte() & 3;
            int numberOfArrays = data.readUnsignedByte();
            int csdLength = 0;
            int csdStartPosition = data.getPosition();

            int bufferPosition;
            int numberOfNalUnits;
            for(int i = 0; i < numberOfArrays; ++i) {
                data.skipBytes(1);
                bufferPosition = data.readUnsignedShort();

                for(i = 0; i < bufferPosition; ++i) {
                    numberOfNalUnits = data.readUnsignedShort();
                    csdLength += 4 + numberOfNalUnits;
                    data.skipBytes(numberOfNalUnits);
                }
            }

            data.setPosition(csdStartPosition);
            byte[] buffer = new byte[csdLength];
            bufferPosition = 0;

            for(int i = 0; i < numberOfArrays; ++i) {
                data.skipBytes(1);
                numberOfNalUnits = data.readUnsignedShort();

                for(int j = 0; j < numberOfNalUnits; ++j) {
                    int nalUnitLength = data.readUnsignedShort();
                    System.arraycopy(NalUnitUtil.NAL_START_CODE, 0, buffer, bufferPosition, NalUnitUtil.NAL_START_CODE.length);
                    bufferPosition += NalUnitUtil.NAL_START_CODE.length;
                    System.arraycopy(data.data, data.getPosition(), buffer, bufferPosition, nalUnitLength);
                    bufferPosition += nalUnitLength;
                    data.skipBytes(nalUnitLength);
                }
            }

            List<byte[]> initializationData = csdLength == 0 ? null : Collections.singletonList(buffer);
            return new HevcConfig(initializationData, lengthSizeMinusOne + 1);
        } catch (ArrayIndexOutOfBoundsException var11) {
            throw new ParserException("Error parsing HEVC config", var11);
        }
    }

    private HevcConfig(@Nullable List<byte[]> initializationData, int nalUnitLengthFieldLength) {
        this.initializationData = initializationData;
        this.nalUnitLengthFieldLength = nalUnitLengthFieldLength;
    }
}
