//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.util;

import android.util.Pair;
import androidx.annotation.Nullable;
import com.zj.playerLib.ParserException;
import java.util.ArrayList;
import java.util.List;

public final class CodecSpecificDataUtil {
    private static final byte[] NAL_START_CODE = new byte[]{0, 0, 0, 1};
    private static final int AUDIO_SPECIFIC_CONFIG_FREQUENCY_INDEX_ARBITRARY = 15;
    private static final int[] AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE = new int[]{96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350};
    private static final int AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID = -1;
    private static final int[] AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE = new int[]{0, 1, 2, 3, 4, 5, 6, 8, -1, -1, -1, 7, 8, -1, 8, -1};
    private static final int AUDIO_OBJECT_TYPE_AAC_LC = 2;
    private static final int AUDIO_OBJECT_TYPE_SBR = 5;
    private static final int AUDIO_OBJECT_TYPE_ER_BSAC = 22;
    private static final int AUDIO_OBJECT_TYPE_PS = 29;
    private static final int AUDIO_OBJECT_TYPE_ESCAPE = 31;

    private CodecSpecificDataUtil() {
    }

    public static Pair<Integer, Integer> parseAacAudioSpecificConfig(byte[] audioSpecificConfig) throws ParserException {
        return parseAacAudioSpecificConfig(new ParsableBitArray(audioSpecificConfig), false);
    }

    public static Pair<Integer, Integer> parseAacAudioSpecificConfig(ParsableBitArray bitArray, boolean forceReadToEnd) throws ParserException {
        int audioObjectType = getAacAudioObjectType(bitArray);
        int sampleRate = getAacSamplingFrequency(bitArray);
        int channelConfiguration = bitArray.readBits(4);
        if (audioObjectType == 5 || audioObjectType == 29) {
            sampleRate = getAacSamplingFrequency(bitArray);
            audioObjectType = getAacAudioObjectType(bitArray);
            if (audioObjectType == 22) {
                channelConfiguration = bitArray.readBits(4);
            }
        }

        int epConfig;
        if (forceReadToEnd) {
            label22:
            switch(audioObjectType) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 6:
            case 7:
            case 17:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
                parseGaSpecificConfig(bitArray, audioObjectType, channelConfiguration);
                switch(audioObjectType) {
                case 17:
                case 19:
                case 20:
                case 21:
                case 22:
                case 23:
                    epConfig = bitArray.readBits(2);
                    if (epConfig == 2 || epConfig == 3) {
                        throw new ParserException("Unsupported epConfig: " + epConfig);
                    }
                case 18:
                    break label22;
                }
            case 5:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 18:
            default:
                throw new ParserException("Unsupported audio object type: " + audioObjectType);
            }
        }

        epConfig = AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE[channelConfiguration];
        Assertions.checkArgument(epConfig != -1);
        return Pair.create(sampleRate, epConfig);
    }

    public static byte[] buildAacLcAudioSpecificConfig(int sampleRate, int numChannels) {
        int sampleRateIndex = -1;

        int channelConfig;
        for(channelConfig = 0; channelConfig < AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE.length; ++channelConfig) {
            if (sampleRate == AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[channelConfig]) {
                sampleRateIndex = channelConfig;
            }
        }

        channelConfig = -1;

        for(int i = 0; i < AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE.length; ++i) {
            if (numChannels == AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE[i]) {
                channelConfig = i;
            }
        }

        if (sampleRate != -1 && channelConfig != -1) {
            return buildAacAudioSpecificConfig(2, sampleRateIndex, channelConfig);
        } else {
            throw new IllegalArgumentException("Invalid sample rate or number of channels: " + sampleRate + ", " + numChannels);
        }
    }

    public static byte[] buildAacAudioSpecificConfig(int audioObjectType, int sampleRateIndex, int channelConfig) {
        byte[] specificConfig = new byte[]{(byte)(audioObjectType << 3 & 248 | sampleRateIndex >> 1 & 7), (byte)(sampleRateIndex << 7 & 128 | channelConfig << 3 & 120)};
        return specificConfig;
    }

    public static String buildAvcCodecString(int profileIdc, int constraintsFlagsAndReservedZero2Bits, int levelIdc) {
        return String.format("avc1.%02X%02X%02X", profileIdc, constraintsFlagsAndReservedZero2Bits, levelIdc);
    }

    public static byte[] buildNalUnit(byte[] data, int offset, int length) {
        byte[] nalUnit = new byte[length + NAL_START_CODE.length];
        System.arraycopy(NAL_START_CODE, 0, nalUnit, 0, NAL_START_CODE.length);
        System.arraycopy(data, offset, nalUnit, NAL_START_CODE.length, length);
        return nalUnit;
    }

    @Nullable
    public static byte[][] splitNalUnits(byte[] data) {
        if (!isNalStartCode(data, 0)) {
            return (byte[][])null;
        } else {
            List<Integer> starts = new ArrayList();
            int nalUnitIndex = 0;

            do {
                starts.add(nalUnitIndex);
                nalUnitIndex = findNalStartCode(data, nalUnitIndex + NAL_START_CODE.length);
            } while(nalUnitIndex != -1);

            byte[][] split = new byte[starts.size()][];

            for(int i = 0; i < starts.size(); ++i) {
                int startIndex = (Integer)starts.get(i);
                int endIndex = i < starts.size() - 1 ? (Integer)starts.get(i + 1) : data.length;
                byte[] nal = new byte[endIndex - startIndex];
                System.arraycopy(data, startIndex, nal, 0, nal.length);
                split[i] = nal;
            }

            return split;
        }
    }

    private static int findNalStartCode(byte[] data, int index) {
        int endIndex = data.length - NAL_START_CODE.length;

        for(int i = index; i <= endIndex; ++i) {
            if (isNalStartCode(data, i)) {
                return i;
            }
        }

        return -1;
    }

    private static boolean isNalStartCode(byte[] data, int index) {
        if (data.length - index <= NAL_START_CODE.length) {
            return false;
        } else {
            for(int j = 0; j < NAL_START_CODE.length; ++j) {
                if (data[index + j] != NAL_START_CODE[j]) {
                    return false;
                }
            }

            return true;
        }
    }

    private static int getAacAudioObjectType(ParsableBitArray bitArray) {
        int audioObjectType = bitArray.readBits(5);
        if (audioObjectType == 31) {
            audioObjectType = 32 + bitArray.readBits(6);
        }

        return audioObjectType;
    }

    private static int getAacSamplingFrequency(ParsableBitArray bitArray) {
        int frequencyIndex = bitArray.readBits(4);
        int samplingFrequency;
        if (frequencyIndex == 15) {
            samplingFrequency = bitArray.readBits(24);
        } else {
            Assertions.checkArgument(frequencyIndex < 13);
            samplingFrequency = AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[frequencyIndex];
        }

        return samplingFrequency;
    }

    private static void parseGaSpecificConfig(ParsableBitArray bitArray, int audioObjectType, int channelConfiguration) {
        bitArray.skipBits(1);
        boolean dependsOnCoreDecoder = bitArray.readBit();
        if (dependsOnCoreDecoder) {
            bitArray.skipBits(14);
        }

        boolean extensionFlag = bitArray.readBit();
        if (channelConfiguration == 0) {
            throw new UnsupportedOperationException();
        } else {
            if (audioObjectType == 6 || audioObjectType == 20) {
                bitArray.skipBits(3);
            }

            if (extensionFlag) {
                if (audioObjectType == 22) {
                    bitArray.skipBits(16);
                }

                if (audioObjectType == 17 || audioObjectType == 19 || audioObjectType == 20 || audioObjectType == 23) {
                    bitArray.skipBits(3);
                }

                bitArray.skipBits(1);
            }

        }
    }
}
