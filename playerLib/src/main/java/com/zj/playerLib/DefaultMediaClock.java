package com.zj.playerLib;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Clock;
import com.zj.playerLib.util.MediaClock;
import com.zj.playerLib.util.StandaloneMediaClock;

final class DefaultMediaClock implements MediaClock {
    private final StandaloneMediaClock standaloneMediaClock;
    private final PlaybackParameterListener listener;
    @Nullable
    private Renderer rendererClockSource;
    @Nullable
    private MediaClock rendererClock;

    public DefaultMediaClock(PlaybackParameterListener listener, Clock clock) {
        this.listener = listener;
        this.standaloneMediaClock = new StandaloneMediaClock(clock);
    }

    public void start() {
        this.standaloneMediaClock.start();
    }

    public void stop() {
        this.standaloneMediaClock.stop();
    }

    public void resetPosition(long positionUs) {
        this.standaloneMediaClock.resetPosition(positionUs);
    }

    public void onRendererEnabled(Renderer renderer) throws PlaybackException {
        MediaClock rendererMediaClock = renderer.getMediaClock();
        if (rendererMediaClock != null && rendererMediaClock != this.rendererClock) {
            if (this.rendererClock != null) {
                throw PlaybackException.createForUnexpected(new IllegalStateException("Multiple renderer media clocks enabled."));
            }

            this.rendererClock = rendererMediaClock;
            this.rendererClockSource = renderer;
            this.rendererClock.setPlaybackParameters(this.standaloneMediaClock.getPlaybackParameters());
            this.ensureSynced();
        }

    }

    public void onRendererDisabled(Renderer renderer) {
        if (renderer == this.rendererClockSource) {
            this.rendererClock = null;
            this.rendererClockSource = null;
        }

    }

    public long syncAndGetPositionUs() {
        if (this.isUsingRendererClock()) {
            this.ensureSynced();
            return this.rendererClock.getPositionUs();
        } else {
            return this.standaloneMediaClock.getPositionUs();
        }
    }

    public long getPositionUs() {
        return this.isUsingRendererClock() ? this.rendererClock.getPositionUs() : this.standaloneMediaClock.getPositionUs();
    }

    public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
        if (this.rendererClock != null) {
            playbackParameters = this.rendererClock.setPlaybackParameters(playbackParameters);
        }

        this.standaloneMediaClock.setPlaybackParameters(playbackParameters);
        this.listener.onPlaybackParametersChanged(playbackParameters);
        return playbackParameters;
    }

    public PlaybackParameters getPlaybackParameters() {
        return this.rendererClock != null ? this.rendererClock.getPlaybackParameters() : this.standaloneMediaClock.getPlaybackParameters();
    }

    private void ensureSynced() {
        long rendererClockPositionUs = this.rendererClock.getPositionUs();
        this.standaloneMediaClock.resetPosition(rendererClockPositionUs);
        PlaybackParameters playbackParameters = this.rendererClock.getPlaybackParameters();
        if (!playbackParameters.equals(this.standaloneMediaClock.getPlaybackParameters())) {
            this.standaloneMediaClock.setPlaybackParameters(playbackParameters);
            this.listener.onPlaybackParametersChanged(playbackParameters);
        }

    }

    private boolean isUsingRendererClock() {
        return this.rendererClockSource != null && !this.rendererClockSource.isEnded() && (this.rendererClockSource.isReady() || !this.rendererClockSource.hasReadStreamToEnd());
    }

    public interface PlaybackParameterListener {
        void onPlaybackParametersChanged(PlaybackParameters var1);
    }
}
