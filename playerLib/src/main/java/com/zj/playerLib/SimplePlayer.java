package com.zj.playerLib;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.SurfaceHolder.Callback;
import android.view.TextureView.SurfaceTextureListener;

import androidx.annotation.Nullable;

import com.zj.playerLib.Player.AudioComponent;
import com.zj.playerLib.Player.MetadataComponent;
import com.zj.playerLib.Player.TextComponent;
import com.zj.playerLib.Player.VideoComponent;
import com.zj.playerLib.PlayerMessage.Target;
import com.zj.playerLib.analytics.PlayerStateCollector;
import com.zj.playerLib.analytics.PlayerStateListener;
import com.zj.playerLib.analytics.PlayerStateCollector.Factory;
import com.zj.playerLib.audio.AudioAttributes;
import com.zj.playerLib.audio.AudioFocusManager;
import com.zj.playerLib.audio.AudioListener;
import com.zj.playerLib.audio.AudioRendererEventListener;
import com.zj.playerLib.audio.AuxEffectInfo;
import com.zj.playerLib.audio.AudioFocusManager.PlayerControl;
import com.zj.playerLib.decoder.DecoderCounters;
import com.zj.playerLib.drm.DefaultDrmSessionManager;
import com.zj.playerLib.drm.DrmSessionManager;
import com.zj.playerLib.drm.FrameworkMediaCrypto;
import com.zj.playerLib.metadata.Metadata;
import com.zj.playerLib.metadata.MetadataOutput;
import com.zj.playerLib.source.MediaSource;
import com.zj.playerLib.source.TrackGroupArray;
import com.zj.playerLib.text.Cue;
import com.zj.playerLib.text.TextOutput;
import com.zj.playerLib.trackselection.TrackSelectionArray;
import com.zj.playerLib.trackselection.TrackSelector;
import com.zj.playerLib.upstream.BandwidthMeter;
import com.zj.playerLib.util.Clock;
import com.zj.playerLib.util.Util;
import com.zj.playerLib.video.VideoFrameMetadataListener;
import com.zj.playerLib.video.VideoListener;
import com.zj.playerLib.video.VideoRendererEventListener;
import com.zj.playerLib.video.spherical.CameraMotionListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;


@SuppressWarnings("unused")
public class SimplePlayer extends BasePlayer implements InlinePlayer, AudioComponent, VideoComponent, TextComponent, MetadataComponent {
    protected final Renderer[] renderers;
    private final PlayerImpl player;
    private final Handler eventHandler;
    private final ComponentListener componentListener;
    private final CopyOnWriteArraySet<VideoListener> videoListeners;
    private final CopyOnWriteArraySet<AudioListener> audioListeners;
    private final CopyOnWriteArraySet<TextOutput> textOutputs;
    private final CopyOnWriteArraySet<MetadataOutput> metadataOutputs;
    private final CopyOnWriteArraySet<VideoRendererEventListener> videoDebugListeners;
    private final CopyOnWriteArraySet<AudioRendererEventListener> audioDebugListeners;
    private final BandwidthMeter bandwidthMeter;
    private final PlayerStateCollector analyticsCollector;
    private final AudioFocusManager audioFocusManager;
    private Format videoFormat;
    private Format audioFormat;
    private Surface surface;
    private boolean ownsSurface;
    private int videoScalingMode;
    private SurfaceHolder surfaceHolder;
    private TextureView textureView;
    private int surfaceWidth;
    private int surfaceHeight;
    private DecoderCounters videoDecoderCounters;
    private DecoderCounters audioDecoderCounters;
    private int audioSessionId;
    private AudioAttributes audioAttributes;
    private float audioVolume;
    private MediaSource mediaSource;
    private List<Cue> currentCues;
    private VideoFrameMetadataListener videoFrameMetadataListener;
    private CameraMotionListener cameraMotionListener;

    protected SimplePlayer(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, BandwidthMeter bandwidthMeter, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, Looper looper) {
        this(context, renderersFactory, trackSelector, loadControl, drmSessionManager, bandwidthMeter, new Factory(), looper);
    }

    protected SimplePlayer(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, BandwidthMeter bandwidthMeter, Factory analyticsCollectorFactory, Looper looper) {
        this(context, renderersFactory, trackSelector, loadControl, drmSessionManager, bandwidthMeter, analyticsCollectorFactory, Clock.DEFAULT, looper);
    }

    protected SimplePlayer(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, BandwidthMeter bandwidthMeter, Factory analyticsCollectorFactory, Clock clock, Looper looper) {
        this.bandwidthMeter = bandwidthMeter;
        this.componentListener = new ComponentListener();
        this.videoListeners = new CopyOnWriteArraySet<>();
        this.audioListeners = new CopyOnWriteArraySet<>();
        this.textOutputs = new CopyOnWriteArraySet<>();
        this.metadataOutputs = new CopyOnWriteArraySet<>();
        this.videoDebugListeners = new CopyOnWriteArraySet<>();
        this.audioDebugListeners = new CopyOnWriteArraySet<>();
        this.eventHandler = new Handler(looper);
        this.renderers = renderersFactory.createRenderers(this.eventHandler, this.componentListener, this.componentListener, this.componentListener, this.componentListener, drmSessionManager);
        this.audioVolume = 1.0F;
        this.audioSessionId = 0;
        this.audioAttributes = AudioAttributes.DEFAULT;
        this.videoScalingMode = 1;
        this.currentCues = Collections.emptyList();
        this.player = new PlayerImpl(this.renderers, trackSelector, loadControl, bandwidthMeter, clock, looper);
        this.analyticsCollector = analyticsCollectorFactory.createAnalyticsCollector(this.player, clock);
        this.addListener(this.analyticsCollector);
        this.videoDebugListeners.add(this.analyticsCollector);
        this.videoListeners.add(this.analyticsCollector);
        this.audioDebugListeners.add(this.analyticsCollector);
        this.audioListeners.add(this.analyticsCollector);
        this.addMetadataOutput(this.analyticsCollector);
        bandwidthMeter.addEventListener(this.eventHandler, this.analyticsCollector);
        if (drmSessionManager instanceof DefaultDrmSessionManager) {
            ((DefaultDrmSessionManager<?>) drmSessionManager).addListener(this.eventHandler, this.analyticsCollector);
        }

        this.audioFocusManager = new AudioFocusManager(context, this.componentListener);
    }

    @Nullable
    public AudioComponent getAudioComponent() {
        return this;
    }

    @Nullable
    public VideoComponent getVideoComponent() {
        return this;
    }

    @Nullable
    public TextComponent getTextComponent() {
        return this;
    }

    @Nullable
    public MetadataComponent getMetadataComponent() {
        return this;
    }

    public void setVideoScalingMode(int videoScalingMode) {
        this.verifyApplicationThread();
        this.videoScalingMode = videoScalingMode;
        for (Renderer renderer : this.renderers) {
            if (renderer.getTrackType() == 2) {
                this.player.createMessage(renderer).setType(4).setPayload(videoScalingMode).send();
            }
        }
    }

    public int getVideoScalingMode() {
        return this.videoScalingMode;
    }

    public void clearVideoSurface() {
        this.verifyApplicationThread();
        this.setVideoSurface(null);
    }

    public void clearVideoSurface(Surface surface) {
        this.verifyApplicationThread();
        if (surface != null && surface == this.surface) {
            this.setVideoSurface(null);
        }

    }

    public void setVideoSurface(@Nullable Surface surface) {
        this.verifyApplicationThread();
        this.removeSurfaceCallbacks();
        this.setVideoSurfaceInternal(surface, false);
        int newSurfaceSize = surface == null ? 0 : -1;
        this.maybeNotifySurfaceSizeChanged(newSurfaceSize, newSurfaceSize);
    }

    public void setVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
        this.verifyApplicationThread();
        this.removeSurfaceCallbacks();
        this.surfaceHolder = surfaceHolder;
        if (surfaceHolder == null) {
            this.setVideoSurfaceInternal(null, false);
            this.maybeNotifySurfaceSizeChanged(0, 0);
        } else {
            surfaceHolder.addCallback(this.componentListener);
            Surface surface = surfaceHolder.getSurface();
            if (surface != null && surface.isValid()) {
                this.setVideoSurfaceInternal(surface, false);
                Rect surfaceSize = surfaceHolder.getSurfaceFrame();
                this.maybeNotifySurfaceSizeChanged(surfaceSize.width(), surfaceSize.height());
            } else {
                this.setVideoSurfaceInternal(null, false);
                this.maybeNotifySurfaceSizeChanged(0, 0);
            }
        }
    }

    public void clearVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
        this.verifyApplicationThread();
        if (surfaceHolder != null && surfaceHolder == this.surfaceHolder) {
            this.setVideoSurfaceHolder(null);
        }
    }

    public void setVideoSurfaceView(SurfaceView surfaceView) {
        this.setVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
    }

    public void clearVideoSurfaceView(SurfaceView surfaceView) {
        this.clearVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
    }

    public void setVideoTextureView(TextureView textureView) {
        this.verifyApplicationThread();
        this.removeSurfaceCallbacks();
        this.textureView = textureView;
        if (textureView == null) {
            this.setVideoSurfaceInternal(null, true);
            this.maybeNotifySurfaceSizeChanged(0, 0);
        } else {
            textureView.setSurfaceTextureListener(this.componentListener);
            SurfaceTexture surfaceTexture = textureView.isAvailable() ? textureView.getSurfaceTexture() : null;
            if (surfaceTexture == null) {
                this.setVideoSurfaceInternal(null, true);
                this.maybeNotifySurfaceSizeChanged(0, 0);
            } else {
                this.setVideoSurfaceInternal(new Surface(surfaceTexture), true);
                this.maybeNotifySurfaceSizeChanged(textureView.getWidth(), textureView.getHeight());
            }
        }

    }

    public void clearVideoTextureView(TextureView textureView) {
        this.verifyApplicationThread();
        if (textureView != null && textureView == this.textureView) {
            this.setVideoTextureView(null);
        }

    }

    public void addAudioListener(AudioListener listener) {
        this.audioListeners.add(listener);
    }

    public void removeAudioListener(AudioListener listener) {
        this.audioListeners.remove(listener);
    }

    public void setAudioAttributes(AudioAttributes audioAttributes) {
        this.setAudioAttributes(audioAttributes, false);
    }

    public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
        this.verifyApplicationThread();
        if (!Util.areEqual(this.audioAttributes, audioAttributes)) {
            this.audioAttributes = audioAttributes;
            for (Renderer renderer : this.renderers) {
                if (renderer.getTrackType() == 1) {
                    this.player.createMessage(renderer).setType(3).setPayload(audioAttributes).send();
                }
            }
            for (AudioListener audioListener : this.audioListeners) {
                audioListener.onAudioAttributesChanged(audioAttributes);
            }
        }

        int playerCommand = this.audioFocusManager.setAudioAttributes(handleAudioFocus ? audioAttributes : null, this.getPlayWhenReady(), this.getPlaybackState());
        this.updatePlayWhenReady(this.getPlayWhenReady(), playerCommand);
    }

    public AudioAttributes getAudioAttributes() {
        return this.audioAttributes;
    }

    public int getAudioSessionId() {
        return this.audioSessionId;
    }

    public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
        this.verifyApplicationThread();
        for (Renderer renderer : this.renderers) {
            if (renderer.getTrackType() == 1) {
                this.player.createMessage(renderer).setType(5).setPayload(auxEffectInfo).send();
            }
        }
    }

    public void clearAuxEffectInfo() {
        this.setAuxEffectInfo(new AuxEffectInfo(0, 0.0F));
    }

    public void setVolume(float audioVolume) {
        this.verifyApplicationThread();
        audioVolume = Util.constrainValue(audioVolume, 0.0F, 1.0F);
        if (this.audioVolume != audioVolume) {
            this.audioVolume = audioVolume;
            this.sendVolumeToRenderers();
            for (AudioListener audioListener : this.audioListeners) {
                audioListener.onVolumeChanged(audioVolume);
            }
        }
    }

    public float getVolume() {
        return this.audioVolume;
    }

    public PlayerStateCollector getAnalyticsCollector() {
        return this.analyticsCollector;
    }

    public void addAnalyticsListener(PlayerStateListener listener) {
        this.verifyApplicationThread();
        this.analyticsCollector.addListener(listener);
    }

    public void removeAnalyticsListener(PlayerStateListener listener) {
        this.verifyApplicationThread();
        this.analyticsCollector.removeListener(listener);
    }

    public Format getVideoFormat() {
        return this.videoFormat;
    }

    public Format getAudioFormat() {
        return this.audioFormat;
    }

    public DecoderCounters getVideoDecoderCounters() {
        return this.videoDecoderCounters;
    }

    public DecoderCounters getAudioDecoderCounters() {
        return this.audioDecoderCounters;
    }

    public void addVideoListener(VideoListener listener) {
        this.videoListeners.add(listener);
    }

    public void removeVideoListener(VideoListener listener) {
        this.videoListeners.remove(listener);
    }

    public void setVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
        this.verifyApplicationThread();
        this.videoFrameMetadataListener = listener;
        for (Renderer renderer : this.renderers) {
            if (renderer.getTrackType() == 2) {
                this.player.createMessage(renderer).setType(6).setPayload(listener).send();
            }
        }

    }

    public void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
        this.verifyApplicationThread();
        if (this.videoFrameMetadataListener == listener) {
            for (Renderer renderer : this.renderers) {
                if (renderer.getTrackType() == 2) {
                    this.player.createMessage(renderer).setType(6).setPayload(null).send();
                }
            }
        }
    }

    public void setCameraMotionListener(CameraMotionListener listener) {
        this.verifyApplicationThread();
        this.cameraMotionListener = listener;
        for (Renderer renderer : this.renderers) {
            if (renderer.getTrackType() == 5) {
                this.player.createMessage(renderer).setType(7).setPayload(listener).send();
            }
        }
    }

    public void clearCameraMotionListener(CameraMotionListener listener) {
        this.verifyApplicationThread();
        if (this.cameraMotionListener == listener) {
            for (Renderer renderer : this.renderers) {
                if (renderer.getTrackType() == 5) {
                    this.player.createMessage(renderer).setType(7).setPayload(null).send();
                }
            }
        }
    }

    public void addTextOutput(TextOutput listener) {
        if (!this.currentCues.isEmpty()) {
            listener.onCues(this.currentCues);
        }

        this.textOutputs.add(listener);
    }

    public void removeTextOutput(TextOutput listener) {
        this.textOutputs.remove(listener);
    }

    public void addMetadataOutput(MetadataOutput listener) {
        this.metadataOutputs.add(listener);
    }

    public void removeMetadataOutput(MetadataOutput listener) {
        this.metadataOutputs.remove(listener);
    }


    public Looper getPlaybackLooper() {
        return this.player.getPlaybackLooper();
    }

    public Looper getApplicationLooper() {
        return this.player.getApplicationLooper();
    }

    public void addListener(EventListener listener) {
        this.verifyApplicationThread();
        this.player.addListener(listener);
    }

    public void removeListener(EventListener listener) {
        this.verifyApplicationThread();
        this.player.removeListener(listener);
    }

    public int getPlaybackState() {
        this.verifyApplicationThread();
        return this.player.getPlaybackState();
    }

    @Nullable
    public PlaybackException getPlaybackError() {
        this.verifyApplicationThread();
        return this.player.getPlaybackError();
    }

    public void retry() {
        this.verifyApplicationThread();
        if (this.mediaSource != null && (this.getPlaybackError() != null || this.getPlaybackState() == 1)) {
            this.prepare(this.mediaSource, false, false);
        }

    }

    public void prepare(MediaSource mediaSource) {
        this.prepare(mediaSource, true, true);
    }

    public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
        this.verifyApplicationThread();
        if (this.mediaSource != null) {
            this.mediaSource.removeEventListener(this.analyticsCollector);
            this.analyticsCollector.resetForNewMediaSource();
        }

        this.mediaSource = mediaSource;
        mediaSource.addEventListener(this.eventHandler, this.analyticsCollector);
        int playerCommand = this.audioFocusManager.handlePrepare(this.getPlayWhenReady());
        this.updatePlayWhenReady(this.getPlayWhenReady(), playerCommand);
        this.player.prepare(mediaSource, resetPosition, resetState);
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        this.verifyApplicationThread();
        int playerCommand = this.audioFocusManager.handleSetPlayWhenReady(playWhenReady, this.getPlaybackState());
        this.updatePlayWhenReady(playWhenReady, playerCommand);
    }

    public boolean getPlayWhenReady() {
        this.verifyApplicationThread();
        return this.player.getPlayWhenReady();
    }

    public int getRepeatMode() {
        this.verifyApplicationThread();
        return this.player.getRepeatMode();
    }

    public void setRepeatMode(int repeatMode) {
        this.verifyApplicationThread();
        this.player.setRepeatMode(repeatMode);
    }

    public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
        this.verifyApplicationThread();
        this.player.setShuffleModeEnabled(shuffleModeEnabled);
    }

    public boolean getShuffleModeEnabled() {
        this.verifyApplicationThread();
        return this.player.getShuffleModeEnabled();
    }

    public boolean isLoading() {
        this.verifyApplicationThread();
        return this.player.isLoading();
    }

    public void seekTo(int windowIndex, long positionMs) {
        this.verifyApplicationThread();
        this.analyticsCollector.notifySeekStarted();
        this.player.seekTo(windowIndex, positionMs);
    }

    public void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters) {
        this.verifyApplicationThread();
        this.player.setPlaybackParameters(playbackParameters);
    }

    public PlaybackParameters getPlaybackParameters() {
        this.verifyApplicationThread();
        return this.player.getPlaybackParameters();
    }

    public void setSeekParameters(@Nullable SeekParameters seekParameters) {
        this.verifyApplicationThread();
        this.player.setSeekParameters(seekParameters);
    }

    public SeekParameters getSeekParameters() {
        this.verifyApplicationThread();
        return this.player.getSeekParameters();
    }

    public void stop(boolean reset) {
        this.verifyApplicationThread();
        this.player.stop(reset);
        if (this.mediaSource != null) {
            this.mediaSource.removeEventListener(this.analyticsCollector);
            this.analyticsCollector.resetForNewMediaSource();
            if (reset) {
                this.mediaSource = null;
            }
        }

        this.audioFocusManager.handleStop();
        this.currentCues = Collections.emptyList();
    }

    public void release() {
        this.audioFocusManager.handleStop();
        this.player.release();
        this.removeSurfaceCallbacks();
        if (this.surface != null) {
            if (this.ownsSurface) {
                this.surface.release();
            }

            this.surface = null;
        }

        if (this.mediaSource != null) {
            this.mediaSource.removeEventListener(this.analyticsCollector);
            this.mediaSource = null;
        }

        this.bandwidthMeter.removeEventListener(this.analyticsCollector);
        this.currentCues = Collections.emptyList();
    }

    public PlayerMessage createMessage(Target target) {
        this.verifyApplicationThread();
        return this.player.createMessage(target);
    }

    public int getRendererCount() {
        this.verifyApplicationThread();
        return this.player.getRendererCount();
    }

    public int getRendererType(int index) {
        this.verifyApplicationThread();
        return this.player.getRendererType(index);
    }

    public TrackGroupArray getCurrentTrackGroups() {
        this.verifyApplicationThread();
        return this.player.getCurrentTrackGroups();
    }

    public TrackSelectionArray getCurrentTrackSelections() {
        this.verifyApplicationThread();
        return this.player.getCurrentTrackSelections();
    }

    public Timeline getCurrentTimeline() {
        this.verifyApplicationThread();
        return this.player.getCurrentTimeline();
    }

    @Nullable
    public Object getCurrentManifest() {
        this.verifyApplicationThread();
        return this.player.getCurrentManifest();
    }

    public int getCurrentPeriodIndex() {
        this.verifyApplicationThread();
        return this.player.getCurrentPeriodIndex();
    }

    public int getCurrentWindowIndex() {
        this.verifyApplicationThread();
        return this.player.getCurrentWindowIndex();
    }

    public long getDuration() {
        this.verifyApplicationThread();
        return this.player.getDuration();
    }

    public long getCurrentPosition() {
        this.verifyApplicationThread();
        return this.player.getCurrentPosition();
    }

    public long getBufferedPosition() {
        this.verifyApplicationThread();
        return this.player.getBufferedPosition();
    }

    public long getTotalBufferedDuration() {
        this.verifyApplicationThread();
        return this.player.getTotalBufferedDuration();
    }

    public boolean isPlayingAd() {
        this.verifyApplicationThread();
        return this.player.isPlayingAd();
    }

    public int getCurrentAdGroupIndex() {
        this.verifyApplicationThread();
        return this.player.getCurrentAdGroupIndex();
    }

    public int getCurrentAdIndexInAdGroup() {
        this.verifyApplicationThread();
        return this.player.getCurrentAdIndexInAdGroup();
    }

    public long getContentPosition() {
        this.verifyApplicationThread();
        return this.player.getContentPosition();
    }

    public long getContentBufferedPosition() {
        this.verifyApplicationThread();
        return this.player.getContentBufferedPosition();
    }

    private void removeSurfaceCallbacks() {
        if (this.textureView != null) {
            if (this.textureView.getSurfaceTextureListener() == this.componentListener) {
                this.textureView.setSurfaceTextureListener(null);
            }
            this.textureView = null;
        }

        if (this.surfaceHolder != null) {
            this.surfaceHolder.removeCallback(this.componentListener);
            this.surfaceHolder = null;
        }
    }

    private void setVideoSurfaceInternal(@Nullable Surface surface, boolean ownsSurface) {
        List<PlayerMessage> messages = new ArrayList<>();
        for (Renderer renderer : this.renderers) {
            if (renderer.getTrackType() == 2) {
                messages.add(this.player.createMessage(renderer).setType(1).setPayload(surface).send());
            }
        }

        if (this.surface != null && this.surface != surface) {
            try {
                for (PlayerMessage message : messages) {
                    message.blockUntilDelivered();
                }
            } catch (InterruptedException var8) {
                Thread.currentThread().interrupt();
            }

            if (this.ownsSurface) {
                this.surface.release();
            }
        }

        this.surface = surface;
        this.ownsSurface = ownsSurface;
    }

    private void maybeNotifySurfaceSizeChanged(int width, int height) {
        if (width != this.surfaceWidth || height != this.surfaceHeight) {
            this.surfaceWidth = width;
            this.surfaceHeight = height;
            for (VideoListener videoListener : this.videoListeners) {
                videoListener.onSurfaceSizeChanged(width, height);
            }
        }

    }

    private void sendVolumeToRenderers() {
        float scaledVolume = this.audioVolume * this.audioFocusManager.getVolumeMultiplier();
        for (Renderer renderer : this.renderers) {
            if (renderer.getTrackType() == 1) {
                this.player.createMessage(renderer).setType(2).setPayload(scaledVolume).send();
            }
        }
    }

    private void updatePlayWhenReady(boolean playWhenReady, int playerCommand) {
        this.player.setPlayWhenReady(playWhenReady && playerCommand != -1, playerCommand != 1);
    }

    private void verifyApplicationThread() {
        if (Looper.myLooper() != this.getApplicationLooper()) {
            boolean hasNotifiedFullWrongThreadWarning = true;
        }
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    private final class ComponentListener implements VideoRendererEventListener, AudioRendererEventListener, TextOutput, MetadataOutput, Callback, SurfaceTextureListener, PlayerControl {
        private ComponentListener() {
        }

        public void onVideoEnabled(DecoderCounters counters) {
            videoDecoderCounters = counters;
            for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
                videoDebugListener.onVideoEnabled(counters);
            }

        }

        public void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
            for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
                videoDebugListener.onVideoDecoderInitialized(decoderName, initializedTimestampMs, initializationDurationMs);
            }

        }

        public void onVideoInputFormatChanged(Format format) {
            videoFormat = format;
            for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
                videoDebugListener.onVideoInputFormatChanged(format);
            }

        }

        public void onDroppedFrames(int count, long elapsed) {
            for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
                videoDebugListener.onDroppedFrames(count, elapsed);
            }

        }

        public void onVideoSizeChanged(int width, int height, int unAppliedRotationDegrees, float pixelWidthHeightRatio) {
            Iterator<?> var5 = videoListeners.iterator();
            while (var5.hasNext()) {
                VideoListener videoListener = (VideoListener) var5.next();
                if (!videoDebugListeners.contains(videoListener)) {
                    videoListener.onVideoSizeChanged(width, height, unAppliedRotationDegrees, pixelWidthHeightRatio);
                }
            }
            var5 = videoDebugListeners.iterator();
            while (var5.hasNext()) {
                VideoRendererEventListener videoDebugListener = (VideoRendererEventListener) var5.next();
                videoDebugListener.onVideoSizeChanged(width, height, unAppliedRotationDegrees, pixelWidthHeightRatio);
            }

        }

        public void onRenderedFirstFrame(Surface surface) {
            Iterator<?> var2;
            if (SimplePlayer.this.surface == surface) {
                var2 = videoListeners.iterator();

                while (var2.hasNext()) {
                    VideoListener videoListener = (VideoListener) var2.next();
                    videoListener.onRenderedFirstFrame();
                }
            }

            var2 = videoDebugListeners.iterator();

            while (var2.hasNext()) {
                VideoRendererEventListener videoDebugListener = (VideoRendererEventListener) var2.next();
                videoDebugListener.onRenderedFirstFrame(surface);
            }

        }

        public void onVideoDisabled(DecoderCounters counters) {
            for (VideoRendererEventListener videoDebugListener : videoDebugListeners) {
                videoDebugListener.onVideoDisabled(counters);
            }

            videoFormat = null;
            videoDecoderCounters = null;
        }

        public void onAudioEnabled(DecoderCounters counters) {
            audioDecoderCounters = counters;
            for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
                audioDebugListener.onAudioEnabled(counters);
            }

        }

        public void onAudioSessionId(int sessionId) {
            if (audioSessionId != sessionId) {
                audioSessionId = sessionId;
                Iterator<?> var2 = audioListeners.iterator();
                while (var2.hasNext()) {
                    AudioListener audioListener = (AudioListener) var2.next();
                    if (!audioDebugListeners.contains(audioListener)) {
                        audioListener.onAudioSessionId(sessionId);
                    }
                }

                var2 = audioDebugListeners.iterator();

                while (var2.hasNext()) {
                    AudioRendererEventListener audioDebugListener = (AudioRendererEventListener) var2.next();
                    audioDebugListener.onAudioSessionId(sessionId);
                }

            }
        }

        public void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
            for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
                audioDebugListener.onAudioDecoderInitialized(decoderName, initializedTimestampMs, initializationDurationMs);
            }

        }

        public void onAudioInputFormatChanged(Format format) {
            audioFormat = format;
            for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
                audioDebugListener.onAudioInputFormatChanged(format);
            }

        }

        public void onAudioSinkUnderRun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
            for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
                audioDebugListener.onAudioSinkUnderRun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
            }

        }

        public void onAudioDisabled(DecoderCounters counters) {
            for (AudioRendererEventListener audioDebugListener : audioDebugListeners) {
                audioDebugListener.onAudioDisabled(counters);
            }

            audioFormat = null;
            audioDecoderCounters = null;
            audioSessionId = 0;
        }

        public void onCues(List<Cue> cues) {
            currentCues = cues;
            for (TextOutput textOutput : textOutputs) {
                textOutput.onCues(cues);
            }

        }

        public void onMetadata(Metadata metadata) {
            for (MetadataOutput metadataOutput : metadataOutputs) {
                metadataOutput.onMetadata(metadata);
            }

        }

        public void surfaceCreated(SurfaceHolder holder) {
            setVideoSurfaceInternal(holder.getSurface(), false);
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            maybeNotifySurfaceSizeChanged(width, height);
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            setVideoSurfaceInternal(null, false);
            maybeNotifySurfaceSizeChanged(0, 0);
        }

        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            setVideoSurfaceInternal(new Surface(surfaceTexture), true);
            maybeNotifySurfaceSizeChanged(width, height);
        }

        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            maybeNotifySurfaceSizeChanged(width, height);
        }

        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            setVideoSurfaceInternal(null, true);
            maybeNotifySurfaceSizeChanged(0, 0);
            return true;
        }

        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

        public void setVolumeMultiplier(float volumeMultiplier) {
            sendVolumeToRenderers();
        }

        public void executePlayerCommand(int playerCommand) {
            updatePlayWhenReady(getPlayWhenReady(), playerCommand);
        }
    }
}
