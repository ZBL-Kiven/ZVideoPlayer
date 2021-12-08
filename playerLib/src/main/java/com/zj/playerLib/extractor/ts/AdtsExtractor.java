package com.zj.playerLib.extractor.ts;

import androidx.annotation.Nullable;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.extractor.ConstantBitrateSeekMap;
import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.ExtractorsFactory;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.extractor.SeekMap;
import com.zj.playerLib.extractor.SeekMap.Unseekable;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.ParsableBitArray;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class AdtsExtractor implements Extractor {
    public static final ExtractorsFactory FACTORY = () -> {
        return new Extractor[]{new AdtsExtractor()};
    };
    public static final int FLAG_ENABLE_CONSTANT_BITRATE_SEEKING = 1;
    private static final int MAX_PACKET_SIZE = 2048;
    private static final int ID3_TAG = Util.getIntegerCodeForString("ID3");
    private static final int MAX_SNIFF_BYTES = 8192;
    private static final int NUM_FRAMES_FOR_AVERAGE_FRAME_SIZE = 1000;
    private final int flags;
    private final AdtsReader reader;
    private final ParsableByteArray packetBuffer;
    private final ParsableByteArray scratch;
    private final ParsableBitArray scratchBits;
    private final long firstStreamSampleTimestampUs;
    @Nullable
    private ExtractorOutput extractorOutput;
    private long firstSampleTimestampUs;
    private long firstFramePosition;
    private int averageFrameSize;
    private boolean hasCalculatedAverageFrameSize;
    private boolean startedPacket;
    private boolean hasOutputSeekMap;

    public AdtsExtractor() {
        this(0L);
    }

    public AdtsExtractor(long firstStreamSampleTimestampUs) {
        this(firstStreamSampleTimestampUs, 0);
    }

    public AdtsExtractor(long firstStreamSampleTimestampUs, int flags) {
        this.firstStreamSampleTimestampUs = firstStreamSampleTimestampUs;
        this.firstSampleTimestampUs = firstStreamSampleTimestampUs;
        this.flags = flags;
        this.reader = new AdtsReader(true);
        this.packetBuffer = new ParsableByteArray(2048);
        this.averageFrameSize = -1;
        this.firstFramePosition = -1L;
        this.scratch = new ParsableByteArray(10);
        this.scratchBits = new ParsableBitArray(this.scratch.data);
    }

    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        int startPosition = this.peekId3Header(input);
        int headerPosition = startPosition;
        int totalValidFramesSize = 0;
        int validFramesCount = 0;

        while(true) {
            while(true) {
                input.peekFully(this.scratch.data, 0, 2);
                this.scratch.setPosition(0);
                int syncBytes = this.scratch.readUnsignedShort();
                if (!AdtsReader.isAdtsSyncWord(syncBytes)) {
                    validFramesCount = 0;
                    totalValidFramesSize = 0;
                    input.resetPeekPosition();
                    ++headerPosition;
                    if (headerPosition - startPosition >= 8192) {
                        return false;
                    }

                    input.advancePeekPosition(headerPosition);
                } else {
                    ++validFramesCount;
                    if (validFramesCount >= 4 && totalValidFramesSize > 188) {
                        return true;
                    }

                    input.peekFully(this.scratch.data, 0, 4);
                    this.scratchBits.setPosition(14);
                    int frameSize = this.scratchBits.readBits(13);
                    if (frameSize <= 6) {
                        return false;
                    }

                    input.advancePeekPosition(frameSize - 6);
                    totalValidFramesSize += frameSize;
                }
            }
        }
    }

    public void init(ExtractorOutput output) {
        this.extractorOutput = output;
        this.reader.createTracks(output, new TrackIdGenerator(0, 1));
        output.endTracks();
    }

    public void seek(long position, long timeUs) {
        this.startedPacket = false;
        this.reader.seek();
        this.firstSampleTimestampUs = this.firstStreamSampleTimestampUs + timeUs;
    }

    public void release() {
    }

    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        long inputLength = input.getLength();
        boolean canUseConstantBitrateSeeking = (this.flags & 1) != 0 && inputLength != -1L;
        if (canUseConstantBitrateSeeking) {
            this.calculateAverageFrameSize(input);
        }

        int bytesRead = input.read(this.packetBuffer.data, 0, 2048);
        boolean readEndOfStream = bytesRead == -1;
        this.maybeOutputSeekMap(inputLength, canUseConstantBitrateSeeking, readEndOfStream);
        if (readEndOfStream) {
            return -1;
        } else {
            this.packetBuffer.setPosition(0);
            this.packetBuffer.setLimit(bytesRead);
            if (!this.startedPacket) {
                this.reader.packetStarted(this.firstSampleTimestampUs, 4);
                this.startedPacket = true;
            }

            this.reader.consume(this.packetBuffer);
            return 0;
        }
    }

    private int peekId3Header(ExtractorInput input) throws IOException, InterruptedException {
        int firstFramePosition = 0;

        while(true) {
            input.peekFully(this.scratch.data, 0, 10);
            this.scratch.setPosition(0);
            if (this.scratch.readUnsignedInt24() != ID3_TAG) {
                input.resetPeekPosition();
                input.advancePeekPosition(firstFramePosition);
                if (this.firstFramePosition == -1L) {
                    this.firstFramePosition = firstFramePosition;
                }

                return firstFramePosition;
            }

            this.scratch.skipBytes(3);
            int length = this.scratch.readSynchSafeInt();
            firstFramePosition += 10 + length;
            input.advancePeekPosition(length);
        }
    }

    private void maybeOutputSeekMap(long inputLength, boolean canUseConstantBitrateSeeking, boolean readEndOfStream) {
        if (!this.hasOutputSeekMap) {
            boolean useConstantBitrateSeeking = canUseConstantBitrateSeeking && this.averageFrameSize > 0;
            if (!useConstantBitrateSeeking || this.reader.getSampleDurationUs() != -Long.MAX_VALUE || readEndOfStream) {
                ExtractorOutput extractorOutput = Assertions.checkNotNull(this.extractorOutput);
                if (useConstantBitrateSeeking && this.reader.getSampleDurationUs() != -Long.MAX_VALUE) {
                    extractorOutput.seekMap(this.getConstantBitrateSeekMap(inputLength));
                } else {
                    extractorOutput.seekMap(new Unseekable(-Long.MAX_VALUE));
                }

                this.hasOutputSeekMap = true;
            }
        }
    }

    private void calculateAverageFrameSize(ExtractorInput input) throws IOException, InterruptedException {
        if (!this.hasCalculatedAverageFrameSize) {
            this.averageFrameSize = -1;
            input.resetPeekPosition();
            if (input.getPosition() == 0L) {
                this.peekId3Header(input);
            }

            int numValidFrames = 0;
            long totalValidFramesSize = 0L;

            while(input.peekFully(this.scratch.data, 0, 2, true)) {
                this.scratch.setPosition(0);
                int syncBytes = this.scratch.readUnsignedShort();
                if (!AdtsReader.isAdtsSyncWord(syncBytes)) {
                    numValidFrames = 0;
                    break;
                }

                if (!input.peekFully(this.scratch.data, 0, 4, true)) {
                    break;
                }

                this.scratchBits.setPosition(14);
                int currentFrameSize = this.scratchBits.readBits(13);
                if (currentFrameSize <= 6) {
                    this.hasCalculatedAverageFrameSize = true;
                    throw new ParserException("Malformed ADTS stream");
                }

                totalValidFramesSize += currentFrameSize;
                ++numValidFrames;
                if (numValidFrames == 1000 || !input.advancePeekPosition(currentFrameSize - 6, true)) {
                    break;
                }
            }

            input.resetPeekPosition();
            if (numValidFrames > 0) {
                this.averageFrameSize = (int)(totalValidFramesSize / (long)numValidFrames);
            } else {
                this.averageFrameSize = -1;
            }

            this.hasCalculatedAverageFrameSize = true;
        }
    }

    private SeekMap getConstantBitrateSeekMap(long inputLength) {
        int bitrate = getBitrateFromFrameSize(this.averageFrameSize, this.reader.getSampleDurationUs());
        return new ConstantBitrateSeekMap(inputLength, this.firstFramePosition, bitrate, this.averageFrameSize);
    }

    private static int getBitrateFromFrameSize(int frameSize, long durationUsPerFrame) {
        return (int)((long)(frameSize * 8) * 1000000L / durationUsPerFrame);
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface Flags {
    }
}
