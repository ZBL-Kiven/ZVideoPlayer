package com.zj.playerLib.extractor.flv;

import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.ExtractorsFactory;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.extractor.SeekMap.Unseekable;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;
import java.io.IOException;

public final class FlvExtractor implements Extractor {
    public static final ExtractorsFactory FACTORY = () -> {
        return new Extractor[]{new FlvExtractor()};
    };
    private static final int STATE_READING_FLV_HEADER = 1;
    private static final int STATE_SKIPPING_TO_TAG_HEADER = 2;
    private static final int STATE_READING_TAG_HEADER = 3;
    private static final int STATE_READING_TAG_DATA = 4;
    private static final int FLV_HEADER_SIZE = 9;
    private static final int FLV_TAG_HEADER_SIZE = 11;
    private static final int TAG_TYPE_AUDIO = 8;
    private static final int TAG_TYPE_VIDEO = 9;
    private static final int TAG_TYPE_SCRIPT_DATA = 18;
    private static final int FLV_TAG = Util.getIntegerCodeForString("FLV");
    private final ParsableByteArray scratch = new ParsableByteArray(4);
    private final ParsableByteArray headerBuffer = new ParsableByteArray(9);
    private final ParsableByteArray tagHeaderBuffer = new ParsableByteArray(11);
    private final ParsableByteArray tagData = new ParsableByteArray();
    private final ScriptTagPayloadReader metadataReader = new ScriptTagPayloadReader();
    private ExtractorOutput extractorOutput;
    private int state = 1;
    private long mediaTagTimestampOffsetUs = -Long.MAX_VALUE;
    private int bytesToNextTagHeader;
    private int tagType;
    private int tagDataSize;
    private long tagTimestampUs;
    private boolean outputSeekMap;
    private AudioTagPayloadReader audioReader;
    private VideoTagPayloadReader videoReader;

    public FlvExtractor() {
    }

    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        input.peekFully(this.scratch.data, 0, 3);
        this.scratch.setPosition(0);
        if (this.scratch.readUnsignedInt24() != FLV_TAG) {
            return false;
        } else {
            input.peekFully(this.scratch.data, 0, 2);
            this.scratch.setPosition(0);
            if ((this.scratch.readUnsignedShort() & 250) != 0) {
                return false;
            } else {
                input.peekFully(this.scratch.data, 0, 4);
                this.scratch.setPosition(0);
                int dataOffset = this.scratch.readInt();
                input.resetPeekPosition();
                input.advancePeekPosition(dataOffset);
                input.peekFully(this.scratch.data, 0, 4);
                this.scratch.setPosition(0);
                return this.scratch.readInt() == 0;
            }
        }
    }

    public void init(ExtractorOutput output) {
        this.extractorOutput = output;
    }

    public void seek(long position, long timeUs) {
        this.state = 1;
        this.mediaTagTimestampOffsetUs = -Long.MAX_VALUE;
        this.bytesToNextTagHeader = 0;
    }

    public void release() {
    }

    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        while(true) {
            switch(this.state) {
            case 1:
                if (this.readFlvHeader(input)) {
                    break;
                }

                return -1;
            case 2:
                this.skipToTagHeader(input);
                break;
            case 3:
                if (this.readTagHeader(input)) {
                    break;
                }

                return -1;
            case 4:
                if (!this.readTagData(input)) {
                    break;
                }

                return 0;
            default:
                throw new IllegalStateException();
            }
        }
    }

    private boolean readFlvHeader(ExtractorInput input) throws IOException, InterruptedException {
        if (!input.readFully(this.headerBuffer.data, 0, 9, true)) {
            return false;
        } else {
            this.headerBuffer.setPosition(0);
            this.headerBuffer.skipBytes(4);
            int flags = this.headerBuffer.readUnsignedByte();
            boolean hasAudio = (flags & 4) != 0;
            boolean hasVideo = (flags & 1) != 0;
            if (hasAudio && this.audioReader == null) {
                this.audioReader = new AudioTagPayloadReader(this.extractorOutput.track(8, 1));
            }

            if (hasVideo && this.videoReader == null) {
                this.videoReader = new VideoTagPayloadReader(this.extractorOutput.track(9, 2));
            }

            this.extractorOutput.endTracks();
            this.bytesToNextTagHeader = this.headerBuffer.readInt() - 9 + 4;
            this.state = 2;
            return true;
        }
    }

    private void skipToTagHeader(ExtractorInput input) throws IOException, InterruptedException {
        input.skipFully(this.bytesToNextTagHeader);
        this.bytesToNextTagHeader = 0;
        this.state = 3;
    }

    private boolean readTagHeader(ExtractorInput input) throws IOException, InterruptedException {
        if (!input.readFully(this.tagHeaderBuffer.data, 0, 11, true)) {
            return false;
        } else {
            this.tagHeaderBuffer.setPosition(0);
            this.tagType = this.tagHeaderBuffer.readUnsignedByte();
            this.tagDataSize = this.tagHeaderBuffer.readUnsignedInt24();
            this.tagTimestampUs = this.tagHeaderBuffer.readUnsignedInt24();
            this.tagTimestampUs = ((long)(this.tagHeaderBuffer.readUnsignedByte() << 24) | this.tagTimestampUs) * 1000L;
            this.tagHeaderBuffer.skipBytes(3);
            this.state = 4;
            return true;
        }
    }

    private boolean readTagData(ExtractorInput input) throws IOException, InterruptedException {
        boolean wasConsumed = true;
        if (this.tagType == 8 && this.audioReader != null) {
            this.ensureReadyForMediaOutput();
            this.audioReader.consume(this.prepareTagData(input), this.mediaTagTimestampOffsetUs + this.tagTimestampUs);
        } else if (this.tagType == 9 && this.videoReader != null) {
            this.ensureReadyForMediaOutput();
            this.videoReader.consume(this.prepareTagData(input), this.mediaTagTimestampOffsetUs + this.tagTimestampUs);
        } else if (this.tagType == 18 && !this.outputSeekMap) {
            this.metadataReader.consume(this.prepareTagData(input), this.tagTimestampUs);
            long durationUs = this.metadataReader.getDurationUs();
            if (durationUs != -Long.MAX_VALUE) {
                this.extractorOutput.seekMap(new Unseekable(durationUs));
                this.outputSeekMap = true;
            }
        } else {
            input.skipFully(this.tagDataSize);
            wasConsumed = false;
        }

        this.bytesToNextTagHeader = 4;
        this.state = 2;
        return wasConsumed;
    }

    private ParsableByteArray prepareTagData(ExtractorInput input) throws IOException, InterruptedException {
        if (this.tagDataSize > this.tagData.capacity()) {
            this.tagData.reset(new byte[Math.max(this.tagData.capacity() * 2, this.tagDataSize)], 0);
        } else {
            this.tagData.setPosition(0);
        }

        this.tagData.setLimit(this.tagDataSize);
        input.readFully(this.tagData.data, 0, this.tagDataSize);
        return this.tagData;
    }

    private void ensureReadyForMediaOutput() {
        if (!this.outputSeekMap) {
            this.extractorOutput.seekMap(new Unseekable(-Long.MAX_VALUE));
            this.outputSeekMap = true;
        }

        if (this.mediaTagTimestampOffsetUs == -Long.MAX_VALUE) {
            this.mediaTagTimestampOffsetUs = this.metadataReader.getDurationUs() == -Long.MAX_VALUE ? -this.tagTimestampUs : 0L;
        }

    }
}
