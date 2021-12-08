package com.zj.playerLib.drm;

import android.annotation.TargetApi;
import android.net.Uri;
import android.text.TextUtils;

import com.zj.playerLib.C;
import com.zj.playerLib.drm.MediaDrm.KeyRequest;
import com.zj.playerLib.drm.MediaDrm.ProvisionRequest;
import com.zj.playerLib.upstream.DataSourceInputStream;
import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.upstream.HttpDataSource;
import com.zj.playerLib.upstream.HttpDataSource.Factory;
import com.zj.playerLib.upstream.HttpDataSource.InvalidResponseCodeException;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

@TargetApi(18)
public final class HttpMediaDrmCallback implements MediaDrmCallback {
    private static final int MAX_MANUAL_REDIRECTS = 5;
    private final Factory dataSourceFactory;
    private final String defaultLicenseUrl;
    private final boolean forceDefaultLicenseUrl;
    private final Map<String, String> keyRequestProperties;

    public HttpMediaDrmCallback(String defaultLicenseUrl, Factory dataSourceFactory) {
        this(defaultLicenseUrl, false, dataSourceFactory);
    }

    public HttpMediaDrmCallback(String defaultLicenseUrl, boolean forceDefaultLicenseUrl, Factory dataSourceFactory) {
        this.dataSourceFactory = dataSourceFactory;
        this.defaultLicenseUrl = defaultLicenseUrl;
        this.forceDefaultLicenseUrl = forceDefaultLicenseUrl;
        this.keyRequestProperties = new HashMap();
    }

    public void setKeyRequestProperty(String name, String value) {
        Assertions.checkNotNull(name);
        Assertions.checkNotNull(value);
        synchronized(this.keyRequestProperties) {
            this.keyRequestProperties.put(name, value);
        }
    }

    public void clearKeyRequestProperty(String name) {
        Assertions.checkNotNull(name);
        synchronized(this.keyRequestProperties) {
            this.keyRequestProperties.remove(name);
        }
    }

    public void clearAllKeyRequestProperties() {
        synchronized(this.keyRequestProperties) {
            this.keyRequestProperties.clear();
        }
    }

    public byte[] executeProvisionRequest(UUID uuid, ProvisionRequest request) throws IOException {
        String url = request.getDefaultUrl() + "&signedRequest=" + Util.fromUtf8Bytes(request.getData());
        return executePost(this.dataSourceFactory, url, Util.EMPTY_BYTE_ARRAY, null);
    }

    public byte[] executeKeyRequest(UUID uuid, KeyRequest request) throws Exception {
        String url = request.getLicenseServerUrl();
        if (this.forceDefaultLicenseUrl || TextUtils.isEmpty(url)) {
            url = this.defaultLicenseUrl;
        }

        Map<String, String> requestProperties = new HashMap();
        String contentType = C.PLAYREADY_UUID.equals(uuid) ? "text/xml" : (C.CLEARKEY_UUID.equals(uuid) ? "application/json" : "application/octet-stream");
        requestProperties.put("Content-Type", contentType);
        if (C.PLAYREADY_UUID.equals(uuid)) {
            requestProperties.put("SOAPAction", "http://schemas.microsoft.com/DRM/2007/03/protocols/AcquireLicense");
        }

        synchronized(this.keyRequestProperties) {
            requestProperties.putAll(this.keyRequestProperties);
        }

        return executePost(this.dataSourceFactory, url, request.getData(), requestProperties);
    }

    private static byte[] executePost(Factory dataSourceFactory, String url, byte[] data, Map<String, String> requestProperties) throws IOException {
        HttpDataSource dataSource = dataSourceFactory.createDataSource();
        if (requestProperties != null) {
            Iterator var5 = requestProperties.entrySet().iterator();

            while(var5.hasNext()) {
                Entry<String, String> requestProperty = (Entry)var5.next();
                dataSource.setRequestProperty(requestProperty.getKey(), requestProperty.getValue());
            }
        }

        int var15 = 0;

        while(true) {
            DataSpec dataSpec = new DataSpec(Uri.parse(url), data, 0L, 0L, -1L, null, 1);
            DataSourceInputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);

            try {
                byte[] var8 = Util.toByteArray(inputStream);
                return var8;
            } catch (InvalidResponseCodeException var13) {
                boolean manuallyRedirect = (var13.responseCode == 307 || var13.responseCode == 308) && var15++ < 5;
                url = manuallyRedirect ? getRedirectUrl(var13) : null;
                if (url == null) {
                    throw var13;
                }
            } finally {
                Util.closeQuietly(inputStream);
            }
        }
    }

    private static String getRedirectUrl(InvalidResponseCodeException exception) {
        Map<String, List<String>> headerFields = exception.headerFields;
        if (headerFields != null) {
            List<String> locationHeaders = headerFields.get("Location");
            if (locationHeaders != null && !locationHeaders.isEmpty()) {
                return locationHeaders.get(0);
            }
        }

        return null;
    }
}
