package com.zj.playerLib.extractor.ts;

import android.util.SparseArray;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.ExtractorsFactory;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.extractor.BinarySearchSeeker.OutputFrameHolder;
import com.zj.playerLib.extractor.SeekMap.Unseekable;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.util.ParsableBitArray;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.TimestampAdjuster;
import java.io.IOException;

public final class PsExtractor implements Extractor {
    public static final ExtractorsFactory FACTORY = () -> {
        return new Extractor[]{new PsExtractor()};
    };
    static final int PACK_START_CODE = 442;
    static final int SYSTEM_HEADER_START_CODE = 443;
    static final int PACKET_START_CODE_PREFIX = 1;
    static final int MPEG_PROGRAM_END_CODE = 441;
    private static final int MAX_STREAM_ID_PLUS_ONE = 256;
    private static final long MAX_SEARCH_LENGTH = 1048576L;
    private static final long MAX_SEARCH_LENGTH_AFTER_AUDIO_AND_VIDEO_FOUND = 8192L;
    public static final int PRIVATE_STREAM_1 = 189;
    public static final int AUDIO_STREAM = 192;
    public static final int AUDIO_STREAM_MASK = 224;
    public static final int VIDEO_STREAM = 224;
    public static final int VIDEO_STREAM_MASK = 240;
    private final TimestampAdjuster timestampAdjuster;
    private final SparseArray<PesReader> psPayloadReaders;
    private final ParsableByteArray psPacketBuffer;
    private final PsDurationReader durationReader;
    private boolean foundAllTracks;
    private boolean foundAudioTrack;
    private boolean foundVideoTrack;
    private long lastTrackPosition;
    private PsBinarySearchSeeker psBinarySearchSeeker;
    private ExtractorOutput output;
    private boolean hasOutputSeekMap;

    public PsExtractor() {
        this(new TimestampAdjuster(0L));
    }

    public PsExtractor(TimestampAdjuster timestampAdjuster) {
        this.timestampAdjuster = timestampAdjuster;
        this.psPacketBuffer = new ParsableByteArray(4096);
        this.psPayloadReaders = new SparseArray();
        this.durationReader = new PsDurationReader();
    }

    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        byte[] scratch = new byte[14];
        input.peekFully(scratch, 0, 14);
        if (442 != ((scratch[0] & 255) << 24 | (scratch[1] & 255) << 16 | (scratch[2] & 255) << 8 | scratch[3] & 255)) {
            return false;
        } else if ((scratch[4] & 196) != 68) {
            return false;
        } else if ((scratch[6] & 4) != 4) {
            return false;
        } else if ((scratch[8] & 4) != 4) {
            return false;
        } else if ((scratch[9] & 1) != 1) {
            return false;
        } else if ((scratch[12] & 3) != 3) {
            return false;
        } else {
            int packStuffingLength = scratch[13] & 7;
            input.advancePeekPosition(packStuffingLength);
            input.peekFully(scratch, 0, 3);
            return 1 == ((scratch[0] & 255) << 16 | (scratch[1] & 255) << 8 | scratch[2] & 255);
        }
    }

    public void init(ExtractorOutput output) {
        this.output = output;
    }

    public void seek(long position, long timeUs) {
        boolean hasNotEncounteredFirstTimestamp = this.timestampAdjuster.getTimestampOffsetUs() == -Long.MAX_VALUE;
        if (hasNotEncounteredFirstTimestamp || this.timestampAdjuster.getFirstSampleTimestampUs() != 0L && this.timestampAdjuster.getFirstSampleTimestampUs() != timeUs) {
            this.timestampAdjuster.reset();
            this.timestampAdjuster.setFirstSampleTimestampUs(timeUs);
        }

        if (this.psBinarySearchSeeker != null) {
            this.psBinarySearchSeeker.setSeekTargetUs(timeUs);
        }

        for(int i = 0; i < this.psPayloadReaders.size(); ++i) {
            this.psPayloadReaders.valueAt(i).seek();
        }

    }

    public void release() {
    }

    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        long inputLength = input.getLength();
        boolean canReadDuration = inputLength != -1L;
        if (canReadDuration && !this.durationReader.isDurationReadFinished()) {
            return this.durationReader.readDuration(input, seekPosition);
        } else {
            this.maybeOutputSeekMap(inputLength);
            if (this.psBinarySearchSeeker != null && this.psBinarySearchSeeker.isSeeking()) {
                return this.psBinarySearchSeeker.handlePendingSeek(input, seekPosition, null);
            } else {
                input.resetPeekPosition();
                long peekBytesLeft = inputLength != -1L ? inputLength - input.getPeekPosition() : -1L;
                if (peekBytesLeft != -1L && peekBytesLeft < 4L) {
                    return -1;
                } else if (!input.peekFully(this.psPacketBuffer.data, 0, 4, true)) {
                    return -1;
                } else {
                    this.psPacketBuffer.setPosition(0);
                    int nextStartCode = this.psPacketBuffer.readInt();
                    if (nextStartCode == 441) {
                        return -1;
                    } else {
                        int streamId;
                        if (nextStartCode == 442) {
                            input.peekFully(this.psPacketBuffer.data, 0, 10);
                            this.psPacketBuffer.setPosition(9);
                            streamId = this.psPacketBuffer.readUnsignedByte() & 7;
                            input.skipFully(streamId + 14);
                            return 0;
                        } else if (nextStartCode == 443) {
                            input.peekFully(this.psPacketBuffer.data, 0, 2);
                            this.psPacketBuffer.setPosition(0);
                            streamId = this.psPacketBuffer.readUnsignedShort();
                            input.skipFully(streamId + 6);
                            return 0;
                        } else if ((nextStartCode & -256) >> 8 != 1) {
                            input.skipFully(1);
                            return 0;
                        } else {
                            streamId = nextStartCode & 255;
                            PesReader payloadReader = this.psPayloadReaders.get(streamId);
                            if (!this.foundAllTracks) {
                                if (payloadReader == null) {
                                    ElementaryStreamReader elementaryStreamReader = null;
                                    if (streamId == 189) {
                                        elementaryStreamReader = new Ac3Reader();
                                        this.foundAudioTrack = true;
                                        this.lastTrackPosition = input.getPosition();
                                    } else if ((streamId & 224) == 192) {
                                        elementaryStreamReader = new MpegAudioReader();
                                        this.foundAudioTrack = true;
                                        this.lastTrackPosition = input.getPosition();
                                    } else if ((streamId & 240) == 224) {
                                        elementaryStreamReader = new H262Reader();
                                        this.foundVideoTrack = true;
                                        this.lastTrackPosition = input.getPosition();
                                    }

                                    if (elementaryStreamReader != null) {
                                        TrackIdGenerator idGenerator = new TrackIdGenerator(streamId, 256);
                                        elementaryStreamReader.createTracks(this.output, idGenerator);
                                        payloadReader = new PesReader(elementaryStreamReader, this.timestampAdjuster);
                                        this.psPayloadReaders.put(streamId, payloadReader);
                                    }
                                }

                                long maxSearchPosition = this.foundAudioTrack && this.foundVideoTrack ? this.lastTrackPosition + 8192L : 1048576L;
                                if (input.getPosition() > maxSearchPosition) {
                                    this.foundAllTracks = true;
                                    this.output.endTracks();
                                }
                            }

                            input.peekFully(this.psPacketBuffer.data, 0, 2);
                            this.psPacketBuffer.setPosition(0);
                            int payloadLength = this.psPacketBuffer.readUnsignedShort();
                            int pesLength = payloadLength + 6;
                            if (payloadReader == null) {
                                input.skipFully(pesLength);
                            } else {
                                this.psPacketBuffer.reset(pesLength);
                                input.readFully(this.psPacketBuffer.data, 0, pesLength);
                                this.psPacketBuffer.setPosition(6);
                                payloadReader.consume(this.psPacketBuffer);
                                this.psPacketBuffer.setLimit(this.psPacketBuffer.capacity());
                            }

                            return 0;
                        }
                    }
                }
            }
        }
    }

    private void maybeOutputSeekMap(long inputLength) {
        if (!this.hasOutputSeekMap) {
            this.hasOutputSeekMap = true;
            if (this.durationReader.getDurationUs() != -Long.MAX_VALUE) {
                this.psBinarySearchSeeker = new PsBinarySearchSeeker(this.durationReader.getScrTimestampAdjuster(), this.durationReader.getDurationUs(), inputLength);
                this.output.seekMap(this.psBinarySearchSeeker.getSeekMap());
            } else {
                this.output.seekMap(new Unseekable(this.durationReader.getDurationUs()));
            }
        }

    }

    private static final class PesReader {
        private static final int PES_SCRATCH_SIZE = 64;
        private final ElementaryStreamReader pesPayloadReader;
        private final TimestampAdjuster timestampAdjuster;
        private final ParsableBitArray pesScratch;
        private boolean ptsFlag;
        private boolean dtsFlag;
        private boolean seenFirstDts;
        private int extendedHeaderLength;
        private long timeUs;

        public PesReader(ElementaryStreamReader pesPayloadReader, TimestampAdjuster timestampAdjuster) {
            this.pesPayloadReader = pesPayloadReader;
            this.timestampAdjuster = timestampAdjuster;
            this.pesScratch = new ParsableBitArray(new byte[64]);
        }

        public void seek() {
            this.seenFirstDts = false;
            this.pesPayloadReader.seek();
        }

        public void consume(ParsableByteArray data) throws ParserException {
            data.readBytes(this.pesScratch.data, 0, 3);
            this.pesScratch.setPosition(0);
            this.parseHeader();
            data.readBytes(this.pesScratch.data, 0, this.extendedHeaderLength);
            this.pesScratch.setPosition(0);
            this.parseHeaderExtension();
            this.pesPayloadReader.packetStarted(this.timeUs, 4);
            this.pesPayloadReader.consume(data);
            this.pesPayloadReader.packetFinished();
        }

        private void parseHeader() {
            this.pesScratch.skipBits(8);
            this.ptsFlag = this.pesScratch.readBit();
            this.dtsFlag = this.pesScratch.readBit();
            this.pesScratch.skipBits(6);
            this.extendedHeaderLength = this.pesScratch.readBits(8);
        }

        private void parseHeaderExtension() {
            this.timeUs = 0L;
            if (this.ptsFlag) {
                this.pesScratch.skipBits(4);
                long pts = (long)this.pesScratch.readBits(3) << 30;
                this.pesScratch.skipBits(1);
                pts |= this.pesScratch.readBits(15) << 15;
                this.pesScratch.skipBits(1);
                pts |= this.pesScratch.readBits(15);
                this.pesScratch.skipBits(1);
                if (!this.seenFirstDts && this.dtsFlag) {
                    this.pesScratch.skipBits(4);
                    long dts = (long)this.pesScratch.readBits(3) << 30;
                    this.pesScratch.skipBits(1);
                    dts |= this.pesScratch.readBits(15) << 15;
                    this.pesScratch.skipBits(1);
                    dts |= this.pesScratch.readBits(15);
                    this.pesScratch.skipBits(1);
                    this.timestampAdjuster.adjustTsTimestamp(dts);
                    this.seenFirstDts = true;
                }

                this.timeUs = this.timestampAdjuster.adjustTsTimestamp(pts);
            }

        }
    }
}
