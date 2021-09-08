//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

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
    public @interface State {
    }

    public static class DrmSessionException extends Exception {
        public DrmSessionException(Throwable cause) {
            super(cause);
        }
    }
}
