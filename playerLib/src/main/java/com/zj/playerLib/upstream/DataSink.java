package com.zj.playerLib.upstream;

import java.io.IOException;

public interface DataSink {
    void open(DataSpec var1) throws IOException;

    void write(byte[] var1, int var2, int var3) throws IOException;

    void close() throws IOException;

    interface Factory {
        DataSink createDataSink();
    }
}
