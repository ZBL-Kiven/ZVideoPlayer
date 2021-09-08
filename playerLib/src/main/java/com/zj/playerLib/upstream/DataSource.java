//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface DataSource {
    void addTransferListener(TransferListener var1);

    long open(DataSpec var1) throws IOException;

    int read(byte[] var1, int var2, int var3) throws IOException;

    @Nullable
    Uri getUri();

    default Map<String, List<String>> getResponseHeaders() {
        return Collections.emptyMap();
    }

    void close() throws IOException;

    public interface Factory {
        DataSource createDataSource();
    }
}
