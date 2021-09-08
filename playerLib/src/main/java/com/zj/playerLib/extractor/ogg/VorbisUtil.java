//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.ogg;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.ParsableByteArray;

import java.util.Arrays;

final class VorbisUtil {

    public static int iLog(int x) {
        int val;
        for (val = 0; x > 0; x >>>= 1) {
            ++val;
        }

        return val;
    }

    public static VorbisIdHeader readVorbisIdentificationHeader(ParsableByteArray headerData) throws ParserException {
        verifyVorbisHeaderCapturePattern(1, headerData, false);
        long version = headerData.readLittleEndianUnsignedInt();
        int channels = headerData.readUnsignedByte();
        long sampleRate = headerData.readLittleEndianUnsignedInt();
        int bitrateMax = headerData.readLittleEndianInt();
        int bitrateNominal = headerData.readLittleEndianInt();
        int bitrateMin = headerData.readLittleEndianInt();
        int blockSize = headerData.readUnsignedByte();
        int blockSize0 = (int) Math.pow(2.0D, blockSize & 15);
        int blockSize1 = (int) Math.pow(2.0D, (blockSize & 240) >> 4);
        boolean framingFlag = (headerData.readUnsignedByte() & 1) > 0;
        byte[] data = Arrays.copyOf(headerData.data, headerData.limit());
        return new VorbisIdHeader(version, channels, sampleRate, bitrateMax, bitrateNominal, bitrateMin, blockSize0, blockSize1, framingFlag, data);
    }

    public static CommentHeader readVorbisCommentHeader(ParsableByteArray headerData) throws ParserException {
        verifyVorbisHeaderCapturePattern(3, headerData, false);
        int length = 7;
        int len = (int) headerData.readLittleEndianUnsignedInt();
        length += 4;
        String vendor = headerData.readString(len);
        length += vendor.length();
        long commentListLen = headerData.readLittleEndianUnsignedInt();
        String[] comments = new String[(int) commentListLen];
        length += 4;
        for (int i = 0; (long) i < commentListLen; ++i) {
            len = (int) headerData.readLittleEndianUnsignedInt();
            length += 4;
            comments[i] = headerData.readString(len);
            length += comments[i].length();
        }

        if ((headerData.readUnsignedByte() & 1) == 0) {
            throw new ParserException("framing bit expected to be set");
        } else {
            ++length;
            return new CommentHeader(vendor, comments, length);
        }
    }

    public static boolean verifyVorbisHeaderCapturePattern(int headerType, ParsableByteArray header, boolean quiet) throws ParserException {
        if (header.bytesLeft() < 7) {
            if (quiet) {
                return false;
            } else {
                throw new ParserException("too short header: " + header.bytesLeft());
            }
        } else if (header.readUnsignedByte() != headerType) {
            if (quiet) {
                return false;
            } else {
                throw new ParserException("expected header type " + Integer.toHexString(headerType));
            }
        } else if (header.readUnsignedByte() == 118 && header.readUnsignedByte() == 111 && header.readUnsignedByte() == 114 && header.readUnsignedByte() == 98 && header.readUnsignedByte() == 105 && header.readUnsignedByte() == 115) {
            return true;
        } else if (quiet) {
            return false;
        } else {
            throw new ParserException("expected characters 'vorbis'");
        }
    }

    public static Mode[] readVorbisModes(ParsableByteArray headerData, int channels) throws ParserException {
        verifyVorbisHeaderCapturePattern(5, headerData, false);
        int numberOfBooks = headerData.readUnsignedByte() + 1;
        VorbisBitArray bitArray = new VorbisBitArray(headerData.data);
        bitArray.skipBits(headerData.getPosition() * 8);

        int timeCount;
        for (timeCount = 0; timeCount < numberOfBooks; ++timeCount) {
            readBook(bitArray);
        }

        timeCount = bitArray.readBits(6) + 1;

        for (int i = 0; i < timeCount; ++i) {
            if (bitArray.readBits(16) != 0) {
                throw new ParserException("placeholder of time domain transforms not zeroed out");
            }
        }

        readFloors(bitArray);
        readResidues(bitArray);
        readMappings(channels, bitArray);
        Mode[] modes = readModes(bitArray);
        if (!bitArray.readBit()) {
            throw new ParserException("framing bit after modes not set as expected");
        } else {
            return modes;
        }
    }

    private static Mode[] readModes(VorbisBitArray bitArray) {
        int modeCount = bitArray.readBits(6) + 1;
        Mode[] modes = new Mode[modeCount];

        for (int i = 0; i < modeCount; ++i) {
            boolean blockFlag = bitArray.readBit();
            int windowType = bitArray.readBits(16);
            int transformType = bitArray.readBits(16);
            int mapping = bitArray.readBits(8);
            modes[i] = new Mode(blockFlag, windowType, transformType, mapping);
        }

        return modes;
    }

    private static void readMappings(int channels, VorbisBitArray bitArray) throws ParserException {
        int mappingsCount = bitArray.readBits(6) + 1;

        label54:
        for (int i = 0; i < mappingsCount; ++i) {
            int mappingType = bitArray.readBits(16);
            if (mappingType == 0) {
                int subMaps;
                if (bitArray.readBit()) {
                    subMaps = bitArray.readBits(4) + 1;
                } else {
                    subMaps = 1;
                }

                int j;
                if (bitArray.readBit()) {
                    int couplingSteps = bitArray.readBits(8) + 1;

                    for (j = 0; j < couplingSteps; ++j) {
                        bitArray.skipBits(iLog(channels - 1));
                        bitArray.skipBits(iLog(channels - 1));
                    }
                }

                if (bitArray.readBits(2) != 0) {
                    throw new ParserException("to reserved bits must be zero after mapping coupling steps");
                }

                if (subMaps > 1) {
                    for (j = 0; j < channels; ++j) {
                        bitArray.skipBits(4);
                    }
                }

                j = 0;

                while (true) {
                    if (j >= subMaps) {
                        continue label54;
                    }

                    bitArray.skipBits(8);
                    bitArray.skipBits(8);
                    bitArray.skipBits(8);
                    ++j;
                }
            }
            Log.e("VorbisUtil", "mapping type other than 0 not supported: " + mappingType);
        }
    }

    private static void readResidues(VorbisBitArray bitArray) throws ParserException {
        int residueCount = bitArray.readBits(6) + 1;

        for (int i = 0; i < residueCount; ++i) {
            int residueType = bitArray.readBits(16);
            if (residueType > 2) {
                throw new ParserException("residueType greater than 2 is not decodable");
            }

            bitArray.skipBits(24);
            bitArray.skipBits(24);
            bitArray.skipBits(24);
            int classifications = bitArray.readBits(6) + 1;
            bitArray.skipBits(8);
            int[] cascade = new int[classifications];

            int j;
            int k;
            for (j = 0; j < classifications; ++j) {
                k = 0;
                int lowBits = bitArray.readBits(3);
                if (bitArray.readBit()) {
                    k = bitArray.readBits(5);
                }

                cascade[j] = k * 8 + lowBits;
            }

            for (j = 0; j < classifications; ++j) {
                for (k = 0; k < 8; ++k) {
                    if ((cascade[j] & 1 << k) != 0) {
                        bitArray.skipBits(8);
                    }
                }
            }
        }

    }

    private static void readFloors(VorbisBitArray bitArray) throws ParserException {
        int floorCount = bitArray.readBits(6) + 1;

        label79:
        for (int i = 0; i < floorCount; ++i) {
            int floorType = bitArray.readBits(16);
            int partitions;
            switch (floorType) {
                case 0:
                    bitArray.skipBits(8);
                    bitArray.skipBits(16);
                    bitArray.skipBits(16);
                    bitArray.skipBits(6);
                    bitArray.skipBits(8);
                    int floorNumberOfBooks = bitArray.readBits(4) + 1;
                    partitions = 0;

                    while (true) {
                        if (partitions >= floorNumberOfBooks) {
                            continue label79;
                        }

                        bitArray.skipBits(8);
                        ++partitions;
                    }
                case 1:
                    partitions = bitArray.readBits(5);
                    int maximumClass = -1;
                    int[] partitionClassList = new int[partitions];

                    for (int j = 0; j < partitions; ++j) {
                        partitionClassList[j] = bitArray.readBits(4);
                        if (partitionClassList[j] > maximumClass) {
                            maximumClass = partitionClassList[j];
                        }
                    }

                    int[] classDimensions = new int[maximumClass + 1];

                    int rangeBits;
                    int count;
                    int j;
                    for (rangeBits = 0; rangeBits < classDimensions.length; ++rangeBits) {
                        classDimensions[rangeBits] = bitArray.readBits(3) + 1;
                        count = bitArray.readBits(2);
                        if (count > 0) {
                            bitArray.skipBits(8);
                        }

                        for (j = 0; j < 1 << count; ++j) {
                            bitArray.skipBits(8);
                        }
                    }

                    bitArray.skipBits(2);
                    rangeBits = bitArray.readBits(4);
                    count = 0;
                    j = 0;
                    int k = 0;

                    while (true) {
                        if (j >= partitions) {
                            continue label79;
                        }

                        int idx = partitionClassList[j];

                        for (count += classDimensions[idx]; k < count; ++k) {
                            bitArray.skipBits(rangeBits);
                        }

                        ++j;
                    }
                default:
                    throw new ParserException("floor type greater than 1 not decodable: " + floorType);
            }
        }

    }

    private static void readBook(VorbisBitArray bitArray) throws ParserException {
        if (bitArray.readBits(24) != 5653314) {
            throw new ParserException("expected code book to start with [0x56, 0x43, 0x42] at " + bitArray.getPosition());
        } else {
            int dimensions = bitArray.readBits(16);
            int entries = bitArray.readBits(24);
            long[] lengthMap = new long[entries];
            boolean isOrdered = bitArray.readBit();
            int valueBits;
            int lookupType;
            if (!isOrdered) {
                boolean isSparse = bitArray.readBit();

                for (valueBits = 0; valueBits < lengthMap.length; ++valueBits) {
                    if (isSparse) {
                        if (bitArray.readBit()) {
                            lengthMap[valueBits] = bitArray.readBits(5) + 1;
                        } else {
                            lengthMap[valueBits] = 0L;
                        }
                    } else {
                        lengthMap[valueBits] = bitArray.readBits(5) + 1;
                    }
                }
            } else {
                lookupType = bitArray.readBits(5) + 1;

                for (valueBits = 0; valueBits < lengthMap.length; ++lookupType) {
                    int num = bitArray.readBits(iLog(entries - valueBits));

                    for (int j = 0; j < num && valueBits < lengthMap.length; ++j) {
                        lengthMap[valueBits] = lookupType;
                        ++valueBits;
                    }
                }
            }

            lookupType = bitArray.readBits(4);
            if (lookupType > 2) {
                throw new ParserException("lookup type greater than 2 not decodable: " + lookupType);
            } else {
                if (lookupType == 1 || lookupType == 2) {
                    bitArray.skipBits(32);
                    bitArray.skipBits(32);
                    valueBits = bitArray.readBits(4) + 1;
                    bitArray.skipBits(1);
                    long lookupValuesCount;
                    if (lookupType == 1) {
                        if (dimensions != 0) {
                            lookupValuesCount = mapType1QuantValues(entries, dimensions);
                        } else {
                            lookupValuesCount = 0L;
                        }
                    } else {
                        lookupValuesCount = (long) entries * (long) dimensions;
                    }

                    bitArray.skipBits((int) (lookupValuesCount * (long) valueBits));
                }

                new CodeBook(dimensions, entries, lengthMap, lookupType, isOrdered);
            }
        }
    }

    private static long mapType1QuantValues(long entries, long dimension) {
        return (long) Math.floor(Math.pow((double) entries, 1.0D / (double) dimension));
    }

    private VorbisUtil() {
    }

    public static final class Mode {
        public final boolean blockFlag;
        public final int windowType;
        public final int transformType;
        public final int mapping;

        public Mode(boolean blockFlag, int windowType, int transformType, int mapping) {
            this.blockFlag = blockFlag;
            this.windowType = windowType;
            this.transformType = transformType;
            this.mapping = mapping;
        }
    }

    public static final class VorbisIdHeader {
        public final long version;
        public final int channels;
        public final long sampleRate;
        public final int bitrateMax;
        public final int bitrateNominal;
        public final int bitrateMin;
        public final int blockSize0;
        public final int blockSize1;
        public final boolean framingFlag;
        public final byte[] data;

        public VorbisIdHeader(long version, int channels, long sampleRate, int bitrateMax, int bitrateNominal, int bitrateMin, int blockSize0, int blockSize1, boolean framingFlag, byte[] data) {
            this.version = version;
            this.channels = channels;
            this.sampleRate = sampleRate;
            this.bitrateMax = bitrateMax;
            this.bitrateNominal = bitrateNominal;
            this.bitrateMin = bitrateMin;
            this.blockSize0 = blockSize0;
            this.blockSize1 = blockSize1;
            this.framingFlag = framingFlag;
            this.data = data;
        }

        public int getApproximateBitrate() {
            return this.bitrateNominal == 0 ? (this.bitrateMin + this.bitrateMax) / 2 : this.bitrateNominal;
        }
    }

    public static final class CommentHeader {
        public final String vendor;
        public final String[] comments;
        public final int length;

        public CommentHeader(String vendor, String[] comments, int length) {
            this.vendor = vendor;
            this.comments = comments;
            this.length = length;
        }
    }

    public static final class CodeBook {
        public final int dimensions;
        public final int entries;
        public final long[] lengthMap;
        public final int lookupType;
        public final boolean isOrdered;

        public CodeBook(int dimensions, int entries, long[] lengthMap, int lookupType, boolean isOrdered) {
            this.dimensions = dimensions;
            this.entries = entries;
            this.lengthMap = lengthMap;
            this.lookupType = lookupType;
            this.isOrdered = isOrdered;
        }
    }
}
