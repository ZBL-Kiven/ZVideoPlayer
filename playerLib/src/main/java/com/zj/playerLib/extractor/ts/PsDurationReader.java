package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.TimestampAdjuster;
import com.zj.playerLib.util.Util;

import java.io.IOException;

final class PsDurationReader {
    private static final int TIMESTAMP_SEARCH_BYTES = 20000;
    private final TimestampAdjuster scrTimestampAdjuster = new TimestampAdjuster(0L);
    private final ParsableByteArray packetBuffer = new ParsableByteArray();
    private boolean isDurationRead;
    private boolean isFirstScrValueRead;
    private boolean isLastScrValueRead;
    private long firstScrValue = -Long.MAX_VALUE;
    private long lastScrValue = -Long.MAX_VALUE;
    private long durationUs = -Long.MAX_VALUE;

    PsDurationReader() {
    }

    public boolean isDurationReadFinished() {
        return this.isDurationRead;
    }

    public TimestampAdjuster getScrTimestampAdjuster() {
        return this.scrTimestampAdjuster;
    }

    public int readDuration(ExtractorInput input, PositionHolder seekPositionHolder) throws IOException, InterruptedException {
        if (!this.isLastScrValueRead) {
            return this.readLastScrValue(input, seekPositionHolder);
        } else if (this.lastScrValue == -Long.MAX_VALUE) {
            return this.finishReadDuration(input);
        } else if (!this.isFirstScrValueRead) {
            return this.readFirstScrValue(input, seekPositionHolder);
        } else if (this.firstScrValue == -Long.MAX_VALUE) {
            return this.finishReadDuration(input);
        } else {
            long minScrPositionUs = this.scrTimestampAdjuster.adjustTsTimestamp(this.firstScrValue);
            long maxScrPositionUs = this.scrTimestampAdjuster.adjustTsTimestamp(this.lastScrValue);
            this.durationUs = maxScrPositionUs - minScrPositionUs;
            return this.finishReadDuration(input);
        }
    }

    public long getDurationUs() {
        return this.durationUs;
    }

    public static long readScrValueFromPack(ParsableByteArray packetBuffer) {
        int originalPosition = packetBuffer.getPosition();
        if (packetBuffer.bytesLeft() < 9) {
            return -Long.MAX_VALUE;
        } else {
            byte[] scrBytes = new byte[9];
            packetBuffer.readBytes(scrBytes, 0, scrBytes.length);
            packetBuffer.setPosition(originalPosition);
            return !checkMarkerBits(scrBytes) ? -Long.MAX_VALUE : readScrValueFromPackHeader(scrBytes);
        }
    }

    private int finishReadDuration(ExtractorInput input) {
        this.packetBuffer.reset(Util.EMPTY_BYTE_ARRAY);
        this.isDurationRead = true;
        input.resetPeekPosition();
        return 0;
    }

    private int readFirstScrValue(ExtractorInput input, PositionHolder seekPositionHolder) throws IOException, InterruptedException {
        int bytesToSearch = (int)Math.min(20000L, input.getLength());
        int searchStartPosition = 0;
        if (input.getPosition() != (long)searchStartPosition) {
            seekPositionHolder.position = searchStartPosition;
            return 1;
        } else {
            this.packetBuffer.reset(bytesToSearch);
            input.resetPeekPosition();
            input.peekFully(this.packetBuffer.data, 0, bytesToSearch);
            this.firstScrValue = this.readFirstScrValueFromBuffer(this.packetBuffer);
            this.isFirstScrValueRead = true;
            return 0;
        }
    }

    private long readFirstScrValueFromBuffer(ParsableByteArray packetBuffer) {
        int searchStartPosition = packetBuffer.getPosition();
        int searchEndPosition = packetBuffer.limit();

        for(int searchPosition = searchStartPosition; searchPosition < searchEndPosition - 3; ++searchPosition) {
            int nextStartCode = this.peekIntAtPosition(packetBuffer.data, searchPosition);
            if (nextStartCode == 442) {
                packetBuffer.setPosition(searchPosition + 4);
                long scrValue = readScrValueFromPack(packetBuffer);
                if (scrValue != -Long.MAX_VALUE) {
                    return scrValue;
                }
            }
        }

        return -Long.MAX_VALUE;
    }

    private int readLastScrValue(ExtractorInput input, PositionHolder seekPositionHolder) throws IOException, InterruptedException {
        long inputLength = input.getLength();
        int bytesToSearch = (int)Math.min(20000L, inputLength);
        long searchStartPosition = inputLength - (long)bytesToSearch;
        if (input.getPosition() != searchStartPosition) {
            seekPositionHolder.position = searchStartPosition;
            return 1;
        } else {
            this.packetBuffer.reset(bytesToSearch);
            input.resetPeekPosition();
            input.peekFully(this.packetBuffer.data, 0, bytesToSearch);
            this.lastScrValue = this.readLastScrValueFromBuffer(this.packetBuffer);
            this.isLastScrValueRead = true;
            return 0;
        }
    }

    private long readLastScrValueFromBuffer(ParsableByteArray packetBuffer) {
        int searchStartPosition = packetBuffer.getPosition();
        int searchEndPosition = packetBuffer.limit();

        for(int searchPosition = searchEndPosition - 4; searchPosition >= searchStartPosition; --searchPosition) {
            int nextStartCode = this.peekIntAtPosition(packetBuffer.data, searchPosition);
            if (nextStartCode == 442) {
                packetBuffer.setPosition(searchPosition + 4);
                long scrValue = readScrValueFromPack(packetBuffer);
                if (scrValue != -Long.MAX_VALUE) {
                    return scrValue;
                }
            }
        }

        return -Long.MAX_VALUE;
    }

    private int peekIntAtPosition(byte[] data, int position) {
        return (data[position] & 255) << 24 | (data[position + 1] & 255) << 16 | (data[position + 2] & 255) << 8 | data[position + 3] & 255;
    }

    private static boolean checkMarkerBits(byte[] scrBytes) {
        if ((scrBytes[0] & 196) != 68) {
            return false;
        } else if ((scrBytes[2] & 4) != 4) {
            return false;
        } else if ((scrBytes[4] & 4) != 4) {
            return false;
        } else if ((scrBytes[5] & 1) != 1) {
            return false;
        } else {
            return (scrBytes[8] & 3) == 3;
        }
    }

    private static long readScrValueFromPackHeader(byte[] scrBytes) {
        return ((long)scrBytes[0] & 56L) >> 3 << 30 | ((long)scrBytes[0] & 3L) << 28 | ((long)scrBytes[1] & 255L) << 20 | ((long)scrBytes[2] & 248L) >> 3 << 15 | ((long)scrBytes[2] & 3L) << 13 | ((long)scrBytes[3] & 255L) << 5 | ((long)scrBytes[4] & 248L) >> 3;
    }
}
