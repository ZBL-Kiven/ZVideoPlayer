package com.zj.playerLib.drm;

import android.annotation.TargetApi;
import android.os.Looper;

@TargetApi(16)
public interface DrmSessionManager<T extends MediaCrypto> {
    boolean canAcquireSession(DrmInitData var1);

    DrmSession<T> acquireSession(Looper var1, DrmInitData var2);

    void releaseSession(DrmSession<T> var1);
}
