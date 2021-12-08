package com.zj.playerLib.drm;

public interface DefaultDrmSessionEventListener {
    default void onDrmSessionAcquired() {
    }

    void onDrmKeysLoaded();

    void onDrmSessionManagerError(Exception var1);

    void onDrmKeysRestored();

    void onDrmKeysRemoved();

    default void onDrmSessionReleased() {
    }
}
