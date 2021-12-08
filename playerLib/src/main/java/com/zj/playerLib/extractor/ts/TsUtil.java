package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.util.ParsableByteArray;

public final class TsUtil {
    public static int findSyncBytePosition(byte[] data, int startPosition, int limitPosition) {
        int position;
        for(position = startPosition; position < limitPosition && data[position] != 71; ++position) {
        }

        return position;
    }

    public static long readPcrFromPacket(ParsableByteArray packetBuffer, int startOfPacket, int pcrPid) {
        packetBuffer.setPosition(startOfPacket);
        if (packetBuffer.bytesLeft() < 5) {
            return -Long.MAX_VALUE;
        } else {
            int tsPacketHeader = packetBuffer.readInt();
            if ((tsPacketHeader & 8388608) != 0) {
                return -Long.MAX_VALUE;
            } else {
                int pid = (tsPacketHeader & 2096896) >> 8;
                if (pid != pcrPid) {
                    return -Long.MAX_VALUE;
                } else {
                    boolean adaptationFieldExists = (tsPacketHeader & 32) != 0;
                    if (!adaptationFieldExists) {
                        return -Long.MAX_VALUE;
                    } else {
                        int adaptationFieldLength = packetBuffer.readUnsignedByte();
                        if (adaptationFieldLength >= 7 && packetBuffer.bytesLeft() >= 7) {
                            int flags = packetBuffer.readUnsignedByte();
                            boolean pcrFlagSet = (flags & 16) == 16;
                            if (pcrFlagSet) {
                                byte[] pcrBytes = new byte[6];
                                packetBuffer.readBytes(pcrBytes, 0, pcrBytes.length);
                                return readPcrValueFromPcrBytes(pcrBytes);
                            }
                        }

                        return -Long.MAX_VALUE;
                    }
                }
            }
        }
    }

    private static long readPcrValueFromPcrBytes(byte[] pcrBytes) {
        return ((long)pcrBytes[0] & 255L) << 25 | ((long)pcrBytes[1] & 255L) << 17 | ((long)pcrBytes[2] & 255L) << 9 | ((long)pcrBytes[3] & 255L) << 1 | ((long)pcrBytes[4] & 255L) >> 7;
    }

    private TsUtil() {
    }
}
