package com.zj.playerLib.extractor.ogg;

import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.SeekMap;
import com.zj.playerLib.extractor.SeekPoint;
import com.zj.playerLib.util.Assertions;

import java.io.EOFException;
import java.io.IOException;

final class DefaultOggSeeker implements OggSeeker {
    public static final int MATCH_RANGE = 72000;
    public static final int MATCH_BYTE_RANGE = 100000;
    private static final int DEFAULT_OFFSET = 30000;
    private static final int STATE_SEEK_TO_END = 0;
    private static final int STATE_READ_LAST_PAGE = 1;
    private static final int STATE_SEEK = 2;
    private static final int STATE_IDLE = 3;
    private final OggPageHeader pageHeader = new OggPageHeader();
    private final long startPosition;
    private final long endPosition;
    private final StreamReader streamReader;
    private int state;
    private long totalGranules;
    private long positionBeforeSeekToEnd;
    private long targetGranule;
    private long start;
    private long end;
    private long startGranule;
    private long endGranule;

    public DefaultOggSeeker(long startPosition, long endPosition, StreamReader streamReader, long firstPayloadPageSize, long firstPayloadPageGranulePosition, boolean firstPayloadPageIsLastPage) {
        Assertions.checkArgument(startPosition >= 0L && endPosition > startPosition);
        this.streamReader = streamReader;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        if (firstPayloadPageSize != endPosition - startPosition && !firstPayloadPageIsLastPage) {
            this.state = 0;
        } else {
            this.totalGranules = firstPayloadPageGranulePosition;
            this.state = 3;
        }

    }

    public long read(ExtractorInput input) throws IOException, InterruptedException {
        switch(this.state) {
        case 0:
            this.positionBeforeSeekToEnd = input.getPosition();
            this.state = 1;
            long lastPageSearchPosition = this.endPosition - 65307L;
            if (lastPageSearchPosition > this.positionBeforeSeekToEnd) {
                return lastPageSearchPosition;
            }
        case 1:
            this.totalGranules = this.readGranuleOfLastPage(input);
            this.state = 3;
            return this.positionBeforeSeekToEnd;
        case 2:
            long currentGranule;
            if (this.targetGranule == 0L) {
                currentGranule = 0L;
            } else {
                long position = this.getNextSeekPosition(this.targetGranule, input);
                if (position >= 0L) {
                    return position;
                }

                currentGranule = this.skipToPageOfGranule(input, this.targetGranule, -(position + 2L));
            }

            this.state = 3;
            return -(currentGranule + 2L);
        case 3:
            return -1L;
        default:
            throw new IllegalStateException();
        }
    }

    public long startSeek(long timeUs) {
        Assertions.checkArgument(this.state == 3 || this.state == 2);
        this.targetGranule = timeUs == 0L ? 0L : this.streamReader.convertTimeToGranule(timeUs);
        this.state = 2;
        this.resetSeeking();
        return this.targetGranule;
    }

    public OggSeekMap createSeekMap() {
        return this.totalGranules != 0L ? new OggSeekMap() : null;
    }

    public void resetSeeking() {
        this.start = this.startPosition;
        this.end = this.endPosition;
        this.startGranule = 0L;
        this.endGranule = this.totalGranules;
    }

    public long getNextSeekPosition(long targetGranule, ExtractorInput input) throws IOException, InterruptedException {
        if (this.start == this.end) {
            return -(this.startGranule + 2L);
        } else {
            long initialPosition = input.getPosition();
            if (!this.skipToNextPage(input, this.end)) {
                if (this.start == initialPosition) {
                    throw new IOException("No ogg page can be found.");
                } else {
                    return this.start;
                }
            } else {
                this.pageHeader.populate(input, false);
                input.resetPeekPosition();
                long granuleDistance = targetGranule - this.pageHeader.granulePosition;
                int pageSize = this.pageHeader.headerSize + this.pageHeader.bodySize;
                if (granuleDistance >= 0L && granuleDistance <= 72000L) {
                    input.skipFully(pageSize);
                    return -(this.pageHeader.granulePosition + 2L);
                } else {
                    if (granuleDistance < 0L) {
                        this.end = initialPosition;
                        this.endGranule = this.pageHeader.granulePosition;
                    } else {
                        this.start = input.getPosition() + (long)pageSize;
                        this.startGranule = this.pageHeader.granulePosition;
                        if (this.end - this.start + (long)pageSize < 100000L) {
                            input.skipFully(pageSize);
                            return -(this.startGranule + 2L);
                        }
                    }

                    if (this.end - this.start < 100000L) {
                        this.end = this.start;
                        return this.start;
                    } else {
                        long offset = (long)pageSize * (granuleDistance <= 0L ? 2L : 1L);
                        long nextPosition = input.getPosition() - offset + granuleDistance * (this.end - this.start) / (this.endGranule - this.startGranule);
                        nextPosition = Math.max(nextPosition, this.start);
                        nextPosition = Math.min(nextPosition, this.end - 1L);
                        return nextPosition;
                    }
                }
            }
        }
    }

    private long getEstimatedPosition(long position, long granuleDistance, long offset) {
        position += granuleDistance * (this.endPosition - this.startPosition) / this.totalGranules - offset;
        if (position < this.startPosition) {
            position = this.startPosition;
        }

        if (position >= this.endPosition) {
            position = this.endPosition - 1L;
        }

        return position;
    }

    void skipToNextPage(ExtractorInput input) throws IOException, InterruptedException {
        if (!this.skipToNextPage(input, this.endPosition)) {
            throw new EOFException();
        }
    }

    boolean skipToNextPage(ExtractorInput input, long limit) throws IOException, InterruptedException {
        limit = Math.min(limit + 3L, this.endPosition);
        byte[] buffer = new byte[2048];
        int peekLength = buffer.length;

        while(true) {
            if (input.getPosition() + (long)peekLength > limit) {
                peekLength = (int)(limit - input.getPosition());
                if (peekLength < 4) {
                    return false;
                }
            }

            input.peekFully(buffer, 0, peekLength, false);

            for(int i = 0; i < peekLength - 3; ++i) {
                if (buffer[i] == 79 && buffer[i + 1] == 103 && buffer[i + 2] == 103 && buffer[i + 3] == 83) {
                    input.skipFully(i);
                    return true;
                }
            }

            input.skipFully(peekLength - 3);
        }
    }

    long readGranuleOfLastPage(ExtractorInput input) throws IOException, InterruptedException {
        this.skipToNextPage(input);
        this.pageHeader.reset();

        while((this.pageHeader.type & 4) != 4 && input.getPosition() < this.endPosition) {
            this.pageHeader.populate(input, false);
            input.skipFully(this.pageHeader.headerSize + this.pageHeader.bodySize);
        }

        return this.pageHeader.granulePosition;
    }

    long skipToPageOfGranule(ExtractorInput input, long targetGranule, long currentGranule) throws IOException, InterruptedException {
        this.pageHeader.populate(input, false);

        while(this.pageHeader.granulePosition < targetGranule) {
            input.skipFully(this.pageHeader.headerSize + this.pageHeader.bodySize);
            currentGranule = this.pageHeader.granulePosition;
            this.pageHeader.populate(input, false);
        }

        input.resetPeekPosition();
        return currentGranule;
    }

    private class OggSeekMap implements SeekMap {
        private OggSeekMap() {
        }

        public boolean isSeekable() {
            return true;
        }

        public SeekPoints getSeekPoints(long timeUs) {
            if (timeUs == 0L) {
                return new SeekPoints(new SeekPoint(0L, DefaultOggSeeker.this.startPosition));
            } else {
                long granule = DefaultOggSeeker.this.streamReader.convertTimeToGranule(timeUs);
                long estimatedPosition = DefaultOggSeeker.this.getEstimatedPosition(DefaultOggSeeker.this.startPosition, granule, 30000L);
                return new SeekPoints(new SeekPoint(timeUs, estimatedPosition));
            }
        }

        public long getDurationUs() {
            return DefaultOggSeeker.this.streamReader.convertGranuleToTime(DefaultOggSeeker.this.totalGranules);
        }
    }
}
