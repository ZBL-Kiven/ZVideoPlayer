package com.zj.playerLib.source;

public interface SequenceableLoader {
    long getBufferedPositionUs();

    long getNextLoadPositionUs();

    boolean continueLoading(long var1);

    void reevaluateBuffer(long var1);

    interface Callback<T extends SequenceableLoader> {
        void onContinueLoadingRequested(T var1);
    }
}
