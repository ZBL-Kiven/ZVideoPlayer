//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.zj.playerLib.upstream.HttpDataSource.RequestProperties;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.Predicate;
import com.zj.playerLib.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class DefaultDataSource implements DataSource {
    private static final String TAG = "DefaultDataSource";
    private static final String SCHEME_ASSET = "asset";
    private static final String SCHEME_CONTENT = "content";
    private static final String SCHEME_RTMP = "rtmp";
    private static final String SCHEME_RAW = "rawresource";
    private final Context context;
    private final List<TransferListener> transferListeners;
    private final DataSource baseDataSource;
    @Nullable
    private DataSource fileDataSource;
    @Nullable
    private DataSource assetDataSource;
    @Nullable
    private DataSource contentDataSource;
    @Nullable
    private DataSource rtmpDataSource;
    @Nullable
    private DataSource dataSchemeDataSource;
    @Nullable
    private DataSource rawResourceDataSource;
    @Nullable
    private DataSource dataSource;

    public DefaultDataSource(Context context, String userAgent, boolean allowCrossProtocolRedirects) {
        this(context, userAgent, 8000, 8000, allowCrossProtocolRedirects);
    }

    public DefaultDataSource(Context context, String userAgent, int connectTimeoutMillis, int readTimeoutMillis, boolean allowCrossProtocolRedirects) {
        this(context, new DefaultHttpDataSource(userAgent, (Predicate)null, connectTimeoutMillis, readTimeoutMillis, allowCrossProtocolRedirects, (RequestProperties)null));
    }

    public DefaultDataSource(Context context, DataSource baseDataSource) {
        this.context = context.getApplicationContext();
        this.baseDataSource = (DataSource)Assertions.checkNotNull(baseDataSource);
        this.transferListeners = new ArrayList();
    }

    /** @deprecated */
    @Deprecated
    public DefaultDataSource(Context context, @Nullable TransferListener listener, String userAgent, boolean allowCrossProtocolRedirects) {
        this(context, listener, userAgent, 8000, 8000, allowCrossProtocolRedirects);
    }

    /** @deprecated */
    @Deprecated
    public DefaultDataSource(Context context, @Nullable TransferListener listener, String userAgent, int connectTimeoutMillis, int readTimeoutMillis, boolean allowCrossProtocolRedirects) {
        this(context, listener, new DefaultHttpDataSource(userAgent, (Predicate)null, listener, connectTimeoutMillis, readTimeoutMillis, allowCrossProtocolRedirects, (RequestProperties)null));
    }

    /** @deprecated */
    @Deprecated
    public DefaultDataSource(Context context, @Nullable TransferListener listener, DataSource baseDataSource) {
        this(context, baseDataSource);
        if (listener != null) {
            this.transferListeners.add(listener);
        }

    }

    public void addTransferListener(TransferListener transferListener) {
        this.baseDataSource.addTransferListener(transferListener);
        this.transferListeners.add(transferListener);
        this.maybeAddListenerToDataSource(this.fileDataSource, transferListener);
        this.maybeAddListenerToDataSource(this.assetDataSource, transferListener);
        this.maybeAddListenerToDataSource(this.contentDataSource, transferListener);
        this.maybeAddListenerToDataSource(this.rtmpDataSource, transferListener);
        this.maybeAddListenerToDataSource(this.dataSchemeDataSource, transferListener);
        this.maybeAddListenerToDataSource(this.rawResourceDataSource, transferListener);
    }

    public long open(DataSpec dataSpec) throws IOException {
        Assertions.checkState(this.dataSource == null);
        String scheme = dataSpec.uri.getScheme();
        if (Util.isLocalFileUri(dataSpec.uri)) {
            if (dataSpec.uri.getPath().startsWith("/android_asset/")) {
                this.dataSource = this.getAssetDataSource();
            } else {
                this.dataSource = this.getFileDataSource();
            }
        } else if ("asset".equals(scheme)) {
            this.dataSource = this.getAssetDataSource();
        } else if ("content".equals(scheme)) {
            this.dataSource = this.getContentDataSource();
        } else if ("rtmp".equals(scheme)) {
            this.dataSource = this.getRtmpDataSource();
        } else if ("data".equals(scheme)) {
            this.dataSource = this.getDataSchemeDataSource();
        } else if ("rawresource".equals(scheme)) {
            this.dataSource = this.getRawResourceDataSource();
        } else {
            this.dataSource = this.baseDataSource;
        }

        return this.dataSource.open(dataSpec);
    }

    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        return ((DataSource)Assertions.checkNotNull(this.dataSource)).read(buffer, offset, readLength);
    }

    @Nullable
    public Uri getUri() {
        return this.dataSource == null ? null : this.dataSource.getUri();
    }

    public Map<String, List<String>> getResponseHeaders() {
        return this.dataSource == null ? Collections.emptyMap() : this.dataSource.getResponseHeaders();
    }

    public void close() throws IOException {
        if (this.dataSource != null) {
            try {
                this.dataSource.close();
            } finally {
                this.dataSource = null;
            }
        }

    }

    private DataSource getFileDataSource() {
        if (this.fileDataSource == null) {
            this.fileDataSource = new FileDataSource();
            this.addListenersToDataSource(this.fileDataSource);
        }

        return this.fileDataSource;
    }

    private DataSource getAssetDataSource() {
        if (this.assetDataSource == null) {
            this.assetDataSource = new AssetDataSource(this.context);
            this.addListenersToDataSource(this.assetDataSource);
        }

        return this.assetDataSource;
    }

    private DataSource getContentDataSource() {
        if (this.contentDataSource == null) {
            this.contentDataSource = new ContentDataSource(this.context);
            this.addListenersToDataSource(this.contentDataSource);
        }

        return this.contentDataSource;
    }

    private DataSource getRtmpDataSource() {
        if (this.rtmpDataSource == null) {
            try {
                Class<?> clazz = Class.forName("com.zj.playerLib.ext.rtmp.RtmpDataSource");
                this.rtmpDataSource = (DataSource)clazz.getConstructor().newInstance();
                this.addListenersToDataSource(this.rtmpDataSource);
            } catch (ClassNotFoundException var2) {
                Log.w("DefaultDataSource", "Attempting to play RTMP stream without depending on the RTMP extension");
            } catch (Exception var3) {
                throw new RuntimeException("Error instantiating RTMP extension", var3);
            }

            if (this.rtmpDataSource == null) {
                this.rtmpDataSource = this.baseDataSource;
            }
        }

        return this.rtmpDataSource;
    }

    private DataSource getDataSchemeDataSource() {
        if (this.dataSchemeDataSource == null) {
            this.dataSchemeDataSource = new DataSchemeDataSource();
            this.addListenersToDataSource(this.dataSchemeDataSource);
        }

        return this.dataSchemeDataSource;
    }

    private DataSource getRawResourceDataSource() {
        if (this.rawResourceDataSource == null) {
            this.rawResourceDataSource = new RawResourceDataSource(this.context);
            this.addListenersToDataSource(this.rawResourceDataSource);
        }

        return this.rawResourceDataSource;
    }

    private void addListenersToDataSource(DataSource dataSource) {
        for(int i = 0; i < this.transferListeners.size(); ++i) {
            dataSource.addTransferListener((TransferListener)this.transferListeners.get(i));
        }

    }

    private void maybeAddListenerToDataSource(@Nullable DataSource dataSource, TransferListener listener) {
        if (dataSource != null) {
            dataSource.addTransferListener(listener);
        }

    }
}
