package com.zj.playerLib.upstream;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Util;

import java.util.ArrayList;

public abstract class BaseDataSource implements DataSource {
    private final boolean isNetwork;
    private final ArrayList<TransferListener> listeners;
    private int listenerCount;
    @Nullable
    private DataSpec dataSpec;

    protected BaseDataSource(boolean isNetwork) {
        this.isNetwork = isNetwork;
        this.listeners = new ArrayList<>(1);
    }

    public final void addTransferListener(TransferListener transferListener) {
        if (!this.listeners.contains(transferListener)) {
            this.listeners.add(transferListener);
            ++this.listenerCount;
        }

    }

    protected final void transferInitializing(DataSpec dataSpec) {
        for(int i = 0; i < this.listenerCount; ++i) {
            this.listeners.get(i).onTransferInitializing(this, dataSpec, this.isNetwork);
        }
    }

    protected final void transferStarted(DataSpec dataSpec) {
        this.dataSpec = dataSpec;

        for(int i = 0; i < this.listenerCount; ++i) {
            this.listeners.get(i).onTransferStart(this, dataSpec, this.isNetwork);
        }

    }

    protected final void bytesTransferred(int bytesTransferred) {
        DataSpec dataSpec = Util.castNonNull(this.dataSpec);

        for(int i = 0; i < this.listenerCount; ++i) {
            this.listeners.get(i).onBytesTransferred(this, dataSpec, this.isNetwork, bytesTransferred);
        }

    }

    protected final void transferEnded() {
        DataSpec dataSpec = Util.castNonNull(this.dataSpec);

        for(int i = 0; i < this.listenerCount; ++i) {
            this.listeners.get(i).onTransferEnd(this, dataSpec, this.isNetwork);
        }

        this.dataSpec = null;
    }
}
