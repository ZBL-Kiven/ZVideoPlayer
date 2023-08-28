package com.zj.playerLib.text.cea;

import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;

public final class CeaUtil {
    private static final String TAG = "CeaUtil";
    public static final int USER_DATA_IDENTIFIER_GA94 = Util.getIntegerCodeForString("GA94");
    public static final int USER_DATA_TYPE_CODE_MPEG_CC = 3;
    private static final int PAYLOAD_TYPE_CC = 4;
    private static final int COUNTRY_CODE = 181;
    private static final int PROVIDER_CODE_ATSC = 49;
    private static final int PROVIDER_CODE_DIRECTV = 47;

    public static void consume(long presentationTimeUs, ParsableByteArray seiBuffer, TrackOutput[] outputs) {
        int nextPayloadPosition;
        for(; seiBuffer.bytesLeft() > 1; seiBuffer.setPosition(nextPayloadPosition)) {
            int payloadType = readNon255TerminatedValue(seiBuffer);
            int payloadSize = readNon255TerminatedValue(seiBuffer);
            nextPayloadPosition = seiBuffer.getPosition() + payloadSize;
            if (payloadSize != -1 && payloadSize <= seiBuffer.bytesLeft()) {
                if (payloadType == 4 && payloadSize >= 8) {
                    int countryCode = seiBuffer.readUnsignedByte();
                    int providerCode = seiBuffer.readUnsignedShort();
                    int userIdentifier = 0;
                    if (providerCode == 49) {
                        userIdentifier = seiBuffer.readInt();
                    }

                    int userDataTypeCode = seiBuffer.readUnsignedByte();
                    if (providerCode == 47) {
                        seiBuffer.skipBytes(1);
                    }

                    boolean messageIsSupportedCeaCaption = countryCode == 181 && (providerCode == 49 || providerCode == 47) && userDataTypeCode == 3;
                    if (providerCode == 49) {
                        messageIsSupportedCeaCaption &= userIdentifier == USER_DATA_IDENTIFIER_GA94;
                    }

                    if (messageIsSupportedCeaCaption) {
                        consumeCcData(presentationTimeUs, seiBuffer, outputs);
                    }
                }
            } else {
                Log.w("CeaUtil", "Skipping remainder of malformed SEI NAL unit.");
                nextPayloadPosition = seiBuffer.limit();
            }
        }

    }

    public static void consumeCcData(long presentationTimeUs, ParsableByteArray ccDataBuffer, TrackOutput[] outputs) {
        int firstByte = ccDataBuffer.readUnsignedByte();
        boolean processCcDataFlag = (firstByte & 64) != 0;
        if (processCcDataFlag) {
            int ccCount = firstByte & 31;
            ccDataBuffer.skipBytes(1);
            int sampleLength = ccCount * 3;
            int sampleStartPosition = ccDataBuffer.getPosition();
            TrackOutput[] var9 = outputs;
            int var10 = outputs.length;

            for(int var11 = 0; var11 < var10; ++var11) {
                TrackOutput output = var9[var11];
                ccDataBuffer.setPosition(sampleStartPosition);
                output.sampleData(ccDataBuffer, sampleLength);
                output.sampleMetadata(presentationTimeUs, 1, sampleLength, 0, null);
            }

        }
    }

    private static int readNon255TerminatedValue(ParsableByteArray buffer) {
        int value = 0;

        while(buffer.bytesLeft() != 0) {
            int b = buffer.readUnsignedByte();
            value += b;
            if (b != 255) {
                return value;
            }
        }

        return -1;
    }

    private CeaUtil() {
    }
}
