//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.decoder;

public interface Decoder<I, O, E extends Exception> {
    String getName();

    I dequeueInputBuffer() throws E;

    void queueInputBuffer(I var1) throws E;

    O dequeueOutputBuffer() throws E;

    void flush();

    void release();
}
