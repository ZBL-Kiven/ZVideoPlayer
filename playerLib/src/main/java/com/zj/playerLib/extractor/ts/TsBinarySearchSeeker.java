package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.extractor.BinarySearchSeeker;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.TimestampAdjuster;
import com.zj.playerLib.util.Util;

import java.io.IOException;

final class TsBinarySearchSeeker extends BinarySearchSeeker {
    private static final long SEEK_TOLERANCE_US = 100000L;
    private static final int MINIMUM_SEARCH_RANGE_BYTES = 940;
    private static final int TIMESTAMP_SEARCH_BYTES = 112800;

    public TsBinarySearchSeeker(TimestampAdjuster pcrTimestampAdjuster, long streamDurationUs, long inputLength, int pcrPid) {
        super(new DefaultSeekTimestampConverter(), new TsPcrSeeker(pcrPid, pcrTimestampAdjuster), streamDurationUs, 0L, streamDurationUs + 1L, 0L, inputLength, 188L, 940);
    }

    private static final class TsPcrSeeker implements TimestampSeeker {
        private final TimestampAdjuster pcrTimestampAdjuster;
        private final ParsableByteArray packetBuffer;
        private final int pcrPid;

        public TsPcrSeeker(int pcrPid, TimestampAdjuster pcrTimestampAdjuster) {
            this.pcrPid = pcrPid;
            this.pcrTimestampAdjuster = pcrTimestampAdjuster;
            this.packetBuffer = new ParsableByteArray();
        }

        public TimestampSearchResult searchForTimestamp(ExtractorInput input, long targetTimestamp, OutputFrameHolder outputFrameHolder) throws IOException, InterruptedException {
            long inputPosition = input.getPosition();
            int bytesToSearch = (int)Math.min(112800L, input.getLength() - inputPosition);
            this.packetBuffer.reset(bytesToSearch);
            input.peekFully(this.packetBuffer.data, 0, bytesToSearch);
            return this.searchForPcrValueInBuffer(this.packetBuffer, targetTimestamp, inputPosition);
        }

        private TimestampSearchResult searchForPcrValueInBuffer(ParsableByteArray packetBuffer, long targetPcrTimeUs, long bufferStartOffset) {
            int limit = packetBuffer.limit();
            long startOfLastPacketPosition = -1L;
            long endOfLastPacketPosition = -1L;

            long lastPcrTimeUsInRange;
            int endOfPacket;
            for(lastPcrTimeUsInRange = -Long.MAX_VALUE; packetBuffer.bytesLeft() >= 188; endOfLastPacketPosition = endOfPacket) {
                int startOfPacket = TsUtil.findSyncBytePosition(packetBuffer.data, packetBuffer.getPosition(), limit);
                endOfPacket = startOfPacket + 188;
                if (endOfPacket > limit) {
                    break;
                }

                long pcrValue = TsUtil.readPcrFromPacket(packetBuffer, startOfPacket, this.pcrPid);
                if (pcrValue != -Long.MAX_VALUE) {
                    long pcrTimeUs = this.pcrTimestampAdjuster.adjustTsTimestamp(pcrValue);
                    if (pcrTimeUs > targetPcrTimeUs) {
                        if (lastPcrTimeUsInRange == -Long.MAX_VALUE) {
                            return TimestampSearchResult.overestimatedResult(pcrTimeUs, bufferStartOffset);
                        }

                        return TimestampSearchResult.targetFoundResult(bufferStartOffset + startOfLastPacketPosition);
                    }

                    if (pcrTimeUs + 100000L > targetPcrTimeUs) {
                        long startOfPacketInStream = bufferStartOffset + (long)startOfPacket;
                        return TimestampSearchResult.targetFoundResult(startOfPacketInStream);
                    }

                    lastPcrTimeUsInRange = pcrTimeUs;
                    startOfLastPacketPosition = startOfPacket;
                }

                packetBuffer.setPosition(endOfPacket);
            }

            if (lastPcrTimeUsInRange != -Long.MAX_VALUE) {
                long endOfLastPacketPositionInStream = bufferStartOffset + endOfLastPacketPosition;
                return TimestampSearchResult.underestimatedResult(lastPcrTimeUsInRange, endOfLastPacketPositionInStream);
            } else {
                return TimestampSearchResult.NO_TIMESTAMP_IN_RANGE_RESULT;
            }
        }

        public void onSeekFinished() {
            this.packetBuffer.reset(Util.EMPTY_BYTE_ARRAY);
        }
    }
}
