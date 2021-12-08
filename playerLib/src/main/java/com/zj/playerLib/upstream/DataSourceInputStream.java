package com.zj.playerLib.upstream;

import androidx.annotation.NonNull;
import com.zj.playerLib.util.Assertions;
import java.io.IOException;
import java.io.InputStream;

public final class DataSourceInputStream extends InputStream {
    private final DataSource dataSource;
    private final DataSpec dataSpec;
    private final byte[] singleByteArray;
    private boolean opened = false;
    private boolean closed = false;
    private long totalBytesRead;

    public DataSourceInputStream(DataSource dataSource, DataSpec dataSpec) {
        this.dataSource = dataSource;
        this.dataSpec = dataSpec;
        this.singleByteArray = new byte[1];
    }

    public long bytesRead() {
        return this.totalBytesRead;
    }

    public void open() throws IOException {
        this.checkOpened();
    }

    public int read() throws IOException {
        int length = this.read(this.singleByteArray);
        return length == -1 ? -1 : this.singleByteArray[0] & 255;
    }

    public int read(@NonNull byte[] buffer) throws IOException {
        return this.read(buffer, 0, buffer.length);
    }

    public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
        Assertions.checkState(!this.closed);
        this.checkOpened();
        int bytesRead = this.dataSource.read(buffer, offset, length);
        if (bytesRead == -1) {
            return -1;
        } else {
            this.totalBytesRead += bytesRead;
            return bytesRead;
        }
    }

    public void close() throws IOException {
        if (!this.closed) {
            this.dataSource.close();
            this.closed = true;
        }

    }

    private void checkOpened() throws IOException {
        if (!this.opened) {
            this.dataSource.open(this.dataSpec);
            this.opened = true;
        }

    }
}
