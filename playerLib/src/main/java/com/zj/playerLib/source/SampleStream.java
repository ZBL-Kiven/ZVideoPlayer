//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source;

import com.zj.playerLib.FormatHolder;
import com.zj.playerLib.decoder.DecoderInputBuffer;
import java.io.IOException;

public interface SampleStream {
    boolean isReady();

    void maybeThrowError() throws IOException;

    int readData(FormatHolder var1, DecoderInputBuffer var2, boolean var3);

    int skipData(long var1);
}
