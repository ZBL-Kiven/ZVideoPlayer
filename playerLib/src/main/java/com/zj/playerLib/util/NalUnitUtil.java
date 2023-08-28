package com.zj.playerLib.util;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class NalUnitUtil {
    private static final String TAG = "NalUnitUtil";
    public static final byte[] NAL_START_CODE = new byte[]{0, 0, 0, 1};
    public static final int EXTENDED_SAR = 255;
    public static final float[] ASPECT_RATIO_IDC_VALUES = new float[]{1.0F, 1.0F, 1.0909091F, 0.90909094F, 1.4545455F, 1.2121212F, 2.1818182F, 1.8181819F, 2.909091F, 2.4242425F, 1.6363636F, 1.3636364F, 1.939394F, 1.6161616F, 1.3333334F, 1.5F, 2.0F};
    private static final int H264_NAL_UNIT_TYPE_SEI = 6;
    private static final int H264_NAL_UNIT_TYPE_SPS = 7;
    private static final int H265_NAL_UNIT_TYPE_PREFIX_SEI = 39;
    private static final Object scratchEscapePositionsLock = new Object();
    private static int[] scratchEscapePositions = new int[10];

    public static int unescapeStream(byte[] data, int limit) {
        synchronized(scratchEscapePositionsLock) {
            int position = 0;
            int scratchEscapeCount = 0;

            while(position < limit) {
                position = findNextUnescapeIndex(data, position, limit);
                if (position < limit) {
                    if (scratchEscapePositions.length <= scratchEscapeCount) {
                        scratchEscapePositions = Arrays.copyOf(scratchEscapePositions, scratchEscapePositions.length * 2);
                    }

                    scratchEscapePositions[scratchEscapeCount++] = position;
                    position += 3;
                }
            }

            int unescapedLength = limit - scratchEscapeCount;
            int escapedPosition = 0;
            int unescapedPosition = 0;

            int remainingLength;
            for(remainingLength = 0; remainingLength < scratchEscapeCount; ++remainingLength) {
                int nextEscapePosition = scratchEscapePositions[remainingLength];
                int copyLength = nextEscapePosition - escapedPosition;
                System.arraycopy(data, escapedPosition, data, unescapedPosition, copyLength);
                unescapedPosition += copyLength;
                data[unescapedPosition++] = 0;
                data[unescapedPosition++] = 0;
                escapedPosition += copyLength + 3;
            }

            remainingLength = unescapedLength - unescapedPosition;
            System.arraycopy(data, escapedPosition, data, unescapedPosition, remainingLength);
            return unescapedLength;
        }
    }

    public static void discardToSps(ByteBuffer data) {
        int length = data.position();
        int consecutiveZeros = 0;

        for(int offset = 0; offset + 1 < length; ++offset) {
            int value = data.get(offset) & 255;
            if (consecutiveZeros == 3) {
                if (value == 1 && (data.get(offset + 1) & 31) == 7) {
                    ByteBuffer offsetData = data.duplicate();
                    offsetData.position(offset - 3);
                    offsetData.limit(length);
                    data.position(0);
                    data.put(offsetData);
                    return;
                }
            } else if (value == 0) {
                ++consecutiveZeros;
            }

            if (value != 0) {
                consecutiveZeros = 0;
            }
        }

        data.clear();
    }

    public static boolean isNalUnitSei(String mimeType, byte nalUnitHeaderFirstByte) {
        return "video/avc".equals(mimeType) && (nalUnitHeaderFirstByte & 31) == 6 || "video/hevc".equals(mimeType) && (nalUnitHeaderFirstByte & 126) >> 1 == 39;
    }

    public static int getNalUnitType(byte[] data, int offset) {
        return data[offset + 3] & 31;
    }

    public static int getH265NalUnitType(byte[] data, int offset) {
        return (data[offset + 3] & 126) >> 1;
    }

    public static SpsData parseSpsNalUnit(byte[] nalData, int nalOffset, int nalLimit) {
        ParsableNalUnitBitArray data = new ParsableNalUnitBitArray(nalData, nalOffset, nalLimit);
        data.skipBits(8);
        int profileIdc = data.readBits(8);
        int constraintsFlagsAndReservedZero2Bits = data.readBits(8);
        int levelIdc = data.readBits(8);
        int seqParameterSetId = data.readUnsignedExpGolombCodedInt();
        int chromaFormatIdc = 1;
        boolean separateColorPlaneFlag = false;
        int picOrderCntType;
        int picOrderCntLsbLength;
        boolean deltaPicOrderAlwaysZeroFlag;
        if (profileIdc == 100 || profileIdc == 110 || profileIdc == 122 || profileIdc == 244 || profileIdc == 44 || profileIdc == 83 || profileIdc == 86 || profileIdc == 118 || profileIdc == 128 || profileIdc == 138) {
            chromaFormatIdc = data.readUnsignedExpGolombCodedInt();
            if (chromaFormatIdc == 3) {
                separateColorPlaneFlag = data.readBit();
            }

            data.readUnsignedExpGolombCodedInt();
            data.readUnsignedExpGolombCodedInt();
            data.skipBit();
            boolean seqScalingMatrixPresentFlag = data.readBit();
            if (seqScalingMatrixPresentFlag) {
                picOrderCntType = chromaFormatIdc != 3 ? 8 : 12;

                for(picOrderCntLsbLength = 0; picOrderCntLsbLength < picOrderCntType; ++picOrderCntLsbLength) {
                    deltaPicOrderAlwaysZeroFlag = data.readBit();
                    if (deltaPicOrderAlwaysZeroFlag) {
                        skipScalingList(data, picOrderCntLsbLength < 6 ? 16 : 64);
                    }
                }
            }
        }

        int frameNumLength = data.readUnsignedExpGolombCodedInt() + 4;
        picOrderCntType = data.readUnsignedExpGolombCodedInt();
        picOrderCntLsbLength = 0;
        deltaPicOrderAlwaysZeroFlag = false;
        if (picOrderCntType == 0) {
            picOrderCntLsbLength = data.readUnsignedExpGolombCodedInt() + 4;
        } else if (picOrderCntType == 1) {
            deltaPicOrderAlwaysZeroFlag = data.readBit();
            data.readSignedExpGolombCodedInt();
            data.readSignedExpGolombCodedInt();
            long numRefFramesInPicOrderCntCycle = data.readUnsignedExpGolombCodedInt();

            for(int i = 0; (long)i < numRefFramesInPicOrderCntCycle; ++i) {
                data.readUnsignedExpGolombCodedInt();
            }
        }

        data.readUnsignedExpGolombCodedInt();
        data.skipBit();
        int picWidthInMbs = data.readUnsignedExpGolombCodedInt() + 1;
        int picHeightInMapUnits = data.readUnsignedExpGolombCodedInt() + 1;
        boolean frameMbsOnlyFlag = data.readBit();
        int frameHeightInMbs = (2 - (frameMbsOnlyFlag ? 1 : 0)) * picHeightInMapUnits;
        if (!frameMbsOnlyFlag) {
            data.skipBit();
        }

        data.skipBit();
        int frameWidth = picWidthInMbs * 16;
        int frameHeight = frameHeightInMbs * 16;
        boolean frameCroppingFlag = data.readBit();
        int aspectRatioIdc;
        int sarWidth;
        int sarHeight;
        if (frameCroppingFlag) {
            int frameCropLeftOffset = data.readUnsignedExpGolombCodedInt();
            int frameCropRightOffset = data.readUnsignedExpGolombCodedInt();
            int frameCropTopOffset = data.readUnsignedExpGolombCodedInt();
            aspectRatioIdc = data.readUnsignedExpGolombCodedInt();
            if (chromaFormatIdc == 0) {
                sarWidth = 1;
                sarHeight = 2 - (frameMbsOnlyFlag ? 1 : 0);
            } else {
                int subWidthC = chromaFormatIdc == 3 ? 1 : 2;
                int subHeightC = chromaFormatIdc == 1 ? 2 : 1;
                sarWidth = subWidthC;
                sarHeight = subHeightC * (2 - (frameMbsOnlyFlag ? 1 : 0));
            }

            frameWidth -= (frameCropLeftOffset + frameCropRightOffset) * sarWidth;
            frameHeight -= (frameCropTopOffset + aspectRatioIdc) * sarHeight;
        }

        float pixelWidthHeightRatio = 1.0F;
        boolean vuiParametersPresentFlag = data.readBit();
        if (vuiParametersPresentFlag) {
            boolean aspectRatioInfoPresentFlag = data.readBit();
            if (aspectRatioInfoPresentFlag) {
                aspectRatioIdc = data.readBits(8);
                if (aspectRatioIdc == 255) {
                    sarWidth = data.readBits(16);
                    sarHeight = data.readBits(16);
                    if (sarWidth != 0 && sarHeight != 0) {
                        pixelWidthHeightRatio = (float)sarWidth / (float)sarHeight;
                    }
                } else if (aspectRatioIdc < ASPECT_RATIO_IDC_VALUES.length) {
                    pixelWidthHeightRatio = ASPECT_RATIO_IDC_VALUES[aspectRatioIdc];
                } else {
                    Log.w("NalUnitUtil", "Unexpected aspect_ratio_idc value: " + aspectRatioIdc);
                }
            }
        }

        return new SpsData(profileIdc, constraintsFlagsAndReservedZero2Bits, levelIdc, seqParameterSetId, frameWidth, frameHeight, pixelWidthHeightRatio, separateColorPlaneFlag, frameMbsOnlyFlag, frameNumLength, picOrderCntType, picOrderCntLsbLength, deltaPicOrderAlwaysZeroFlag);
    }

    public static PpsData parsePpsNalUnit(byte[] nalData, int nalOffset, int nalLimit) {
        ParsableNalUnitBitArray data = new ParsableNalUnitBitArray(nalData, nalOffset, nalLimit);
        data.skipBits(8);
        int picParameterSetId = data.readUnsignedExpGolombCodedInt();
        int seqParameterSetId = data.readUnsignedExpGolombCodedInt();
        data.skipBit();
        boolean bottomFieldPicOrderInFramePresentFlag = data.readBit();
        return new PpsData(picParameterSetId, seqParameterSetId, bottomFieldPicOrderInFramePresentFlag);
    }

    public static int findNalUnit(byte[] data, int startOffset, int endOffset, boolean[] prefixFlags) {
        int length = endOffset - startOffset;
        Assertions.checkState(length >= 0);
        if (length == 0) {
            return endOffset;
        } else {
            if (prefixFlags != null) {
                if (prefixFlags[0]) {
                    clearPrefixFlags(prefixFlags);
                    return startOffset - 3;
                }

                if (length > 1 && prefixFlags[1] && data[startOffset] == 1) {
                    clearPrefixFlags(prefixFlags);
                    return startOffset - 2;
                }

                if (length > 2 && prefixFlags[2] && data[startOffset] == 0 && data[startOffset + 1] == 1) {
                    clearPrefixFlags(prefixFlags);
                    return startOffset - 1;
                }
            }

            int limit = endOffset - 1;

            for(int i = startOffset + 2; i < limit; i += 3) {
                if ((data[i] & 254) == 0) {
                    if (data[i - 2] == 0 && data[i - 1] == 0 && data[i] == 1) {
                        if (prefixFlags != null) {
                            clearPrefixFlags(prefixFlags);
                        }

                        return i - 2;
                    }

                    i -= 2;
                }
            }

            if (prefixFlags != null) {
                prefixFlags[0] = length > 2 ? data[endOffset - 3] == 0 && data[endOffset - 2] == 0 && data[endOffset - 1] == 1 : (length == 2 ? prefixFlags[2] && data[endOffset - 2] == 0 && data[endOffset - 1] == 1 : prefixFlags[1] && data[endOffset - 1] == 1);
                prefixFlags[1] = length > 1 ? data[endOffset - 2] == 0 && data[endOffset - 1] == 0 : prefixFlags[2] && data[endOffset - 1] == 0;
                prefixFlags[2] = data[endOffset - 1] == 0;
            }

            return endOffset;
        }
    }

    public static void clearPrefixFlags(boolean[] prefixFlags) {
        prefixFlags[0] = false;
        prefixFlags[1] = false;
        prefixFlags[2] = false;
    }

    private static int findNextUnescapeIndex(byte[] bytes, int offset, int limit) {
        for(int i = offset; i < limit - 2; ++i) {
            if (bytes[i] == 0 && bytes[i + 1] == 0 && bytes[i + 2] == 3) {
                return i;
            }
        }

        return limit;
    }

    private static void skipScalingList(ParsableNalUnitBitArray bitArray, int size) {
        int lastScale = 8;
        int nextScale = 8;

        for(int i = 0; i < size; ++i) {
            if (nextScale != 0) {
                int deltaScale = bitArray.readSignedExpGolombCodedInt();
                nextScale = (lastScale + deltaScale + 256) % 256;
            }

            lastScale = nextScale == 0 ? lastScale : nextScale;
        }

    }

    private NalUnitUtil() {
    }

    public static final class PpsData {
        public final int picParameterSetId;
        public final int seqParameterSetId;
        public final boolean bottomFieldPicOrderInFramePresentFlag;

        public PpsData(int picParameterSetId, int seqParameterSetId, boolean bottomFieldPicOrderInFramePresentFlag) {
            this.picParameterSetId = picParameterSetId;
            this.seqParameterSetId = seqParameterSetId;
            this.bottomFieldPicOrderInFramePresentFlag = bottomFieldPicOrderInFramePresentFlag;
        }
    }

    public static final class SpsData {
        public final int profileIdc;
        public final int constraintsFlagsAndReservedZero2Bits;
        public final int levelIdc;
        public final int seqParameterSetId;
        public final int width;
        public final int height;
        public final float pixelWidthAspectRatio;
        public final boolean separateColorPlaneFlag;
        public final boolean frameMbsOnlyFlag;
        public final int frameNumLength;
        public final int picOrderCountType;
        public final int picOrderCntLsbLength;
        public final boolean deltaPicOrderAlwaysZeroFlag;

        public SpsData(int profileIdc, int constraintsFlagsAndReservedZero2Bits, int levelIdc, int seqParameterSetId, int width, int height, float pixelWidthAspectRatio, boolean separateColorPlaneFlag, boolean frameMbsOnlyFlag, int frameNumLength, int picOrderCountType, int picOrderCntLsbLength, boolean deltaPicOrderAlwaysZeroFlag) {
            this.profileIdc = profileIdc;
            this.constraintsFlagsAndReservedZero2Bits = constraintsFlagsAndReservedZero2Bits;
            this.levelIdc = levelIdc;
            this.seqParameterSetId = seqParameterSetId;
            this.width = width;
            this.height = height;
            this.pixelWidthAspectRatio = pixelWidthAspectRatio;
            this.separateColorPlaneFlag = separateColorPlaneFlag;
            this.frameMbsOnlyFlag = frameMbsOnlyFlag;
            this.frameNumLength = frameNumLength;
            this.picOrderCountType = picOrderCountType;
            this.picOrderCntLsbLength = picOrderCntLsbLength;
            this.deltaPicOrderAlwaysZeroFlag = deltaPicOrderAlwaysZeroFlag;
        }
    }
}
