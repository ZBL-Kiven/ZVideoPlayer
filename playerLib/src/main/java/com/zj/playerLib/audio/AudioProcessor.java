//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public interface AudioProcessor {
    ByteBuffer EMPTY_BUFFER = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());

    boolean configure(int var1, int var2, int var3) throws UnhandledFormatException;

    boolean isActive();

    int getOutputChannelCount();

    int getOutputEncoding();

    int getOutputSampleRateHz();

    void queueInput(ByteBuffer var1);

    void queueEndOfStream();

    ByteBuffer getOutput();

    boolean isEnded();

    void flush();

    void reset();

    public static final class UnhandledFormatException extends Exception {
        public UnhandledFormatException(int sampleRateHz, int channelCount, int encoding) {
            super("Unhandled format: " + sampleRateHz + " Hz, " + channelCount + " channels in encoding " + encoding);
        }
    }
}
