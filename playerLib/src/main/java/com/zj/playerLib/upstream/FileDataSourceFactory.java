//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream;

import androidx.annotation.Nullable;

import com.zj.playerLib.upstream.DataSource.Factory;

public final class FileDataSourceFactory implements Factory {
    @Nullable
    private final TransferListener listener;

    public FileDataSourceFactory() {
        this((TransferListener)null);
    }

    public FileDataSourceFactory(@Nullable TransferListener listener) {
        this.listener = listener;
    }

    public DataSource createDataSource() {
        FileDataSource dataSource = new FileDataSource();
        if (this.listener != null) {
            dataSource.addTransferListener(this.listener);
        }

        return dataSource;
    }
}
