//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source;

public interface SequenceableLoader {
    long getBufferedPositionUs();

    long getNextLoadPositionUs();

    boolean continueLoading(long var1);

    void reevaluateBuffer(long var1);

    public interface Callback<T extends SequenceableLoader> {
        void onContinueLoadingRequested(T var1);
    }
}
