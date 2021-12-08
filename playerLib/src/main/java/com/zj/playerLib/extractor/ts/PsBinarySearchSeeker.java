package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.extractor.BinarySearchSeeker;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.TimestampAdjuster;
import com.zj.playerLib.util.Util;

import java.io.IOException;

final class PsBinarySearchSeeker extends BinarySearchSeeker {
    private static final long SEEK_TOLERANCE_US = 100000L;
    private static final int MINIMUM_SEARCH_RANGE_BYTES = 1000;
    private static final int TIMESTAMP_SEARCH_BYTES = 20000;

    public PsBinarySearchSeeker(TimestampAdjuster scrTimestampAdjuster, long streamDurationUs, long inputLength) {
        super(new DefaultSeekTimestampConverter(), new PsScrSeeker(scrTimestampAdjuster), streamDurationUs, 0L, streamDurationUs + 1L, 0L, inputLength, 188L, 1000);
    }

    private static int peekIntAtPosition(byte[] data, int position) {
        return (data[position] & 255) << 24 | (data[position + 1] & 255) << 16 | (data[position + 2] & 255) << 8 | data[position + 3] & 255;
    }

    private static final class PsScrSeeker implements TimestampSeeker {
        private final TimestampAdjuster scrTimestampAdjuster;
        private final ParsableByteArray packetBuffer;

        private PsScrSeeker(TimestampAdjuster scrTimestampAdjuster) {
            this.scrTimestampAdjuster = scrTimestampAdjuster;
            this.packetBuffer = new ParsableByteArray();
        }

        public TimestampSearchResult searchForTimestamp(ExtractorInput input, long targetTimestamp, OutputFrameHolder outputFrameHolder) throws IOException, InterruptedException {
            long inputPosition = input.getPosition();
            int bytesToSearch = (int)Math.min(20000L, input.getLength() - inputPosition);
            this.packetBuffer.reset(bytesToSearch);
            input.peekFully(this.packetBuffer.data, 0, bytesToSearch);
            return this.searchForScrValueInBuffer(this.packetBuffer, targetTimestamp, inputPosition);
        }

        public void onSeekFinished() {
            this.packetBuffer.reset(Util.EMPTY_BYTE_ARRAY);
        }

        private TimestampSearchResult searchForScrValueInBuffer(ParsableByteArray packetBuffer, long targetScrTimeUs, long bufferStartOffset) {
            int startOfLastPacketPosition = -1;
            int endOfLastPacketPosition = -1;
            long lastScrTimeUsInRange = -Long.MAX_VALUE;

            while(packetBuffer.bytesLeft() >= 4) {
                int nextStartCode = PsBinarySearchSeeker.peekIntAtPosition(packetBuffer.data, packetBuffer.getPosition());
                if (nextStartCode != 442) {
                    packetBuffer.skipBytes(1);
                } else {
                    packetBuffer.skipBytes(4);
                    long scrValue = PsDurationReader.readScrValueFromPack(packetBuffer);
                    if (scrValue != -Long.MAX_VALUE) {
                        long scrTimeUs = this.scrTimestampAdjuster.adjustTsTimestamp(scrValue);
                        if (scrTimeUs > targetScrTimeUs) {
                            if (lastScrTimeUsInRange == -Long.MAX_VALUE) {
                                return TimestampSearchResult.overestimatedResult(scrTimeUs, bufferStartOffset);
                            }

                            return TimestampSearchResult.targetFoundResult(bufferStartOffset + (long)startOfLastPacketPosition);
                        }

                        if (scrTimeUs + 100000L > targetScrTimeUs) {
                            long startOfPacketInStream = bufferStartOffset + (long)packetBuffer.getPosition();
                            return TimestampSearchResult.targetFoundResult(startOfPacketInStream);
                        }

                        lastScrTimeUsInRange = scrTimeUs;
                        startOfLastPacketPosition = packetBuffer.getPosition();
                    }

                    skipToEndOfCurrentPack(packetBuffer);
                    endOfLastPacketPosition = packetBuffer.getPosition();
                }
            }

            if (lastScrTimeUsInRange != -Long.MAX_VALUE) {
                long endOfLastPacketPositionInStream = bufferStartOffset + (long)endOfLastPacketPosition;
                return TimestampSearchResult.underestimatedResult(lastScrTimeUsInRange, endOfLastPacketPositionInStream);
            } else {
                return TimestampSearchResult.NO_TIMESTAMP_IN_RANGE_RESULT;
            }
        }

        private static void skipToEndOfCurrentPack(ParsableByteArray packetBuffer) {
            int limit = packetBuffer.limit();
            if (packetBuffer.bytesLeft() < 10) {
                packetBuffer.setPosition(limit);
            } else {
                packetBuffer.skipBytes(9);
                int packStuffingLength = packetBuffer.readUnsignedByte() & 7;
                if (packetBuffer.bytesLeft() < packStuffingLength) {
                    packetBuffer.setPosition(limit);
                } else {
                    packetBuffer.skipBytes(packStuffingLength);
                    if (packetBuffer.bytesLeft() < 4) {
                        packetBuffer.setPosition(limit);
                    } else {
                        int nextStartCode = PsBinarySearchSeeker.peekIntAtPosition(packetBuffer.data, packetBuffer.getPosition());
                        int pesPacketLength;
                        if (nextStartCode == 443) {
                            packetBuffer.skipBytes(4);
                            pesPacketLength = packetBuffer.readUnsignedShort();
                            if (packetBuffer.bytesLeft() < pesPacketLength) {
                                packetBuffer.setPosition(limit);
                                return;
                            }

                            packetBuffer.skipBytes(pesPacketLength);
                        }

                        while(packetBuffer.bytesLeft() >= 4) {
                            nextStartCode = PsBinarySearchSeeker.peekIntAtPosition(packetBuffer.data, packetBuffer.getPosition());
                            if (nextStartCode == 442 || nextStartCode == 441 || nextStartCode >>> 8 != 1) {
                                break;
                            }

                            packetBuffer.skipBytes(4);
                            if (packetBuffer.bytesLeft() < 2) {
                                packetBuffer.setPosition(limit);
                                return;
                            }

                            pesPacketLength = packetBuffer.readUnsignedShort();
                            packetBuffer.setPosition(Math.min(packetBuffer.limit(), packetBuffer.getPosition() + pesPacketLength));
                        }

                    }
                }
            }
        }
    }
}
