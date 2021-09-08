//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class StatsDataSource implements DataSource {
    private final DataSource dataSource;
    private long bytesRead;
    private Uri lastOpenedUri;
    private Map<String, List<String>> lastResponseHeaders;

    public StatsDataSource(DataSource dataSource) {
        this.dataSource = (DataSource)Assertions.checkNotNull(dataSource);
        this.lastOpenedUri = Uri.EMPTY;
        this.lastResponseHeaders = Collections.emptyMap();
    }

    public void resetBytesRead() {
        this.bytesRead = 0L;
    }

    public long getBytesRead() {
        return this.bytesRead;
    }

    public Uri getLastOpenedUri() {
        return this.lastOpenedUri;
    }

    public Map<String, List<String>> getLastResponseHeaders() {
        return this.lastResponseHeaders;
    }

    public void addTransferListener(TransferListener transferListener) {
        this.dataSource.addTransferListener(transferListener);
    }

    public long open(DataSpec dataSpec) throws IOException {
        this.lastOpenedUri = dataSpec.uri;
        this.lastResponseHeaders = Collections.emptyMap();
        long availableBytes = this.dataSource.open(dataSpec);
        this.lastOpenedUri = (Uri)Assertions.checkNotNull(this.getUri());
        this.lastResponseHeaders = this.getResponseHeaders();
        return availableBytes;
    }

    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        int bytesRead = this.dataSource.read(buffer, offset, readLength);
        if (bytesRead != -1) {
            this.bytesRead += (long)bytesRead;
        }

        return bytesRead;
    }

    @Nullable
    public Uri getUri() {
        return this.dataSource.getUri();
    }

    public Map<String, List<String>> getResponseHeaders() {
        return this.dataSource.getResponseHeaders();
    }

    public void close() throws IOException {
        this.dataSource.close();
    }
}
