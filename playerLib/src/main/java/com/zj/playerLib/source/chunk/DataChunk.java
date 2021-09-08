//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source.chunk;

import com.zj.playerLib.Format;
import com.zj.playerLib.upstream.DataSource;
import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.util.Util;

import java.io.IOException;
import java.util.Arrays;

public abstract class DataChunk extends Chunk {
    private static final int READ_GRANULARITY = 16384;
    private byte[] data;
    private volatile boolean loadCanceled;

    public DataChunk(DataSource dataSource, DataSpec dataSpec, int type, Format trackFormat, int trackSelectionReason, Object trackSelectionData, byte[] data) {
        super(dataSource, dataSpec, type, trackFormat, trackSelectionReason, trackSelectionData, -9223372036854775807L, -9223372036854775807L);
        this.data = data;
    }

    public byte[] getDataHolder() {
        return this.data;
    }

    public final void cancelLoad() {
        this.loadCanceled = true;
    }

    public final void load() throws IOException, InterruptedException {
        try {
            this.dataSource.open(this.dataSpec);
            int limit = 0;
            int bytesRead = 0;

            while(true) {
                if (bytesRead == -1 || this.loadCanceled) {
                    if (!this.loadCanceled) {
                        this.consume(this.data, limit);
                    }
                    break;
                }

                this.maybeExpandData(limit);
                bytesRead = this.dataSource.read(this.data, limit, 16384);
                if (bytesRead != -1) {
                    limit += bytesRead;
                }
            }
        } finally {
            Util.closeQuietly(this.dataSource);
        }

    }

    protected abstract void consume(byte[] var1, int var2) throws IOException;

    private void maybeExpandData(int limit) {
        if (this.data == null) {
            this.data = new byte[16384];
        } else if (this.data.length < limit + 16384) {
            this.data = Arrays.copyOf(this.data, this.data.length + 16384);
        }

    }
}
