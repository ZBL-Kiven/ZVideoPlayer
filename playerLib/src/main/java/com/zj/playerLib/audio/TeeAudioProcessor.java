package com.zj.playerLib.audio;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.Util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class TeeAudioProcessor implements AudioProcessor {
    private final AudioBufferSink audioBufferSink;
    private int sampleRateHz;
    private int channelCount;
    private int encoding;
    private boolean isActive;
    private ByteBuffer buffer;
    private ByteBuffer outputBuffer;
    private boolean inputEnded;

    public TeeAudioProcessor(AudioBufferSink audioBufferSink) {
        this.audioBufferSink = Assertions.checkNotNull(audioBufferSink);
        this.buffer = EMPTY_BUFFER;
        this.outputBuffer = EMPTY_BUFFER;
        this.channelCount = -1;
        this.sampleRateHz = -1;
    }

    public boolean configure(int sampleRateHz, int channelCount, int encoding) {
        this.sampleRateHz = sampleRateHz;
        this.channelCount = channelCount;
        this.encoding = encoding;
        boolean wasActive = this.isActive;
        this.isActive = true;
        return !wasActive;
    }

    public boolean isActive() {
        return this.isActive;
    }

    public int getOutputChannelCount() {
        return this.channelCount;
    }

    public int getOutputEncoding() {
        return this.encoding;
    }

    public int getOutputSampleRateHz() {
        return this.sampleRateHz;
    }

    public void queueInput(ByteBuffer buffer) {
        int remaining = buffer.remaining();
        if (remaining != 0) {
            this.audioBufferSink.handleBuffer(buffer.asReadOnlyBuffer());
            if (this.buffer.capacity() < remaining) {
                this.buffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder());
            } else {
                this.buffer.clear();
            }

            this.buffer.put(buffer);
            this.buffer.flip();
            this.outputBuffer = this.buffer;
        }
    }

    public void queueEndOfStream() {
        this.inputEnded = true;
    }

    public ByteBuffer getOutput() {
        ByteBuffer outputBuffer = this.outputBuffer;
        this.outputBuffer = EMPTY_BUFFER;
        return outputBuffer;
    }

    public boolean isEnded() {
        return this.inputEnded && this.buffer == EMPTY_BUFFER;
    }

    public void flush() {
        this.outputBuffer = EMPTY_BUFFER;
        this.inputEnded = false;
        this.audioBufferSink.flush(this.sampleRateHz, this.channelCount, this.encoding);
    }

    public void reset() {
        this.flush();
        this.buffer = EMPTY_BUFFER;
        this.sampleRateHz = -1;
        this.channelCount = -1;
        this.encoding = -1;
    }

    public static final class WavFileAudioBufferSink implements AudioBufferSink {
        private static final String TAG = "WaveFileAudioBufferSink";
        private static final int FILE_SIZE_MINUS_8_OFFSET = 4;
        private static final int FILE_SIZE_MINUS_44_OFFSET = 40;
        private static final int HEADER_LENGTH = 44;
        private final String outputFileNamePrefix;
        private final byte[] scratchBuffer;
        private final ByteBuffer scratchByteBuffer;
        private int sampleRateHz;
        private int channelCount;
        private int encoding;
        @Nullable
        private RandomAccessFile randomAccessFile;
        private int counter;
        private int bytesWritten;

        public WavFileAudioBufferSink(String outputFileNamePrefix) {
            this.outputFileNamePrefix = outputFileNamePrefix;
            this.scratchBuffer = new byte[1024];
            this.scratchByteBuffer = ByteBuffer.wrap(this.scratchBuffer).order(ByteOrder.LITTLE_ENDIAN);
        }

        public void flush(int sampleRateHz, int channelCount, int encoding) {
            try {
                this.reset();
            } catch (IOException var5) {
                Log.e("WaveFileAudioBufferSink", "Error resetting", var5);
            }

            this.sampleRateHz = sampleRateHz;
            this.channelCount = channelCount;
            this.encoding = encoding;
        }

        public void handleBuffer(ByteBuffer buffer) {
            try {
                this.maybePrepareFile();
                this.writeBuffer(buffer);
            } catch (IOException var3) {
                Log.e("WaveFileAudioBufferSink", "Error writing data", var3);
            }

        }

        private void maybePrepareFile() throws IOException {
            if (this.randomAccessFile == null) {
                RandomAccessFile randomAccessFile = new RandomAccessFile(this.getNextOutputFileName(), "rw");
                this.writeFileHeader(randomAccessFile);
                this.randomAccessFile = randomAccessFile;
                this.bytesWritten = 44;
            }
        }

        private void writeFileHeader(RandomAccessFile randomAccessFile) throws IOException {
            randomAccessFile.writeInt(WavUtil.RIFF_FOURCC);
            randomAccessFile.writeInt(-1);
            randomAccessFile.writeInt(WavUtil.WAVE_FOURCC);
            randomAccessFile.writeInt(WavUtil.FMT_FOURCC);
            this.scratchByteBuffer.clear();
            this.scratchByteBuffer.putInt(16);
            this.scratchByteBuffer.putShort((short) WavUtil.getTypeForEncoding(this.encoding));
            this.scratchByteBuffer.putShort((short) this.channelCount);
            this.scratchByteBuffer.putInt(this.sampleRateHz);
            int bytesPerSample = Util.getPcmFrameSize(this.encoding, this.channelCount);
            this.scratchByteBuffer.putInt(bytesPerSample * this.sampleRateHz);
            this.scratchByteBuffer.putShort((short) bytesPerSample);
            this.scratchByteBuffer.putShort((short) (8 * bytesPerSample / this.channelCount));
            randomAccessFile.write(this.scratchBuffer, 0, this.scratchByteBuffer.position());
            randomAccessFile.writeInt(WavUtil.DATA_FOURCC);
            randomAccessFile.writeInt(-1);
        }

        private void writeBuffer(ByteBuffer buffer) throws IOException {
            int bytesToWrite;
            for (RandomAccessFile randomAccessFile = Assertions.checkNotNull(this.randomAccessFile); buffer.hasRemaining(); this.bytesWritten += bytesToWrite) {
                bytesToWrite = Math.min(buffer.remaining(), this.scratchBuffer.length);
                buffer.get(this.scratchBuffer, 0, bytesToWrite);
                randomAccessFile.write(this.scratchBuffer, 0, bytesToWrite);
            }

        }

        private void reset() throws IOException {
            RandomAccessFile randomAccessFile = this.randomAccessFile;
            if (randomAccessFile != null) {
                try {
                    this.scratchByteBuffer.clear();
                    this.scratchByteBuffer.putInt(this.bytesWritten - 8);
                    randomAccessFile.seek(4L);
                    randomAccessFile.write(this.scratchBuffer, 0, 4);
                    this.scratchByteBuffer.clear();
                    this.scratchByteBuffer.putInt(this.bytesWritten - 44);
                    randomAccessFile.seek(40L);
                    randomAccessFile.write(this.scratchBuffer, 0, 4);
                } catch (IOException var7) {
                    Log.w("WaveFileAudioBufferSink", "Error updating file size", var7);
                }

                try {
                    randomAccessFile.close();
                } finally {
                    this.randomAccessFile = null;
                }

            }
        }

        private String getNextOutputFileName() {
            return Util.formatInvariant("%s-%04d.wav", this.outputFileNamePrefix, this.counter++);
        }
    }

    public interface AudioBufferSink {
        void flush(int var1, int var2, int var3);

        void handleBuffer(ByteBuffer var1);
    }
}
