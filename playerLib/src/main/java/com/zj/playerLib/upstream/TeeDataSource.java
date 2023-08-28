package com.zj.playerLib.upstream;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class TeeDataSource implements DataSource {
    private final DataSource upstream;
    private final DataSink dataSink;
    private boolean dataSinkNeedsClosing;
    private long bytesRemaining;

    public TeeDataSource(DataSource upstream, DataSink dataSink) {
        this.upstream = Assertions.checkNotNull(upstream);
        this.dataSink = Assertions.checkNotNull(dataSink);
    }

    public void addTransferListener(TransferListener transferListener) {
        this.upstream.addTransferListener(transferListener);
    }

    public long open(DataSpec dataSpec) throws IOException {
        this.bytesRemaining = this.upstream.open(dataSpec);
        if (this.bytesRemaining == 0L) {
            return 0L;
        } else {
            if (dataSpec.length == -1L && this.bytesRemaining != -1L) {
                dataSpec = dataSpec.subrange(0L, this.bytesRemaining);
            }

            this.dataSinkNeedsClosing = true;
            this.dataSink.open(dataSpec);
            return this.bytesRemaining;
        }
    }

    public int read(byte[] buffer, int offset, int max) throws IOException {
        if (this.bytesRemaining == 0L) {
            return -1;
        } else {
            int bytesRead = this.upstream.read(buffer, offset, max);
            if (bytesRead > 0) {
                this.dataSink.write(buffer, offset, bytesRead);
                if (this.bytesRemaining != -1L) {
                    this.bytesRemaining -= bytesRead;
                }
            }

            return bytesRead;
        }
    }

    @Nullable
    public Uri getUri() {
        return this.upstream.getUri();
    }

    public Map<String, List<String>> getResponseHeaders() {
        return this.upstream.getResponseHeaders();
    }

    public void close() throws IOException {
        try {
            this.upstream.close();
        } finally {
            if (this.dataSinkNeedsClosing) {
                this.dataSinkNeedsClosing = false;
                this.dataSink.close();
            }

        }

    }
}
