package com.zj.playerLib.drm;

import com.zj.playerLib.util.Assertions;
import java.util.Map;

public final class ErrorStateDrmSession<T extends MediaCrypto> implements DrmSession<T> {
    private final DrmSessionException error;

    public ErrorStateDrmSession(DrmSessionException error) {
        this.error = Assertions.checkNotNull(error);
    }

    public int getState() {
        return 1;
    }

    public DrmSessionException getError() {
        return this.error;
    }

    public T getMediaCrypto() {
        return null;
    }

    public Map<String, String> queryKeyStatus() {
        return null;
    }

    public byte[] getOfflineLicenseKeySetId() {
        return null;
    }
}
