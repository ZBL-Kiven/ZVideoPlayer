//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.drm;

import android.media.DeniedByServerException;
import android.media.MediaCryptoException;
import android.media.MediaDrmException;
import android.media.NotProvisionedException;
import androidx.annotation.Nullable;
import com.zj.playerLib.drm.DrmInitData.SchemeData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface MediaDrm<T extends MediaCrypto> {
    int EVENT_KEY_REQUIRED = 2;
    int EVENT_KEY_EXPIRED = 3;
    int EVENT_PROVISION_REQUIRED = 1;
    int KEY_TYPE_STREAMING = 1;
    int KEY_TYPE_OFFLINE = 2;
    int KEY_TYPE_RELEASE = 3;

    void setOnEventListener(OnEventListener<? super T> var1);

    void setOnKeyStatusChangeListener(OnKeyStatusChangeListener<? super T> var1);

    byte[] openSession() throws MediaDrmException;

    void closeSession(byte[] var1);

    KeyRequest getKeyRequest(byte[] var1, @Nullable List<SchemeData> var2, int var3, @Nullable HashMap<String, String> var4) throws NotProvisionedException;

    byte[] provideKeyResponse(byte[] var1, byte[] var2) throws NotProvisionedException, DeniedByServerException;

    ProvisionRequest getProvisionRequest();

    void provideProvisionResponse(byte[] var1) throws DeniedByServerException;

    Map<String, String> queryKeyStatus(byte[] var1);

    void release();

    void restoreKeys(byte[] var1, byte[] var2);

    String getPropertyString(String var1);

    byte[] getPropertyByteArray(String var1);

    void setPropertyString(String var1, String var2);

    void setPropertyByteArray(String var1, byte[] var2);

    T createMediaCrypto(byte[] var1) throws MediaCryptoException;

    public static final class ProvisionRequest {
        private final byte[] data;
        private final String defaultUrl;

        public ProvisionRequest(byte[] data, String defaultUrl) {
            this.data = data;
            this.defaultUrl = defaultUrl;
        }

        public byte[] getData() {
            return this.data;
        }

        public String getDefaultUrl() {
            return this.defaultUrl;
        }
    }

    public static final class KeyRequest {
        private final byte[] data;
        private final String licenseServerUrl;

        public KeyRequest(byte[] data, String licenseServerUrl) {
            this.data = data;
            this.licenseServerUrl = licenseServerUrl;
        }

        public byte[] getData() {
            return this.data;
        }

        public String getLicenseServerUrl() {
            return this.licenseServerUrl;
        }
    }

    public static final class KeyStatus {
        private final int statusCode;
        private final byte[] keyId;

        public KeyStatus(int statusCode, byte[] keyId) {
            this.statusCode = statusCode;
            this.keyId = keyId;
        }

        public int getStatusCode() {
            return this.statusCode;
        }

        public byte[] getKeyId() {
            return this.keyId;
        }
    }

    public interface OnKeyStatusChangeListener<T extends MediaCrypto> {
        void onKeyStatusChange(MediaDrm<? extends T> var1, byte[] var2, List<KeyStatus> var3, boolean var4);
    }

    public interface OnEventListener<T extends MediaCrypto> {
        void onEvent(MediaDrm<? extends T> var1, byte[] var2, int var3, int var4, @Nullable byte[] var5);
    }
}
