package com.zj.playerLib.source;

import com.zj.playerLib.Format;
import com.zj.playerLib.FormatHolder;
import com.zj.playerLib.decoder.DecoderInputBuffer;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

final class SampleMetadataQueue {
    private static final int SAMPLE_CAPACITY_INCREMENT = 1000;
    private int capacity = 1000;
    private int[] sourceIds;
    private long[] offsets;
    private int[] sizes;
    private int[] flags;
    private long[] timesUs;
    private CryptoData[] cryptoDatas;
    private Format[] formats;
    private int length;
    private int absoluteFirstIndex;
    private int relativeFirstIndex;
    private int readPosition;
    private long largestDiscardedTimestampUs;
    private long largestQueuedTimestampUs;
    private boolean upstreamKeyframeRequired;
    private boolean upstreamFormatRequired;
    private Format upstreamFormat;
    private int upstreamSourceId;

    public SampleMetadataQueue() {
        this.sourceIds = new int[this.capacity];
        this.offsets = new long[this.capacity];
        this.timesUs = new long[this.capacity];
        this.flags = new int[this.capacity];
        this.sizes = new int[this.capacity];
        this.cryptoDatas = new CryptoData[this.capacity];
        this.formats = new Format[this.capacity];
        this.largestDiscardedTimestampUs = -9223372036854775808L;
        this.largestQueuedTimestampUs = -9223372036854775808L;
        this.upstreamFormatRequired = true;
        this.upstreamKeyframeRequired = true;
    }

    public void reset(boolean resetUpstreamFormat) {
        this.length = 0;
        this.absoluteFirstIndex = 0;
        this.relativeFirstIndex = 0;
        this.readPosition = 0;
        this.upstreamKeyframeRequired = true;
        this.largestDiscardedTimestampUs = -9223372036854775808L;
        this.largestQueuedTimestampUs = -9223372036854775808L;
        if (resetUpstreamFormat) {
            this.upstreamFormat = null;
            this.upstreamFormatRequired = true;
        }

    }

    public int getWriteIndex() {
        return this.absoluteFirstIndex + this.length;
    }

    public long discardUpstreamSamples(int discardFromIndex) {
        int discardCount = this.getWriteIndex() - discardFromIndex;
        Assertions.checkArgument(0 <= discardCount && discardCount <= this.length - this.readPosition);
        this.length -= discardCount;
        this.largestQueuedTimestampUs = Math.max(this.largestDiscardedTimestampUs, this.getLargestTimestamp(this.length));
        if (this.length == 0) {
            return 0L;
        } else {
            int relativeLastWriteIndex = this.getRelativeIndex(this.length - 1);
            return this.offsets[relativeLastWriteIndex] + (long)this.sizes[relativeLastWriteIndex];
        }
    }

    public void sourceId(int sourceId) {
        this.upstreamSourceId = sourceId;
    }

    public int getFirstIndex() {
        return this.absoluteFirstIndex;
    }

    public int getReadIndex() {
        return this.absoluteFirstIndex + this.readPosition;
    }

    public int peekSourceId() {
        int relativeReadIndex = this.getRelativeIndex(this.readPosition);
        return this.hasNextSample() ? this.sourceIds[relativeReadIndex] : this.upstreamSourceId;
    }

    public synchronized boolean hasNextSample() {
        return this.readPosition != this.length;
    }

    public synchronized Format getUpstreamFormat() {
        return this.upstreamFormatRequired ? null : this.upstreamFormat;
    }

    public synchronized long getLargestQueuedTimestampUs() {
        return this.largestQueuedTimestampUs;
    }

    public synchronized long getFirstTimestampUs() {
        return this.length == 0 ? -9223372036854775808L : this.timesUs[this.relativeFirstIndex];
    }

    public synchronized void rewind() {
        this.readPosition = 0;
    }

    public synchronized int read(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired, boolean loadingFinished, Format downstreamFormat, SampleExtrasHolder extrasHolder) {
        if (!this.hasNextSample()) {
            if (loadingFinished) {
                buffer.setFlags(4);
                return -4;
            } else if (this.upstreamFormat == null || !formatRequired && this.upstreamFormat == downstreamFormat) {
                return -3;
            } else {
                formatHolder.format = this.upstreamFormat;
                return -5;
            }
        } else {
            int relativeReadIndex = this.getRelativeIndex(this.readPosition);
            if (!formatRequired && this.formats[relativeReadIndex] == downstreamFormat) {
                if (buffer.isFlagsOnly()) {
                    return -3;
                } else {
                    buffer.timeUs = this.timesUs[relativeReadIndex];
                    buffer.setFlags(this.flags[relativeReadIndex]);
                    extrasHolder.size = this.sizes[relativeReadIndex];
                    extrasHolder.offset = this.offsets[relativeReadIndex];
                    extrasHolder.cryptoData = this.cryptoDatas[relativeReadIndex];
                    ++this.readPosition;
                    return -4;
                }
            } else {
                formatHolder.format = this.formats[relativeReadIndex];
                return -5;
            }
        }
    }

    public synchronized int advanceTo(long timeUs, boolean toKeyframe, boolean allowTimeBeyondBuffer) {
        int relativeReadIndex = this.getRelativeIndex(this.readPosition);
        if (this.hasNextSample() && timeUs >= this.timesUs[relativeReadIndex] && (timeUs <= this.largestQueuedTimestampUs || allowTimeBeyondBuffer)) {
            int offset = this.findSampleBefore(relativeReadIndex, this.length - this.readPosition, timeUs, toKeyframe);
            if (offset == -1) {
                return -1;
            } else {
                this.readPosition += offset;
                return offset;
            }
        } else {
            return -1;
        }
    }

    public synchronized int advanceToEnd() {
        int skipCount = this.length - this.readPosition;
        this.readPosition = this.length;
        return skipCount;
    }

    public synchronized boolean setReadPosition(int sampleIndex) {
        if (this.absoluteFirstIndex <= sampleIndex && sampleIndex <= this.absoluteFirstIndex + this.length) {
            this.readPosition = sampleIndex - this.absoluteFirstIndex;
            return true;
        } else {
            return false;
        }
    }

    public synchronized long discardTo(long timeUs, boolean toKeyframe, boolean stopAtReadPosition) {
        if (this.length != 0 && timeUs >= this.timesUs[this.relativeFirstIndex]) {
            int searchLength = stopAtReadPosition && this.readPosition != this.length ? this.readPosition + 1 : this.length;
            int discardCount = this.findSampleBefore(this.relativeFirstIndex, searchLength, timeUs, toKeyframe);
            return discardCount == -1 ? -1L : this.discardSamples(discardCount);
        } else {
            return -1L;
        }
    }

    public synchronized long discardToRead() {
        return this.readPosition == 0 ? -1L : this.discardSamples(this.readPosition);
    }

    public synchronized long discardToEnd() {
        return this.length == 0 ? -1L : this.discardSamples(this.length);
    }

    public synchronized boolean format(Format format) {
        if (format == null) {
            this.upstreamFormatRequired = true;
            return false;
        } else {
            this.upstreamFormatRequired = false;
            if (Util.areEqual(format, this.upstreamFormat)) {
                return false;
            } else {
                this.upstreamFormat = format;
                return true;
            }
        }
    }

    public synchronized void commitSample(long timeUs, int sampleFlags, long offset, int size, CryptoData cryptoData) {
        if (this.upstreamKeyframeRequired) {
            if ((sampleFlags & 1) == 0) {
                return;
            }

            this.upstreamKeyframeRequired = false;
        }

        Assertions.checkState(!this.upstreamFormatRequired);
        this.commitSampleTimestamp(timeUs);
        int relativeEndIndex = this.getRelativeIndex(this.length);
        this.timesUs[relativeEndIndex] = timeUs;
        this.offsets[relativeEndIndex] = offset;
        this.sizes[relativeEndIndex] = size;
        this.flags[relativeEndIndex] = sampleFlags;
        this.cryptoDatas[relativeEndIndex] = cryptoData;
        this.formats[relativeEndIndex] = this.upstreamFormat;
        this.sourceIds[relativeEndIndex] = this.upstreamSourceId;
        ++this.length;
        if (this.length == this.capacity) {
            int newCapacity = this.capacity + 1000;
            int[] newSourceIds = new int[newCapacity];
            long[] newOffsets = new long[newCapacity];
            long[] newTimesUs = new long[newCapacity];
            int[] newFlags = new int[newCapacity];
            int[] newSizes = new int[newCapacity];
            CryptoData[] newCryptoDatas = new CryptoData[newCapacity];
            Format[] newFormats = new Format[newCapacity];
            int beforeWrap = this.capacity - this.relativeFirstIndex;
            System.arraycopy(this.offsets, this.relativeFirstIndex, newOffsets, 0, beforeWrap);
            System.arraycopy(this.timesUs, this.relativeFirstIndex, newTimesUs, 0, beforeWrap);
            System.arraycopy(this.flags, this.relativeFirstIndex, newFlags, 0, beforeWrap);
            System.arraycopy(this.sizes, this.relativeFirstIndex, newSizes, 0, beforeWrap);
            System.arraycopy(this.cryptoDatas, this.relativeFirstIndex, newCryptoDatas, 0, beforeWrap);
            System.arraycopy(this.formats, this.relativeFirstIndex, newFormats, 0, beforeWrap);
            System.arraycopy(this.sourceIds, this.relativeFirstIndex, newSourceIds, 0, beforeWrap);
            int afterWrap = this.relativeFirstIndex;
            System.arraycopy(this.offsets, 0, newOffsets, beforeWrap, afterWrap);
            System.arraycopy(this.timesUs, 0, newTimesUs, beforeWrap, afterWrap);
            System.arraycopy(this.flags, 0, newFlags, beforeWrap, afterWrap);
            System.arraycopy(this.sizes, 0, newSizes, beforeWrap, afterWrap);
            System.arraycopy(this.cryptoDatas, 0, newCryptoDatas, beforeWrap, afterWrap);
            System.arraycopy(this.formats, 0, newFormats, beforeWrap, afterWrap);
            System.arraycopy(this.sourceIds, 0, newSourceIds, beforeWrap, afterWrap);
            this.offsets = newOffsets;
            this.timesUs = newTimesUs;
            this.flags = newFlags;
            this.sizes = newSizes;
            this.cryptoDatas = newCryptoDatas;
            this.formats = newFormats;
            this.sourceIds = newSourceIds;
            this.relativeFirstIndex = 0;
            this.length = this.capacity;
            this.capacity = newCapacity;
        }

    }

    public synchronized void commitSampleTimestamp(long timeUs) {
        this.largestQueuedTimestampUs = Math.max(this.largestQueuedTimestampUs, timeUs);
    }

    public synchronized boolean attemptSplice(long timeUs) {
        if (this.length == 0) {
            return timeUs > this.largestDiscardedTimestampUs;
        } else {
            long largestReadTimestampUs = Math.max(this.largestDiscardedTimestampUs, this.getLargestTimestamp(this.readPosition));
            if (largestReadTimestampUs >= timeUs) {
                return false;
            } else {
                int retainCount = this.length;
                int relativeSampleIndex = this.getRelativeIndex(this.length - 1);

                while(retainCount > this.readPosition && this.timesUs[relativeSampleIndex] >= timeUs) {
                    --retainCount;
                    --relativeSampleIndex;
                    if (relativeSampleIndex == -1) {
                        relativeSampleIndex = this.capacity - 1;
                    }
                }

                this.discardUpstreamSamples(this.absoluteFirstIndex + retainCount);
                return true;
            }
        }
    }

    private int findSampleBefore(int relativeStartIndex, int length, long timeUs, boolean keyframe) {
        int sampleCountToTarget = -1;
        int searchIndex = relativeStartIndex;

        for(int i = 0; i < length && this.timesUs[searchIndex] <= timeUs; ++i) {
            if (!keyframe || (this.flags[searchIndex] & 1) != 0) {
                sampleCountToTarget = i;
            }

            ++searchIndex;
            if (searchIndex == this.capacity) {
                searchIndex = 0;
            }
        }

        return sampleCountToTarget;
    }

    private long discardSamples(int discardCount) {
        this.largestDiscardedTimestampUs = Math.max(this.largestDiscardedTimestampUs, this.getLargestTimestamp(discardCount));
        this.length -= discardCount;
        this.absoluteFirstIndex += discardCount;
        this.relativeFirstIndex += discardCount;
        if (this.relativeFirstIndex >= this.capacity) {
            this.relativeFirstIndex -= this.capacity;
        }

        this.readPosition -= discardCount;
        if (this.readPosition < 0) {
            this.readPosition = 0;
        }

        if (this.length == 0) {
            int relativeLastDiscardIndex = (this.relativeFirstIndex == 0 ? this.capacity : this.relativeFirstIndex) - 1;
            return this.offsets[relativeLastDiscardIndex] + (long)this.sizes[relativeLastDiscardIndex];
        } else {
            return this.offsets[this.relativeFirstIndex];
        }
    }

    private long getLargestTimestamp(int length) {
        if (length == 0) {
            return -9223372036854775808L;
        } else {
            long largestTimestampUs = -9223372036854775808L;
            int relativeSampleIndex = this.getRelativeIndex(length - 1);

            for(int i = 0; i < length; ++i) {
                largestTimestampUs = Math.max(largestTimestampUs, this.timesUs[relativeSampleIndex]);
                if ((this.flags[relativeSampleIndex] & 1) != 0) {
                    break;
                }

                --relativeSampleIndex;
                if (relativeSampleIndex == -1) {
                    relativeSampleIndex = this.capacity - 1;
                }
            }

            return largestTimestampUs;
        }
    }

    private int getRelativeIndex(int offset) {
        int relativeIndex = this.relativeFirstIndex + offset;
        return relativeIndex < this.capacity ? relativeIndex : relativeIndex - this.capacity;
    }

    public static final class SampleExtrasHolder {
        public int size;
        public long offset;
        public CryptoData cryptoData;

        public SampleExtrasHolder() {
        }
    }
}
