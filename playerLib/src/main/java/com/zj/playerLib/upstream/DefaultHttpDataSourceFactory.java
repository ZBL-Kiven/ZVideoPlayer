package com.zj.playerLib.upstream;

import androidx.annotation.Nullable;

import com.zj.playerLib.upstream.HttpDataSource.BaseFactory;
import com.zj.playerLib.upstream.HttpDataSource.RequestProperties;
import com.zj.playerLib.util.Predicate;

public final class DefaultHttpDataSourceFactory extends BaseFactory {
    private final String userAgent;
    @Nullable
    private final TransferListener listener;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final boolean allowCrossProtocolRedirects;

    public DefaultHttpDataSourceFactory(String userAgent) {
        this(userAgent, null);
    }

    public DefaultHttpDataSourceFactory(String userAgent, @Nullable TransferListener listener) {
        this(userAgent, listener, 8000, 8000, false);
    }

    public DefaultHttpDataSourceFactory(String userAgent, int connectTimeoutMillis, int readTimeoutMillis, boolean allowCrossProtocolRedirects) {
        this(userAgent, null, connectTimeoutMillis, readTimeoutMillis, allowCrossProtocolRedirects);
    }

    public DefaultHttpDataSourceFactory(String userAgent, @Nullable TransferListener listener, int connectTimeoutMillis, int readTimeoutMillis, boolean allowCrossProtocolRedirects) {
        this.userAgent = userAgent;
        this.listener = listener;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
    }

    protected DefaultHttpDataSource createDataSourceInternal(RequestProperties defaultRequestProperties) {
        DefaultHttpDataSource dataSource = new DefaultHttpDataSource(this.userAgent, null, this.connectTimeoutMillis, this.readTimeoutMillis, this.allowCrossProtocolRedirects, defaultRequestProperties);
        if (this.listener != null) {
            dataSource.addTransferListener(this.listener);
        }

        return dataSource;
    }
}
