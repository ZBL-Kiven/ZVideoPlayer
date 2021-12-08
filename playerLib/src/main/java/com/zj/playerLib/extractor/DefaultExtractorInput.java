package com.zj.playerLib.extractor;

import com.zj.playerLib.upstream.DataSource;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

public final class DefaultExtractorInput implements ExtractorInput {
    private static final int PEEK_MIN_FREE_SPACE_AFTER_RESIZE = 65536;
    private static final int PEEK_MAX_FREE_SPACE = 524288;
    private static final int SCRATCH_SPACE_SIZE = 4096;
    private final byte[] scratchSpace;
    private final DataSource dataSource;
    private final long streamLength;
    private long position;
    private byte[] peekBuffer;
    private int peekBufferPosition;
    private int peekBufferLength;

    public DefaultExtractorInput(DataSource dataSource, long position, long length) {
        this.dataSource = dataSource;
        this.position = position;
        this.streamLength = length;
        this.peekBuffer = new byte[65536];
        this.scratchSpace = new byte[4096];
    }

    public int read(byte[] target, int offset, int length) throws IOException, InterruptedException {
        int bytesRead = this.readFromPeekBuffer(target, offset, length);
        if (bytesRead == 0) {
            bytesRead = this.readFromDataSource(target, offset, length, 0, true);
        }

        this.commitBytesRead(bytesRead);
        return bytesRead;
    }

    public boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        int bytesRead;
        for(bytesRead = this.readFromPeekBuffer(target, offset, length); bytesRead < length && bytesRead != -1; bytesRead = this.readFromDataSource(target, offset, length, bytesRead, allowEndOfInput)) {
        }

        this.commitBytesRead(bytesRead);
        return bytesRead != -1;
    }

    public void readFully(byte[] target, int offset, int length) throws IOException, InterruptedException {
        this.readFully(target, offset, length, false);
    }

    public int skip(int length) throws IOException, InterruptedException {
        int bytesSkipped = this.skipFromPeekBuffer(length);
        if (bytesSkipped == 0) {
            bytesSkipped = this.readFromDataSource(this.scratchSpace, 0, Math.min(length, this.scratchSpace.length), 0, true);
        }

        this.commitBytesRead(bytesSkipped);
        return bytesSkipped;
    }

    public boolean skipFully(int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        int bytesSkipped;
        int minLength;
        for(bytesSkipped = this.skipFromPeekBuffer(length); bytesSkipped < length && bytesSkipped != -1; bytesSkipped = this.readFromDataSource(this.scratchSpace, -bytesSkipped, minLength, bytesSkipped, allowEndOfInput)) {
            minLength = Math.min(length, bytesSkipped + this.scratchSpace.length);
        }

        this.commitBytesRead(bytesSkipped);
        return bytesSkipped != -1;
    }

    public void skipFully(int length) throws IOException, InterruptedException {
        this.skipFully(length, false);
    }

    public boolean peekFully(byte[] target, int offset, int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        if (!this.advancePeekPosition(length, allowEndOfInput)) {
            return false;
        } else {
            System.arraycopy(this.peekBuffer, this.peekBufferPosition - length, target, offset, length);
            return true;
        }
    }

    public void peekFully(byte[] target, int offset, int length) throws IOException, InterruptedException {
        this.peekFully(target, offset, length, false);
    }

    public boolean advancePeekPosition(int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        this.ensureSpaceForPeek(length);

        for(int bytesPeeked = this.peekBufferLength - this.peekBufferPosition; bytesPeeked < length; this.peekBufferLength = this.peekBufferPosition + bytesPeeked) {
            bytesPeeked = this.readFromDataSource(this.peekBuffer, this.peekBufferPosition, length, bytesPeeked, allowEndOfInput);
            if (bytesPeeked == -1) {
                return false;
            }
        }

        this.peekBufferPosition += length;
        return true;
    }

    public void advancePeekPosition(int length) throws IOException, InterruptedException {
        this.advancePeekPosition(length, false);
    }

    public void resetPeekPosition() {
        this.peekBufferPosition = 0;
    }

    public long getPeekPosition() {
        return this.position + (long)this.peekBufferPosition;
    }

    public long getPosition() {
        return this.position;
    }

    public long getLength() {
        return this.streamLength;
    }

    public <E extends Throwable> void setRetryPosition(long position, E e) throws E {
        Assertions.checkArgument(position >= 0L);
        this.position = position;
        throw e;
    }

    private void ensureSpaceForPeek(int length) {
        int requiredLength = this.peekBufferPosition + length;
        if (requiredLength > this.peekBuffer.length) {
            int newPeekCapacity = Util.constrainValue(this.peekBuffer.length * 2, requiredLength + 65536, requiredLength + 524288);
            this.peekBuffer = Arrays.copyOf(this.peekBuffer, newPeekCapacity);
        }

    }

    private int skipFromPeekBuffer(int length) {
        int bytesSkipped = Math.min(this.peekBufferLength, length);
        this.updatePeekBuffer(bytesSkipped);
        return bytesSkipped;
    }

    private int readFromPeekBuffer(byte[] target, int offset, int length) {
        if (this.peekBufferLength == 0) {
            return 0;
        } else {
            int peekBytes = Math.min(this.peekBufferLength, length);
            System.arraycopy(this.peekBuffer, 0, target, offset, peekBytes);
            this.updatePeekBuffer(peekBytes);
            return peekBytes;
        }
    }

    private void updatePeekBuffer(int bytesConsumed) {
        this.peekBufferLength -= bytesConsumed;
        this.peekBufferPosition = 0;
        byte[] newPeekBuffer = this.peekBuffer;
        if (this.peekBufferLength < this.peekBuffer.length - 524288) {
            newPeekBuffer = new byte[this.peekBufferLength + 65536];
        }

        System.arraycopy(this.peekBuffer, bytesConsumed, newPeekBuffer, 0, this.peekBufferLength);
        this.peekBuffer = newPeekBuffer;
    }

    private int readFromDataSource(byte[] target, int offset, int length, int bytesAlreadyRead, boolean allowEndOfInput) throws InterruptedException, IOException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        } else {
            int bytesRead = this.dataSource.read(target, offset + bytesAlreadyRead, length - bytesAlreadyRead);
            if (bytesRead == -1) {
                if (bytesAlreadyRead == 0 && allowEndOfInput) {
                    return -1;
                } else {
                    throw new EOFException();
                }
            } else {
                return bytesAlreadyRead + bytesRead;
            }
        }
    }

    private void commitBytesRead(int bytesRead) {
        if (bytesRead != -1) {
            this.position += bytesRead;
        }

    }
}
