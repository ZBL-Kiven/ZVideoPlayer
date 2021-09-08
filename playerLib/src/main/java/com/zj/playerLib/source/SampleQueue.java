//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source;

import androidx.annotation.Nullable;
import com.zj.playerLib.Format;
import com.zj.playerLib.FormatHolder;
import com.zj.playerLib.decoder.DecoderInputBuffer;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.source.SampleMetadataQueue.SampleExtrasHolder;
import com.zj.playerLib.upstream.Allocation;
import com.zj.playerLib.upstream.Allocator;
import com.zj.playerLib.util.ParsableByteArray;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;

public class SampleQueue implements TrackOutput {
    public static final int ADVANCE_FAILED = -1;
    private static final int INITIAL_SCRATCH_SIZE = 32;
    private final Allocator allocator;
    private final int allocationLength;
    private final SampleMetadataQueue metadataQueue;
    private final SampleExtrasHolder extrasHolder;
    private final ParsableByteArray scratch;
    private AllocationNode firstAllocationNode;
    private AllocationNode readAllocationNode;
    private AllocationNode writeAllocationNode;
    private Format downstreamFormat;
    private boolean pendingFormatAdjustment;
    private Format lastUnadjustedFormat;
    private long sampleOffsetUs;
    private long totalBytesWritten;
    private boolean pendingSplice;
    private UpstreamFormatChangedListener upstreamFormatChangeListener;

    public SampleQueue(Allocator allocator) {
        this.allocator = allocator;
        this.allocationLength = allocator.getIndividualAllocationLength();
        this.metadataQueue = new SampleMetadataQueue();
        this.extrasHolder = new SampleExtrasHolder();
        this.scratch = new ParsableByteArray(32);
        this.firstAllocationNode = new AllocationNode(0L, this.allocationLength);
        this.readAllocationNode = this.firstAllocationNode;
        this.writeAllocationNode = this.firstAllocationNode;
    }

    public void reset() {
        this.reset(false);
    }

    public void reset(boolean resetUpstreamFormat) {
        this.metadataQueue.reset(resetUpstreamFormat);
        this.clearAllocationNodes(this.firstAllocationNode);
        this.firstAllocationNode = new AllocationNode(0L, this.allocationLength);
        this.readAllocationNode = this.firstAllocationNode;
        this.writeAllocationNode = this.firstAllocationNode;
        this.totalBytesWritten = 0L;
        this.allocator.trim();
    }

    public void sourceId(int sourceId) {
        this.metadataQueue.sourceId(sourceId);
    }

    public void splice() {
        this.pendingSplice = true;
    }

    public int getWriteIndex() {
        return this.metadataQueue.getWriteIndex();
    }

    public void discardUpstreamSamples(int discardFromIndex) {
        this.totalBytesWritten = this.metadataQueue.discardUpstreamSamples(discardFromIndex);
        if (this.totalBytesWritten != 0L && this.totalBytesWritten != this.firstAllocationNode.startPosition) {
            AllocationNode lastNodeToKeep;
            for(lastNodeToKeep = this.firstAllocationNode; this.totalBytesWritten > lastNodeToKeep.endPosition; lastNodeToKeep = lastNodeToKeep.next) {
            }

            AllocationNode firstNodeToDiscard = lastNodeToKeep.next;
            this.clearAllocationNodes(firstNodeToDiscard);
            lastNodeToKeep.next = new AllocationNode(lastNodeToKeep.endPosition, this.allocationLength);
            this.writeAllocationNode = this.totalBytesWritten == lastNodeToKeep.endPosition ? lastNodeToKeep.next : lastNodeToKeep;
            if (this.readAllocationNode == firstNodeToDiscard) {
                this.readAllocationNode = lastNodeToKeep.next;
            }
        } else {
            this.clearAllocationNodes(this.firstAllocationNode);
            this.firstAllocationNode = new AllocationNode(this.totalBytesWritten, this.allocationLength);
            this.readAllocationNode = this.firstAllocationNode;
            this.writeAllocationNode = this.firstAllocationNode;
        }

    }

    public boolean hasNextSample() {
        return this.metadataQueue.hasNextSample();
    }

    public int getFirstIndex() {
        return this.metadataQueue.getFirstIndex();
    }

    public int getReadIndex() {
        return this.metadataQueue.getReadIndex();
    }

    public int peekSourceId() {
        return this.metadataQueue.peekSourceId();
    }

    public Format getUpstreamFormat() {
        return this.metadataQueue.getUpstreamFormat();
    }

    public long getLargestQueuedTimestampUs() {
        return this.metadataQueue.getLargestQueuedTimestampUs();
    }

    public long getFirstTimestampUs() {
        return this.metadataQueue.getFirstTimestampUs();
    }

    public void rewind() {
        this.metadataQueue.rewind();
        this.readAllocationNode = this.firstAllocationNode;
    }

    public void discardTo(long timeUs, boolean toKeyframe, boolean stopAtReadPosition) {
        this.discardDownstreamTo(this.metadataQueue.discardTo(timeUs, toKeyframe, stopAtReadPosition));
    }

    public void discardToRead() {
        this.discardDownstreamTo(this.metadataQueue.discardToRead());
    }

    public void discardToEnd() {
        this.discardDownstreamTo(this.metadataQueue.discardToEnd());
    }

    public int advanceToEnd() {
        return this.metadataQueue.advanceToEnd();
    }

    public int advanceTo(long timeUs, boolean toKeyframe, boolean allowTimeBeyondBuffer) {
        return this.metadataQueue.advanceTo(timeUs, toKeyframe, allowTimeBeyondBuffer);
    }

    public boolean setReadPosition(int sampleIndex) {
        return this.metadataQueue.setReadPosition(sampleIndex);
    }

    public int read(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired, boolean loadingFinished, long decodeOnlyUntilUs) {
        int result = this.metadataQueue.read(formatHolder, buffer, formatRequired, loadingFinished, this.downstreamFormat, this.extrasHolder);
        switch(result) {
        case -5:
            this.downstreamFormat = formatHolder.format;
            return -5;
        case -4:
            if (!buffer.isEndOfStream()) {
                if (buffer.timeUs < decodeOnlyUntilUs) {
                    buffer.addFlag(-2147483648);
                }

                if (buffer.isEncrypted()) {
                    this.readEncryptionData(buffer, this.extrasHolder);
                }

                buffer.ensureSpaceForWrite(this.extrasHolder.size);
                this.readData(this.extrasHolder.offset, buffer.data, this.extrasHolder.size);
            }

            return -4;
        case -3:
            return -3;
        default:
            throw new IllegalStateException();
        }
    }

    private void readEncryptionData(DecoderInputBuffer buffer, SampleExtrasHolder extrasHolder) {
        long offset = extrasHolder.offset;
        this.scratch.reset(1);
        this.readData(offset, (byte[])this.scratch.data, 1);
        ++offset;
        byte signalByte = this.scratch.data[0];
        boolean subsampleEncryption = (signalByte & 128) != 0;
        int ivSize = signalByte & 127;
        if (buffer.cryptoInfo.iv == null) {
            buffer.cryptoInfo.iv = new byte[16];
        }

        this.readData(offset, buffer.cryptoInfo.iv, ivSize);
        offset += (long)ivSize;
        int subsampleCount;
        if (subsampleEncryption) {
            this.scratch.reset(2);
            this.readData(offset, (byte[])this.scratch.data, 2);
            offset += 2L;
            subsampleCount = this.scratch.readUnsignedShort();
        } else {
            subsampleCount = 1;
        }

        int[] clearDataSizes = buffer.cryptoInfo.numBytesOfClearData;
        if (clearDataSizes == null || clearDataSizes.length < subsampleCount) {
            clearDataSizes = new int[subsampleCount];
        }

        int[] encryptedDataSizes = buffer.cryptoInfo.numBytesOfEncryptedData;
        if (encryptedDataSizes == null || encryptedDataSizes.length < subsampleCount) {
            encryptedDataSizes = new int[subsampleCount];
        }

        int i;
        if (subsampleEncryption) {
            int subsampleDataLength = 6 * subsampleCount;
            this.scratch.reset(subsampleDataLength);
            this.readData(offset, this.scratch.data, subsampleDataLength);
            offset += (long)subsampleDataLength;
            this.scratch.setPosition(0);

            for(i = 0; i < subsampleCount; ++i) {
                clearDataSizes[i] = this.scratch.readUnsignedShort();
                encryptedDataSizes[i] = this.scratch.readUnsignedIntToInt();
            }
        } else {
            clearDataSizes[0] = 0;
            encryptedDataSizes[0] = extrasHolder.size - (int)(offset - extrasHolder.offset);
        }

        CryptoData cryptoData = extrasHolder.cryptoData;
        buffer.cryptoInfo.set(subsampleCount, clearDataSizes, encryptedDataSizes, cryptoData.encryptionKey, buffer.cryptoInfo.iv, cryptoData.cryptoMode, cryptoData.encryptedBlocks, cryptoData.clearBlocks);
        i = (int)(offset - extrasHolder.offset);
        extrasHolder.offset += (long)i;
        extrasHolder.size -= i;
    }

    private void readData(long absolutePosition, ByteBuffer target, int length) {
        this.advanceReadTo(absolutePosition);
        int remaining = length;

        while(remaining > 0) {
            int toCopy = Math.min(remaining, (int)(this.readAllocationNode.endPosition - absolutePosition));
            Allocation allocation = this.readAllocationNode.allocation;
            target.put(allocation.data, this.readAllocationNode.translateOffset(absolutePosition), toCopy);
            remaining -= toCopy;
            absolutePosition += (long)toCopy;
            if (absolutePosition == this.readAllocationNode.endPosition) {
                this.readAllocationNode = this.readAllocationNode.next;
            }
        }

    }

    private void readData(long absolutePosition, byte[] target, int length) {
        this.advanceReadTo(absolutePosition);
        int remaining = length;

        while(remaining > 0) {
            int toCopy = Math.min(remaining, (int)(this.readAllocationNode.endPosition - absolutePosition));
            Allocation allocation = this.readAllocationNode.allocation;
            System.arraycopy(allocation.data, this.readAllocationNode.translateOffset(absolutePosition), target, length - remaining, toCopy);
            remaining -= toCopy;
            absolutePosition += (long)toCopy;
            if (absolutePosition == this.readAllocationNode.endPosition) {
                this.readAllocationNode = this.readAllocationNode.next;
            }
        }

    }

    private void advanceReadTo(long absolutePosition) {
        while(absolutePosition >= this.readAllocationNode.endPosition) {
            this.readAllocationNode = this.readAllocationNode.next;
        }

    }

    private void discardDownstreamTo(long absolutePosition) {
        if (absolutePosition != -1L) {
            while(absolutePosition >= this.firstAllocationNode.endPosition) {
                this.allocator.release(this.firstAllocationNode.allocation);
                this.firstAllocationNode = this.firstAllocationNode.clear();
            }

            if (this.readAllocationNode.startPosition < this.firstAllocationNode.startPosition) {
                this.readAllocationNode = this.firstAllocationNode;
            }

        }
    }

    public void setUpstreamFormatChangeListener(UpstreamFormatChangedListener listener) {
        this.upstreamFormatChangeListener = listener;
    }

    public void setSampleOffsetUs(long sampleOffsetUs) {
        if (this.sampleOffsetUs != sampleOffsetUs) {
            this.sampleOffsetUs = sampleOffsetUs;
            this.pendingFormatAdjustment = true;
        }

    }

    public void format(Format format) {
        Format adjustedFormat = getAdjustedSampleFormat(format, this.sampleOffsetUs);
        boolean formatChanged = this.metadataQueue.format(adjustedFormat);
        this.lastUnadjustedFormat = format;
        this.pendingFormatAdjustment = false;
        if (this.upstreamFormatChangeListener != null && formatChanged) {
            this.upstreamFormatChangeListener.onUpstreamFormatChanged(adjustedFormat);
        }

    }

    public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        length = this.preAppend(length);
        int bytesAppended = input.read(this.writeAllocationNode.allocation.data, this.writeAllocationNode.translateOffset(this.totalBytesWritten), length);
        if (bytesAppended == -1) {
            if (allowEndOfInput) {
                return -1;
            } else {
                throw new EOFException();
            }
        } else {
            this.postAppend(bytesAppended);
            return bytesAppended;
        }
    }

    public void sampleData(ParsableByteArray buffer, int length) {
        while(length > 0) {
            int bytesAppended = this.preAppend(length);
            buffer.readBytes(this.writeAllocationNode.allocation.data, this.writeAllocationNode.translateOffset(this.totalBytesWritten), bytesAppended);
            length -= bytesAppended;
            this.postAppend(bytesAppended);
        }

    }

    public void sampleMetadata(long timeUs, int flags, int size, int offset, @Nullable CryptoData cryptoData) {
        if (this.pendingFormatAdjustment) {
            this.format(this.lastUnadjustedFormat);
        }

        timeUs += this.sampleOffsetUs;
        if (this.pendingSplice) {
            if ((flags & 1) == 0 || !this.metadataQueue.attemptSplice(timeUs)) {
                return;
            }

            this.pendingSplice = false;
        }

        long absoluteOffset = this.totalBytesWritten - (long)size - (long)offset;
        this.metadataQueue.commitSample(timeUs, flags, absoluteOffset, size, cryptoData);
    }

    private void clearAllocationNodes(AllocationNode fromNode) {
        if (fromNode.wasInitialized) {
            int allocationCount = (this.writeAllocationNode.wasInitialized ? 1 : 0) + (int)(this.writeAllocationNode.startPosition - fromNode.startPosition) / this.allocationLength;
            Allocation[] allocationsToRelease = new Allocation[allocationCount];
            AllocationNode currentNode = fromNode;

            for(int i = 0; i < allocationsToRelease.length; ++i) {
                allocationsToRelease[i] = currentNode.allocation;
                currentNode = currentNode.clear();
            }

            this.allocator.release(allocationsToRelease);
        }
    }

    private int preAppend(int length) {
        if (!this.writeAllocationNode.wasInitialized) {
            this.writeAllocationNode.initialize(this.allocator.allocate(), new AllocationNode(this.writeAllocationNode.endPosition, this.allocationLength));
        }

        return Math.min(length, (int)(this.writeAllocationNode.endPosition - this.totalBytesWritten));
    }

    private void postAppend(int length) {
        this.totalBytesWritten += (long)length;
        if (this.totalBytesWritten == this.writeAllocationNode.endPosition) {
            this.writeAllocationNode = this.writeAllocationNode.next;
        }

    }

    private static Format getAdjustedSampleFormat(Format format, long sampleOffsetUs) {
        if (format == null) {
            return null;
        } else {
            if (sampleOffsetUs != 0L && format.subSampleOffsetUs != 9223372036854775807L) {
                format = format.copyWithSubSampleOffsetUs(format.subSampleOffsetUs + sampleOffsetUs);
            }

            return format;
        }
    }

    private static final class AllocationNode {
        public final long startPosition;
        public final long endPosition;
        public boolean wasInitialized;
        @Nullable
        public Allocation allocation;
        @Nullable
        public SampleQueue.AllocationNode next;

        public AllocationNode(long startPosition, int allocationLength) {
            this.startPosition = startPosition;
            this.endPosition = startPosition + (long)allocationLength;
        }

        public void initialize(Allocation allocation, AllocationNode next) {
            this.allocation = allocation;
            this.next = next;
            this.wasInitialized = true;
        }

        public int translateOffset(long absolutePosition) {
            return (int)(absolutePosition - this.startPosition) + this.allocation.offset;
        }

        public AllocationNode clear() {
            this.allocation = null;
            AllocationNode temp = this.next;
            this.next = null;
            return temp;
        }
    }

    public interface UpstreamFormatChangedListener {
        void onUpstreamFormatChanged(Format var1);
    }
}
