package com.zj.playerLib.drm;

import android.annotation.TargetApi;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

@TargetApi(16)
public interface DrmSession<T extends MediaCrypto> {
    int STATE_RELEASED = 0;
    int STATE_ERROR = 1;
    int STATE_OPENING = 2;
    int STATE_OPENED = 3;
    int STATE_OPENED_WITH_KEYS = 4;

    int getState();

    DrmSessionException getError();

    T getMediaCrypto();

    Map<String, String> queryKeyStatus();

    byte[] getOfflineLicenseKeySetId();

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @interface State {
    }

    class DrmSessionException extends Exception {
        public DrmSessionException(Throwable cause) {
            super(cause);
        }
    }
}
