package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.TimestampAdjuster;
import com.zj.playerLib.util.Util;
import java.io.IOException;

final class TsDurationReader {
    private static final int TIMESTAMP_SEARCH_BYTES = 112800;
    private final TimestampAdjuster pcrTimestampAdjuster = new TimestampAdjuster(0L);
    private final ParsableByteArray packetBuffer = new ParsableByteArray();
    private boolean isDurationRead;
    private boolean isFirstPcrValueRead;
    private boolean isLastPcrValueRead;
    private long firstPcrValue = -Long.MAX_VALUE;
    private long lastPcrValue = -Long.MAX_VALUE;
    private long durationUs = -Long.MAX_VALUE;

    TsDurationReader() {
    }

    public boolean isDurationReadFinished() {
        return this.isDurationRead;
    }

    public int readDuration(ExtractorInput input, PositionHolder seekPositionHolder, int pcrPid) throws IOException, InterruptedException {
        if (pcrPid <= 0) {
            return this.finishReadDuration(input);
        } else if (!this.isLastPcrValueRead) {
            return this.readLastPcrValue(input, seekPositionHolder, pcrPid);
        } else if (this.lastPcrValue == -Long.MAX_VALUE) {
            return this.finishReadDuration(input);
        } else if (!this.isFirstPcrValueRead) {
            return this.readFirstPcrValue(input, seekPositionHolder, pcrPid);
        } else if (this.firstPcrValue == -Long.MAX_VALUE) {
            return this.finishReadDuration(input);
        } else {
            long minPcrPositionUs = this.pcrTimestampAdjuster.adjustTsTimestamp(this.firstPcrValue);
            long maxPcrPositionUs = this.pcrTimestampAdjuster.adjustTsTimestamp(this.lastPcrValue);
            this.durationUs = maxPcrPositionUs - minPcrPositionUs;
            return this.finishReadDuration(input);
        }
    }

    public long getDurationUs() {
        return this.durationUs;
    }

    public TimestampAdjuster getPcrTimestampAdjuster() {
        return this.pcrTimestampAdjuster;
    }

    private int finishReadDuration(ExtractorInput input) {
        this.packetBuffer.reset(Util.EMPTY_BYTE_ARRAY);
        this.isDurationRead = true;
        input.resetPeekPosition();
        return 0;
    }

    private int readFirstPcrValue(ExtractorInput input, PositionHolder seekPositionHolder, int pcrPid) throws IOException, InterruptedException {
        int bytesToSearch = (int)Math.min(112800L, input.getLength());
        int searchStartPosition = 0;
        if (input.getPosition() != (long)searchStartPosition) {
            seekPositionHolder.position = searchStartPosition;
            return 1;
        } else {
            this.packetBuffer.reset(bytesToSearch);
            input.resetPeekPosition();
            input.peekFully(this.packetBuffer.data, 0, bytesToSearch);
            this.firstPcrValue = this.readFirstPcrValueFromBuffer(this.packetBuffer, pcrPid);
            this.isFirstPcrValueRead = true;
            return 0;
        }
    }

    private long readFirstPcrValueFromBuffer(ParsableByteArray packetBuffer, int pcrPid) {
        int searchStartPosition = packetBuffer.getPosition();
        int searchEndPosition = packetBuffer.limit();

        for(int searchPosition = searchStartPosition; searchPosition < searchEndPosition; ++searchPosition) {
            if (packetBuffer.data[searchPosition] == 71) {
                long pcrValue = TsUtil.readPcrFromPacket(packetBuffer, searchPosition, pcrPid);
                if (pcrValue != -Long.MAX_VALUE) {
                    return pcrValue;
                }
            }
        }

        return -Long.MAX_VALUE;
    }

    private int readLastPcrValue(ExtractorInput input, PositionHolder seekPositionHolder, int pcrPid) throws IOException, InterruptedException {
        long inputLength = input.getLength();
        int bytesToSearch = (int)Math.min(112800L, inputLength);
        long searchStartPosition = inputLength - (long)bytesToSearch;
        if (input.getPosition() != searchStartPosition) {
            seekPositionHolder.position = searchStartPosition;
            return 1;
        } else {
            this.packetBuffer.reset(bytesToSearch);
            input.resetPeekPosition();
            input.peekFully(this.packetBuffer.data, 0, bytesToSearch);
            this.lastPcrValue = this.readLastPcrValueFromBuffer(this.packetBuffer, pcrPid);
            this.isLastPcrValueRead = true;
            return 0;
        }
    }

    private long readLastPcrValueFromBuffer(ParsableByteArray packetBuffer, int pcrPid) {
        int searchStartPosition = packetBuffer.getPosition();
        int searchEndPosition = packetBuffer.limit();

        for(int searchPosition = searchEndPosition - 1; searchPosition >= searchStartPosition; --searchPosition) {
            if (packetBuffer.data[searchPosition] == 71) {
                long pcrValue = TsUtil.readPcrFromPacket(packetBuffer, searchPosition, pcrPid);
                if (pcrValue != -Long.MAX_VALUE) {
                    return pcrValue;
                }
            }
        }

        return -Long.MAX_VALUE;
    }
}
