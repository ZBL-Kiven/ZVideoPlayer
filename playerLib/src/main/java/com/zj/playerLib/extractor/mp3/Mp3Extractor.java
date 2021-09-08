//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.mp3;

import androidx.annotation.Nullable;

import com.zj.playerLib.Format;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.ExtractorsFactory;
import com.zj.playerLib.extractor.GaplessInfoHolder;
import com.zj.playerLib.extractor.Id3Peeker;
import com.zj.playerLib.extractor.MpegAudioHeader;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.extractor.SeekMap;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.metadata.Metadata;
import com.zj.playerLib.metadata.Metadata.Entry;
import com.zj.playerLib.metadata.id3.Id3Decoder.FramePredicate;
import com.zj.playerLib.metadata.id3.MlltFrame;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;

import java.io.EOFException;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

public final class Mp3Extractor implements Extractor {
    public static final ExtractorsFactory FACTORY = () -> {
        return new Extractor[]{new Mp3Extractor()};
    };
    public static final int FLAG_ENABLE_CONSTANT_BITRATE_SEEKING = 1;
    public static final int FLAG_DISABLE_ID3_METADATA = 2;
    private static final FramePredicate REQUIRED_ID3_FRAME_PREDICATE = (majorVersion, id0, id1, id2, id3) -> {
        return id0 == 67 && id1 == 79 && id2 == 77 && (id3 == 77 || majorVersion == 2) || id0 == 77 && id1 == 76 && id2 == 76 && (id3 == 84 || majorVersion == 2);
    };
    private static final int MAX_SYNC_BYTES = 131072;
    private static final int MAX_SNIFF_BYTES = 16384;
    private static final int SCRATCH_LENGTH = 10;
    private static final int MPEG_AUDIO_HEADER_MASK = -128000;
    private static final int SEEK_HEADER_XING = Util.getIntegerCodeForString("Xing");
    private static final int SEEK_HEADER_INFO = Util.getIntegerCodeForString("Info");
    private static final int SEEK_HEADER_VBRI = Util.getIntegerCodeForString("VBRI");
    private static final int SEEK_HEADER_UNSET = 0;
    private final int flags;
    private final long forcedFirstSampleTimestampUs;
    private final ParsableByteArray scratch;
    private final MpegAudioHeader synchronizedHeader;
    private final GaplessInfoHolder gaplessInfoHolder;
    private final Id3Peeker id3Peeker;
    private ExtractorOutput extractorOutput;
    private TrackOutput trackOutput;
    private int synchronizedHeaderData;
    private Metadata metadata;
    private Seeker seeker;
    private long basisTimeUs;
    private long samplesRead;
    private int sampleBytesRemaining;

    public Mp3Extractor() {
        this(0);
    }

    public Mp3Extractor(int flags) {
        this(flags, -9223372036854775807L);
    }

    public Mp3Extractor(int flags, long forcedFirstSampleTimestampUs) {
        this.flags = flags;
        this.forcedFirstSampleTimestampUs = forcedFirstSampleTimestampUs;
        this.scratch = new ParsableByteArray(10);
        this.synchronizedHeader = new MpegAudioHeader();
        this.gaplessInfoHolder = new GaplessInfoHolder();
        this.basisTimeUs = -9223372036854775807L;
        this.id3Peeker = new Id3Peeker();
    }

    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        return this.synchronize(input, true);
    }

    public void init(ExtractorOutput output) {
        this.extractorOutput = output;
        this.trackOutput = this.extractorOutput.track(0, 1);
        this.extractorOutput.endTracks();
    }

    public void seek(long position, long timeUs) {
        this.synchronizedHeaderData = 0;
        this.basisTimeUs = -9223372036854775807L;
        this.samplesRead = 0L;
        this.sampleBytesRemaining = 0;
    }

    public void release() {
    }

    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        if (this.synchronizedHeaderData == 0) {
            try {
                this.synchronize(input, false);
            } catch (EOFException var5) {
                return -1;
            }
        }

        if (this.seeker == null) {
            Seeker seekFrameSeeker = this.maybeReadSeekFrame(input);
            Seeker metadataSeeker = maybeHandleSeekMetadata(this.metadata, input.getPosition());
            if (metadataSeeker != null) {
                this.seeker = metadataSeeker;
            } else if (seekFrameSeeker != null) {
                this.seeker = seekFrameSeeker;
            }

            if (this.seeker == null || !this.seeker.isSeekable() && (this.flags & 1) != 0) {
                this.seeker = this.getConstantBitrateSeeker(input);
            }

            this.extractorOutput.seekMap(this.seeker);
            this.trackOutput.format(Format.createAudioSampleFormat((String)null, this.synchronizedHeader.mimeType, (String)null, -1, 4096, this.synchronizedHeader.channels, this.synchronizedHeader.sampleRate, -1, this.gaplessInfoHolder.encoderDelay, this.gaplessInfoHolder.encoderPadding, (List)null, (DrmInitData)null, 0, (String)null, (this.flags & 2) != 0 ? null : this.metadata));
        }

        return this.readSample(input);
    }

    private int readSample(ExtractorInput extractorInput) throws IOException, InterruptedException {
        int sampleHeaderData;
        long embeddedFirstSampleTimestampUs;
        if (this.sampleBytesRemaining == 0) {
            extractorInput.resetPeekPosition();
            if (this.peekEndOfStreamOrHeader(extractorInput)) {
                return -1;
            }

            this.scratch.setPosition(0);
            sampleHeaderData = this.scratch.readInt();
            if (!headersMatch(sampleHeaderData, (long)this.synchronizedHeaderData) || MpegAudioHeader.getFrameSize(sampleHeaderData) == -1) {
                extractorInput.skipFully(1);
                this.synchronizedHeaderData = 0;
                return 0;
            }

            MpegAudioHeader.populateHeader(sampleHeaderData, this.synchronizedHeader);
            if (this.basisTimeUs == -9223372036854775807L) {
                this.basisTimeUs = this.seeker.getTimeUs(extractorInput.getPosition());
                if (this.forcedFirstSampleTimestampUs != -9223372036854775807L) {
                    embeddedFirstSampleTimestampUs = this.seeker.getTimeUs(0L);
                    this.basisTimeUs += this.forcedFirstSampleTimestampUs - embeddedFirstSampleTimestampUs;
                }
            }

            this.sampleBytesRemaining = this.synchronizedHeader.frameSize;
        }

        sampleHeaderData = this.trackOutput.sampleData(extractorInput, this.sampleBytesRemaining, true);
        if (sampleHeaderData == -1) {
            return -1;
        } else {
            this.sampleBytesRemaining -= sampleHeaderData;
            if (this.sampleBytesRemaining > 0) {
                return 0;
            } else {
                embeddedFirstSampleTimestampUs = this.basisTimeUs + this.samplesRead * 1000000L / (long)this.synchronizedHeader.sampleRate;
                this.trackOutput.sampleMetadata(embeddedFirstSampleTimestampUs, 1, this.synchronizedHeader.frameSize, 0, (CryptoData)null);
                this.samplesRead += (long)this.synchronizedHeader.samplesPerFrame;
                this.sampleBytesRemaining = 0;
                return 0;
            }
        }
    }

    private boolean synchronize(ExtractorInput input, boolean sniffing) throws IOException, InterruptedException {
        int validFrameCount = 0;
        int candidateSynchronizedHeaderData = 0;
        int peekedId3Bytes = 0;
        int searchedBytes = 0;
        int searchLimitBytes = sniffing ? 16384 : 131072;
        input.resetPeekPosition();
        if (input.getPosition() == 0L) {
            boolean parseAllId3Frames = (this.flags & 2) == 0;
            FramePredicate id3FramePredicate = parseAllId3Frames ? null : REQUIRED_ID3_FRAME_PREDICATE;
            this.metadata = this.id3Peeker.peekId3Data(input, id3FramePredicate);
            if (this.metadata != null) {
                this.gaplessInfoHolder.setFromMetadata(this.metadata);
            }

            peekedId3Bytes = (int)input.getPeekPosition();
            if (!sniffing) {
                input.skipFully(peekedId3Bytes);
            }
        }

        while(true) {
            if (this.peekEndOfStreamOrHeader(input)) {
                if (validFrameCount <= 0) {
                    throw new EOFException();
                }
                break;
            }

            this.scratch.setPosition(0);
            int headerData = this.scratch.readInt();
            int frameSize;
            if ((candidateSynchronizedHeaderData == 0 || headersMatch(headerData, (long)candidateSynchronizedHeaderData)) && (frameSize = MpegAudioHeader.getFrameSize(headerData)) != -1) {
                ++validFrameCount;
                if (validFrameCount == 1) {
                    MpegAudioHeader.populateHeader(headerData, this.synchronizedHeader);
                    candidateSynchronizedHeaderData = headerData;
                } else if (validFrameCount == 4) {
                    break;
                }

                input.advancePeekPosition(frameSize - 4);
            } else {
                if (searchedBytes++ == searchLimitBytes) {
                    if (!sniffing) {
                        throw new ParserException("Searched too many bytes.");
                    }

                    return false;
                }

                validFrameCount = 0;
                candidateSynchronizedHeaderData = 0;
                if (sniffing) {
                    input.resetPeekPosition();
                    input.advancePeekPosition(peekedId3Bytes + searchedBytes);
                } else {
                    input.skipFully(1);
                }
            }
        }

        if (sniffing) {
            input.skipFully(peekedId3Bytes + searchedBytes);
        } else {
            input.resetPeekPosition();
        }

        this.synchronizedHeaderData = candidateSynchronizedHeaderData;
        return true;
    }

    private boolean peekEndOfStreamOrHeader(ExtractorInput extractorInput) throws IOException, InterruptedException {
        return this.seeker != null && extractorInput.getPeekPosition() == this.seeker.getDataEndPosition() || !extractorInput.peekFully(this.scratch.data, 0, 4, true);
    }

    private Seeker maybeReadSeekFrame(ExtractorInput input) throws IOException, InterruptedException {
        ParsableByteArray frame = new ParsableByteArray(this.synchronizedHeader.frameSize);
        input.peekFully(frame.data, 0, this.synchronizedHeader.frameSize);
        int xingBase = (this.synchronizedHeader.version & 1) != 0 ? (this.synchronizedHeader.channels != 1 ? 36 : 21) : (this.synchronizedHeader.channels != 1 ? 21 : 13);
        int seekHeader = getSeekFrameHeader(frame, xingBase);
        Object seeker;
        if (seekHeader != SEEK_HEADER_XING && seekHeader != SEEK_HEADER_INFO) {
            if (seekHeader == SEEK_HEADER_VBRI) {
                seeker = VbriSeeker.create(input.getLength(), input.getPosition(), this.synchronizedHeader, frame);
                input.skipFully(this.synchronizedHeader.frameSize);
            } else {
                seeker = null;
                input.resetPeekPosition();
            }
        } else {
            seeker = XingSeeker.create(input.getLength(), input.getPosition(), this.synchronizedHeader, frame);
            if (seeker != null && !this.gaplessInfoHolder.hasGaplessInfo()) {
                input.resetPeekPosition();
                input.advancePeekPosition(xingBase + 141);
                input.peekFully(this.scratch.data, 0, 3);
                this.scratch.setPosition(0);
                this.gaplessInfoHolder.setFromXingHeaderValue(this.scratch.readUnsignedInt24());
            }

            input.skipFully(this.synchronizedHeader.frameSize);
            if (seeker != null && !((Seeker)seeker).isSeekable() && seekHeader == SEEK_HEADER_INFO) {
                return this.getConstantBitrateSeeker(input);
            }
        }

        return (Seeker)seeker;
    }

    private Seeker getConstantBitrateSeeker(ExtractorInput input) throws IOException, InterruptedException {
        input.peekFully(this.scratch.data, 0, 4);
        this.scratch.setPosition(0);
        MpegAudioHeader.populateHeader(this.scratch.readInt(), this.synchronizedHeader);
        return new ConstantBitrateSeeker(input.getLength(), input.getPosition(), this.synchronizedHeader);
    }

    private static boolean headersMatch(int headerA, long headerB) {
        return (long)(headerA & -128000) == (headerB & -128000L);
    }

    private static int getSeekFrameHeader(ParsableByteArray frame, int xingBase) {
        if (frame.limit() >= xingBase + 4) {
            frame.setPosition(xingBase);
            int headerData = frame.readInt();
            if (headerData == SEEK_HEADER_XING || headerData == SEEK_HEADER_INFO) {
                return headerData;
            }
        }

        if (frame.limit() >= 40) {
            frame.setPosition(36);
            if (frame.readInt() == SEEK_HEADER_VBRI) {
                return SEEK_HEADER_VBRI;
            }
        }

        return 0;
    }

    @Nullable
    private static MlltSeeker maybeHandleSeekMetadata(Metadata metadata, long firstFramePosition) {
        if (metadata != null) {
            int length = metadata.length();

            for(int i = 0; i < length; ++i) {
                Entry entry = metadata.get(i);
                if (entry instanceof MlltFrame) {
                    return MlltSeeker.create(firstFramePosition, (MlltFrame)entry);
                }
            }
        }

        return null;
    }

    interface Seeker extends SeekMap {
        long getTimeUs(long var1);

        long getDataEndPosition();
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface Flags {
    }
}
