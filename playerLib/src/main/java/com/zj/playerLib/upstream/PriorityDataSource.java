//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.PriorityTaskManager;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class PriorityDataSource implements DataSource {
    private final DataSource upstream;
    private final PriorityTaskManager priorityTaskManager;
    private final int priority;

    public PriorityDataSource(DataSource upstream, PriorityTaskManager priorityTaskManager, int priority) {
        this.upstream = (DataSource)Assertions.checkNotNull(upstream);
        this.priorityTaskManager = (PriorityTaskManager)Assertions.checkNotNull(priorityTaskManager);
        this.priority = priority;
    }

    public void addTransferListener(TransferListener transferListener) {
        this.upstream.addTransferListener(transferListener);
    }

    public long open(DataSpec dataSpec) throws IOException {
        this.priorityTaskManager.proceedOrThrow(this.priority);
        return this.upstream.open(dataSpec);
    }

    public int read(byte[] buffer, int offset, int max) throws IOException {
        this.priorityTaskManager.proceedOrThrow(this.priority);
        return this.upstream.read(buffer, offset, max);
    }

    @Nullable
    public Uri getUri() {
        return this.upstream.getUri();
    }

    public Map<String, List<String>> getResponseHeaders() {
        return this.upstream.getResponseHeaders();
    }

    public void close() throws IOException {
        this.upstream.close();
    }
}
