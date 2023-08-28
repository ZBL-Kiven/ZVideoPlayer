package com.zj.playerLib.extractor.ogg;

import com.zj.playerLib.Format;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.SeekMap;
import com.zj.playerLib.extractor.SeekPoint;
import com.zj.playerLib.util.FlacStreamInfo;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class FlacReader extends StreamReader {
    private static final byte AUDIO_PACKET_TYPE = -1;
    private static final byte SEEKTABLE_PACKET_TYPE = 3;
    private static final int FRAME_HEADER_SAMPLE_NUMBER_OFFSET = 4;
    private FlacStreamInfo streamInfo;
    private FlacOggSeeker flacOggSeeker;

    FlacReader() {
    }

    public static boolean verifyBitstreamType(ParsableByteArray data) {
        return data.bytesLeft() >= 5 && data.readUnsignedByte() == 127 && data.readUnsignedInt() == 1179402563L;
    }

    protected void reset(boolean headerData) {
        super.reset(headerData);
        if (headerData) {
            this.streamInfo = null;
            this.flacOggSeeker = null;
        }

    }

    private static boolean isAudioPacket(byte[] data) {
        return data[0] == -1;
    }

    protected long preparePayload(ParsableByteArray packet) {
        return !isAudioPacket(packet.data) ? -1L : (long)this.getFlacFrameBlockSize(packet);
    }

    protected boolean readHeaders(ParsableByteArray packet, long position, SetupData setupData) throws IOException, InterruptedException {
        byte[] data = packet.data;
        if (this.streamInfo == null) {
            this.streamInfo = new FlacStreamInfo(data, 17);
            byte[] metadata = Arrays.copyOfRange(data, 9, packet.limit());
            metadata[4] = -128;
            List<byte[]> initializationData = Collections.singletonList(metadata);
            setupData.format = Format.createAudioSampleFormat(null, "audio/flac", null, -1, this.streamInfo.bitRate(), this.streamInfo.channels, this.streamInfo.sampleRate, initializationData, null, 0, null);
        } else if ((data[0] & 127) == 3) {
            this.flacOggSeeker = new FlacOggSeeker();
            this.flacOggSeeker.parseSeekTable(packet);
        } else if (isAudioPacket(data)) {
            if (this.flacOggSeeker != null) {
                this.flacOggSeeker.setFirstFrameOffset(position);
                setupData.oggSeeker = this.flacOggSeeker;
            }

            return false;
        }

        return true;
    }

    private int getFlacFrameBlockSize(ParsableByteArray packet) {
        int blockSizeCode = (packet.data[2] & 255) >> 4;
        switch(blockSizeCode) {
        case 1:
            return 192;
        case 2:
        case 3:
        case 4:
        case 5:
            return 576 << blockSizeCode - 2;
        case 6:
        case 7:
            packet.skipBytes(4);
            packet.readUtf8EncodedLong();
            int value = blockSizeCode == 6 ? packet.readUnsignedByte() : packet.readUnsignedShort();
            packet.setPosition(0);
            return value + 1;
        case 8:
        case 9:
        case 10:
        case 11:
        case 12:
        case 13:
        case 14:
        case 15:
            return 256 << blockSizeCode - 8;
        default:
            return -1;
        }
    }

    private class FlacOggSeeker implements OggSeeker, SeekMap {
        private static final int METADATA_LENGTH_OFFSET = 1;
        private static final int SEEK_POINT_SIZE = 18;
        private long[] seekPointGranules;
        private long[] seekPointOffsets;
        private long firstFrameOffset = -1L;
        private long pendingSeekGranule = -1L;

        public FlacOggSeeker() {
        }

        public void setFirstFrameOffset(long firstFrameOffset) {
            this.firstFrameOffset = firstFrameOffset;
        }

        public void parseSeekTable(ParsableByteArray data) {
            data.skipBytes(1);
            int length = data.readUnsignedInt24();
            int numberOfSeekPoints = length / 18;
            this.seekPointGranules = new long[numberOfSeekPoints];
            this.seekPointOffsets = new long[numberOfSeekPoints];

            for(int i = 0; i < numberOfSeekPoints; ++i) {
                this.seekPointGranules[i] = data.readLong();
                this.seekPointOffsets[i] = data.readLong();
                data.skipBytes(2);
            }

        }

        public long read(ExtractorInput input) throws IOException, InterruptedException {
            if (this.pendingSeekGranule >= 0L) {
                long result = -(this.pendingSeekGranule + 2L);
                this.pendingSeekGranule = -1L;
                return result;
            } else {
                return -1L;
            }
        }

        public long startSeek(long timeUs) {
            long granule = FlacReader.this.convertTimeToGranule(timeUs);
            int index = Util.binarySearchFloor(this.seekPointGranules, granule, true, true);
            this.pendingSeekGranule = this.seekPointGranules[index];
            return granule;
        }

        public SeekMap createSeekMap() {
            return this;
        }

        public boolean isSeekable() {
            return true;
        }

        public SeekPoints getSeekPoints(long timeUs) {
            long granule = FlacReader.this.convertTimeToGranule(timeUs);
            int index = Util.binarySearchFloor(this.seekPointGranules, granule, true, true);
            long seekTimeUs = FlacReader.this.convertGranuleToTime(this.seekPointGranules[index]);
            long seekPosition = this.firstFrameOffset + this.seekPointOffsets[index];
            SeekPoint seekPoint = new SeekPoint(seekTimeUs, seekPosition);
            if (seekTimeUs < timeUs && index != this.seekPointGranules.length - 1) {
                long secondSeekTimeUs = FlacReader.this.convertGranuleToTime(this.seekPointGranules[index + 1]);
                long secondSeekPosition = this.firstFrameOffset + this.seekPointOffsets[index + 1];
                SeekPoint secondSeekPoint = new SeekPoint(secondSeekTimeUs, secondSeekPosition);
                return new SeekPoints(seekPoint, secondSeekPoint);
            } else {
                return new SeekPoints(seekPoint);
            }
        }

        public long getDurationUs() {
            return FlacReader.this.streamInfo.durationUs();
        }
    }
}
