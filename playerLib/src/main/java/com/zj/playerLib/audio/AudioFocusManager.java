package com.zj.playerLib.audio;

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioFocusRequest.Builder;
import android.media.AudioManager.OnAudioFocusChangeListener;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.Util;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@SuppressWarnings("unused")
public final class AudioFocusManager {
    public static final int PLAYER_COMMAND_DO_NOT_PLAY = -1;
    public static final int PLAYER_COMMAND_WAIT_FOR_CALLBACK = 0;
    public static final int PLAYER_COMMAND_PLAY_WHEN_READY = 1;
    private static final int AUDIO_FOCUS_STATE_LOST_FOCUS = -1;
    private static final int AUDIO_FOCUS_STATE_NO_FOCUS = 0;
    private static final int AUDIO_FOCUS_STATE_HAVE_FOCUS = 1;
    private static final int AUDIO_FOCUS_STATE_LOSS_TRANSIENT = 2;
    private static final int AUDIO_FOCUS_STATE_LOSS_TRANSIENT_DUCK = 3;
    private static final String TAG = "AudioFocusManager";
    private static final float VOLUME_MULTIPLIER_DUCK = 0.2F;
    private static final float VOLUME_MULTIPLIER_DEFAULT = 1.0F;
    @Nullable
    private final AudioManager audioManager;
    private final AudioFocusListener focusListener;
    private final PlayerControl playerControl;
    @Nullable
    private AudioAttributes audioAttributes;
    private int audioFocusState;
    private int focusGain;
    private float volumeMultiplier = 1.0F;
    private AudioFocusRequest audioFocusRequest;
    private boolean rebuildAudioFocusRequest;

    public AudioFocusManager(@Nullable Context context, PlayerControl playerControl) {
        this.audioManager = context == null ? null : (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        this.playerControl = playerControl;
        this.focusListener = new AudioFocusListener();
        this.audioFocusState = 0;
    }

    public float getVolumeMultiplier() {
        return this.volumeMultiplier;
    }

    public int setAudioAttributes(@Nullable AudioAttributes audioAttributes, boolean playWhenReady, int playerState) {
        if (this.audioAttributes == null && audioAttributes == null) {
            return playWhenReady ? 1 : -1;
        } else {
            Assertions.checkNotNull(this.audioManager, "Player must be created with a context to handle audio focus.");
            if (!Util.areEqual(this.audioAttributes, audioAttributes)) {
                this.audioAttributes = audioAttributes;
                this.focusGain = convertAudioAttributesToFocusGain(audioAttributes);
                Assertions.checkArgument(this.focusGain == 1 || this.focusGain == 0, "Automatic handling of audio focus is only available for USAGE_MEDIA and USAGE_GAME.");
                if (playWhenReady && (playerState == 2 || playerState == 3)) {
                    return this.requestAudioFocus();
                }
            }

            return playerState == 1 ? this.handleIdle(playWhenReady) : this.handlePrepare(playWhenReady);
        }
    }

    public int handlePrepare(boolean playWhenReady) {
        if (this.audioManager == null) {
            return 1;
        } else {
            return playWhenReady ? this.requestAudioFocus() : -1;
        }
    }

    public int handleSetPlayWhenReady(boolean playWhenReady, int playerState) {
        if (this.audioManager == null) {
            return 1;
        } else if (!playWhenReady) {
            this.abandonAudioFocus();
            return -1;
        } else {
            return playerState == 1 ? this.handleIdle(playWhenReady) : this.requestAudioFocus();
        }
    }

    public void handleStop() {
        if (this.audioManager != null) {
            this.abandonAudioFocus(true);
        }
    }

    private int handleIdle(boolean playWhenReady) {
        return playWhenReady ? 1 : -1;
    }

    private int requestAudioFocus() {
        if (this.focusGain == 0) {
            if (this.audioFocusState != 0) {
                this.abandonAudioFocus(true);
            }

            return 1;
        } else {
            if (this.audioFocusState == 0) {
                int focusRequestResult;
                if (Util.SDK_INT >= 26) {
                    focusRequestResult = this.requestAudioFocusV26();
                } else {
                    focusRequestResult = this.requestAudioFocusDefault();
                }

                this.audioFocusState = focusRequestResult == 1 ? 1 : 0;
            }

            if (this.audioFocusState == 0) {
                return -1;
            } else {
                return this.audioFocusState == 2 ? 0 : 1;
            }
        }
    }

    private void abandonAudioFocus() {
        this.abandonAudioFocus(false);
    }

    private void abandonAudioFocus(boolean forceAbandon) {
        if (this.focusGain != 0 || this.audioFocusState != 0) {
            if (this.focusGain != 1 || this.audioFocusState == -1 || forceAbandon) {
                if (Util.SDK_INT >= 26) {
                    this.abandonAudioFocusV26();
                } else {
                    this.abandonAudioFocusDefault();
                }

                this.audioFocusState = 0;
            }

        }
    }

    private int requestAudioFocusDefault() {
        AudioManager audioManager = Assertions.checkNotNull(this.audioManager);
        return audioManager.requestAudioFocus(this.focusListener, Util.getStreamTypeForAudioUsage(Assertions.checkNotNull(this.audioAttributes).usage), this.focusGain);
    }

    @RequiresApi(26)
    private int requestAudioFocusV26() {
        if (this.audioFocusRequest == null || this.rebuildAudioFocusRequest) {
            Builder builder = this.audioFocusRequest == null ? new Builder(this.focusGain) : new Builder(this.audioFocusRequest);
            boolean willPauseWhenDucked = this.willPauseWhenDucked();
            this.audioFocusRequest = builder.setAudioAttributes(Assertions.checkNotNull(this.audioAttributes).getAudioAttributesV21()).setWillPauseWhenDucked(willPauseWhenDucked).setOnAudioFocusChangeListener(this.focusListener).build();
            this.rebuildAudioFocusRequest = false;
        }

        return Assertions.checkNotNull(this.audioManager).requestAudioFocus(this.audioFocusRequest);
    }

    private void abandonAudioFocusDefault() {
        Assertions.checkNotNull(this.audioManager).abandonAudioFocus(this.focusListener);
    }

    @RequiresApi(26)
    private void abandonAudioFocusV26() {
        if (this.audioFocusRequest != null) {
            Assertions.checkNotNull(this.audioManager).abandonAudioFocusRequest(this.audioFocusRequest);
        }

    }

    private boolean willPauseWhenDucked() {
        return this.audioAttributes != null && this.audioAttributes.contentType == 1;
    }

    private static int convertAudioAttributesToFocusGain(@Nullable AudioAttributes audioAttributes) {
        if (audioAttributes == null) {
            return 0;
        } else {
            switch (audioAttributes.usage) {
                case 0:
                    Log.w("AudioFocusManager", "Specify a proper usage in the audio attributes for audio focus handling. Using AUDIOFOCUS_GAIN by default.");
                    return 1;
                case 1:
                case 14:
                    return 1;
                case 2:
                case 4:
                    return 2;
                case 3:
                    return 0;
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                case 10:
                case 12:
                case 13:
                    return 3;
                case 11:
                    if (audioAttributes.contentType == 1) {
                        return 2;
                    }

                    return 3;
                case 15:
                default:
                    Log.w("AudioFocusManager", "Unidentified audio usage: " + audioAttributes.usage);
                    return 0;
                case 16:
                    return Util.SDK_INT >= 19 ? 4 : 2;
            }
        }
    }

    private class AudioFocusListener implements OnAudioFocusChangeListener {
        private AudioFocusListener() {
        }

        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case -3:
                    if (AudioFocusManager.this.willPauseWhenDucked()) {
                        AudioFocusManager.this.audioFocusState = 2;
                    } else {
                        AudioFocusManager.this.audioFocusState = 3;
                    }
                    break;
                case -2:
                    AudioFocusManager.this.audioFocusState = 2;
                    break;
                case -1:
                    AudioFocusManager.this.audioFocusState = -1;
                    break;
                case 0:
                default:
                    Log.w("AudioFocusManager", "Unknown focus change type: " + focusChange);
                    return;
                case 1:
                    AudioFocusManager.this.audioFocusState = 1;
            }

            switch (AudioFocusManager.this.audioFocusState) {
                case -1:
                    AudioFocusManager.this.playerControl.executePlayerCommand(-1);
                    AudioFocusManager.this.abandonAudioFocus(true);
                case 3:
                    break;
                case 1:
                    AudioFocusManager.this.playerControl.executePlayerCommand(1);
                    break;
                case 2:
                    AudioFocusManager.this.playerControl.executePlayerCommand(0);
                    break;
                default:
                    throw new IllegalStateException("Unknown audio focus state: " + AudioFocusManager.this.audioFocusState);
            }

            float volumeMultiplier = AudioFocusManager.this.audioFocusState == 3 ? 0.2F : 1.0F;
            if (AudioFocusManager.this.volumeMultiplier != volumeMultiplier) {
                AudioFocusManager.this.volumeMultiplier = volumeMultiplier;
                AudioFocusManager.this.playerControl.setVolumeMultiplier(volumeMultiplier);
            }

        }
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface PlayerCommand {}

    public interface PlayerControl {
        void setVolumeMultiplier(float var1);

        void executePlayerCommand(int var1);
    }
}
