package com.zj.playerLib.extractor.amr;

import androidx.annotation.Nullable;

import com.zj.playerLib.Format;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.ConstantBitrateSeekMap;
import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.ExtractorsFactory;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.extractor.SeekMap;
import com.zj.playerLib.extractor.SeekMap.Unseekable;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.util.Util;

import java.io.EOFException;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;

public final class AmrExtractor implements Extractor {
    public static final ExtractorsFactory FACTORY = () -> {
        return new Extractor[]{new AmrExtractor()};
    };
    public static final int FLAG_ENABLE_CONSTANT_BITRATE_SEEKING = 1;
    private static final int[] frameSizeBytesByTypeNb = new int[]{13, 14, 16, 18, 20, 21, 27, 32, 6, 7, 6, 6, 1, 1, 1, 1};
    private static final int[] frameSizeBytesByTypeWb = new int[]{18, 24, 33, 37, 41, 47, 51, 59, 61, 6, 1, 1, 1, 1, 1, 1};
    private static final byte[] amrSignatureNb = Util.getUtf8Bytes("#!AMR\n");
    private static final byte[] amrSignatureWb = Util.getUtf8Bytes("#!AMR-WB\n");
    private static final int MAX_FRAME_SIZE_BYTES;
    private static final int NUM_SAME_SIZE_CONSTANT_BIT_RATE_THRESHOLD = 20;
    private static final int SAMPLE_RATE_WB = 16000;
    private static final int SAMPLE_RATE_NB = 8000;
    private static final int SAMPLE_TIME_PER_FRAME_US = 20000;
    private final byte[] scratch;
    private final int flags;
    private boolean isWideBand;
    private long currentSampleTimeUs;
    private int currentSampleSize;
    private int currentSampleBytesRemaining;
    private boolean hasOutputSeekMap;
    private long firstSamplePosition;
    private int firstSampleSize;
    private int numSamplesWithSameSize;
    private long timeOffsetUs;
    private ExtractorOutput extractorOutput;
    private TrackOutput trackOutput;
    @Nullable
    private SeekMap seekMap;
    private boolean hasOutputFormat;

    public AmrExtractor() {
        this(0);
    }

    public AmrExtractor(int flags) {
        this.flags = flags;
        this.scratch = new byte[1];
        this.firstSampleSize = -1;
    }

    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        return this.readAmrHeader(input);
    }

    public void init(ExtractorOutput extractorOutput) {
        this.extractorOutput = extractorOutput;
        this.trackOutput = extractorOutput.track(0, 1);
        extractorOutput.endTracks();
    }

    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        if (input.getPosition() == 0L && !this.readAmrHeader(input)) {
            throw new ParserException("Could not find AMR header.");
        } else {
            this.maybeOutputFormat();
            int sampleReadResult = this.readSample(input);
            this.maybeOutputSeekMap(input.getLength(), sampleReadResult);
            return sampleReadResult;
        }
    }

    public void seek(long position, long timeUs) {
        this.currentSampleTimeUs = 0L;
        this.currentSampleSize = 0;
        this.currentSampleBytesRemaining = 0;
        if (position != 0L && this.seekMap instanceof ConstantBitrateSeekMap) {
            this.timeOffsetUs = ((ConstantBitrateSeekMap)this.seekMap).getTimeUsAtPosition(position);
        } else {
            this.timeOffsetUs = 0L;
        }

    }

    public void release() {
    }

    static int frameSizeBytesByTypeNb(int frameType) {
        return frameSizeBytesByTypeNb[frameType];
    }

    static int frameSizeBytesByTypeWb(int frameType) {
        return frameSizeBytesByTypeWb[frameType];
    }

    static byte[] amrSignatureNb() {
        return Arrays.copyOf(amrSignatureNb, amrSignatureNb.length);
    }

    static byte[] amrSignatureWb() {
        return Arrays.copyOf(amrSignatureWb, amrSignatureWb.length);
    }

    private boolean readAmrHeader(ExtractorInput input) throws IOException, InterruptedException {
        if (this.peekAmrSignature(input, amrSignatureNb)) {
            this.isWideBand = false;
            input.skipFully(amrSignatureNb.length);
            return true;
        } else if (this.peekAmrSignature(input, amrSignatureWb)) {
            this.isWideBand = true;
            input.skipFully(amrSignatureWb.length);
            return true;
        } else {
            return false;
        }
    }

    private boolean peekAmrSignature(ExtractorInput input, byte[] amrSignature) throws IOException, InterruptedException {
        input.resetPeekPosition();
        byte[] header = new byte[amrSignature.length];
        input.peekFully(header, 0, amrSignature.length);
        return Arrays.equals(header, amrSignature);
    }

    private void maybeOutputFormat() {
        if (!this.hasOutputFormat) {
            this.hasOutputFormat = true;
            String mimeType = this.isWideBand ? "audio/amr-wb" : "audio/3gpp";
            int sampleRate = this.isWideBand ? 16000 : 8000;
            this.trackOutput.format(Format.createAudioSampleFormat(null, mimeType, null, -1, MAX_FRAME_SIZE_BYTES, 1, sampleRate, -1, null, null, 0, null));
        }

    }

    private int readSample(ExtractorInput extractorInput) throws IOException, InterruptedException {
        if (this.currentSampleBytesRemaining == 0) {
            try {
                this.currentSampleSize = this.peekNextSampleSize(extractorInput);
            } catch (EOFException var3) {
                return -1;
            }

            this.currentSampleBytesRemaining = this.currentSampleSize;
            if (this.firstSampleSize == -1) {
                this.firstSamplePosition = extractorInput.getPosition();
                this.firstSampleSize = this.currentSampleSize;
            }

            if (this.firstSampleSize == this.currentSampleSize) {
                ++this.numSamplesWithSameSize;
            }
        }

        int bytesAppended = this.trackOutput.sampleData(extractorInput, this.currentSampleBytesRemaining, true);
        if (bytesAppended == -1) {
            return -1;
        } else {
            this.currentSampleBytesRemaining -= bytesAppended;
            if (this.currentSampleBytesRemaining > 0) {
                return 0;
            } else {
                this.trackOutput.sampleMetadata(this.timeOffsetUs + this.currentSampleTimeUs, 1, this.currentSampleSize, 0, null);
                this.currentSampleTimeUs += 20000L;
                return 0;
            }
        }
    }

    private int peekNextSampleSize(ExtractorInput extractorInput) throws IOException, InterruptedException {
        extractorInput.resetPeekPosition();
        extractorInput.peekFully(this.scratch, 0, 1);
        byte frameHeader = this.scratch[0];
        if ((frameHeader & 131) > 0) {
            throw new ParserException("Invalid padding bits for frame header " + frameHeader);
        } else {
            int frameType = frameHeader >> 3 & 15;
            return this.getFrameSizeInBytes(frameType);
        }
    }

    private int getFrameSizeInBytes(int frameType) throws ParserException {
        if (!this.isValidFrameType(frameType)) {
            throw new ParserException("Illegal AMR " + (this.isWideBand ? "WB" : "NB") + " frame type " + frameType);
        } else {
            return this.isWideBand ? frameSizeBytesByTypeWb[frameType] : frameSizeBytesByTypeNb[frameType];
        }
    }

    private boolean isValidFrameType(int frameType) {
        return frameType >= 0 && frameType <= 15 && (this.isWideBandValidFrameType(frameType) || this.isNarrowBandValidFrameType(frameType));
    }

    private boolean isWideBandValidFrameType(int frameType) {
        return this.isWideBand && (frameType < 10 || frameType > 13);
    }

    private boolean isNarrowBandValidFrameType(int frameType) {
        return !this.isWideBand && (frameType < 12 || frameType > 14);
    }

    private void maybeOutputSeekMap(long inputLength, int sampleReadResult) {
        if (!this.hasOutputSeekMap) {
            if ((this.flags & 1) == 0 || inputLength == -1L || this.firstSampleSize != -1 && this.firstSampleSize != this.currentSampleSize) {
                this.seekMap = new Unseekable(-Long.MAX_VALUE);
                this.extractorOutput.seekMap(this.seekMap);
                this.hasOutputSeekMap = true;
            } else if (this.numSamplesWithSameSize >= 20 || sampleReadResult == -1) {
                this.seekMap = this.getConstantBitrateSeekMap(inputLength);
                this.extractorOutput.seekMap(this.seekMap);
                this.hasOutputSeekMap = true;
            }

        }
    }

    private SeekMap getConstantBitrateSeekMap(long inputLength) {
        int bitrate = getBitrateFromFrameSize(this.firstSampleSize, 20000L);
        return new ConstantBitrateSeekMap(inputLength, this.firstSamplePosition, bitrate, this.firstSampleSize);
    }

    private static int getBitrateFromFrameSize(int frameSize, long durationUsPerFrame) {
        return (int)((long)(frameSize * 8) * 1000000L / durationUsPerFrame);
    }

    static {
        MAX_FRAME_SIZE_BYTES = frameSizeBytesByTypeWb[8];
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface Flags {
    }
}
