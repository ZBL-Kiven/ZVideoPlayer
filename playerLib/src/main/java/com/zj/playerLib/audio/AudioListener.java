package com.zj.playerLib.audio;

public interface AudioListener {
    default void onAudioSessionId(int audioSessionId) {
    }

    default void onAudioAttributesChanged(AudioAttributes audioAttributes) {
    }

    default void onVolumeChanged(float volume) {
    }
}
