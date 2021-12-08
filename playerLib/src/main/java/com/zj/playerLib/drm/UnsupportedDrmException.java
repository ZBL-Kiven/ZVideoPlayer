package com.zj.playerLib.drm;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class UnsupportedDrmException extends Exception {
    public static final int REASON_UNSUPPORTED_SCHEME = 1;
    public static final int REASON_INSTANTIATION_ERROR = 2;
    public final int reason;

    public UnsupportedDrmException(int reason) {
        this.reason = reason;
    }

    public UnsupportedDrmException(int reason, Exception cause) {
        super(cause);
        this.reason = reason;
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface Reason {
    }
}
