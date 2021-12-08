package com.zj.playerLib.audio;

import com.zj.playerLib.util.Util;

public final class WavUtil {
    public static final int RIFF_FOURCC = Util.getIntegerCodeForString("RIFF");
    public static final int WAVE_FOURCC = Util.getIntegerCodeForString("WAVE");
    public static final int FMT_FOURCC = Util.getIntegerCodeForString("fmt ");
    public static final int DATA_FOURCC = Util.getIntegerCodeForString("data");
    private static final int TYPE_PCM = 1;
    private static final int TYPE_FLOAT = 3;
    private static final int TYPE_A_LAW = 6;
    private static final int TYPE_MU_LAW = 7;
    private static final int TYPE_WAVE_FORMAT_EXTENSIBLE = 65534;

    public static int getTypeForEncoding(int encoding) {
        switch(encoding) {
        case -2147483648:
        case 2:
        case 3:
        case 1073741824:
            return 1;
        case -1:
        case 0:
        default:
            throw new IllegalArgumentException();
        case 4:
            return 3;
        case 268435456:
            return 7;
        case 536870912:
            return 6;
        }
    }

    public static int getEncodingForType(int type, int bitsPerSample) {
        switch(type) {
        case 1:
        case 65534:
            return Util.getPcmEncoding(bitsPerSample);
        case 3:
            return bitsPerSample == 32 ? 4 : 0;
        case 6:
            return 536870912;
        case 7:
            return 268435456;
        default:
            return 0;
        }
    }

    private WavUtil() {
    }
}
