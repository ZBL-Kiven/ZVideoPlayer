package com.zj.playerLib.upstream;

import android.content.Context;

import androidx.annotation.Nullable;

import com.zj.playerLib.upstream.DataSource.Factory;

public final class DefaultDataSourceFactory implements Factory {
    private final Context context;
    @Nullable
    private final TransferListener listener;
    private final Factory baseDataSourceFactory;

    public DefaultDataSourceFactory(Context context, String userAgent) {
        this(context, userAgent, null);
    }

    public DefaultDataSourceFactory(Context context, String userAgent, @Nullable TransferListener listener) {
        this(context, listener, new DefaultHttpDataSourceFactory(userAgent, listener));
    }

    public DefaultDataSourceFactory(Context context, Factory baseDataSourceFactory) {
        this(context, null, baseDataSourceFactory);
    }

    public DefaultDataSourceFactory(Context context, @Nullable TransferListener listener, Factory baseDataSourceFactory) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.baseDataSourceFactory = baseDataSourceFactory;
    }

    public DefaultDataSource createDataSource() {
        DefaultDataSource dataSource = new DefaultDataSource(this.context, this.baseDataSourceFactory.createDataSource());
        if (this.listener != null) {
            dataSource.addTransferListener(this.listener);
        }

        return dataSource;
    }
}
