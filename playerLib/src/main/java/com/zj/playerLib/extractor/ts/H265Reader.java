package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.Format;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.NalUnitUtil;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.ParsableNalUnitBitArray;

import java.util.Collections;

public final class H265Reader implements ElementaryStreamReader {
    private static final int RASL_R = 9;
    private static final int BLA_W_LP = 16;
    private static final int CRA_NUT = 21;
    private static final int VPS_NUT = 32;
    private static final int SPS_NUT = 33;
    private static final int PPS_NUT = 34;
    private static final int PREFIX_SEI_NUT = 39;
    private static final int SUFFIX_SEI_NUT = 40;
    private final SeiReader seiReader;
    private String formatId;
    private TrackOutput output;
    private SampleReader sampleReader;
    private boolean hasOutputFormat;
    private final boolean[] prefixFlags;
    private final NalUnitTargetBuffer vps;
    private final NalUnitTargetBuffer sps;
    private final NalUnitTargetBuffer pps;
    private final NalUnitTargetBuffer prefixSei;
    private final NalUnitTargetBuffer suffixSei;
    private long totalBytesWritten;
    private long pesTimeUs;
    private final ParsableByteArray seiWrapper;

    public H265Reader(SeiReader seiReader) {
        this.seiReader = seiReader;
        this.prefixFlags = new boolean[3];
        this.vps = new NalUnitTargetBuffer(32, 128);
        this.sps = new NalUnitTargetBuffer(33, 128);
        this.pps = new NalUnitTargetBuffer(34, 128);
        this.prefixSei = new NalUnitTargetBuffer(39, 128);
        this.suffixSei = new NalUnitTargetBuffer(40, 128);
        this.seiWrapper = new ParsableByteArray();
    }

    public void seek() {
        NalUnitUtil.clearPrefixFlags(this.prefixFlags);
        this.vps.reset();
        this.sps.reset();
        this.pps.reset();
        this.prefixSei.reset();
        this.suffixSei.reset();
        this.sampleReader.reset();
        this.totalBytesWritten = 0L;
    }

    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
        idGenerator.generateNewId();
        this.formatId = idGenerator.getFormatId();
        this.output = extractorOutput.track(idGenerator.getTrackId(), 2);
        this.sampleReader = new SampleReader(this.output);
        this.seiReader.createTracks(extractorOutput, idGenerator);
    }

    public void packetStarted(long pesTimeUs, int flags) {
        this.pesTimeUs = pesTimeUs;
    }

    public void consume(ParsableByteArray data) {
        label29:
        while(true) {
            if (data.bytesLeft() > 0) {
                int offset = data.getPosition();
                int limit = data.limit();
                byte[] dataArray = data.data;
                this.totalBytesWritten += data.bytesLeft();
                this.output.sampleData(data, data.bytesLeft());

                while(true) {
                    if (offset >= limit) {
                        continue label29;
                    }

                    int nalUnitOffset = NalUnitUtil.findNalUnit(dataArray, offset, limit, this.prefixFlags);
                    if (nalUnitOffset == limit) {
                        this.nalUnitData(dataArray, offset, limit);
                        return;
                    }

                    int nalUnitType = NalUnitUtil.getH265NalUnitType(dataArray, nalUnitOffset);
                    int lengthToNalUnit = nalUnitOffset - offset;
                    if (lengthToNalUnit > 0) {
                        this.nalUnitData(dataArray, offset, nalUnitOffset);
                    }

                    int bytesWrittenPastPosition = limit - nalUnitOffset;
                    long absolutePosition = this.totalBytesWritten - (long)bytesWrittenPastPosition;
                    this.endNalUnit(absolutePosition, bytesWrittenPastPosition, lengthToNalUnit < 0 ? -lengthToNalUnit : 0, this.pesTimeUs);
                    this.startNalUnit(absolutePosition, bytesWrittenPastPosition, nalUnitType, this.pesTimeUs);
                    offset = nalUnitOffset + 3;
                }
            }

            return;
        }
    }

    public void packetFinished() {
    }

    private void startNalUnit(long position, int offset, int nalUnitType, long pesTimeUs) {
        if (this.hasOutputFormat) {
            this.sampleReader.startNalUnit(position, offset, nalUnitType, pesTimeUs);
        } else {
            this.vps.startNalUnit(nalUnitType);
            this.sps.startNalUnit(nalUnitType);
            this.pps.startNalUnit(nalUnitType);
        }

        this.prefixSei.startNalUnit(nalUnitType);
        this.suffixSei.startNalUnit(nalUnitType);
    }

    private void nalUnitData(byte[] dataArray, int offset, int limit) {
        if (this.hasOutputFormat) {
            this.sampleReader.readNalUnitData(dataArray, offset, limit);
        } else {
            this.vps.appendToNalUnit(dataArray, offset, limit);
            this.sps.appendToNalUnit(dataArray, offset, limit);
            this.pps.appendToNalUnit(dataArray, offset, limit);
        }

        this.prefixSei.appendToNalUnit(dataArray, offset, limit);
        this.suffixSei.appendToNalUnit(dataArray, offset, limit);
    }

    private void endNalUnit(long position, int offset, int discardPadding, long pesTimeUs) {
        if (this.hasOutputFormat) {
            this.sampleReader.endNalUnit(position, offset);
        } else {
            this.vps.endNalUnit(discardPadding);
            this.sps.endNalUnit(discardPadding);
            this.pps.endNalUnit(discardPadding);
            if (this.vps.isCompleted() && this.sps.isCompleted() && this.pps.isCompleted()) {
                this.output.format(parseMediaFormat(this.formatId, this.vps, this.sps, this.pps));
                this.hasOutputFormat = true;
            }
        }

        int unescapedLength;
        if (this.prefixSei.endNalUnit(discardPadding)) {
            unescapedLength = NalUnitUtil.unescapeStream(this.prefixSei.nalData, this.prefixSei.nalLength);
            this.seiWrapper.reset(this.prefixSei.nalData, unescapedLength);
            this.seiWrapper.skipBytes(5);
            this.seiReader.consume(pesTimeUs, this.seiWrapper);
        }

        if (this.suffixSei.endNalUnit(discardPadding)) {
            unescapedLength = NalUnitUtil.unescapeStream(this.suffixSei.nalData, this.suffixSei.nalLength);
            this.seiWrapper.reset(this.suffixSei.nalData, unescapedLength);
            this.seiWrapper.skipBytes(5);
            this.seiReader.consume(pesTimeUs, this.seiWrapper);
        }

    }

    private static Format parseMediaFormat(String formatId, NalUnitTargetBuffer vps, NalUnitTargetBuffer sps, NalUnitTargetBuffer pps) {
        byte[] csd = new byte[vps.nalLength + sps.nalLength + pps.nalLength];
        System.arraycopy(vps.nalData, 0, csd, 0, vps.nalLength);
        System.arraycopy(sps.nalData, 0, csd, vps.nalLength, sps.nalLength);
        System.arraycopy(pps.nalData, 0, csd, vps.nalLength + sps.nalLength, pps.nalLength);
        ParsableNalUnitBitArray bitArray = new ParsableNalUnitBitArray(sps.nalData, 0, sps.nalLength);
        bitArray.skipBits(44);
        int maxSubLayersMinus1 = bitArray.readBits(3);
        bitArray.skipBit();
        bitArray.skipBits(88);
        bitArray.skipBits(8);
        int toSkip = 0;

        int chromaFormatIdc;
        for(chromaFormatIdc = 0; chromaFormatIdc < maxSubLayersMinus1; ++chromaFormatIdc) {
            if (bitArray.readBit()) {
                toSkip += 89;
            }

            if (bitArray.readBit()) {
                toSkip += 8;
            }
        }

        bitArray.skipBits(toSkip);
        if (maxSubLayersMinus1 > 0) {
            bitArray.skipBits(2 * (8 - maxSubLayersMinus1));
        }

        bitArray.readUnsignedExpGolombCodedInt();
        chromaFormatIdc = bitArray.readUnsignedExpGolombCodedInt();
        if (chromaFormatIdc == 3) {
            bitArray.skipBit();
        }

        int picWidthInLumaSamples = bitArray.readUnsignedExpGolombCodedInt();
        int picHeightInLumaSamples = bitArray.readUnsignedExpGolombCodedInt();
        int log2MaxPicOrderCntLsbMinus4;
        int i;
        int aspectRatioIdc;
        int sarWidth;
        int sarHeight;
        if (bitArray.readBit()) {
            log2MaxPicOrderCntLsbMinus4 = bitArray.readUnsignedExpGolombCodedInt();
            i = bitArray.readUnsignedExpGolombCodedInt();
            aspectRatioIdc = bitArray.readUnsignedExpGolombCodedInt();
            sarWidth = chromaFormatIdc != 1 && chromaFormatIdc != 2 ? 1 : 2;
            sarHeight = chromaFormatIdc == 1 ? 2 : 1;
            picWidthInLumaSamples -= sarWidth * (log2MaxPicOrderCntLsbMinus4 + i);
            picHeightInLumaSamples -= sarHeight * (i + aspectRatioIdc);
        }

        bitArray.readUnsignedExpGolombCodedInt();
        bitArray.readUnsignedExpGolombCodedInt();
        log2MaxPicOrderCntLsbMinus4 = bitArray.readUnsignedExpGolombCodedInt();

        for(i = bitArray.readBit() ? 0 : maxSubLayersMinus1; i <= maxSubLayersMinus1; ++i) {
            bitArray.readUnsignedExpGolombCodedInt();
            bitArray.readUnsignedExpGolombCodedInt();
            bitArray.readUnsignedExpGolombCodedInt();
        }

        bitArray.readUnsignedExpGolombCodedInt();
        bitArray.readUnsignedExpGolombCodedInt();
        bitArray.readUnsignedExpGolombCodedInt();
        bitArray.readUnsignedExpGolombCodedInt();
        bitArray.readUnsignedExpGolombCodedInt();
        bitArray.readUnsignedExpGolombCodedInt();
        boolean scalingListEnabled = bitArray.readBit();
        if (scalingListEnabled && bitArray.readBit()) {
            skipScalingList(bitArray);
        }

        bitArray.skipBits(2);
        if (bitArray.readBit()) {
            bitArray.skipBits(8);
            bitArray.readUnsignedExpGolombCodedInt();
            bitArray.readUnsignedExpGolombCodedInt();
            bitArray.skipBit();
        }

        skipShortTermRefPicSets(bitArray);
        if (bitArray.readBit()) {
            for(i = 0; i < bitArray.readUnsignedExpGolombCodedInt(); ++i) {
                aspectRatioIdc = log2MaxPicOrderCntLsbMinus4 + 4;
                bitArray.skipBits(aspectRatioIdc + 1);
            }
        }

        bitArray.skipBits(2);
        float pixelWidthHeightRatio = 1.0F;
        if (bitArray.readBit() && bitArray.readBit()) {
            aspectRatioIdc = bitArray.readBits(8);
            if (aspectRatioIdc == 255) {
                sarWidth = bitArray.readBits(16);
                sarHeight = bitArray.readBits(16);
                if (sarWidth != 0 && sarHeight != 0) {
                    pixelWidthHeightRatio = (float)sarWidth / (float)sarHeight;
                }
            } else if (aspectRatioIdc < NalUnitUtil.ASPECT_RATIO_IDC_VALUES.length) {
                pixelWidthHeightRatio = NalUnitUtil.ASPECT_RATIO_IDC_VALUES[aspectRatioIdc];
            } else {
                Log.w("H265Reader", "Unexpected aspect_ratio_idc value: " + aspectRatioIdc);
            }
        }

        return Format.createVideoSampleFormat(formatId, "video/hevc", null, -1, -1, picWidthInLumaSamples, picHeightInLumaSamples, -1.0F, Collections.singletonList(csd), -1, pixelWidthHeightRatio, null);
    }

    private static void skipScalingList(ParsableNalUnitBitArray bitArray) {
        for(int sizeId = 0; sizeId < 4; ++sizeId) {
            for(int matrixId = 0; matrixId < 6; matrixId += sizeId == 3 ? 3 : 1) {
                if (!bitArray.readBit()) {
                    bitArray.readUnsignedExpGolombCodedInt();
                } else {
                    int coefNum = Math.min(64, 1 << 4 + (sizeId << 1));
                    if (sizeId > 1) {
                        bitArray.readSignedExpGolombCodedInt();
                    }

                    for(int i = 0; i < coefNum; ++i) {
                        bitArray.readSignedExpGolombCodedInt();
                    }
                }
            }
        }

    }

    private static void skipShortTermRefPicSets(ParsableNalUnitBitArray bitArray) {
        int numShortTermRefPicSets = bitArray.readUnsignedExpGolombCodedInt();
        boolean interRefPicSetPredictionFlag = false;
        int previousNumDeltaPocs = 0;

        for(int stRpsIdx = 0; stRpsIdx < numShortTermRefPicSets; ++stRpsIdx) {
            if (stRpsIdx != 0) {
                interRefPicSetPredictionFlag = bitArray.readBit();
            }

            int i;
            if (interRefPicSetPredictionFlag) {
                bitArray.skipBit();
                bitArray.readUnsignedExpGolombCodedInt();

                for(i = 0; i <= previousNumDeltaPocs; ++i) {
                    if (bitArray.readBit()) {
                        bitArray.skipBit();
                    }
                }
            } else {
                int numNegativePics = bitArray.readUnsignedExpGolombCodedInt();
                int numPositivePics = bitArray.readUnsignedExpGolombCodedInt();
                previousNumDeltaPocs = numNegativePics + numPositivePics;

                for(i = 0; i < numNegativePics; ++i) {
                    bitArray.readUnsignedExpGolombCodedInt();
                    bitArray.skipBit();
                }

                for(i = 0; i < numPositivePics; ++i) {
                    bitArray.readUnsignedExpGolombCodedInt();
                    bitArray.skipBit();
                }
            }
        }

    }

    private static final class SampleReader {
        private final TrackOutput output;
        private long nalUnitStartPosition;
        private boolean nalUnitHasKeyframeData;
        private int nalUnitBytesRead;
        private long nalUnitTimeUs;
        private boolean lookingForFirstSliceFlag;
        private boolean isFirstSlice;
        private boolean isFirstParameterSet;
        private boolean readingSample;
        private boolean writingParameterSets;
        private long samplePosition;
        private long sampleTimeUs;
        private boolean sampleIsKeyframe;

        public SampleReader(TrackOutput output) {
            this.output = output;
        }

        public void reset() {
            this.lookingForFirstSliceFlag = false;
            this.isFirstSlice = false;
            this.isFirstParameterSet = false;
            this.readingSample = false;
            this.writingParameterSets = false;
        }

        public void startNalUnit(long position, int offset, int nalUnitType, long pesTimeUs) {
            this.isFirstSlice = false;
            this.isFirstParameterSet = false;
            this.nalUnitTimeUs = pesTimeUs;
            this.nalUnitBytesRead = 0;
            this.nalUnitStartPosition = position;
            if (nalUnitType >= 32) {
                if (!this.writingParameterSets && this.readingSample) {
                    this.outputSample(offset);
                    this.readingSample = false;
                }

                if (nalUnitType <= 34) {
                    this.isFirstParameterSet = !this.writingParameterSets;
                    this.writingParameterSets = true;
                }
            }

            this.nalUnitHasKeyframeData = nalUnitType >= 16 && nalUnitType <= 21;
            this.lookingForFirstSliceFlag = this.nalUnitHasKeyframeData || nalUnitType <= 9;
        }

        public void readNalUnitData(byte[] data, int offset, int limit) {
            if (this.lookingForFirstSliceFlag) {
                int headerOffset = offset + 2 - this.nalUnitBytesRead;
                if (headerOffset < limit) {
                    this.isFirstSlice = (data[headerOffset] & 128) != 0;
                    this.lookingForFirstSliceFlag = false;
                } else {
                    this.nalUnitBytesRead += limit - offset;
                }
            }

        }

        public void endNalUnit(long position, int offset) {
            if (this.writingParameterSets && this.isFirstSlice) {
                this.sampleIsKeyframe = this.nalUnitHasKeyframeData;
                this.writingParameterSets = false;
            } else if (this.isFirstParameterSet || this.isFirstSlice) {
                if (this.readingSample) {
                    int nalUnitLength = (int)(position - this.nalUnitStartPosition);
                    this.outputSample(offset + nalUnitLength);
                }

                this.samplePosition = this.nalUnitStartPosition;
                this.sampleTimeUs = this.nalUnitTimeUs;
                this.readingSample = true;
                this.sampleIsKeyframe = this.nalUnitHasKeyframeData;
            }

        }

        private void outputSample(int offset) {
            int flags = this.sampleIsKeyframe ? 1 : 0;
            int size = (int)(this.nalUnitStartPosition - this.samplePosition);
            this.output.sampleMetadata(this.sampleTimeUs, flags, size, offset, null);
        }
    }
}
