package com.zj.playerLib.decoder;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;

import java.util.ArrayDeque;

public abstract class SimpleDecoder<I extends DecoderInputBuffer, O extends OutputBuffer, E extends Exception> implements Decoder<I, O, E> {
    private final Thread decodeThread;
    private final Object lock = new Object();
    private final ArrayDeque<I> queuedInputBuffers = new ArrayDeque<>();
    private final ArrayDeque<O> queuedOutputBuffers = new ArrayDeque<>();
    private final I[] availableInputBuffers;
    private final O[] availableOutputBuffers;
    private int availableInputBufferCount;
    private int availableOutputBufferCount;
    private I dequeuedInputBuffer;
    private E exception;
    private boolean flushed;
    private boolean released;
    private int skippedOutputBufferCount;

    protected SimpleDecoder(I[] inputBuffers, O[] outputBuffers) {
        this.availableInputBuffers = inputBuffers;
        this.availableInputBufferCount = inputBuffers.length;

        int i;
        for (i = 0; i < this.availableInputBufferCount; ++i) {
            this.availableInputBuffers[i] = this.createInputBuffer();
        }

        this.availableOutputBuffers = outputBuffers;
        this.availableOutputBufferCount = outputBuffers.length;

        for (i = 0; i < this.availableOutputBufferCount; ++i) {
            this.availableOutputBuffers[i] = this.createOutputBuffer();
        }

        this.decodeThread = new Thread() {
            public void run() {
                SimpleDecoder.this.run();
            }
        };
        this.decodeThread.start();
    }

    protected final void setInitialInputBufferSize(int size) {
        Assertions.checkState(this.availableInputBufferCount == this.availableInputBuffers.length);
        for (I inputBuffer : this.availableInputBuffers) {
            inputBuffer.ensureSpaceForWrite(size);
        }
    }

    public final I dequeueInputBuffer() throws E {
        synchronized (this.lock) {
            this.maybeThrowException();
            Assertions.checkState(this.dequeuedInputBuffer == null);
            this.dequeuedInputBuffer = this.availableInputBufferCount == 0 ? null : this.availableInputBuffers[--this.availableInputBufferCount];
            return this.dequeuedInputBuffer;
        }
    }

    public final void queueInputBuffer(I inputBuffer) throws E {
        synchronized (this.lock) {
            this.maybeThrowException();
            Assertions.checkArgument(inputBuffer == this.dequeuedInputBuffer);
            this.queuedInputBuffers.addLast(inputBuffer);
            this.maybeNotifyDecodeLoop();
            this.dequeuedInputBuffer = null;
        }
    }

    public final O dequeueOutputBuffer() throws E {
        synchronized (this.lock) {
            this.maybeThrowException();
            return this.queuedOutputBuffers.isEmpty() ? null : this.queuedOutputBuffers.removeFirst();
        }
    }

    protected void releaseOutputBuffer(O outputBuffer) {
        synchronized (this.lock) {
            this.releaseOutputBufferInternal(outputBuffer);
            this.maybeNotifyDecodeLoop();
        }
    }

    public final void flush() {
        synchronized (this.lock) {
            this.flushed = true;
            this.skippedOutputBufferCount = 0;
            if (this.dequeuedInputBuffer != null) {
                this.releaseInputBufferInternal(this.dequeuedInputBuffer);
                this.dequeuedInputBuffer = null;
            }

            while (!this.queuedInputBuffers.isEmpty()) {
                this.releaseInputBufferInternal(this.queuedInputBuffers.removeFirst());
            }

            while (!this.queuedOutputBuffers.isEmpty()) {
                this.queuedOutputBuffers.removeFirst().release();
            }
        }
    }

    public void release() {
        synchronized (this.lock) {
            this.released = true;
            this.lock.notify();
        }

        try {
            this.decodeThread.join();
        } catch (InterruptedException var3) {
            Thread.currentThread().interrupt();
        }

    }

    private void maybeThrowException() throws E {
        if (this.exception != null) {
            throw this.exception;
        }
    }

    private void maybeNotifyDecodeLoop() {
        if (this.canDecodeBuffer()) {
            this.lock.notify();
        }

    }

    private void run() {
        try {
            //noinspection StatementWithEmptyBody
            while (this.decode()) {
            }
        } catch (InterruptedException var2) {
            throw new IllegalStateException(var2);
        }
    }

    private boolean decode() throws InterruptedException {
        I inputBuffer;
        O outputBuffer;
        boolean resetDecoder;
        synchronized (this.lock) {
            while (!this.released && !this.canDecodeBuffer()) {
                this.lock.wait();
            }

            if (this.released) {
                return false;
            }

            inputBuffer = this.queuedInputBuffers.removeFirst();
            outputBuffer = this.availableOutputBuffers[--this.availableOutputBufferCount];
            resetDecoder = this.flushed;
            this.flushed = false;
        }

        if (inputBuffer.isEndOfStream()) {
            outputBuffer.addFlag(4);
        } else {
            if (inputBuffer.isDecodeOnly()) {
                outputBuffer.addFlag(-2147483648);
            }

            try {
                this.exception = this.decode(inputBuffer, outputBuffer, resetDecoder);
            } catch (RuntimeException | OutOfMemoryError var10) {
                this.exception = this.createUnexpectedDecodeException(var10);
            }

            if (this.exception != null) {
                synchronized (this.lock) {
                    return false;
                }
            }
        }

        synchronized (this.lock) {
            if (this.flushed) {
                outputBuffer.release();
            } else if (outputBuffer.isDecodeOnly()) {
                ++this.skippedOutputBufferCount;
                outputBuffer.release();
            } else {
                outputBuffer.skippedOutputBufferCount = this.skippedOutputBufferCount;
                this.skippedOutputBufferCount = 0;
                this.queuedOutputBuffers.addLast(outputBuffer);
            }

            this.releaseInputBufferInternal(inputBuffer);
            return true;
        }
    }

    private boolean canDecodeBuffer() {
        return !this.queuedInputBuffers.isEmpty() && this.availableOutputBufferCount > 0;
    }

    private void releaseInputBufferInternal(I inputBuffer) {
        inputBuffer.clear();
        this.availableInputBuffers[this.availableInputBufferCount++] = inputBuffer;
    }

    private void releaseOutputBufferInternal(O outputBuffer) {
        outputBuffer.clear();
        this.availableOutputBuffers[this.availableOutputBufferCount++] = outputBuffer;
    }

    protected abstract I createInputBuffer();

    protected abstract O createOutputBuffer();

    protected abstract E createUnexpectedDecodeException(Throwable var1);

    @Nullable
    protected abstract E decode(I var1, O var2, boolean var3);
}
