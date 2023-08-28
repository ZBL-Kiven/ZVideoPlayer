package com.zj.playerLib.text.cea;

import androidx.annotation.NonNull;

import com.zj.playerLib.text.Subtitle;
import com.zj.playerLib.text.SubtitleDecoder;
import com.zj.playerLib.text.SubtitleDecoderException;
import com.zj.playerLib.text.SubtitleInputBuffer;
import com.zj.playerLib.text.SubtitleOutputBuffer;
import com.zj.playerLib.util.Assertions;

import java.util.ArrayDeque;
import java.util.PriorityQueue;

abstract class CeaDecoder implements SubtitleDecoder {
    private static final int NUM_INPUT_BUFFERS = 10;
    private static final int NUM_OUTPUT_BUFFERS = 2;
    private final ArrayDeque<CeaInputBuffer> availableInputBuffers = new ArrayDeque();
    private final ArrayDeque<SubtitleOutputBuffer> availableOutputBuffers;
    private final PriorityQueue<CeaInputBuffer> queuedInputBuffers;
    private CeaInputBuffer dequeuedInputBuffer;
    private long playbackPositionUs;
    private long queuedInputBufferCount;

    public CeaDecoder() {
        int i;
        for(i = 0; i < 10; ++i) {
            this.availableInputBuffers.add(new CeaInputBuffer());
        }

        this.availableOutputBuffers = new ArrayDeque();

        for(i = 0; i < 2; ++i) {
            this.availableOutputBuffers.add(new CeaOutputBuffer());
        }

        this.queuedInputBuffers = new PriorityQueue();
    }

    public abstract String getName();

    public void setPositionUs(long positionUs) {
        this.playbackPositionUs = positionUs;
    }

    public SubtitleInputBuffer dequeueInputBuffer() throws SubtitleDecoderException {
        Assertions.checkState(this.dequeuedInputBuffer == null);
        if (this.availableInputBuffers.isEmpty()) {
            return null;
        } else {
            this.dequeuedInputBuffer = this.availableInputBuffers.pollFirst();
            return this.dequeuedInputBuffer;
        }
    }

    public void queueInputBuffer(SubtitleInputBuffer inputBuffer) throws SubtitleDecoderException {
        Assertions.checkArgument(inputBuffer == this.dequeuedInputBuffer);
        if (inputBuffer.isDecodeOnly()) {
            this.releaseInputBuffer(this.dequeuedInputBuffer);
        } else {
            this.dequeuedInputBuffer.queuedInputBufferCount = this.queuedInputBufferCount++;
            this.queuedInputBuffers.add(this.dequeuedInputBuffer);
        }

        this.dequeuedInputBuffer = null;
    }

    public SubtitleOutputBuffer dequeueOutputBuffer() throws SubtitleDecoderException {
        if (this.availableOutputBuffers.isEmpty()) {
            return null;
        } else {
            CeaInputBuffer inputBuffer;
            for(; !this.queuedInputBuffers.isEmpty() && this.queuedInputBuffers.peek().timeUs <= this.playbackPositionUs; this.releaseInputBuffer(inputBuffer)) {
                inputBuffer = this.queuedInputBuffers.poll();
                if (inputBuffer.isEndOfStream()) {
                    SubtitleOutputBuffer outputBuffer = this.availableOutputBuffers.pollFirst();
                    outputBuffer.addFlag(4);
                    this.releaseInputBuffer(inputBuffer);
                    return outputBuffer;
                }

                this.decode(inputBuffer);
                if (this.isNewSubtitleDataAvailable()) {
                    Subtitle subtitle = this.createSubtitle();
                    if (!inputBuffer.isDecodeOnly()) {
                        SubtitleOutputBuffer outputBuffer = this.availableOutputBuffers.pollFirst();
                        outputBuffer.setContent(inputBuffer.timeUs, subtitle, Long.MAX_VALUE);
                        this.releaseInputBuffer(inputBuffer);
                        return outputBuffer;
                    }
                }
            }

            return null;
        }
    }

    private void releaseInputBuffer(CeaInputBuffer inputBuffer) {
        inputBuffer.clear();
        this.availableInputBuffers.add(inputBuffer);
    }

    protected void releaseOutputBuffer(SubtitleOutputBuffer outputBuffer) {
        outputBuffer.clear();
        this.availableOutputBuffers.add(outputBuffer);
    }

    public void flush() {
        this.queuedInputBufferCount = 0L;
        this.playbackPositionUs = 0L;

        while(!this.queuedInputBuffers.isEmpty()) {
            this.releaseInputBuffer(this.queuedInputBuffers.poll());
        }

        if (this.dequeuedInputBuffer != null) {
            this.releaseInputBuffer(this.dequeuedInputBuffer);
            this.dequeuedInputBuffer = null;
        }

    }

    public void release() {
    }

    protected abstract boolean isNewSubtitleDataAvailable();

    protected abstract Subtitle createSubtitle();

    protected abstract void decode(SubtitleInputBuffer var1);

    private final class CeaOutputBuffer extends SubtitleOutputBuffer {
        private CeaOutputBuffer() {
        }

        public final void release() {
            CeaDecoder.this.releaseOutputBuffer(this);
        }
    }

    private static final class CeaInputBuffer extends SubtitleInputBuffer implements Comparable<CeaInputBuffer> {
        private long queuedInputBufferCount;

        private CeaInputBuffer() {
        }

        public int compareTo(@NonNull CeaDecoder.CeaInputBuffer other) {
            if (this.isEndOfStream() != other.isEndOfStream()) {
                return this.isEndOfStream() ? 1 : -1;
            } else {
                long delta = this.timeUs - other.timeUs;
                if (delta == 0L) {
                    delta = this.queuedInputBufferCount - other.queuedInputBufferCount;
                    if (delta == 0L) {
                        return 0;
                    }
                }

                return delta > 0L ? 1 : -1;
            }
        }
    }
}
