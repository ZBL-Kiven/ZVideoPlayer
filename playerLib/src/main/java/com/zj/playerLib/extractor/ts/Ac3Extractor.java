package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.audio.Ac3Util;
import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.ExtractorsFactory;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.extractor.SeekMap.Unseekable;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;

import java.io.IOException;

public final class Ac3Extractor implements Extractor {
    public static final ExtractorsFactory FACTORY = () -> {
        return new Extractor[]{new Ac3Extractor()};
    };
    private static final int MAX_SNIFF_BYTES = 8192;
    private static final int AC3_SYNC_WORD = 2935;
    private static final int MAX_SYNC_FRAME_SIZE = 2786;
    private static final int ID3_TAG = Util.getIntegerCodeForString("ID3");
    private final long firstSampleTimestampUs;
    private final Ac3Reader reader;
    private final ParsableByteArray sampleData;
    private boolean startedPacket;

    public Ac3Extractor() {
        this(0L);
    }

    public Ac3Extractor(long firstSampleTimestampUs) {
        this.firstSampleTimestampUs = firstSampleTimestampUs;
        this.reader = new Ac3Reader();
        this.sampleData = new ParsableByteArray(2786);
    }

    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        ParsableByteArray scratch = new ParsableByteArray(10);
        int startPosition = 0;

        while(true) {
            input.peekFully(scratch.data, 0, 10);
            scratch.setPosition(0);
            int headerPosition;
            if (scratch.readUnsignedInt24() != ID3_TAG) {
                input.resetPeekPosition();
                input.advancePeekPosition(startPosition);
                headerPosition = startPosition;
                int validFramesCount = 0;

                while(true) {
                    while(true) {
                        input.peekFully(scratch.data, 0, 6);
                        scratch.setPosition(0);
                        int syncBytes = scratch.readUnsignedShort();
                        if (syncBytes != 2935) {
                            validFramesCount = 0;
                            input.resetPeekPosition();
                            ++headerPosition;
                            if (headerPosition - startPosition >= 8192) {
                                return false;
                            }

                            input.advancePeekPosition(headerPosition);
                        } else {
                            ++validFramesCount;
                            if (validFramesCount >= 4) {
                                return true;
                            }

                            int frameSize = Ac3Util.parseAc3SyncframeSize(scratch.data);
                            if (frameSize == -1) {
                                return false;
                            }

                            input.advancePeekPosition(frameSize - 6);
                        }
                    }
                }
            }

            scratch.skipBytes(3);
            headerPosition = scratch.readSynchSafeInt();
            startPosition += 10 + headerPosition;
            input.advancePeekPosition(headerPosition);
        }
    }

    public void init(ExtractorOutput output) {
        this.reader.createTracks(output, new TrackIdGenerator(0, 1));
        output.endTracks();
        output.seekMap(new Unseekable(-Long.MAX_VALUE));
    }

    public void seek(long position, long timeUs) {
        this.startedPacket = false;
        this.reader.seek();
    }

    public void release() {
    }

    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        int bytesRead = input.read(this.sampleData.data, 0, 2786);
        if (bytesRead == -1) {
            return -1;
        } else {
            this.sampleData.setPosition(0);
            this.sampleData.setLimit(bytesRead);
            if (!this.startedPacket) {
                this.reader.packetStarted(this.firstSampleTimestampUs, 4);
                this.startedPacket = true;
            }

            this.reader.consume(this.sampleData);
            return 0;
        }
    }
}
