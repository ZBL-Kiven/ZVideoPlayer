package com.zj.playerLib.extractor.ts;

import android.util.SparseArray;

import com.zj.playerLib.Format;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.util.CodecSpecificDataUtil;
import com.zj.playerLib.util.NalUnitUtil;
import com.zj.playerLib.util.NalUnitUtil.PpsData;
import com.zj.playerLib.util.NalUnitUtil.SpsData;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.ParsableNalUnitBitArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class H264Reader implements ElementaryStreamReader {
    private static final int NAL_UNIT_TYPE_SEI = 6;
    private static final int NAL_UNIT_TYPE_SPS = 7;
    private static final int NAL_UNIT_TYPE_PPS = 8;
    private final SeiReader seiReader;
    private final boolean allowNonIdrKeyframes;
    private final boolean detectAccessUnits;
    private final NalUnitTargetBuffer sps;
    private final NalUnitTargetBuffer pps;
    private final NalUnitTargetBuffer sei;
    private long totalBytesWritten;
    private final boolean[] prefixFlags;
    private String formatId;
    private TrackOutput output;
    private SampleReader sampleReader;
    private boolean hasOutputFormat;
    private long pesTimeUs;
    private boolean randomAccessIndicator;
    private final ParsableByteArray seiWrapper;

    public H264Reader(SeiReader seiReader, boolean allowNonIdrKeyframes, boolean detectAccessUnits) {
        this.seiReader = seiReader;
        this.allowNonIdrKeyframes = allowNonIdrKeyframes;
        this.detectAccessUnits = detectAccessUnits;
        this.prefixFlags = new boolean[3];
        this.sps = new NalUnitTargetBuffer(7, 128);
        this.pps = new NalUnitTargetBuffer(8, 128);
        this.sei = new NalUnitTargetBuffer(6, 128);
        this.seiWrapper = new ParsableByteArray();
    }

    public void seek() {
        NalUnitUtil.clearPrefixFlags(this.prefixFlags);
        this.sps.reset();
        this.pps.reset();
        this.sei.reset();
        this.sampleReader.reset();
        this.totalBytesWritten = 0L;
        this.randomAccessIndicator = false;
    }

    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
        idGenerator.generateNewId();
        this.formatId = idGenerator.getFormatId();
        this.output = extractorOutput.track(idGenerator.getTrackId(), 2);
        this.sampleReader = new SampleReader(this.output, this.allowNonIdrKeyframes, this.detectAccessUnits);
        this.seiReader.createTracks(extractorOutput, idGenerator);
    }

    public void packetStarted(long pesTimeUs, int flags) {
        this.pesTimeUs = pesTimeUs;
        this.randomAccessIndicator |= (flags & 2) != 0;
    }

    public void consume(ParsableByteArray data) {
        int offset = data.getPosition();
        int limit = data.limit();
        byte[] dataArray = data.data;
        this.totalBytesWritten += data.bytesLeft();
        this.output.sampleData(data, data.bytesLeft());

        while(true) {
            int nalUnitOffset = NalUnitUtil.findNalUnit(dataArray, offset, limit, this.prefixFlags);
            if (nalUnitOffset == limit) {
                this.nalUnitData(dataArray, offset, limit);
                return;
            }

            int nalUnitType = NalUnitUtil.getNalUnitType(dataArray, nalUnitOffset);
            int lengthToNalUnit = nalUnitOffset - offset;
            if (lengthToNalUnit > 0) {
                this.nalUnitData(dataArray, offset, nalUnitOffset);
            }

            int bytesWrittenPastPosition = limit - nalUnitOffset;
            long absolutePosition = this.totalBytesWritten - (long)bytesWrittenPastPosition;
            this.endNalUnit(absolutePosition, bytesWrittenPastPosition, lengthToNalUnit < 0 ? -lengthToNalUnit : 0, this.pesTimeUs);
            this.startNalUnit(absolutePosition, nalUnitType, this.pesTimeUs);
            offset = nalUnitOffset + 3;
        }
    }

    public void packetFinished() {
    }

    private void startNalUnit(long position, int nalUnitType, long pesTimeUs) {
        if (!this.hasOutputFormat || this.sampleReader.needsSpsPps()) {
            this.sps.startNalUnit(nalUnitType);
            this.pps.startNalUnit(nalUnitType);
        }

        this.sei.startNalUnit(nalUnitType);
        this.sampleReader.startNalUnit(position, nalUnitType, pesTimeUs);
    }

    private void nalUnitData(byte[] dataArray, int offset, int limit) {
        if (!this.hasOutputFormat || this.sampleReader.needsSpsPps()) {
            this.sps.appendToNalUnit(dataArray, offset, limit);
            this.pps.appendToNalUnit(dataArray, offset, limit);
        }

        this.sei.appendToNalUnit(dataArray, offset, limit);
        this.sampleReader.appendToNalUnit(dataArray, offset, limit);
    }

    private void endNalUnit(long position, int offset, int discardPadding, long pesTimeUs) {
        if (!this.hasOutputFormat || this.sampleReader.needsSpsPps()) {
            this.sps.endNalUnit(discardPadding);
            this.pps.endNalUnit(discardPadding);
            if (!this.hasOutputFormat) {
                if (this.sps.isCompleted() && this.pps.isCompleted()) {
                    List<byte[]> initializationData = new ArrayList();
                    initializationData.add(Arrays.copyOf(this.sps.nalData, this.sps.nalLength));
                    initializationData.add(Arrays.copyOf(this.pps.nalData, this.pps.nalLength));
                    SpsData spsData = NalUnitUtil.parseSpsNalUnit(this.sps.nalData, 3, this.sps.nalLength);
                    PpsData ppsData = NalUnitUtil.parsePpsNalUnit(this.pps.nalData, 3, this.pps.nalLength);
                    this.output.format(Format.createVideoSampleFormat(this.formatId, "video/avc", CodecSpecificDataUtil.buildAvcCodecString(spsData.profileIdc, spsData.constraintsFlagsAndReservedZero2Bits, spsData.levelIdc), -1, -1, spsData.width, spsData.height, -1.0F, initializationData, -1, spsData.pixelWidthAspectRatio, null));
                    this.hasOutputFormat = true;
                    this.sampleReader.putSps(spsData);
                    this.sampleReader.putPps(ppsData);
                    this.sps.reset();
                    this.pps.reset();
                }
            } else if (this.sps.isCompleted()) {
                SpsData spsData = NalUnitUtil.parseSpsNalUnit(this.sps.nalData, 3, this.sps.nalLength);
                this.sampleReader.putSps(spsData);
                this.sps.reset();
            } else if (this.pps.isCompleted()) {
                PpsData ppsData = NalUnitUtil.parsePpsNalUnit(this.pps.nalData, 3, this.pps.nalLength);
                this.sampleReader.putPps(ppsData);
                this.pps.reset();
            }
        }

        if (this.sei.endNalUnit(discardPadding)) {
            int unescapedLength = NalUnitUtil.unescapeStream(this.sei.nalData, this.sei.nalLength);
            this.seiWrapper.reset(this.sei.nalData, unescapedLength);
            this.seiWrapper.setPosition(4);
            this.seiReader.consume(pesTimeUs, this.seiWrapper);
        }

        boolean sampleIsKeyFrame = this.sampleReader.endNalUnit(position, offset, this.hasOutputFormat, this.randomAccessIndicator);
        if (sampleIsKeyFrame) {
            this.randomAccessIndicator = false;
        }

    }

    private static final class SampleReader {
        private static final int DEFAULT_BUFFER_SIZE = 128;
        private static final int NAL_UNIT_TYPE_NON_IDR = 1;
        private static final int NAL_UNIT_TYPE_PARTITION_A = 2;
        private static final int NAL_UNIT_TYPE_IDR = 5;
        private static final int NAL_UNIT_TYPE_AUD = 9;
        private final TrackOutput output;
        private final boolean allowNonIdrKeyframes;
        private final boolean detectAccessUnits;
        private final SparseArray<SpsData> sps;
        private final SparseArray<PpsData> pps;
        private final ParsableNalUnitBitArray bitArray;
        private byte[] buffer;
        private int bufferLength;
        private int nalUnitType;
        private long nalUnitStartPosition;
        private boolean isFilling;
        private long nalUnitTimeUs;
        private SliceHeaderData previousSliceHeader;
        private SliceHeaderData sliceHeader;
        private boolean readingSample;
        private long samplePosition;
        private long sampleTimeUs;
        private boolean sampleIsKeyframe;

        public SampleReader(TrackOutput output, boolean allowNonIdrKeyframes, boolean detectAccessUnits) {
            this.output = output;
            this.allowNonIdrKeyframes = allowNonIdrKeyframes;
            this.detectAccessUnits = detectAccessUnits;
            this.sps = new SparseArray();
            this.pps = new SparseArray();
            this.previousSliceHeader = new SliceHeaderData();
            this.sliceHeader = new SliceHeaderData();
            this.buffer = new byte[128];
            this.bitArray = new ParsableNalUnitBitArray(this.buffer, 0, 0);
            this.reset();
        }

        public boolean needsSpsPps() {
            return this.detectAccessUnits;
        }

        public void putSps(SpsData spsData) {
            this.sps.append(spsData.seqParameterSetId, spsData);
        }

        public void putPps(PpsData ppsData) {
            this.pps.append(ppsData.picParameterSetId, ppsData);
        }

        public void reset() {
            this.isFilling = false;
            this.readingSample = false;
            this.sliceHeader.clear();
        }

        public void startNalUnit(long position, int type, long pesTimeUs) {
            this.nalUnitType = type;
            this.nalUnitTimeUs = pesTimeUs;
            this.nalUnitStartPosition = position;
            if (this.allowNonIdrKeyframes && this.nalUnitType == 1 || this.detectAccessUnits && (this.nalUnitType == 5 || this.nalUnitType == 1 || this.nalUnitType == 2)) {
                SliceHeaderData newSliceHeader = this.previousSliceHeader;
                this.previousSliceHeader = this.sliceHeader;
                this.sliceHeader = newSliceHeader;
                this.sliceHeader.clear();
                this.bufferLength = 0;
                this.isFilling = true;
            }

        }

        public void appendToNalUnit(byte[] data, int offset, int limit) {
            if (this.isFilling) {
                int readLength = limit - offset;
                if (this.buffer.length < this.bufferLength + readLength) {
                    this.buffer = Arrays.copyOf(this.buffer, (this.bufferLength + readLength) * 2);
                }

                System.arraycopy(data, offset, this.buffer, this.bufferLength, readLength);
                this.bufferLength += readLength;
                this.bitArray.reset(this.buffer, 0, this.bufferLength);
                if (this.bitArray.canReadBits(8)) {
                    this.bitArray.skipBit();
                    int nalRefIdc = this.bitArray.readBits(2);
                    this.bitArray.skipBits(5);
                    if (this.bitArray.canReadExpGolombCodedNum()) {
                        this.bitArray.readUnsignedExpGolombCodedInt();
                        if (this.bitArray.canReadExpGolombCodedNum()) {
                            int sliceType = this.bitArray.readUnsignedExpGolombCodedInt();
                            if (!this.detectAccessUnits) {
                                this.isFilling = false;
                                this.sliceHeader.setSliceType(sliceType);
                            } else if (this.bitArray.canReadExpGolombCodedNum()) {
                                int picParameterSetId = this.bitArray.readUnsignedExpGolombCodedInt();
                                if (this.pps.indexOfKey(picParameterSetId) < 0) {
                                    this.isFilling = false;
                                } else {
                                    PpsData ppsData = this.pps.get(picParameterSetId);
                                    SpsData spsData = this.sps.get(ppsData.seqParameterSetId);
                                    if (spsData.separateColorPlaneFlag) {
                                        if (!this.bitArray.canReadBits(2)) {
                                            return;
                                        }

                                        this.bitArray.skipBits(2);
                                    }

                                    if (this.bitArray.canReadBits(spsData.frameNumLength)) {
                                        boolean fieldPicFlag = false;
                                        boolean bottomFieldFlagPresent = false;
                                        boolean bottomFieldFlag = false;
                                        int frameNum = this.bitArray.readBits(spsData.frameNumLength);
                                        if (!spsData.frameMbsOnlyFlag) {
                                            if (!this.bitArray.canReadBits(1)) {
                                                return;
                                            }

                                            fieldPicFlag = this.bitArray.readBit();
                                            if (fieldPicFlag) {
                                                if (!this.bitArray.canReadBits(1)) {
                                                    return;
                                                }

                                                bottomFieldFlag = this.bitArray.readBit();
                                                bottomFieldFlagPresent = true;
                                            }
                                        }

                                        boolean idrPicFlag = this.nalUnitType == 5;
                                        int idrPicId = 0;
                                        if (idrPicFlag) {
                                            if (!this.bitArray.canReadExpGolombCodedNum()) {
                                                return;
                                            }

                                            idrPicId = this.bitArray.readUnsignedExpGolombCodedInt();
                                        }

                                        int picOrderCntLsb = 0;
                                        int deltaPicOrderCntBottom = 0;
                                        int deltaPicOrderCnt0 = 0;
                                        int deltaPicOrderCnt1 = 0;
                                        if (spsData.picOrderCountType == 0) {
                                            if (!this.bitArray.canReadBits(spsData.picOrderCntLsbLength)) {
                                                return;
                                            }

                                            picOrderCntLsb = this.bitArray.readBits(spsData.picOrderCntLsbLength);
                                            if (ppsData.bottomFieldPicOrderInFramePresentFlag && !fieldPicFlag) {
                                                if (!this.bitArray.canReadExpGolombCodedNum()) {
                                                    return;
                                                }

                                                deltaPicOrderCntBottom = this.bitArray.readSignedExpGolombCodedInt();
                                            }
                                        } else if (spsData.picOrderCountType == 1 && !spsData.deltaPicOrderAlwaysZeroFlag) {
                                            if (!this.bitArray.canReadExpGolombCodedNum()) {
                                                return;
                                            }

                                            deltaPicOrderCnt0 = this.bitArray.readSignedExpGolombCodedInt();
                                            if (ppsData.bottomFieldPicOrderInFramePresentFlag && !fieldPicFlag) {
                                                if (!this.bitArray.canReadExpGolombCodedNum()) {
                                                    return;
                                                }

                                                deltaPicOrderCnt1 = this.bitArray.readSignedExpGolombCodedInt();
                                            }
                                        }

                                        this.sliceHeader.setAll(spsData, nalRefIdc, sliceType, frameNum, picParameterSetId, fieldPicFlag, bottomFieldFlagPresent, bottomFieldFlag, idrPicFlag, idrPicId, picOrderCntLsb, deltaPicOrderCntBottom, deltaPicOrderCnt0, deltaPicOrderCnt1);
                                        this.isFilling = false;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        public boolean endNalUnit(long position, int offset, boolean hasOutputFormat, boolean randomAccessIndicator) {
            if (this.nalUnitType == 9 || this.detectAccessUnits && this.sliceHeader.isFirstVclNalUnitOfPicture(this.previousSliceHeader)) {
                if (hasOutputFormat && this.readingSample) {
                    int nalUnitLength = (int)(position - this.nalUnitStartPosition);
                    this.outputSample(offset + nalUnitLength);
                }

                this.samplePosition = this.nalUnitStartPosition;
                this.sampleTimeUs = this.nalUnitTimeUs;
                this.sampleIsKeyframe = false;
                this.readingSample = true;
            }

            boolean treatIFrameAsKeyframe = this.allowNonIdrKeyframes ? this.sliceHeader.isISlice() : randomAccessIndicator;
            this.sampleIsKeyframe |= this.nalUnitType == 5 || treatIFrameAsKeyframe && this.nalUnitType == 1;
            return this.sampleIsKeyframe;
        }

        private void outputSample(int offset) {
            int flags = this.sampleIsKeyframe ? 1 : 0;
            int size = (int)(this.nalUnitStartPosition - this.samplePosition);
            this.output.sampleMetadata(this.sampleTimeUs, flags, size, offset, null);
        }

        private static final class SliceHeaderData {
            private static final int SLICE_TYPE_I = 2;
            private static final int SLICE_TYPE_ALL_I = 7;
            private boolean isComplete;
            private boolean hasSliceType;
            private SpsData spsData;
            private int nalRefIdc;
            private int sliceType;
            private int frameNum;
            private int picParameterSetId;
            private boolean fieldPicFlag;
            private boolean bottomFieldFlagPresent;
            private boolean bottomFieldFlag;
            private boolean idrPicFlag;
            private int idrPicId;
            private int picOrderCntLsb;
            private int deltaPicOrderCntBottom;
            private int deltaPicOrderCnt0;
            private int deltaPicOrderCnt1;

            private SliceHeaderData() {
            }

            public void clear() {
                this.hasSliceType = false;
                this.isComplete = false;
            }

            public void setSliceType(int sliceType) {
                this.sliceType = sliceType;
                this.hasSliceType = true;
            }

            public void setAll(SpsData spsData, int nalRefIdc, int sliceType, int frameNum, int picParameterSetId, boolean fieldPicFlag, boolean bottomFieldFlagPresent, boolean bottomFieldFlag, boolean idrPicFlag, int idrPicId, int picOrderCntLsb, int deltaPicOrderCntBottom, int deltaPicOrderCnt0, int deltaPicOrderCnt1) {
                this.spsData = spsData;
                this.nalRefIdc = nalRefIdc;
                this.sliceType = sliceType;
                this.frameNum = frameNum;
                this.picParameterSetId = picParameterSetId;
                this.fieldPicFlag = fieldPicFlag;
                this.bottomFieldFlagPresent = bottomFieldFlagPresent;
                this.bottomFieldFlag = bottomFieldFlag;
                this.idrPicFlag = idrPicFlag;
                this.idrPicId = idrPicId;
                this.picOrderCntLsb = picOrderCntLsb;
                this.deltaPicOrderCntBottom = deltaPicOrderCntBottom;
                this.deltaPicOrderCnt0 = deltaPicOrderCnt0;
                this.deltaPicOrderCnt1 = deltaPicOrderCnt1;
                this.isComplete = true;
                this.hasSliceType = true;
            }

            public boolean isISlice() {
                return this.hasSliceType && (this.sliceType == 7 || this.sliceType == 2);
            }

            private boolean isFirstVclNalUnitOfPicture(SliceHeaderData other) {
                return this.isComplete && (!other.isComplete || this.frameNum != other.frameNum || this.picParameterSetId != other.picParameterSetId || this.fieldPicFlag != other.fieldPicFlag || this.bottomFieldFlagPresent && other.bottomFieldFlagPresent && this.bottomFieldFlag != other.bottomFieldFlag || this.nalRefIdc != other.nalRefIdc && (this.nalRefIdc == 0 || other.nalRefIdc == 0) || this.spsData.picOrderCountType == 0 && other.spsData.picOrderCountType == 0 && (this.picOrderCntLsb != other.picOrderCntLsb || this.deltaPicOrderCntBottom != other.deltaPicOrderCntBottom) || this.spsData.picOrderCountType == 1 && other.spsData.picOrderCountType == 1 && (this.deltaPicOrderCnt0 != other.deltaPicOrderCnt0 || this.deltaPicOrderCnt1 != other.deltaPicOrderCnt1) || this.idrPicFlag != other.idrPicFlag || this.idrPicFlag && other.idrPicFlag && this.idrPicId != other.idrPicId);
            }
        }
    }
}
