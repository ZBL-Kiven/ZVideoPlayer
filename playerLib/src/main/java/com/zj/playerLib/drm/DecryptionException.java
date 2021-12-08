package com.zj.playerLib.drm;

public class DecryptionException extends Exception {
    public final int errorCode;

    public DecryptionException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
