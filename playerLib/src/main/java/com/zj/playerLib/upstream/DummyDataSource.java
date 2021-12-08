package com.zj.playerLib.upstream;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.IOException;

public final class DummyDataSource implements DataSource {
    public static final DummyDataSource INSTANCE = new DummyDataSource();
    public static final Factory FACTORY = DummyDataSource::new;

    private DummyDataSource() {
    }

    public void addTransferListener(TransferListener transferListener) {
    }

    public long open(DataSpec dataSpec) throws IOException {
        throw new IOException("Dummy source");
    }

    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Nullable
    public Uri getUri() {
        return null;
    }

    public void close() throws IOException {
    }
}
