//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source;

import com.zj.playerLib.FormatHolder;
import com.zj.playerLib.decoder.DecoderInputBuffer;

import java.io.IOException;

public final class EmptySampleStream implements SampleStream {
    public EmptySampleStream() {
    }

    public boolean isReady() {
        return true;
    }

    public void maybeThrowError() throws IOException {
    }

    public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired) {
        buffer.setFlags(4);
        return -4;
    }

    public int skipData(long positionUs) {
        return 0;
    }
}
