package com.zj.playerLib.audio;

import androidx.annotation.Nullable;

import com.zj.playerLib.PlaybackParameters;

import java.nio.ByteBuffer;

public interface AudioSink {
    long CURRENT_POSITION_NOT_SET = -9223372036854775808L;

    void setListener(Listener var1);

    boolean supportsOutput(int var1, int var2);

    long getCurrentPositionUs(boolean var1);

    void configure(int var1, int var2, int var3, int var4, @Nullable int[] var5, int var6, int var7) throws ConfigurationException;

    void play();

    void handleDiscontinuity();

    boolean handleBuffer(ByteBuffer var1, long var2) throws InitializationException, WriteException;

    void playToEndOfStream() throws WriteException;

    boolean isEnded();

    boolean hasPendingData();

    PlaybackParameters setPlaybackParameters(PlaybackParameters var1);

    PlaybackParameters getPlaybackParameters();

    void setAudioAttributes(AudioAttributes var1);

    void setAudioSessionId(int var1);

    void setAuxEffectInfo(AuxEffectInfo var1);

    void enableTunnelingV21(int var1);

    void disableTunneling();

    void setVolume(float var1);

    void pause();

    void reset();

    void release();

    final class WriteException extends Exception {
        public final int errorCode;

        public WriteException(int errorCode) {
            super("AudioTrack write failed: " + errorCode);
            this.errorCode = errorCode;
        }
    }

    final class InitializationException extends Exception {
        public final int audioTrackState;

        public InitializationException(int audioTrackState, int sampleRate, int channelConfig, int bufferSize) {
            super("AudioTrack init failed: " + audioTrackState + ", Config(" + sampleRate + ", " + channelConfig + ", " + bufferSize + ")");
            this.audioTrackState = audioTrackState;
        }
    }

    final class ConfigurationException extends Exception {
        public ConfigurationException(Throwable cause) {
            super(cause);
        }

        public ConfigurationException(String message) {
            super(message);
        }
    }

    interface Listener {
        void onAudioSessionId(int var1);

        void onPositionDiscontinuity();

        void onUnderRun(int var1, long var2, long var4);
    }
}
