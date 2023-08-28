package com.zj.playerLib.text;

import com.zj.playerLib.decoder.SimpleDecoder;

import java.nio.ByteBuffer;

public abstract class SimpleSubtitleDecoder extends SimpleDecoder<SubtitleInputBuffer, SubtitleOutputBuffer, SubtitleDecoderException> implements SubtitleDecoder {
    private final String name;

    protected SimpleSubtitleDecoder(String name) {
        super(new SubtitleInputBuffer[2], new SubtitleOutputBuffer[2]);
        this.name = name;
        this.setInitialInputBufferSize(1024);
    }

    public final String getName() {
        return this.name;
    }

    public void setPositionUs(long timeUs) {
    }

    protected final SubtitleInputBuffer createInputBuffer() {
        return new SubtitleInputBuffer();
    }

    protected final SubtitleOutputBuffer createOutputBuffer() {
        return new SimpleSubtitleOutputBuffer(this);
    }

    protected final SubtitleDecoderException createUnexpectedDecodeException(Throwable error) {
        return new SubtitleDecoderException("Unexpected decode error", error);
    }

    protected final void releaseOutputBuffer(SubtitleOutputBuffer buffer) {
        super.releaseOutputBuffer(buffer);
    }

    protected final SubtitleDecoderException decode(SubtitleInputBuffer inputBuffer, SubtitleOutputBuffer outputBuffer, boolean reset) {
        try {
            ByteBuffer inputData = inputBuffer.data;
            Subtitle subtitle = this.decode(inputData.array(), inputData.limit(), reset);
            outputBuffer.setContent(inputBuffer.timeUs, subtitle, inputBuffer.subSampleOffsetUs);
            outputBuffer.clearFlag(-2147483648);
            return null;
        } catch (SubtitleDecoderException var6) {
            return var6;
        }
    }

    protected abstract Subtitle decode(byte[] var1, int var2, boolean var3) throws SubtitleDecoderException;
}
