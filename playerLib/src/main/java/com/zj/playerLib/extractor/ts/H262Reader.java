package com.zj.playerLib.extractor.ts;

import android.util.Pair;

import com.zj.playerLib.Format;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.util.NalUnitUtil;
import com.zj.playerLib.util.ParsableByteArray;

import java.util.Arrays;
import java.util.Collections;

public final class H262Reader implements ElementaryStreamReader {
    private static final int START_PICTURE = 0;
    private static final int START_SEQUENCE_HEADER = 179;
    private static final int START_EXTENSION = 181;
    private static final int START_GROUP = 184;
    private static final int START_USER_DATA = 178;
    private String formatId;
    private TrackOutput output;
    private static final double[] FRAME_RATE_VALUES = new double[]{23.976023976023978D, 24.0D, 25.0D, 29.97002997002997D, 30.0D, 50.0D, 59.94005994005994D, 60.0D};
    private boolean hasOutputFormat;
    private long frameDurationUs;
    private final UserDataReader userDataReader;
    private final ParsableByteArray userDataParsable;
    private final boolean[] prefixFlags;
    private final CsdBuffer csdBuffer;
    private final NalUnitTargetBuffer userData;
    private long totalBytesWritten;
    private boolean startedFirstSample;
    private long pesTimeUs;
    private long samplePosition;
    private long sampleTimeUs;
    private boolean sampleIsKeyframe;
    private boolean sampleHasPicture;

    public H262Reader() {
        this(null);
    }

    public H262Reader(UserDataReader userDataReader) {
        this.userDataReader = userDataReader;
        this.prefixFlags = new boolean[4];
        this.csdBuffer = new CsdBuffer(128);
        if (userDataReader != null) {
            this.userData = new NalUnitTargetBuffer(178, 128);
            this.userDataParsable = new ParsableByteArray();
        } else {
            this.userData = null;
            this.userDataParsable = null;
        }

    }

    public void seek() {
        NalUnitUtil.clearPrefixFlags(this.prefixFlags);
        this.csdBuffer.reset();
        if (this.userDataReader != null) {
            this.userData.reset();
        }

        this.totalBytesWritten = 0L;
        this.startedFirstSample = false;
    }

    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
        idGenerator.generateNewId();
        this.formatId = idGenerator.getFormatId();
        this.output = extractorOutput.track(idGenerator.getTrackId(), 2);
        if (this.userDataReader != null) {
            this.userDataReader.createTracks(extractorOutput, idGenerator);
        }

    }

    public void packetStarted(long pesTimeUs, int flags) {
        this.pesTimeUs = pesTimeUs;
    }

    public void consume(ParsableByteArray data) {
        int offset = data.getPosition();
        int limit = data.limit();
        byte[] dataArray = data.data;
        this.totalBytesWritten += data.bytesLeft();
        this.output.sampleData(data, data.bytesLeft());

        while(true) {
            int startCodeOffset = NalUnitUtil.findNalUnit(dataArray, offset, limit, this.prefixFlags);
            if (startCodeOffset == limit) {
                if (!this.hasOutputFormat) {
                    this.csdBuffer.onData(dataArray, offset, limit);
                }

                if (this.userDataReader != null) {
                    this.userData.appendToNalUnit(dataArray, offset, limit);
                }

                return;
            }

            int startCodeValue = data.data[startCodeOffset + 3] & 255;
            int lengthToStartCode = startCodeOffset - offset;
            int bytesWrittenPastStartCode;
            if (!this.hasOutputFormat) {
                if (lengthToStartCode > 0) {
                    this.csdBuffer.onData(dataArray, offset, startCodeOffset);
                }

                bytesWrittenPastStartCode = lengthToStartCode < 0 ? -lengthToStartCode : 0;
                if (this.csdBuffer.onStartCode(startCodeValue, bytesWrittenPastStartCode)) {
                    Pair<Format, Long> result = parseCsdBuffer(this.csdBuffer, this.formatId);
                    this.output.format(result.first);
                    this.frameDurationUs = result.second;
                    this.hasOutputFormat = true;
                }
            }

            int unescapedLength;
            if (this.userDataReader != null) {
                bytesWrittenPastStartCode = 0;
                if (lengthToStartCode > 0) {
                    this.userData.appendToNalUnit(dataArray, offset, startCodeOffset);
                } else {
                    bytesWrittenPastStartCode = -lengthToStartCode;
                }

                if (this.userData.endNalUnit(bytesWrittenPastStartCode)) {
                    unescapedLength = NalUnitUtil.unescapeStream(this.userData.nalData, this.userData.nalLength);
                    this.userDataParsable.reset(this.userData.nalData, unescapedLength);
                    this.userDataReader.consume(this.sampleTimeUs, this.userDataParsable);
                }

                if (startCodeValue == 178 && data.data[startCodeOffset + 2] == 1) {
                    this.userData.startNalUnit(startCodeValue);
                }
            }

            if (startCodeValue != 0 && startCodeValue != 179) {
                if (startCodeValue == 184) {
                    this.sampleIsKeyframe = true;
                }
            } else {
                bytesWrittenPastStartCode = limit - startCodeOffset;
                if (this.startedFirstSample && this.sampleHasPicture && this.hasOutputFormat) {
                    unescapedLength = this.sampleIsKeyframe ? 1 : 0;
                    int size = (int)(this.totalBytesWritten - this.samplePosition) - bytesWrittenPastStartCode;
                    this.output.sampleMetadata(this.sampleTimeUs, unescapedLength, size, bytesWrittenPastStartCode, null);
                }

                if (!this.startedFirstSample || this.sampleHasPicture) {
                    this.samplePosition = this.totalBytesWritten - (long)bytesWrittenPastStartCode;
                    this.sampleTimeUs = this.pesTimeUs != -Long.MAX_VALUE ? this.pesTimeUs : (this.startedFirstSample ? this.sampleTimeUs + this.frameDurationUs : 0L);
                    this.sampleIsKeyframe = false;
                    this.pesTimeUs = -Long.MAX_VALUE;
                    this.startedFirstSample = true;
                }

                this.sampleHasPicture = startCodeValue == 0;
            }

            offset = startCodeOffset + 3;
        }
    }

    public void packetFinished() {
    }

    private static Pair<Format, Long> parseCsdBuffer(CsdBuffer csdBuffer, String formatId) {
        byte[] csdData = Arrays.copyOf(csdBuffer.data, csdBuffer.length);
        int firstByte = csdData[4] & 255;
        int secondByte = csdData[5] & 255;
        int thirdByte = csdData[6] & 255;
        int width = firstByte << 4 | secondByte >> 4;
        int height = (secondByte & 15) << 8 | thirdByte;
        float pixelWidthHeightRatio = 1.0F;
        int aspectRatioCode = (csdData[7] & 240) >> 4;
        switch(aspectRatioCode) {
        case 2:
            pixelWidthHeightRatio = (float)(4 * height) / (float)(3 * width);
            break;
        case 3:
            pixelWidthHeightRatio = (float)(16 * height) / (float)(9 * width);
            break;
        case 4:
            pixelWidthHeightRatio = (float)(121 * height) / (float)(100 * width);
        }

        Format format = Format.createVideoSampleFormat(formatId, "video/mpeg2", null, -1, -1, width, height, -1.0F, Collections.singletonList(csdData), -1, pixelWidthHeightRatio, null);
        long frameDurationUs = 0L;
        int frameRateCodeMinusOne = (csdData[7] & 15) - 1;
        if (0 <= frameRateCodeMinusOne && frameRateCodeMinusOne < FRAME_RATE_VALUES.length) {
            double frameRate = FRAME_RATE_VALUES[frameRateCodeMinusOne];
            int sequenceExtensionPosition = csdBuffer.sequenceExtensionPosition;
            int frameRateExtensionN = (csdData[sequenceExtensionPosition + 9] & 96) >> 5;
            int frameRateExtensionD = csdData[sequenceExtensionPosition + 9] & 31;
            if (frameRateExtensionN != frameRateExtensionD) {
                frameRate *= ((double)frameRateExtensionN + 1.0D) / (double)(frameRateExtensionD + 1);
            }

            frameDurationUs = (long)(1000000.0D / frameRate);
        }

        return Pair.create(format, frameDurationUs);
    }

    private static final class CsdBuffer {
        private static final byte[] START_CODE = new byte[]{0, 0, 1};
        private boolean isFilling;
        public int length;
        public int sequenceExtensionPosition;
        public byte[] data;

        public CsdBuffer(int initialCapacity) {
            this.data = new byte[initialCapacity];
        }

        public void reset() {
            this.isFilling = false;
            this.length = 0;
            this.sequenceExtensionPosition = 0;
        }

        public boolean onStartCode(int startCodeValue, int bytesAlreadyPassed) {
            if (this.isFilling) {
                this.length -= bytesAlreadyPassed;
                if (this.sequenceExtensionPosition != 0 || startCodeValue != 181) {
                    this.isFilling = false;
                    return true;
                }

                this.sequenceExtensionPosition = this.length;
            } else if (startCodeValue == 179) {
                this.isFilling = true;
            }

            this.onData(START_CODE, 0, START_CODE.length);
            return false;
        }

        public void onData(byte[] newData, int offset, int limit) {
            if (this.isFilling) {
                int readLength = limit - offset;
                if (this.data.length < this.length + readLength) {
                    this.data = Arrays.copyOf(this.data, (this.length + readLength) * 2);
                }

                System.arraycopy(newData, offset, this.data, this.length, readLength);
                this.length += readLength;
            }
        }
    }
}
