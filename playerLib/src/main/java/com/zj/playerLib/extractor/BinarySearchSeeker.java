package com.zj.playerLib.extractor;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class BinarySearchSeeker {
    private static final long MAX_SKIP_BYTES = 262144L;
    protected final BinarySearchSeekMap seekMap;
    protected final TimestampSeeker timestampSeeker;
    @Nullable
    protected BinarySearchSeeker.SeekOperationParams seekOperationParams;
    private final int minimumSearchRange;

    protected BinarySearchSeeker(SeekTimestampConverter seekTimestampConverter, TimestampSeeker timestampSeeker, long durationUs, long floorTimePosition, long ceilingTimePosition, long floorBytePosition, long ceilingBytePosition, long approxBytesPerFrame, int minimumSearchRange) {
        this.timestampSeeker = timestampSeeker;
        this.minimumSearchRange = minimumSearchRange;
        this.seekMap = new BinarySearchSeekMap(seekTimestampConverter, durationUs, floorTimePosition, ceilingTimePosition, floorBytePosition, ceilingBytePosition, approxBytesPerFrame);
    }

    public final SeekMap getSeekMap() {
        return this.seekMap;
    }

    public final void setSeekTargetUs(long timeUs) {
        if (this.seekOperationParams == null || this.seekOperationParams.getSeekTimeUs() != timeUs) {
            this.seekOperationParams = this.createSeekParamsForTargetTimeUs(timeUs);
        }
    }

    public final boolean isSeeking() {
        return this.seekOperationParams != null;
    }

    public int handlePendingSeek(ExtractorInput input, PositionHolder seekPositionHolder, OutputFrameHolder outputFrameHolder) throws InterruptedException, IOException {
        TimestampSeeker timestampSeeker = Assertions.checkNotNull(this.timestampSeeker);

        while(true) {
            SeekOperationParams seekOperationParams = Assertions.checkNotNull(this.seekOperationParams);
            long floorPosition = seekOperationParams.getFloorBytePosition();
            long ceilingPosition = seekOperationParams.getCeilingBytePosition();
            long searchPosition = seekOperationParams.getNextSearchBytePosition();
            if (ceilingPosition - floorPosition <= (long)this.minimumSearchRange) {
                this.markSeekOperationFinished(false, floorPosition);
                return this.seekToPosition(input, floorPosition, seekPositionHolder);
            }

            if (!this.skipInputUntilPosition(input, searchPosition)) {
                return this.seekToPosition(input, searchPosition, seekPositionHolder);
            }

            input.resetPeekPosition();
            TimestampSearchResult timestampSearchResult = timestampSeeker.searchForTimestamp(input, seekOperationParams.getTargetTimePosition(), outputFrameHolder);
            switch(timestampSearchResult.type) {
            case -3:
                this.markSeekOperationFinished(false, searchPosition);
                return this.seekToPosition(input, searchPosition, seekPositionHolder);
            case -2:
                seekOperationParams.updateSeekFloor(timestampSearchResult.timestampToUpdate, timestampSearchResult.bytePositionToUpdate);
                break;
            case -1:
                seekOperationParams.updateSeekCeiling(timestampSearchResult.timestampToUpdate, timestampSearchResult.bytePositionToUpdate);
                break;
            case 0:
                this.markSeekOperationFinished(true, timestampSearchResult.bytePositionToUpdate);
                this.skipInputUntilPosition(input, timestampSearchResult.bytePositionToUpdate);
                return this.seekToPosition(input, timestampSearchResult.bytePositionToUpdate, seekPositionHolder);
            default:
                throw new IllegalStateException("Invalid case");
            }
        }
    }

    protected SeekOperationParams createSeekParamsForTargetTimeUs(long timeUs) {
        return new SeekOperationParams(timeUs, this.seekMap.timeUsToTargetTime(timeUs), this.seekMap.floorTimePosition, this.seekMap.ceilingTimePosition, this.seekMap.floorBytePosition, this.seekMap.ceilingBytePosition, this.seekMap.approxBytesPerFrame);
    }

    protected final void markSeekOperationFinished(boolean foundTargetFrame, long resultPosition) {
        this.seekOperationParams = null;
        this.timestampSeeker.onSeekFinished();
        this.onSeekOperationFinished(foundTargetFrame, resultPosition);
    }

    protected void onSeekOperationFinished(boolean foundTargetFrame, long resultPosition) {
    }

    protected final boolean skipInputUntilPosition(ExtractorInput input, long position) throws IOException, InterruptedException {
        long bytesToSkip = position - input.getPosition();
        if (bytesToSkip >= 0L && bytesToSkip <= 262144L) {
            input.skipFully((int)bytesToSkip);
            return true;
        } else {
            return false;
        }
    }

    protected final int seekToPosition(ExtractorInput input, long position, PositionHolder seekPositionHolder) {
        if (position == input.getPosition()) {
            return 0;
        } else {
            seekPositionHolder.position = position;
            return 1;
        }
    }

    public static class BinarySearchSeekMap implements SeekMap {
        private final SeekTimestampConverter seekTimestampConverter;
        private final long durationUs;
        private final long floorTimePosition;
        private final long ceilingTimePosition;
        private final long floorBytePosition;
        private final long ceilingBytePosition;
        private final long approxBytesPerFrame;

        public BinarySearchSeekMap(SeekTimestampConverter seekTimestampConverter, long durationUs, long floorTimePosition, long ceilingTimePosition, long floorBytePosition, long ceilingBytePosition, long approxBytesPerFrame) {
            this.seekTimestampConverter = seekTimestampConverter;
            this.durationUs = durationUs;
            this.floorTimePosition = floorTimePosition;
            this.ceilingTimePosition = ceilingTimePosition;
            this.floorBytePosition = floorBytePosition;
            this.ceilingBytePosition = ceilingBytePosition;
            this.approxBytesPerFrame = approxBytesPerFrame;
        }

        public boolean isSeekable() {
            return true;
        }

        public SeekPoints getSeekPoints(long timeUs) {
            long nextSearchPosition = SeekOperationParams.calculateNextSearchBytePosition(this.seekTimestampConverter.timeUsToTargetTime(timeUs), this.floorTimePosition, this.ceilingTimePosition, this.floorBytePosition, this.ceilingBytePosition, this.approxBytesPerFrame);
            return new SeekPoints(new SeekPoint(timeUs, nextSearchPosition));
        }

        public long getDurationUs() {
            return this.durationUs;
        }

        public long timeUsToTargetTime(long timeUs) {
            return this.seekTimestampConverter.timeUsToTargetTime(timeUs);
        }
    }

    public static final class TimestampSearchResult {
        public static final int TYPE_TARGET_TIMESTAMP_FOUND = 0;
        public static final int TYPE_POSITION_OVERESTIMATED = -1;
        public static final int TYPE_POSITION_UNDERESTIMATED = -2;
        public static final int TYPE_NO_TIMESTAMP = -3;
        public static final TimestampSearchResult NO_TIMESTAMP_IN_RANGE_RESULT = new TimestampSearchResult(-3, -Long.MAX_VALUE, -1L);
        private final int type;
        private final long timestampToUpdate;
        private final long bytePositionToUpdate;

        private TimestampSearchResult(int type, long timestampToUpdate, long bytePositionToUpdate) {
            this.type = type;
            this.timestampToUpdate = timestampToUpdate;
            this.bytePositionToUpdate = bytePositionToUpdate;
        }

        public static TimestampSearchResult overestimatedResult(long newCeilingTimestamp, long newCeilingBytePosition) {
            return new TimestampSearchResult(-1, newCeilingTimestamp, newCeilingBytePosition);
        }

        public static TimestampSearchResult underestimatedResult(long newFloorTimestamp, long newCeilingBytePosition) {
            return new TimestampSearchResult(-2, newFloorTimestamp, newCeilingBytePosition);
        }

        public static TimestampSearchResult targetFoundResult(long resultBytePosition) {
            return new TimestampSearchResult(0, -Long.MAX_VALUE, resultBytePosition);
        }
    }

    protected static class SeekOperationParams {
        private final long seekTimeUs;
        private final long targetTimePosition;
        private final long approxBytesPerFrame;
        private long floorTimePosition;
        private long ceilingTimePosition;
        private long floorBytePosition;
        private long ceilingBytePosition;
        private long nextSearchBytePosition;

        protected static long calculateNextSearchBytePosition(long targetTimePosition, long floorTimePosition, long ceilingTimePosition, long floorBytePosition, long ceilingBytePosition, long approxBytesPerFrame) {
            if (floorBytePosition + 1L < ceilingBytePosition && floorTimePosition + 1L < ceilingTimePosition) {
                long seekTimeDuration = targetTimePosition - floorTimePosition;
                float estimatedBytesPerTimeUnit = (float)(ceilingBytePosition - floorBytePosition) / (float)(ceilingTimePosition - floorTimePosition);
                long bytesToSkip = (long)((float)seekTimeDuration * estimatedBytesPerTimeUnit);
                long confidenceInterval = bytesToSkip / 20L;
                long estimatedFramePosition = floorBytePosition + bytesToSkip - approxBytesPerFrame;
                long estimatedPosition = estimatedFramePosition - confidenceInterval;
                return Util.constrainValue(estimatedPosition, floorBytePosition, ceilingBytePosition - 1L);
            } else {
                return floorBytePosition;
            }
        }

        protected SeekOperationParams(long seekTimeUs, long targetTimePosition, long floorTimePosition, long ceilingTimePosition, long floorBytePosition, long ceilingBytePosition, long approxBytesPerFrame) {
            this.seekTimeUs = seekTimeUs;
            this.targetTimePosition = targetTimePosition;
            this.floorTimePosition = floorTimePosition;
            this.ceilingTimePosition = ceilingTimePosition;
            this.floorBytePosition = floorBytePosition;
            this.ceilingBytePosition = ceilingBytePosition;
            this.approxBytesPerFrame = approxBytesPerFrame;
            this.nextSearchBytePosition = calculateNextSearchBytePosition(targetTimePosition, floorTimePosition, ceilingTimePosition, floorBytePosition, ceilingBytePosition, approxBytesPerFrame);
        }

        private long getFloorBytePosition() {
            return this.floorBytePosition;
        }

        private long getCeilingBytePosition() {
            return this.ceilingBytePosition;
        }

        private long getTargetTimePosition() {
            return this.targetTimePosition;
        }

        private long getSeekTimeUs() {
            return this.seekTimeUs;
        }

        private void updateSeekFloor(long floorTimePosition, long floorBytePosition) {
            this.floorTimePosition = floorTimePosition;
            this.floorBytePosition = floorBytePosition;
            this.updateNextSearchBytePosition();
        }

        private void updateSeekCeiling(long ceilingTimePosition, long ceilingBytePosition) {
            this.ceilingTimePosition = ceilingTimePosition;
            this.ceilingBytePosition = ceilingBytePosition;
            this.updateNextSearchBytePosition();
        }

        private long getNextSearchBytePosition() {
            return this.nextSearchBytePosition;
        }

        private void updateNextSearchBytePosition() {
            this.nextSearchBytePosition = calculateNextSearchBytePosition(this.targetTimePosition, this.floorTimePosition, this.ceilingTimePosition, this.floorBytePosition, this.ceilingBytePosition, this.approxBytesPerFrame);
        }
    }

    protected interface SeekTimestampConverter {
        long timeUsToTargetTime(long var1);
    }

    public static final class DefaultSeekTimestampConverter implements SeekTimestampConverter {
        public DefaultSeekTimestampConverter() {
        }

        public long timeUsToTargetTime(long timeUs) {
            return timeUs;
        }
    }

    public static final class OutputFrameHolder {
        public long timeUs = 0L;
        public ByteBuffer byteBuffer;

        public OutputFrameHolder(ByteBuffer outputByteBuffer) {
            this.byteBuffer = outputByteBuffer;
        }
    }

    protected interface TimestampSeeker {
        TimestampSearchResult searchForTimestamp(ExtractorInput var1, long var2, OutputFrameHolder var4) throws IOException, InterruptedException;

        default void onSeekFinished() {
        }
    }
}
