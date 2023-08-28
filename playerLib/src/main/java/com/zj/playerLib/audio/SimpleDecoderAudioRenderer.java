package com.zj.playerLib.audio;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.zj.playerLib.BaseRenderer;
import com.zj.playerLib.Format;
import com.zj.playerLib.FormatHolder;
import com.zj.playerLib.PlaybackException;
import com.zj.playerLib.PlaybackParameters;
import com.zj.playerLib.audio.AudioRendererEventListener.EventDispatcher;
import com.zj.playerLib.audio.AudioSink.ConfigurationException;
import com.zj.playerLib.audio.AudioSink.InitializationException;
import com.zj.playerLib.audio.AudioSink.Listener;
import com.zj.playerLib.audio.AudioSink.WriteException;
import com.zj.playerLib.decoder.DecoderCounters;
import com.zj.playerLib.decoder.DecoderInputBuffer;
import com.zj.playerLib.decoder.SimpleDecoder;
import com.zj.playerLib.decoder.SimpleOutputBuffer;
import com.zj.playerLib.drm.DrmSession;
import com.zj.playerLib.drm.DrmSession.DrmSessionException;
import com.zj.playerLib.drm.DrmSessionManager;
import com.zj.playerLib.drm.MediaCrypto;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.MediaClock;
import com.zj.playerLib.util.MimeTypes;
import com.zj.playerLib.util.TraceUtil;
import com.zj.playerLib.util.Util;


@SuppressWarnings("unused")
public abstract class SimpleDecoderAudioRenderer extends BaseRenderer implements MediaClock {
    private static final int REINITIALIZATION_STATE_NONE = 0;
    private static final int REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM = 1;
    private static final int REINITIALIZATION_STATE_WAIT_END_OF_STREAM = 2;
    private final DrmSessionManager<MediaCrypto> drmSessionManager;
    private final boolean playClearSamplesWithoutKeys;
    private final EventDispatcher eventDispatcher;
    private final AudioSink audioSink;
    private final FormatHolder formatHolder;
    private final DecoderInputBuffer flagsOnlyBuffer;
    private DecoderCounters decoderCounters;
    private Format inputFormat;
    private int encoderDelay;
    private int encoderPadding;
    private SimpleDecoder<DecoderInputBuffer, ? extends SimpleOutputBuffer, ? extends AudioDecoderException> decoder;
    private DecoderInputBuffer inputBuffer;
    private SimpleOutputBuffer outputBuffer;
    private DrmSession<MediaCrypto> drmSession;
    private DrmSession<MediaCrypto> pendingDrmSession;
    private int decoderReinitializationState;
    private boolean decoderReceivedBuffers;
    private boolean audioTrackNeedsConfigure;
    private long currentPositionUs;
    private boolean allowFirstBufferPositionDiscontinuity;
    private boolean allowPositionDiscontinuity;
    private boolean inputStreamEnded;
    private boolean outputStreamEnded;
    private boolean waitingForKeys;

    public SimpleDecoderAudioRenderer() {
        this(null, null);
    }

    public SimpleDecoderAudioRenderer(@Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener, AudioProcessor... audioProcessors) {
        this(eventHandler, eventListener, null, null, false, audioProcessors);
    }

    public SimpleDecoderAudioRenderer(@Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener, @Nullable AudioCapabilities audioCapabilities) {
        this(eventHandler, eventListener, audioCapabilities, null, false);
    }

    public SimpleDecoderAudioRenderer(@Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener, @Nullable AudioCapabilities audioCapabilities, @Nullable DrmSessionManager<MediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, AudioProcessor... audioProcessors) {
        this(eventHandler, eventListener, drmSessionManager, playClearSamplesWithoutKeys, new DefaultAudioSink(audioCapabilities, audioProcessors));
    }

    public SimpleDecoderAudioRenderer(@Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener, @Nullable DrmSessionManager<MediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, AudioSink audioSink) {
        super(1);
        this.drmSessionManager = drmSessionManager;
        this.playClearSamplesWithoutKeys = playClearSamplesWithoutKeys;
        this.eventDispatcher = new EventDispatcher(eventHandler, eventListener);
        this.audioSink = audioSink;
        audioSink.setListener(new AudioSinkListener());
        this.formatHolder = new FormatHolder();
        this.flagsOnlyBuffer = DecoderInputBuffer.newFlagsOnlyInstance();
        this.decoderReinitializationState = 0;
        this.audioTrackNeedsConfigure = true;
    }

    public MediaClock getMediaClock() {
        return this;
    }

    public final int supportsFormat(Format format) {
        if (!MimeTypes.isAudio(format.sampleMimeType)) {
            return 0;
        } else {
            int formatSupport = this.supportsFormatInternal(this.drmSessionManager, format);
            if (formatSupport <= 2) {
                return formatSupport;
            } else {
                int tunnelingSupport = Util.SDK_INT >= 21 ? 32 : 0;
                return 8 | tunnelingSupport | formatSupport;
            }
        }
    }

    protected abstract int supportsFormatInternal(DrmSessionManager<MediaCrypto> var1, Format var2);

    protected final boolean supportsOutput(int channelCount, int encoding) {
        return this.audioSink.supportsOutput(channelCount, encoding);
    }

    public void render(long positionUs, long elapsedRealtimeUs) throws PlaybackException {
        if (this.outputStreamEnded) {
            try {
                this.audioSink.playToEndOfStream();
            } catch (WriteException var6) {
                throw PlaybackException.createForRenderer(var6, this.getIndex());
            }
        } else {
            if (this.inputFormat == null) {
                this.flagsOnlyBuffer.clear();
                int result = this.readSource(this.formatHolder, this.flagsOnlyBuffer, true);
                if (result != -5) {
                    if (result == -4) {
                        Assertions.checkState(this.flagsOnlyBuffer.isEndOfStream());
                        this.inputStreamEnded = true;
                        this.processEndOfStream();
                        return;
                    }

                    return;
                }

                this.onInputFormatChanged(this.formatHolder.format);
            }

            this.maybeInitDecoder();
            if (this.decoder != null) {
                try {
                    TraceUtil.beginSection("drainAndFeed");

                    while (true) {
                        if (!this.drainOutputBuffer()) {

                            //noinspection StatementWithEmptyBody
                            while (this.feedInputBuffer()) {
                            }

                            TraceUtil.endSection();
                            break;
                        }
                    }
                } catch (ConfigurationException | InitializationException | WriteException | AudioDecoderException var7) {
                    throw PlaybackException.createForRenderer(var7, this.getIndex());
                }

                this.decoderCounters.ensureUpdated();
            }

        }
    }

    protected void onAudioSessionId(int audioSessionId) {
    }

    protected void onAudioTrackPositionDiscontinuity() {
    }

    protected void onAudioTrackUnderRun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    }

    protected abstract SimpleDecoder<DecoderInputBuffer, ? extends SimpleOutputBuffer, ? extends AudioDecoderException> createDecoder(Format var1, MediaCrypto var2) throws AudioDecoderException;

    protected Format getOutputFormat() {
        return Format.createAudioSampleFormat(null, "audio/raw", null, -1, -1, this.inputFormat.channelCount, this.inputFormat.sampleRate, 2, null, null, 0, null);
    }

    private boolean drainOutputBuffer() throws PlaybackException, AudioDecoderException, ConfigurationException, InitializationException, WriteException {
        if (this.outputBuffer == null) {
            this.outputBuffer = this.decoder.dequeueOutputBuffer();
            if (this.outputBuffer == null) {
                return false;
            }

            DecoderCounters var10000 = this.decoderCounters;
            var10000.skippedOutputBufferCount += this.outputBuffer.skippedOutputBufferCount;
        }

        if (this.outputBuffer.isEndOfStream()) {
            if (this.decoderReinitializationState == 2) {
                this.releaseDecoder();
                this.maybeInitDecoder();
                this.audioTrackNeedsConfigure = true;
            } else {
                this.outputBuffer.release();
                this.outputBuffer = null;
                this.processEndOfStream();
            }

            return false;
        } else {
            if (this.audioTrackNeedsConfigure) {
                Format outputFormat = this.getOutputFormat();
                this.audioSink.configure(outputFormat.pcmEncoding, outputFormat.channelCount, outputFormat.sampleRate, 0, null, this.encoderDelay, this.encoderPadding);
                this.audioTrackNeedsConfigure = false;
            }

            if (this.audioSink.handleBuffer(this.outputBuffer.data, this.outputBuffer.timeUs)) {
                ++this.decoderCounters.renderedOutputBufferCount;
                this.outputBuffer.release();
                this.outputBuffer = null;
                return true;
            } else {
                return false;
            }
        }
    }

    private boolean feedInputBuffer() throws AudioDecoderException, PlaybackException {
        if (this.decoder != null && this.decoderReinitializationState != 2 && !this.inputStreamEnded) {
            if (this.inputBuffer == null) {
                this.inputBuffer = this.decoder.dequeueInputBuffer();
                if (this.inputBuffer == null) {
                    return false;
                }
            }

            if (this.decoderReinitializationState == 1) {
                this.inputBuffer.setFlags(4);
                this.decoder.queueInputBuffer(this.inputBuffer);
                this.inputBuffer = null;
                this.decoderReinitializationState = 2;
                return false;
            } else {
                int result;
                if (this.waitingForKeys) {
                    result = -4;
                } else {
                    result = this.readSource(this.formatHolder, this.inputBuffer, false);
                }

                if (result == -3) {
                    return false;
                } else if (result == -5) {
                    this.onInputFormatChanged(this.formatHolder.format);
                    return true;
                } else if (this.inputBuffer.isEndOfStream()) {
                    this.inputStreamEnded = true;
                    this.decoder.queueInputBuffer(this.inputBuffer);
                    this.inputBuffer = null;
                    return false;
                } else {
                    boolean bufferEncrypted = this.inputBuffer.isEncrypted();
                    this.waitingForKeys = this.shouldWaitForKeys(bufferEncrypted);
                    if (this.waitingForKeys) {
                        return false;
                    } else {
                        this.inputBuffer.flip();
                        this.onQueueInputBuffer(this.inputBuffer);
                        this.decoder.queueInputBuffer(this.inputBuffer);
                        this.decoderReceivedBuffers = true;
                        ++this.decoderCounters.inputBufferCount;
                        this.inputBuffer = null;
                        return true;
                    }
                }
            }
        } else {
            return false;
        }
    }

    private boolean shouldWaitForKeys(boolean bufferEncrypted) throws PlaybackException {
        if (this.drmSession == null || !bufferEncrypted && this.playClearSamplesWithoutKeys) {
            return false;
        } else {
            int drmSessionState = this.drmSession.getState();
            if (drmSessionState == 1) {
                throw PlaybackException.createForRenderer(this.drmSession.getError(), this.getIndex());
            } else {
                return drmSessionState != 4;
            }
        }
    }

    private void processEndOfStream() throws PlaybackException {
        this.outputStreamEnded = true;

        try {
            this.audioSink.playToEndOfStream();
        } catch (WriteException var2) {
            throw PlaybackException.createForRenderer(var2, this.getIndex());
        }
    }

    private void flushDecoder() throws PlaybackException {
        this.waitingForKeys = false;
        if (this.decoderReinitializationState != 0) {
            this.releaseDecoder();
            this.maybeInitDecoder();
        } else {
            this.inputBuffer = null;
            if (this.outputBuffer != null) {
                this.outputBuffer.release();
                this.outputBuffer = null;
            }

            this.decoder.flush();
            this.decoderReceivedBuffers = false;
        }

    }

    public boolean isEnded() {
        return this.outputStreamEnded && this.audioSink.isEnded();
    }

    public boolean isReady() {
        return this.audioSink.hasPendingData() || this.inputFormat != null && !this.waitingForKeys && (this.isSourceReady() || this.outputBuffer != null);
    }

    public long getPositionUs() {
        if (this.getState() == 2) {
            this.updateCurrentPosition();
        }

        return this.currentPositionUs;
    }

    public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
        return this.audioSink.setPlaybackParameters(playbackParameters);
    }

    public PlaybackParameters getPlaybackParameters() {
        return this.audioSink.getPlaybackParameters();
    }

    protected void onEnabled(boolean joining) {
        this.decoderCounters = new DecoderCounters();
        this.eventDispatcher.enabled(this.decoderCounters);
        int tunnelingAudioSessionId = this.getConfiguration().tunnelingAudioSessionId;
        if (tunnelingAudioSessionId != 0) {
            this.audioSink.enableTunnelingV21(tunnelingAudioSessionId);
        } else {
            this.audioSink.disableTunneling();
        }

    }

    protected void onPositionReset(long positionUs, boolean joining) throws PlaybackException {
        this.audioSink.reset();
        this.currentPositionUs = positionUs;
        this.allowFirstBufferPositionDiscontinuity = true;
        this.allowPositionDiscontinuity = true;
        this.inputStreamEnded = false;
        this.outputStreamEnded = false;
        if (this.decoder != null) {
            this.flushDecoder();
        }

    }

    protected void onStarted() {
        this.audioSink.play();
    }

    protected void onStopped() {
        this.updateCurrentPosition();
        this.audioSink.pause();
    }

    protected void onDisabled() {
        this.inputFormat = null;
        this.audioTrackNeedsConfigure = true;
        this.waitingForKeys = false;

        try {
            this.releaseDecoder();
            this.audioSink.release();
        } finally {
            try {
                if (this.drmSession != null) {
                    this.drmSessionManager.releaseSession(this.drmSession);
                }
            } finally {
                try {
                    if (this.pendingDrmSession != null && this.pendingDrmSession != this.drmSession) {
                        this.drmSessionManager.releaseSession(this.pendingDrmSession);
                    }
                } finally {
                    this.drmSession = null;
                    this.pendingDrmSession = null;
                    this.decoderCounters.ensureUpdated();
                    this.eventDispatcher.disabled(this.decoderCounters);
                }

            }

        }

    }

    public void handleMessage(int messageType, @Nullable Object message) throws PlaybackException {
        switch (messageType) {
            case 2:
                if (message != null) this.audioSink.setVolume((Float) message);
                break;
            case 3:
                AudioAttributes audioAttributes = (AudioAttributes) message;
                this.audioSink.setAudioAttributes(audioAttributes);
                break;
            case 4:
            default:
                super.handleMessage(messageType, message);
                break;
            case 5:
                AuxEffectInfo auxEffectInfo = (AuxEffectInfo) message;
                this.audioSink.setAuxEffectInfo(auxEffectInfo);
        }

    }

    private void maybeInitDecoder() throws PlaybackException {
        if (this.decoder == null) {
            this.drmSession = this.pendingDrmSession;
            MediaCrypto mediaCrypto = null;
            if (this.drmSession != null) {
                mediaCrypto = this.drmSession.getMediaCrypto();
                if (mediaCrypto == null) {
                    DrmSessionException drmError = this.drmSession.getError();
                    if (drmError == null) {
                        return;
                    }
                }
            }

            try {
                long codecInitializingTimestamp = SystemClock.elapsedRealtime();
                TraceUtil.beginSection("createAudioDecoder");
                this.decoder = this.createDecoder(this.inputFormat, mediaCrypto);
                TraceUtil.endSection();
                long codecInitializedTimestamp = SystemClock.elapsedRealtime();
                this.eventDispatcher.decoderInitialized(this.decoder.getName(), codecInitializedTimestamp, codecInitializedTimestamp - codecInitializingTimestamp);
                ++this.decoderCounters.decoderInitCount;
            } catch (AudioDecoderException var6) {
                throw PlaybackException.createForRenderer(var6, this.getIndex());
            }
        }
    }

    private void releaseDecoder() {
        if (this.decoder != null) {
            this.inputBuffer = null;
            this.outputBuffer = null;
            this.decoder.release();
            this.decoder = null;
            ++this.decoderCounters.decoderReleaseCount;
            this.decoderReinitializationState = 0;
            this.decoderReceivedBuffers = false;
        }
    }

    private void onInputFormatChanged(Format newFormat) throws PlaybackException {
        Format oldFormat = this.inputFormat;
        this.inputFormat = newFormat;
        boolean drmInitDataChanged = !Util.areEqual(this.inputFormat.drmInitData, oldFormat == null ? null : oldFormat.drmInitData);
        if (drmInitDataChanged) {
            if (this.inputFormat.drmInitData != null) {
                if (this.drmSessionManager == null) {
                    throw PlaybackException.createForRenderer(new IllegalStateException("Media requires a DrmSessionManager"), this.getIndex());
                }

                this.pendingDrmSession = this.drmSessionManager.acquireSession(Looper.myLooper(), this.inputFormat.drmInitData);
                if (this.pendingDrmSession == this.drmSession) {
                    this.drmSessionManager.releaseSession(this.pendingDrmSession);
                }
            } else {
                this.pendingDrmSession = null;
            }
        }

        if (this.decoderReceivedBuffers) {
            this.decoderReinitializationState = 1;
        } else {
            this.releaseDecoder();
            this.maybeInitDecoder();
            this.audioTrackNeedsConfigure = true;
        }

        this.encoderDelay = newFormat.encoderDelay;
        this.encoderPadding = newFormat.encoderPadding;
        this.eventDispatcher.inputFormatChanged(newFormat);
    }

    private void onQueueInputBuffer(DecoderInputBuffer buffer) {
        if (this.allowFirstBufferPositionDiscontinuity && !buffer.isDecodeOnly()) {
            if (Math.abs(buffer.timeUs - this.currentPositionUs) > 500000L) {
                this.currentPositionUs = buffer.timeUs;
            }

            this.allowFirstBufferPositionDiscontinuity = false;
        }

    }

    private void updateCurrentPosition() {
        long newCurrentPositionUs = this.audioSink.getCurrentPositionUs(this.isEnded());
        if (newCurrentPositionUs != -9223372036854775808L) {
            this.currentPositionUs = this.allowPositionDiscontinuity ? newCurrentPositionUs : Math.max(this.currentPositionUs, newCurrentPositionUs);
            this.allowPositionDiscontinuity = false;
        }

    }

    private final class AudioSinkListener implements Listener {
        private AudioSinkListener() {
        }

        public void onAudioSessionId(int audioSessionId) {
            SimpleDecoderAudioRenderer.this.eventDispatcher.audioSessionId(audioSessionId);
            SimpleDecoderAudioRenderer.this.onAudioSessionId(audioSessionId);
        }

        public void onPositionDiscontinuity() {
            SimpleDecoderAudioRenderer.this.onAudioTrackPositionDiscontinuity();
            SimpleDecoderAudioRenderer.this.allowPositionDiscontinuity = true;
        }

        public void onUnderRun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
            SimpleDecoderAudioRenderer.this.eventDispatcher.audioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
            SimpleDecoderAudioRenderer.this.onAudioTrackUnderRun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
        }
    }
}
