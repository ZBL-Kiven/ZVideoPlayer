package com.zj.playerLib;

import androidx.annotation.Nullable;

public final class RendererConfiguration {
    public static final RendererConfiguration DEFAULT = new RendererConfiguration(0);
    public final int tunnelingAudioSessionId;

    public RendererConfiguration(int tunnelingAudioSessionId) {
        this.tunnelingAudioSessionId = tunnelingAudioSessionId;
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            RendererConfiguration other = (RendererConfiguration)obj;
            return this.tunnelingAudioSessionId == other.tunnelingAudioSessionId;
        } else {
            return false;
        }
    }

    public int hashCode() {
        return this.tunnelingAudioSessionId;
    }
}
