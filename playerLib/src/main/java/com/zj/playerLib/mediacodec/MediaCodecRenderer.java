package com.zj.playerLib.mediacodec;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CodecException;
import android.media.MediaCodec.CryptoException;
import android.media.MediaCodec.CryptoInfo;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.zj.playerLib.BaseRenderer;
import com.zj.playerLib.Format;
import com.zj.playerLib.FormatHolder;
import com.zj.playerLib.PlaybackException;
import com.zj.playerLib.decoder.DecoderCounters;
import com.zj.playerLib.decoder.DecoderInputBuffer;
import com.zj.playerLib.drm.DrmSession;
import com.zj.playerLib.drm.DrmSession.DrmSessionException;
import com.zj.playerLib.drm.DrmSessionManager;
import com.zj.playerLib.drm.FrameworkMediaCrypto;
import com.zj.playerLib.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.NalUnitUtil;
import com.zj.playerLib.util.TimedValueQueue;
import com.zj.playerLib.util.TraceUtil;
import com.zj.playerLib.util.Util;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
@androidx.annotation.RequiresApi(api = android.os.Build.VERSION_CODES.JELLY_BEAN)
public abstract class MediaCodecRenderer extends BaseRenderer {
    protected static final float CODEC_OPERATING_RATE_UNSET = -1.0F;
    private static final long MAX_CODEC_HOTSWAP_TIME_MS = 1000L;
    protected static final int KEEP_CODEC_RESULT_NO = 0;
    protected static final int KEEP_CODEC_RESULT_YES_WITHOUT_RECONFIGURATION = 1;
    protected static final int KEEP_CODEC_RESULT_YES_WITH_RECONFIGURATION = 3;
    private static final int RECONFIGURATION_STATE_NONE = 0;
    private static final int RECONFIGURATION_STATE_WRITE_PENDING = 1;
    private static final int RECONFIGURATION_STATE_QUEUE_PENDING = 2;
    private static final int REINITIALIZATION_STATE_NONE = 0;
    private static final int REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM = 1;
    private static final int REINITIALIZATION_STATE_WAIT_END_OF_STREAM = 2;
    private static final int ADAPTATION_WORKAROUND_MODE_NEVER = 0;
    private static final int ADAPTATION_WORKAROUND_MODE_SAME_RESOLUTION = 1;
    private static final int ADAPTATION_WORKAROUND_MODE_ALWAYS = 2;
    private static final int ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT = 32;
    private static final byte[] ADAPTATION_WORKAROUND_BUFFER = Util.getBytesFromHexString("0000016742C00BDA259000000168CE0F13200000016588840DCE7118A0002FBF1C31C3275D78");
    private final MediaCodecSelector mediaCodecSelector;
    @Nullable
    private final DrmSessionManager<FrameworkMediaCrypto> drmSessionManager;
    private final boolean playClearSamplesWithoutKeys;
    private final float assumedMinimumCodecOperatingRate;
    private final DecoderInputBuffer buffer;
    private final DecoderInputBuffer flagsOnlyBuffer;
    private final FormatHolder formatHolder;
    private final TimedValueQueue formatQueue;
    private final List<Long> decodeOnlyPresentationTimestamps;
    private final BufferInfo outputBufferInfo;
    private Format format;
    private Format pendingFormat;
    private Format outputFormat;
    private DrmSession<FrameworkMediaCrypto> drmSession;
    private DrmSession<FrameworkMediaCrypto> pendingDrmSession;
    private MediaCodec codec;
    private float rendererOperatingRate;
    private float codecOperatingRate;
    private boolean codecConfiguredWithOperatingRate;
    @Nullable
    private ArrayDeque<MediaCodecInfo> availableCodecInfos;
    @Nullable
    private MediaCodecRenderer.DecoderInitializationException preferredDecoderInitializationException;
    @Nullable
    private MediaCodecInfo codecInfo;
    private int codecAdaptationWorkaroundMode;
    private boolean codecNeedsReconfigureWorkaround;
    private boolean codecNeedsDiscardToSpsWorkaround;
    private boolean codecNeedsFlushWorkaround;
    private boolean codecNeedsEosFlushWorkaround;
    private boolean codecNeedsEosOutputExceptionWorkaround;
    private boolean codecNeedsMonoChannelCountWorkaround;
    private boolean codecNeedsAdaptationWorkaroundBuffer;
    private boolean shouldSkipAdaptationWorkaroundOutputBuffer;
    private boolean codecNeedsEosPropagation;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;
    private long codecHotswapDeadlineMs;
    private int inputIndex;
    private int outputIndex;
    private ByteBuffer outputBuffer;
    private boolean shouldSkipOutputBuffer;
    private boolean codecReconfigured;
    private int codecReconfigurationState;
    private int codecReinitializationState;
    private boolean codecReceivedBuffers;
    private boolean codecReceivedEos;
    private boolean inputStreamEnded;
    private boolean outputStreamEnded;
    private boolean waitingForKeys;
    private boolean waitingForFirstSyncFrame;
    protected DecoderCounters decoderCounters;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public MediaCodecRenderer(int trackType, MediaCodecSelector mediaCodecSelector, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, float assumedMinimumCodecOperatingRate) {
        super(trackType);
        Assertions.checkState(Util.SDK_INT >= 16);
        this.mediaCodecSelector = Assertions.checkNotNull(mediaCodecSelector);
        this.drmSessionManager = drmSessionManager;
        this.playClearSamplesWithoutKeys = playClearSamplesWithoutKeys;
        this.assumedMinimumCodecOperatingRate = assumedMinimumCodecOperatingRate;
        this.buffer = new DecoderInputBuffer(0);
        this.flagsOnlyBuffer = DecoderInputBuffer.newFlagsOnlyInstance();
        this.formatHolder = new FormatHolder();
        this.formatQueue = new TimedValueQueue();
        this.decodeOnlyPresentationTimestamps = new ArrayList<>();
        this.outputBufferInfo = new BufferInfo();
        this.codecReconfigurationState = 0;
        this.codecReinitializationState = 0;
        this.codecOperatingRate = -1.0F;
        this.rendererOperatingRate = 1.0F;
    }

    public final int supportsMixedMimeTypeAdaptation() {
        return 8;
    }

    public final int supportsFormat(Format format) throws PlaybackException {
        try {
            return this.supportsFormat(this.mediaCodecSelector, this.drmSessionManager, format);
        } catch (DecoderQueryException var3) {
            throw PlaybackException.createForRenderer(var3, this.getIndex());
        }
    }

    protected abstract int supportsFormat(MediaCodecSelector var1, DrmSessionManager<FrameworkMediaCrypto> var2, Format var3) throws DecoderQueryException;

    protected List<MediaCodecInfo> getDecoderInfo(MediaCodecSelector mediaCodecSelector, Format format, boolean requiresSecureDecoder) throws DecoderQueryException {
        return mediaCodecSelector.getDecoderInfos(format.sampleMimeType, requiresSecureDecoder);
    }

    protected abstract void configureCodec(MediaCodecInfo var1, MediaCodec var2, Format var3, MediaCrypto var4, float var5) throws DecoderQueryException;

    protected final void maybeInitCodec() throws PlaybackException {
        if (this.codec == null && this.format != null) {
            this.drmSession = this.pendingDrmSession;
            String mimeType = this.format.sampleMimeType;
            MediaCrypto wrappedMediaCrypto = null;
            boolean drmSessionRequiresSecureDecoder = false;
            if (this.drmSession != null) {
                FrameworkMediaCrypto mediaCrypto = this.drmSession.getMediaCrypto();
                if (mediaCrypto == null) {
                    DrmSessionException drmError = this.drmSession.getError();
                    if (drmError == null) {
                        return;
                    }
                } else {
                    wrappedMediaCrypto = mediaCrypto.getWrappedMediaCrypto();
                    drmSessionRequiresSecureDecoder = mediaCrypto.requiresSecureDecoderComponent(mimeType);
                }

                if (this.deviceNeedsDrmKeysToConfigureCodecWorkaround()) {
                    int drmSessionState = this.drmSession.getState();
                    if (drmSessionState == 1) {
                        throw PlaybackException.createForRenderer(this.drmSession.getError(), this.getIndex());
                    }

                    if (drmSessionState != 4) {
                        return;
                    }
                }
            }

            try {
                if (!this.initCodecWithFallback(wrappedMediaCrypto, drmSessionRequiresSecureDecoder)) {
                    return;
                }
            } catch (DecoderInitializationException var6) {
                throw PlaybackException.createForRenderer(var6, this.getIndex());
            }
            String codecName = this.codecInfo.name;
            this.codecAdaptationWorkaroundMode = this.codecAdaptationWorkaroundMode(codecName);
            this.codecNeedsReconfigureWorkaround = codecNeedsReconfigureWorkaround(codecName);
            this.codecNeedsDiscardToSpsWorkaround = codecNeedsDiscardToSpsWorkaround(codecName, this.format);
            this.codecNeedsFlushWorkaround = codecNeedsFlushWorkaround(codecName);
            this.codecNeedsEosFlushWorkaround = codecNeedsEosFlushWorkaround(codecName);
            this.codecNeedsEosOutputExceptionWorkaround = codecNeedsEosOutputExceptionWorkaround(codecName);
            this.codecNeedsMonoChannelCountWorkaround = codecNeedsMonoChannelCountWorkaround(codecName, this.format);
            this.codecNeedsEosPropagation = codecNeedsEosPropagationWorkaround(this.codecInfo) || this.getCodecNeedsEosPropagation();
            this.codecHotswapDeadlineMs = this.getState() == 2 ? SystemClock.elapsedRealtime() + 1000L : -9223372036854775807L;
            this.resetInputBuffer();
            this.resetOutputBuffer();
            this.waitingForFirstSyncFrame = true;
            ++this.decoderCounters.decoderInitCount;
        }
    }

    protected boolean shouldInitCodec(MediaCodecInfo codecInfo) {
        return true;
    }

    protected boolean getCodecNeedsEosPropagation() {
        return false;
    }

    @Nullable
    protected final Format updateOutputFormatForTime(long presentationTimeUs) {
        Format format = (Format) this.formatQueue.pollFloor(presentationTimeUs);
        if (format != null) {
            this.outputFormat = format;
        }

        return format;
    }

    protected final MediaCodec getCodec() {
        return this.codec;
    }

    @Nullable
    protected final MediaCodecInfo getCodecInfo() {
        return this.codecInfo;
    }

    protected void onEnabled(boolean joining) throws PlaybackException {
        this.decoderCounters = new DecoderCounters();
    }

    protected void onPositionReset(long positionUs, boolean joining) throws PlaybackException {
        this.inputStreamEnded = false;
        this.outputStreamEnded = false;
        if (this.codec != null) {
            this.flushCodec();
        }

        this.formatQueue.clear();
    }

    public final void setOperatingRate(float operatingRate) throws PlaybackException {
        this.rendererOperatingRate = operatingRate;
        this.updateCodecOperatingRate();
    }

    protected void onDisabled() {
        this.format = null;
        this.availableCodecInfos = null;

        try {
            this.releaseCodec();
        } finally {
            try {
                if (this.drmSession != null && this.drmSessionManager != null) {
                    this.drmSessionManager.releaseSession(this.drmSession);
                }
            } finally {
                try {
                    if (this.drmSessionManager != null && this.pendingDrmSession != null && this.pendingDrmSession != this.drmSession) {
                        this.drmSessionManager.releaseSession(this.pendingDrmSession);
                    }
                } finally {
                    this.drmSession = null;
                    this.pendingDrmSession = null;
                }

            }

        }

    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    protected void releaseCodec() {
        this.codecHotswapDeadlineMs = -9223372036854775807L;
        this.resetInputBuffer();
        this.resetOutputBuffer();
        this.waitingForKeys = false;
        this.shouldSkipOutputBuffer = false;
        this.decodeOnlyPresentationTimestamps.clear();
        this.resetCodecBuffers();
        this.codecInfo = null;
        this.codecReconfigured = false;
        this.codecReceivedBuffers = false;
        this.codecNeedsDiscardToSpsWorkaround = false;
        this.codecNeedsFlushWorkaround = false;
        this.codecAdaptationWorkaroundMode = 0;
        this.codecNeedsReconfigureWorkaround = false;
        this.codecNeedsEosFlushWorkaround = false;
        this.codecNeedsMonoChannelCountWorkaround = false;
        this.codecNeedsAdaptationWorkaroundBuffer = false;
        this.shouldSkipAdaptationWorkaroundOutputBuffer = false;
        this.codecNeedsEosPropagation = false;
        this.codecReceivedEos = false;
        this.codecReconfigurationState = 0;
        this.codecReinitializationState = 0;
        this.codecConfiguredWithOperatingRate = false;
        if (this.codec != null) {
            ++this.decoderCounters.decoderReleaseCount;

            try {
                this.codec.stop();
            } finally {
                try {
                    this.codec.release();
                } finally {
                    this.codec = null;
                    if (this.drmSession != null && this.drmSessionManager != null && this.pendingDrmSession != this.drmSession) {
                        try {
                            this.drmSessionManager.releaseSession(this.drmSession);
                        } finally {
                            this.drmSession = null;
                        }
                    }

                }
            }
        }

    }

    protected void onStarted() {
    }

    protected void onStopped() {
    }

    public void render(long positionUs, long elapsedRealtimeUs) throws PlaybackException {
        if (this.outputStreamEnded) {
            this.renderToEndOfStream();
        } else {
            int result;
            if (this.format == null) {
                this.flagsOnlyBuffer.clear();
                result = this.readSource(this.formatHolder, this.flagsOnlyBuffer, true);
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

            this.maybeInitCodec();
            if (this.codec != null) {
                TraceUtil.beginSection("drainAndFeed");

                while (true) {
                    if (!this.drainOutputBuffer(positionUs, elapsedRealtimeUs)) {

                        //noinspection StatementWithEmptyBody
                        while (this.feedInputBuffer()) {
                        }

                        TraceUtil.endSection();
                        break;
                    }
                }
            } else {
                DecoderCounters var10000 = this.decoderCounters;
                var10000.skippedInputBufferCount += this.skipSource(positionUs);
                this.flagsOnlyBuffer.clear();
                result = this.readSource(this.formatHolder, this.flagsOnlyBuffer, false);
                if (result == -5) {
                    this.onInputFormatChanged(this.formatHolder.format);
                } else if (result == -4) {
                    Assertions.checkState(this.flagsOnlyBuffer.isEndOfStream());
                    this.inputStreamEnded = true;
                    this.processEndOfStream();
                }
            }

            this.decoderCounters.ensureUpdated();
        }
    }

    protected void flushCodec() throws PlaybackException {
        this.codecHotswapDeadlineMs = -9223372036854775807L;
        this.resetInputBuffer();
        this.resetOutputBuffer();
        this.waitingForFirstSyncFrame = true;
        this.waitingForKeys = false;
        this.shouldSkipOutputBuffer = false;
        this.decodeOnlyPresentationTimestamps.clear();
        this.codecNeedsAdaptationWorkaroundBuffer = false;
        this.shouldSkipAdaptationWorkaroundOutputBuffer = false;
        if (this.codecNeedsFlushWorkaround || this.codecNeedsEosFlushWorkaround && this.codecReceivedEos) {
            this.releaseCodec();
            this.maybeInitCodec();
        } else if (this.codecReinitializationState != 0) {
            this.releaseCodec();
            this.maybeInitCodec();
        } else {
            this.codec.flush();
            this.codecReceivedBuffers = false;
        }

        if (this.codecReconfigured && this.format != null) {
            this.codecReconfigurationState = 1;
        }

    }

    private boolean initCodecWithFallback(MediaCrypto crypto, boolean drmSessionRequiresSecureDecoder) throws DecoderInitializationException {
        if (this.availableCodecInfos == null) {
            try {
                this.availableCodecInfos = new ArrayDeque<>(this.getAvailableCodecInfos(drmSessionRequiresSecureDecoder));
                this.preferredDecoderInitializationException = null;
            } catch (DecoderQueryException var6) {
                throw new DecoderInitializationException(this.format, var6, drmSessionRequiresSecureDecoder, -49998);
            }
        }

        if (this.availableCodecInfos.isEmpty()) {
            throw new DecoderInitializationException(this.format, null, drmSessionRequiresSecureDecoder, -49999);
        } else {
            while (true) {
                MediaCodecInfo codecInfo = this.availableCodecInfos.peekFirst();
                if (!this.shouldInitCodec(codecInfo)) {
                    return false;
                }
                try {
                    this.initCodec(codecInfo, crypto);
                    return true;
                } catch (Exception var7) {
                    Log.w("MediaCodecRenderer", "Failed to initialize decoder: " + codecInfo, var7);
                    this.availableCodecInfos.removeFirst();
                    DecoderInitializationException exception = new DecoderInitializationException(this.format, var7, drmSessionRequiresSecureDecoder, codecInfo.name);
                    if (this.preferredDecoderInitializationException == null) {
                        this.preferredDecoderInitializationException = exception;
                    } else {
                        this.preferredDecoderInitializationException = this.preferredDecoderInitializationException.copyWithFallbackException(exception);
                    }

                    if (this.availableCodecInfos.isEmpty()) {
                        throw this.preferredDecoderInitializationException;
                    }
                }
            }
        }
    }

    private List<MediaCodecInfo> getAvailableCodecInfos(boolean drmSessionRequiresSecureDecoder) throws DecoderQueryException {
        List<MediaCodecInfo> codecInfos = this.getDecoderInfo(this.mediaCodecSelector, this.format, drmSessionRequiresSecureDecoder);
        if (codecInfos.isEmpty() && drmSessionRequiresSecureDecoder) {
            codecInfos = this.getDecoderInfo(this.mediaCodecSelector, this.format, false);
            if (!codecInfos.isEmpty()) {
                Log.w("MediaCodecRenderer", "Drm session requires secure decoder for " + this.format.sampleMimeType + ", but no secure decoder available. Trying to proceed with " + codecInfos + ".");
            }
        }

        return codecInfos;
    }

    private void initCodec(MediaCodecInfo codecInfo, MediaCrypto crypto) throws Exception {
        MediaCodec codec = null;
        String name = codecInfo.name;
        this.updateCodecOperatingRate();
        boolean configureWithOperatingRate = this.codecOperatingRate > this.assumedMinimumCodecOperatingRate;

        long codecInitializingTimestamp;
        long codecInitializedTimestamp;
        try {
            codecInitializingTimestamp = SystemClock.elapsedRealtime();
            TraceUtil.beginSection("createCodec:" + name);
            codec = MediaCodec.createByCodecName(name);
            TraceUtil.endSection();
            TraceUtil.beginSection("configureCodec");
            this.configureCodec(codecInfo, codec, this.format, crypto, configureWithOperatingRate ? this.codecOperatingRate : -1.0F);
            this.codecConfiguredWithOperatingRate = configureWithOperatingRate;
            TraceUtil.endSection();
            TraceUtil.beginSection("startCodec");
            codec.start();
            TraceUtil.endSection();
            codecInitializedTimestamp = SystemClock.elapsedRealtime();
            this.getCodecBuffers(codec);
        } catch (Exception var12) {
            if (codec != null) {
                this.resetCodecBuffers();
                codec.release();
            }

            throw var12;
        }

        this.codec = codec;
        this.codecInfo = codecInfo;
        long elapsed = codecInitializedTimestamp - codecInitializingTimestamp;
        this.onCodecInitialized(name, codecInitializedTimestamp, elapsed);
    }

    @SuppressWarnings("deprecation")
    private void getCodecBuffers(MediaCodec codec) {
        if (Util.SDK_INT < 21) {
            this.inputBuffers = codec.getInputBuffers();
            this.outputBuffers = codec.getOutputBuffers();
        }
    }

    private void resetCodecBuffers() {
        if (Util.SDK_INT < 21) {
            this.inputBuffers = null;
            this.outputBuffers = null;
        }
    }

    private ByteBuffer getInputBuffer(int inputIndex) {
        return Util.SDK_INT >= 21 ? this.codec.getInputBuffer(inputIndex) : this.inputBuffers[inputIndex];
    }

    private ByteBuffer getOutputBuffer(int outputIndex) {
        return Util.SDK_INT >= 21 ? this.codec.getOutputBuffer(outputIndex) : this.outputBuffers[outputIndex];
    }

    private boolean hasOutputBuffer() {
        return this.outputIndex >= 0;
    }

    private void resetInputBuffer() {
        this.inputIndex = -1;
        this.buffer.data = null;
    }

    private void resetOutputBuffer() {
        this.outputIndex = -1;
        this.outputBuffer = null;
    }

    private boolean feedInputBuffer() throws PlaybackException {
        if (this.codec != null && this.codecReinitializationState != 2 && !this.inputStreamEnded) {
            if (this.inputIndex < 0) {
                this.inputIndex = this.codec.dequeueInputBuffer(0L);
                if (this.inputIndex < 0) {
                    return false;
                }

                this.buffer.data = this.getInputBuffer(this.inputIndex);
                this.buffer.clear();
            }

            if (this.codecReinitializationState == 1) {
                if (!this.codecNeedsEosPropagation) {
                    this.codecReceivedEos = true;
                    this.codec.queueInputBuffer(this.inputIndex, 0, 0, 0L, 4);
                    this.resetInputBuffer();
                }

                this.codecReinitializationState = 2;
                return false;
            } else if (this.codecNeedsAdaptationWorkaroundBuffer) {
                this.codecNeedsAdaptationWorkaroundBuffer = false;
                this.buffer.data.put(ADAPTATION_WORKAROUND_BUFFER);
                this.codec.queueInputBuffer(this.inputIndex, 0, ADAPTATION_WORKAROUND_BUFFER.length, 0L, 0);
                this.resetInputBuffer();
                this.codecReceivedBuffers = true;
                return true;
            } else {
                int adaptiveReconfigurationBytes = 0;
                int result;
                if (this.waitingForKeys) {
                    result = -4;
                } else {
                    if (this.codecReconfigurationState == 1) {
                        for (int i = 0; i < this.format.initializationData.size(); ++i) {
                            byte[] data = this.format.initializationData.get(i);
                            this.buffer.data.put(data);
                        }
                        this.codecReconfigurationState = 2;
                    }
                    adaptiveReconfigurationBytes = this.buffer.data.position();
                    result = this.readSource(this.formatHolder, this.buffer, false);
                }

                if (result == -3) {
                    return false;
                } else if (result == -5) {
                    if (this.codecReconfigurationState == 2) {
                        this.buffer.clear();
                        this.codecReconfigurationState = 1;
                    }

                    this.onInputFormatChanged(this.formatHolder.format);
                    return true;
                } else if (this.buffer.isEndOfStream()) {
                    if (this.codecReconfigurationState == 2) {
                        this.buffer.clear();
                        this.codecReconfigurationState = 1;
                    }

                    this.inputStreamEnded = true;
                    if (!this.codecReceivedBuffers) {
                        this.processEndOfStream();
                        return false;
                    } else {
                        try {
                            if (!this.codecNeedsEosPropagation) {
                                this.codecReceivedEos = true;
                                this.codec.queueInputBuffer(this.inputIndex, 0, 0, 0L, 4);
                                this.resetInputBuffer();
                            }
                            return false;
                        } catch (CryptoException var7) {
                            throw PlaybackException.createForRenderer(var7, this.getIndex());
                        }
                    }
                } else if (this.waitingForFirstSyncFrame && !this.buffer.isKeyFrame()) {
                    this.buffer.clear();
                    if (this.codecReconfigurationState == 2) {
                        this.codecReconfigurationState = 1;
                    }

                    return true;
                } else {
                    this.waitingForFirstSyncFrame = false;
                    boolean bufferEncrypted = this.buffer.isEncrypted();
                    this.waitingForKeys = this.shouldWaitForKeys(bufferEncrypted);
                    if (this.waitingForKeys) {
                        return false;
                    } else {
                        if (this.codecNeedsDiscardToSpsWorkaround && !bufferEncrypted) {
                            NalUnitUtil.discardToSps(this.buffer.data);
                            if (this.buffer.data.position() == 0) {
                                return true;
                            }

                            this.codecNeedsDiscardToSpsWorkaround = false;
                        }

                        try {
                            long presentationTimeUs = this.buffer.timeUs;
                            if (this.buffer.isDecodeOnly()) {
                                this.decodeOnlyPresentationTimestamps.add(presentationTimeUs);
                            }

                            if (this.pendingFormat != null) {
                                this.formatQueue.add(presentationTimeUs, this.pendingFormat);
                                this.pendingFormat = null;
                            }

                            this.buffer.flip();
                            this.onQueueInputBuffer(this.buffer);
                            if (bufferEncrypted) {
                                CryptoInfo cryptoInfo = getFrameworkCryptoInfo(this.buffer, adaptiveReconfigurationBytes);
                                this.codec.queueSecureInputBuffer(this.inputIndex, 0, cryptoInfo, presentationTimeUs, 0);
                            } else {
                                this.codec.queueInputBuffer(this.inputIndex, 0, this.buffer.data.limit(), presentationTimeUs, 0);
                            }

                            this.resetInputBuffer();
                            this.codecReceivedBuffers = true;
                            this.codecReconfigurationState = 0;
                            ++this.decoderCounters.inputBufferCount;
                            return true;
                        } catch (CryptoException var8) {
                            throw PlaybackException.createForRenderer(var8, this.getIndex());
                        }
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

    protected void onCodecInitialized(String name, long initializedTimestampMs, long initializationDurationMs) {
    }

    protected void onInputFormatChanged(Format newFormat) throws PlaybackException {
        Format oldFormat = this.format;
        this.format = newFormat;
        this.pendingFormat = newFormat;
        boolean drmInitDataChanged = !Util.areEqual(this.format.drmInitData, oldFormat == null ? null : oldFormat.drmInitData);
        if (drmInitDataChanged) {
            if (this.format.drmInitData != null) {
                if (this.drmSessionManager == null) {
                    throw PlaybackException.createForRenderer(new IllegalStateException("Media requires a DrmSessionManager"), this.getIndex());
                }

                this.pendingDrmSession = this.drmSessionManager.acquireSession(Looper.myLooper(), this.format.drmInitData);
                if (this.pendingDrmSession == this.drmSession) {
                    this.drmSessionManager.releaseSession(this.pendingDrmSession);
                }
            } else {
                this.pendingDrmSession = null;
            }
        }

        boolean keepingCodec = false;
        if (this.pendingDrmSession == this.drmSession && this.codec != null) {
            switch (this.canKeepCodec(this.codec, this.codecInfo, oldFormat, this.format)) {
                case 0:
                    break;
                case 1:
                    keepingCodec = true;
                    break;
                case 2:
                default:
                    throw new IllegalStateException();
                case 3:
                    if (!this.codecNeedsReconfigureWorkaround) {
                        keepingCodec = true;
                        this.codecReconfigured = true;
                        this.codecReconfigurationState = 1;
                        boolean sameFormat = false;
                        if (oldFormat != null) sameFormat = this.format.width == oldFormat.width && this.format.height == oldFormat.height;
                        this.codecNeedsAdaptationWorkaroundBuffer = this.codecAdaptationWorkaroundMode == 2 || this.codecAdaptationWorkaroundMode == 1 && sameFormat;
                    }
            }
        }

        if (!keepingCodec) {
            this.reinitializeCodec();
        } else {
            this.updateCodecOperatingRate();
        }

    }

    protected void onOutputFormatChanged(MediaCodec codec, MediaFormat outputFormat) throws PlaybackException {
    }

    protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
    }

    protected void onProcessedOutputBuffer(long presentationTimeUs) {
    }

    protected int canKeepCodec(MediaCodec codec, MediaCodecInfo codecInfo, Format oldFormat, Format newFormat) {
        return 0;
    }

    public boolean isEnded() {
        return this.outputStreamEnded;
    }

    public boolean isReady() {
        return this.format != null && !this.waitingForKeys && (this.isSourceReady() || this.hasOutputBuffer() || this.codecHotswapDeadlineMs != -9223372036854775807L && SystemClock.elapsedRealtime() < this.codecHotswapDeadlineMs);
    }

    protected long getDequeueOutputBufferTimeoutUs() {
        return 0L;
    }

    protected float getCodecOperatingRate(float operatingRate, Format format, Format[] streamFormats) {
        return -1.0F;
    }

    private void updateCodecOperatingRate() throws PlaybackException {
        if (this.format != null && Util.SDK_INT >= 23) {
            float codecOperatingRate = this.getCodecOperatingRate(this.rendererOperatingRate, this.format, this.getStreamFormats());
            if (this.codecOperatingRate != codecOperatingRate) {
                this.codecOperatingRate = codecOperatingRate;
                if (this.codec != null && this.codecReinitializationState == 0) {
                    if (codecOperatingRate == -1.0F && this.codecConfiguredWithOperatingRate) {
                        this.reinitializeCodec();
                    } else if (codecOperatingRate != -1.0F && (this.codecConfiguredWithOperatingRate || codecOperatingRate > this.assumedMinimumCodecOperatingRate)) {
                        Bundle codecParameters = new Bundle();
                        codecParameters.putFloat("operating-rate", codecOperatingRate);
                        this.codec.setParameters(codecParameters);
                        this.codecConfiguredWithOperatingRate = true;
                    }
                }

            }
        }
    }

    private void reinitializeCodec() throws PlaybackException {
        this.availableCodecInfos = null;
        if (this.codecReceivedBuffers) {
            this.codecReinitializationState = 1;
        } else {
            this.releaseCodec();
            this.maybeInitCodec();
        }

    }

    private boolean drainOutputBuffer(long positionUs, long elapsedRealtimeUs) throws PlaybackException {
        if (!this.hasOutputBuffer()) {
            int outputIndex;
            if (this.codecNeedsEosOutputExceptionWorkaround && this.codecReceivedEos) {
                try {
                    outputIndex = this.codec.dequeueOutputBuffer(this.outputBufferInfo, this.getDequeueOutputBufferTimeoutUs());
                } catch (IllegalStateException var7) {
                    this.processEndOfStream();
                    if (this.outputStreamEnded) {
                        this.releaseCodec();
                    }

                    return false;
                }
            } else {
                outputIndex = this.codec.dequeueOutputBuffer(this.outputBufferInfo, this.getDequeueOutputBufferTimeoutUs());
            }

            if (outputIndex < 0) {
                if (outputIndex == -2) {
                    this.processOutputFormat();
                    return true;
                }

                if (outputIndex == -3) {
                    this.processOutputBuffersChanged();
                    return true;
                }

                if (this.codecNeedsEosPropagation && (this.inputStreamEnded || this.codecReinitializationState == 2)) {
                    this.processEndOfStream();
                }

                return false;
            }

            if (this.shouldSkipAdaptationWorkaroundOutputBuffer) {
                this.shouldSkipAdaptationWorkaroundOutputBuffer = false;
                this.codec.releaseOutputBuffer(outputIndex, false);
                return true;
            }

            if (this.outputBufferInfo.size == 0 && (this.outputBufferInfo.flags & 4) != 0) {
                this.processEndOfStream();
                return false;
            }

            this.outputIndex = outputIndex;
            this.outputBuffer = this.getOutputBuffer(outputIndex);
            if (this.outputBuffer != null) {
                this.outputBuffer.position(this.outputBufferInfo.offset);
                this.outputBuffer.limit(this.outputBufferInfo.offset + this.outputBufferInfo.size);
            }

            this.shouldSkipOutputBuffer = this.shouldSkipOutputBuffer(this.outputBufferInfo.presentationTimeUs);
            this.updateOutputFormatForTime(this.outputBufferInfo.presentationTimeUs);
        }

        boolean processedOutputBuffer;
        if (this.codecNeedsEosOutputExceptionWorkaround && this.codecReceivedEos) {
            try {
                processedOutputBuffer = this.processOutputBuffer(positionUs, elapsedRealtimeUs, this.codec, this.outputBuffer, this.outputIndex, this.outputBufferInfo.flags, this.outputBufferInfo.presentationTimeUs, this.shouldSkipOutputBuffer, this.outputFormat);
            } catch (IllegalStateException var8) {
                this.processEndOfStream();
                if (this.outputStreamEnded) {
                    this.releaseCodec();
                }

                return false;
            }
        } else {
            processedOutputBuffer = this.processOutputBuffer(positionUs, elapsedRealtimeUs, this.codec, this.outputBuffer, this.outputIndex, this.outputBufferInfo.flags, this.outputBufferInfo.presentationTimeUs, this.shouldSkipOutputBuffer, this.outputFormat);
        }

        if (processedOutputBuffer) {
            this.onProcessedOutputBuffer(this.outputBufferInfo.presentationTimeUs);
            boolean isEndOfStream = (this.outputBufferInfo.flags & 4) != 0;
            this.resetOutputBuffer();
            if (!isEndOfStream) {
                return true;
            }

            this.processEndOfStream();
        }

        return false;
    }

    private void processOutputFormat() throws PlaybackException {
        MediaFormat format = this.codec.getOutputFormat();
        if (this.codecAdaptationWorkaroundMode != 0 && format.getInteger("width") == 32 && format.getInteger("height") == 32) {
            this.shouldSkipAdaptationWorkaroundOutputBuffer = true;
        } else {
            if (this.codecNeedsMonoChannelCountWorkaround) {
                format.setInteger("channel-count", 1);
            }

            this.onOutputFormatChanged(this.codec, format);
        }
    }

    @SuppressWarnings("deprecation")
    private void processOutputBuffersChanged() {
        if (Util.SDK_INT < 21) {
            this.outputBuffers = this.codec.getOutputBuffers();
        }
    }

    protected abstract boolean processOutputBuffer(long var1, long var3, MediaCodec var5, ByteBuffer var6, int var7, int var8, long var9, boolean var11, Format var12) throws PlaybackException;

    protected void renderToEndOfStream() throws PlaybackException {
    }

    private void processEndOfStream() throws PlaybackException {
        if (this.codecReinitializationState == 2) {
            this.releaseCodec();
            this.maybeInitCodec();
        } else {
            this.outputStreamEnded = true;
            this.renderToEndOfStream();
        }

    }

    private boolean shouldSkipOutputBuffer(long presentationTimeUs) {
        int size = this.decodeOnlyPresentationTimestamps.size();

        for (int i = 0; i < size; ++i) {
            if (this.decodeOnlyPresentationTimestamps.get(i) == presentationTimeUs) {
                this.decodeOnlyPresentationTimestamps.remove(i);
                return true;
            }
        }

        return false;
    }

    private static CryptoInfo getFrameworkCryptoInfo(DecoderInputBuffer buffer, int adaptiveReconfigurationBytes) {
        CryptoInfo cryptoInfo = buffer.cryptoInfo.getFrameworkCryptoInfoV16();
        if (adaptiveReconfigurationBytes != 0) {
            if (cryptoInfo.numBytesOfClearData == null) {
                cryptoInfo.numBytesOfClearData = new int[1];
            }

            int[] var10000 = cryptoInfo.numBytesOfClearData;
            var10000[0] += adaptiveReconfigurationBytes;
        }
        return cryptoInfo;
    }

    private boolean deviceNeedsDrmKeysToConfigureCodecWorkaround() {
        return "Amazon".equals(Util.MANUFACTURER) && ("AFTM".equals(Util.MODEL) || "AFTB".equals(Util.MODEL));
    }

    private static boolean codecNeedsFlushWorkaround(String name) {
        return Util.SDK_INT < 18 || Util.SDK_INT == 18 && ("OMX.SEC.avc.dec".equals(name) || "OMX.SEC.avc.dec.secure".equals(name)) || Util.SDK_INT == 19 && Util.MODEL.startsWith("SM-G800") && ("OMX.Exynos.avc.dec".equals(name) || "OMX.Exynos.avc.dec.secure".equals(name));
    }

    private int codecAdaptationWorkaroundMode(String name) {
        if (Util.SDK_INT <= 25 && "OMX.Exynos.avc.dec.secure".equals(name) && (Util.MODEL.startsWith("SM-T585") || Util.MODEL.startsWith("SM-A510") || Util.MODEL.startsWith("SM-A520") || Util.MODEL.startsWith("SM-J700"))) {
            return 2;
        } else {
            return Util.SDK_INT < 24 && ("OMX.Nvidia.h264.decode".equals(name) || "OMX.Nvidia.h264.decode.secure".equals(name)) && ("flounder".equals(Util.DEVICE) || "flounder_lte".equals(Util.DEVICE) || "grouper".equals(Util.DEVICE) || "tilapia".equals(Util.DEVICE)) ? 1 : 0;
        }
    }

    private static boolean codecNeedsReconfigureWorkaround(String name) {
        return Util.MODEL.startsWith("SM-T230") && "OMX.MARVELL.VIDEO.HW.CODA7542DECODER".equals(name);
    }

    private static boolean codecNeedsDiscardToSpsWorkaround(String name, Format format) {
        return Util.SDK_INT < 21 && format.initializationData.isEmpty() && "OMX.MTK.VIDEO.DECODER.AVC".equals(name);
    }

    private static boolean codecNeedsEosPropagationWorkaround(MediaCodecInfo codecInfo) {
        String name = codecInfo.name;
        return Util.SDK_INT <= 17 && ("OMX.rk.video_decoder.avc".equals(name) || "OMX.allwinner.video.decoder.avc".equals(name)) || "Amazon".equals(Util.MANUFACTURER) && "AFTS".equals(Util.MODEL) && codecInfo.secure;
    }

    private static boolean codecNeedsEosFlushWorkaround(String name) {
        return Util.SDK_INT <= 23 && "OMX.google.vorbis.decoder".equals(name) || Util.SDK_INT <= 19 && ("hb2000".equals(Util.DEVICE) || "stvm8".equals(Util.DEVICE)) && ("OMX.amlogic.avc.decoder.awesome".equals(name) || "OMX.amlogic.avc.decoder.awesome.secure".equals(name));
    }

    private static boolean codecNeedsEosOutputExceptionWorkaround(String name) {
        return Util.SDK_INT == 21 && "OMX.google.aac.decoder".equals(name);
    }

    private static boolean codecNeedsMonoChannelCountWorkaround(String name, Format format) {
        return Util.SDK_INT <= 18 && format.channelCount == 1 && "OMX.MTK.AUDIO.DECODER.MP3".equals(name);
    }

    public static class DecoderInitializationException extends Exception {
        private static final int CUSTOM_ERROR_CODE_BASE = -50000;
        private static final int NO_SUITABLE_DECODER_ERROR = -49999;
        private static final int DECODER_QUERY_ERROR = -49998;
        public final String mimeType;
        public final boolean secureDecoderRequired;
        public final String decoderName;
        public final String diagnosticInfo;
        @Nullable
        public final MediaCodecRenderer.DecoderInitializationException fallbackDecoderInitializationException;

        public DecoderInitializationException(Format format, Throwable cause, boolean secureDecoderRequired, int errorCode) {
            this("Decoder init failed: [" + errorCode + "], " + format, cause, format.sampleMimeType, secureDecoderRequired, null, buildCustomDiagnosticInfo(errorCode), null);
        }

        public DecoderInitializationException(Format format, Throwable cause, boolean secureDecoderRequired, String decoderName) {
            this("Decoder init failed: " + decoderName + ", " + format, cause, format.sampleMimeType, secureDecoderRequired, decoderName, Util.SDK_INT >= 21 ? getDiagnosticInfoV21(cause) : null, null);
        }

        private DecoderInitializationException(String message, Throwable cause, String mimeType, boolean secureDecoderRequired, @Nullable String decoderName, @Nullable String diagnosticInfo, @Nullable MediaCodecRenderer.DecoderInitializationException fallbackDecoderInitializationException) {
            super(message, cause);
            this.mimeType = mimeType;
            this.secureDecoderRequired = secureDecoderRequired;
            this.decoderName = decoderName;
            this.diagnosticInfo = diagnosticInfo;
            this.fallbackDecoderInitializationException = fallbackDecoderInitializationException;
        }

        @CheckResult
        private MediaCodecRenderer.DecoderInitializationException copyWithFallbackException(DecoderInitializationException fallbackException) {
            return new DecoderInitializationException(this.getMessage(), this.getCause(), this.mimeType, this.secureDecoderRequired, this.decoderName, this.diagnosticInfo, fallbackException);
        }

        @TargetApi(21)
        private static String getDiagnosticInfoV21(Throwable cause) {
            return cause instanceof CodecException ? ((CodecException) cause).getDiagnosticInfo() : null;
        }

        private static String buildCustomDiagnosticInfo(int errorCode) {
            String sign = errorCode < 0 ? "neg_" : "";
            return "com.google.android.exoplayer.MediaCodecTrackRenderer_" + sign + Math.abs(errorCode);
        }
    }
}
