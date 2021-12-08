package com.zj.playerLib.audio;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Handler;
import android.view.Surface;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import com.zj.playerLib.PlaybackException;
import com.zj.playerLib.Format;
import com.zj.playerLib.PlaybackParameters;
import com.zj.playerLib.audio.AudioRendererEventListener.EventDispatcher;
import com.zj.playerLib.audio.AudioSink.ConfigurationException;
import com.zj.playerLib.audio.AudioSink.InitializationException;
import com.zj.playerLib.audio.AudioSink.Listener;
import com.zj.playerLib.audio.AudioSink.WriteException;
import com.zj.playerLib.decoder.DecoderInputBuffer;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.drm.DrmSessionManager;
import com.zj.playerLib.drm.FrameworkMediaCrypto;
import com.zj.playerLib.mediacodec.MediaCodecInfo;
import com.zj.playerLib.mediacodec.MediaCodecRenderer;
import com.zj.playerLib.mediacodec.MediaCodecSelector;
import com.zj.playerLib.mediacodec.MediaFormatUtil;
import com.zj.playerLib.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.MediaClock;
import com.zj.playerLib.util.MimeTypes;
import com.zj.playerLib.util.Util;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

@TargetApi(16)
public class MediaCodecAudioRenderer extends MediaCodecRenderer implements MediaClock {
    private static final int MAX_PENDING_STREAM_CHANGE_COUNT = 10;
    private static final String TAG = "MediaCodecAudioRenderer";
    private final Context context;
    private final EventDispatcher eventDispatcher;
    private final AudioSink audioSink;
    private final long[] pendingStreamChangeTimesUs;
    private int codecMaxInputSize;
    private boolean passthroughEnabled;
    private boolean codecNeedsDiscardChannelsWorkaround;
    private boolean codecNeedsEosBufferTimestampWorkaround;
    private MediaFormat passthroughMediaFormat;
    private int pcmEncoding;
    private int channelCount;
    private int encoderDelay;
    private int encoderPadding;
    private long currentPositionUs;
    private boolean allowFirstBufferPositionDiscontinuity;
    private boolean allowPositionDiscontinuity;
    private long lastInputTimeUs;
    private int pendingStreamChangeCount;

    public MediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector) {
        this(context, mediaCodecSelector, null, false);
    }

    public MediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys) {
        this(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, null, null);
    }

    public MediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector, @Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener) {
        this(context, mediaCodecSelector, null, false, eventHandler, eventListener);
    }

    public MediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener) {
        this(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener, (AudioCapabilities)null);
    }

    public MediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener, @Nullable AudioCapabilities audioCapabilities, AudioProcessor... audioProcessors) {
        this(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener, new DefaultAudioSink(audioCapabilities, audioProcessors));
    }

    public MediaCodecAudioRenderer(Context context, MediaCodecSelector mediaCodecSelector, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener, AudioSink audioSink) {
        super(1, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, 44100.0F);
        this.context = context.getApplicationContext();
        this.audioSink = audioSink;
        this.lastInputTimeUs = -Long.MAX_VALUE;
        this.pendingStreamChangeTimesUs = new long[10];
        this.eventDispatcher = new EventDispatcher(eventHandler, eventListener);
        audioSink.setListener(new AudioSinkListener());
    }

    protected int supportsFormat(MediaCodecSelector mediaCodecSelector, DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, Format format) throws DecoderQueryException {
        String mimeType = format.sampleMimeType;
        if (!MimeTypes.isAudio(mimeType)) {
            return 0;
        } else {
            int tunnelingSupport = Util.SDK_INT >= 21 ? 32 : 0;
            boolean supportsFormatDrm = supportsFormatDrm(drmSessionManager, format.drmInitData);
            if (supportsFormatDrm && this.allowPassthrough(format.channelCount, mimeType) && mediaCodecSelector.getPassthroughDecoderInfo() != null) {
                return 8 | tunnelingSupport | 4;
            } else if ((!"audio/raw".equals(mimeType) || this.audioSink.supportsOutput(format.channelCount, format.pcmEncoding)) && this.audioSink.supportsOutput(format.channelCount, 2)) {
                boolean requiresSecureDecryption = false;
                DrmInitData drmInitData = format.drmInitData;
                if (drmInitData != null) {
                    for(int i = 0; i < drmInitData.schemeDataCount; ++i) {
                        requiresSecureDecryption |= drmInitData.get(i).requiresSecureDecryption;
                    }
                }

                List<MediaCodecInfo> decoderInfos = mediaCodecSelector.getDecoderInfos(format.sampleMimeType, requiresSecureDecryption);
                if (decoderInfos.isEmpty()) {
                    return requiresSecureDecryption && !mediaCodecSelector.getDecoderInfos(format.sampleMimeType, false).isEmpty() ? 2 : 1;
                } else if (!supportsFormatDrm) {
                    return 2;
                } else {
                    MediaCodecInfo decoderInfo = decoderInfos.get(0);
                    boolean isFormatSupported = decoderInfo.isFormatSupported(format);
                    int adaptiveSupport = isFormatSupported && decoderInfo.isSeamlessAdaptationSupported(format) ? 16 : 8;
                    int formatSupport = isFormatSupported ? 4 : 3;
                    return adaptiveSupport | tunnelingSupport | formatSupport;
                }
            } else {
                return 1;
            }
        }
    }

    protected List<MediaCodecInfo> getDecoderInfo(MediaCodecSelector mediaCodecSelector, Format format, boolean requiresSecureDecoder) throws DecoderQueryException {
        if (this.allowPassthrough(format.channelCount, format.sampleMimeType)) {
            MediaCodecInfo passthroughDecoderInfo = mediaCodecSelector.getPassthroughDecoderInfo();
            if (passthroughDecoderInfo != null) {
                return Collections.singletonList(passthroughDecoderInfo);
            }
        }

        return super.getDecoderInfo(mediaCodecSelector, format, requiresSecureDecoder);
    }

    protected boolean allowPassthrough(int channelCount, String mimeType) {
        return this.audioSink.supportsOutput(channelCount, MimeTypes.getEncoding(mimeType));
    }

    protected void configureCodec(MediaCodecInfo codecInfo, MediaCodec codec, Format format, MediaCrypto crypto, float codecOperatingRate) {
        this.codecMaxInputSize = this.getCodecMaxInputSize(codecInfo, format, this.getStreamFormats());
        this.codecNeedsDiscardChannelsWorkaround = codecNeedsDiscardChannelsWorkaround(codecInfo.name);
        this.codecNeedsEosBufferTimestampWorkaround = codecNeedsEosBufferTimestampWorkaround(codecInfo.name);
        this.passthroughEnabled = codecInfo.passThrough;
        String codecMimeType = codecInfo.mimeType == null ? "audio/raw" : codecInfo.mimeType;
        MediaFormat mediaFormat = this.getMediaFormat(format, codecMimeType, this.codecMaxInputSize, codecOperatingRate);
        codec.configure(mediaFormat, null, crypto, 0);
        if (this.passthroughEnabled) {
            this.passthroughMediaFormat = mediaFormat;
            this.passthroughMediaFormat.setString("mime", format.sampleMimeType);
        } else {
            this.passthroughMediaFormat = null;
        }

    }

    protected int canKeepCodec(MediaCodec codec, MediaCodecInfo codecInfo, Format oldFormat, Format newFormat) {
        return this.getCodecMaxInputSize(codecInfo, newFormat) <= this.codecMaxInputSize && codecInfo.isSeamlessAdaptationSupported(oldFormat, newFormat, true) && oldFormat.encoderDelay == 0 && oldFormat.encoderPadding == 0 && newFormat.encoderDelay == 0 && newFormat.encoderPadding == 0 ? 1 : 0;
    }

    public MediaClock getMediaClock() {
        return this;
    }

    protected float getCodecOperatingRate(float operatingRate, Format format, Format[] streamFormats) {
        int maxSampleRate = -1;
        Format[] var5 = streamFormats;
        int var6 = streamFormats.length;

        for(int var7 = 0; var7 < var6; ++var7) {
            Format streamFormat = var5[var7];
            int streamSampleRate = streamFormat.sampleRate;
            if (streamSampleRate != -1) {
                maxSampleRate = Math.max(maxSampleRate, streamSampleRate);
            }
        }

        return maxSampleRate == -1 ? -1.0F : (float)maxSampleRate * operatingRate;
    }

    protected void onCodecInitialized(String name, long initializedTimestampMs, long initializationDurationMs) {
        this.eventDispatcher.decoderInitialized(name, initializedTimestampMs, initializationDurationMs);
    }

    protected void onInputFormatChanged(Format newFormat) throws PlaybackException {
        super.onInputFormatChanged(newFormat);
        this.eventDispatcher.inputFormatChanged(newFormat);
        this.pcmEncoding = "audio/raw".equals(newFormat.sampleMimeType) ? newFormat.pcmEncoding : 2;
        this.channelCount = newFormat.channelCount;
        this.encoderDelay = newFormat.encoderDelay;
        this.encoderPadding = newFormat.encoderPadding;
    }

    protected void onOutputFormatChanged(MediaCodec codec, MediaFormat outputFormat) throws PlaybackException {
        int encoding;
        MediaFormat format;
        if (this.passthroughMediaFormat != null) {
            encoding = MimeTypes.getEncoding(this.passthroughMediaFormat.getString("mime"));
            format = this.passthroughMediaFormat;
        } else {
            encoding = this.pcmEncoding;
            format = outputFormat;
        }

        int channelCount = format.getInteger("channel-count");
        int sampleRate = format.getInteger("sample-rate");
        int[] channelMap;
        if (this.codecNeedsDiscardChannelsWorkaround && channelCount == 6 && this.channelCount < 6) {
            channelMap = new int[this.channelCount];

            for(int i = 0; i < this.channelCount; channelMap[i] = i++) {
            }
        } else {
            channelMap = null;
        }

        try {
            this.audioSink.configure(encoding, channelCount, sampleRate, 0, channelMap, this.encoderDelay, this.encoderPadding);
        } catch (ConfigurationException var9) {
            throw PlaybackException.createForRenderer(var9, this.getIndex());
        }
    }

    protected void onAudioSessionId(int audioSessionId) {
    }

    protected void onAudioTrackPositionDiscontinuity() {
    }

    protected void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    }

    protected void onEnabled(boolean joining) throws PlaybackException {
        super.onEnabled(joining);
        this.eventDispatcher.enabled(this.decoderCounters);
        int tunnelingAudioSessionId = this.getConfiguration().tunnelingAudioSessionId;
        if (tunnelingAudioSessionId != 0) {
            this.audioSink.enableTunnelingV21(tunnelingAudioSessionId);
        } else {
            this.audioSink.disableTunneling();
        }

    }

    protected void onStreamChanged(Format[] formats, long offsetUs) throws PlaybackException {
        super.onStreamChanged(formats, offsetUs);
        if (this.lastInputTimeUs != -Long.MAX_VALUE) {
            if (this.pendingStreamChangeCount == this.pendingStreamChangeTimesUs.length) {
                Log.w("MediaCodecAudioRenderer", "Too many stream changes, so dropping change at " + this.pendingStreamChangeTimesUs[this.pendingStreamChangeCount - 1]);
            } else {
                ++this.pendingStreamChangeCount;
            }

            this.pendingStreamChangeTimesUs[this.pendingStreamChangeCount - 1] = this.lastInputTimeUs;
        }

    }

    protected void onPositionReset(long positionUs, boolean joining) throws PlaybackException {
        super.onPositionReset(positionUs, joining);
        this.audioSink.reset();
        this.currentPositionUs = positionUs;
        this.allowFirstBufferPositionDiscontinuity = true;
        this.allowPositionDiscontinuity = true;
        this.lastInputTimeUs = -Long.MAX_VALUE;
        this.pendingStreamChangeCount = 0;
    }

    protected void onStarted() {
        super.onStarted();
        this.audioSink.play();
    }

    protected void onStopped() {
        this.updateCurrentPosition();
        this.audioSink.pause();
        super.onStopped();
    }

    protected void onDisabled() {
        try {
            this.lastInputTimeUs = -Long.MAX_VALUE;
            this.pendingStreamChangeCount = 0;
            this.audioSink.release();
        } finally {
            try {
                super.onDisabled();
            } finally {
                this.decoderCounters.ensureUpdated();
                this.eventDispatcher.disabled(this.decoderCounters);
            }
        }

    }

    public boolean isEnded() {
        return super.isEnded() && this.audioSink.isEnded();
    }

    public boolean isReady() {
        return this.audioSink.hasPendingData() || super.isReady();
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

    protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
        if (this.allowFirstBufferPositionDiscontinuity && !buffer.isDecodeOnly()) {
            if (Math.abs(buffer.timeUs - this.currentPositionUs) > 500000L) {
                this.currentPositionUs = buffer.timeUs;
            }

            this.allowFirstBufferPositionDiscontinuity = false;
        }

        this.lastInputTimeUs = Math.max(buffer.timeUs, this.lastInputTimeUs);
    }

    @CallSuper
    protected void onProcessedOutputBuffer(long presentationTimeUs) {
        while(this.pendingStreamChangeCount != 0 && presentationTimeUs >= this.pendingStreamChangeTimesUs[0]) {
            this.audioSink.handleDiscontinuity();
            --this.pendingStreamChangeCount;
            System.arraycopy(this.pendingStreamChangeTimesUs, 1, this.pendingStreamChangeTimesUs, 0, this.pendingStreamChangeCount);
        }

    }

    protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec codec, ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs, boolean shouldSkip, Format format) throws PlaybackException {
        if (this.codecNeedsEosBufferTimestampWorkaround && bufferPresentationTimeUs == 0L && (bufferFlags & 4) != 0 && this.lastInputTimeUs != -Long.MAX_VALUE) {
            bufferPresentationTimeUs = this.lastInputTimeUs;
        }

        if (this.passthroughEnabled && (bufferFlags & 2) != 0) {
            codec.releaseOutputBuffer(bufferIndex, false);
            return true;
        } else if (shouldSkip) {
            codec.releaseOutputBuffer(bufferIndex, false);
            ++this.decoderCounters.skippedOutputBufferCount;
            this.audioSink.handleDiscontinuity();
            return true;
        } else {
            try {
                if (this.audioSink.handleBuffer(buffer, bufferPresentationTimeUs)) {
                    codec.releaseOutputBuffer(bufferIndex, false);
                    ++this.decoderCounters.renderedOutputBufferCount;
                    return true;
                } else {
                    return false;
                }
            } catch (WriteException | InitializationException var14) {
                throw PlaybackException.createForRenderer(var14, this.getIndex());
            }
        }
    }

    protected void renderToEndOfStream() throws PlaybackException {
        try {
            this.audioSink.playToEndOfStream();
        } catch (WriteException var2) {
            throw PlaybackException.createForRenderer(var2, this.getIndex());
        }
    }

    public void handleMessage(int messageType, @Nullable Object message) throws PlaybackException {
        switch(messageType) {
        case 2:
            this.audioSink.setVolume((Float)message);
            break;
        case 3:
            AudioAttributes audioAttributes = (AudioAttributes)message;
            this.audioSink.setAudioAttributes(audioAttributes);
            break;
        case 4:
        default:
            super.handleMessage(messageType, message);
            break;
        case 5:
            AuxEffectInfo auxEffectInfo = (AuxEffectInfo)message;
            this.audioSink.setAuxEffectInfo(auxEffectInfo);
        }

    }

    protected int getCodecMaxInputSize(MediaCodecInfo codecInfo, Format format, Format[] streamFormats) {
        int maxInputSize = this.getCodecMaxInputSize(codecInfo, format);
        if (streamFormats.length == 1) {
            return maxInputSize;
        } else {
            Format[] var5 = streamFormats;
            int var6 = streamFormats.length;

            for(int var7 = 0; var7 < var6; ++var7) {
                Format streamFormat = var5[var7];
                if (codecInfo.isSeamlessAdaptationSupported(format, streamFormat, false)) {
                    maxInputSize = Math.max(maxInputSize, this.getCodecMaxInputSize(codecInfo, streamFormat));
                }
            }

            return maxInputSize;
        }
    }

    private int getCodecMaxInputSize(MediaCodecInfo codecInfo, Format format) {
        if (Util.SDK_INT < 24 && "OMX.google.raw.decoder".equals(codecInfo.name)) {
            boolean needsRawDecoderWorkaround = true;
            if (Util.SDK_INT == 23) {
                PackageManager packageManager = this.context.getPackageManager();
                if (packageManager != null && packageManager.hasSystemFeature("android.software.leanback")) {
                    needsRawDecoderWorkaround = false;
                }
            }

            if (needsRawDecoderWorkaround) {
                return -1;
            }
        }

        return format.maxInputSize;
    }

    @SuppressLint({"InlinedApi"})
    protected MediaFormat getMediaFormat(Format format, String codecMimeType, int codecMaxInputSize, float codecOperatingRate) {
        MediaFormat mediaFormat = new MediaFormat();
        mediaFormat.setString("mime", codecMimeType);
        mediaFormat.setInteger("channel-count", format.channelCount);
        mediaFormat.setInteger("sample-rate", format.sampleRate);
        MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);
        MediaFormatUtil.maybeSetInteger(mediaFormat, "max-input-size", codecMaxInputSize);
        if (Util.SDK_INT >= 23) {
            mediaFormat.setInteger("priority", 0);
            if (codecOperatingRate != -1.0F) {
                mediaFormat.setFloat("operating-rate", codecOperatingRate);
            }
        }

        return mediaFormat;
    }

    private void updateCurrentPosition() {
        long newCurrentPositionUs = this.audioSink.getCurrentPositionUs(this.isEnded());
        if (newCurrentPositionUs != -9223372036854775808L) {
            this.currentPositionUs = this.allowPositionDiscontinuity ? newCurrentPositionUs : Math.max(this.currentPositionUs, newCurrentPositionUs);
            this.allowPositionDiscontinuity = false;
        }

    }

    private static boolean codecNeedsDiscardChannelsWorkaround(String codecName) {
        return Util.SDK_INT < 24 && "OMX.SEC.aac.dec".equals(codecName) && "samsung".equals(Util.MANUFACTURER) && (Util.DEVICE.startsWith("zeroflte") || Util.DEVICE.startsWith("herolte") || Util.DEVICE.startsWith("heroqlte"));
    }

    private static boolean codecNeedsEosBufferTimestampWorkaround(String codecName) {
        return Util.SDK_INT < 21 && "OMX.SEC.mp3.dec".equals(codecName) && "samsung".equals(Util.MANUFACTURER) && (Util.DEVICE.startsWith("baffin") || Util.DEVICE.startsWith("grand") || Util.DEVICE.startsWith("fortuna") || Util.DEVICE.startsWith("gprimelte") || Util.DEVICE.startsWith("j2y18lte") || Util.DEVICE.startsWith("ms01"));
    }

    private final class AudioSinkListener implements Listener {
        private AudioSinkListener() {
        }

        public void onAudioSessionId(int audioSessionId) {
            MediaCodecAudioRenderer.this.eventDispatcher.audioSessionId(audioSessionId);
            MediaCodecAudioRenderer.this.onAudioSessionId(audioSessionId);
        }

        public void onPositionDiscontinuity() {
            MediaCodecAudioRenderer.this.onAudioTrackPositionDiscontinuity();
            MediaCodecAudioRenderer.this.allowPositionDiscontinuity = true;
        }

        public void onUnderRun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
            MediaCodecAudioRenderer.this.eventDispatcher.audioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
            MediaCodecAudioRenderer.this.onAudioTrackUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
        }
    }
}
