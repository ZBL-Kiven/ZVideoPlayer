package com.zj.playerLib.video;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaCodec.OnFrameRenderedListener;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zj.playerLib.Format;
import com.zj.playerLib.PlaybackException;
import com.zj.playerLib.decoder.DecoderCounters;
import com.zj.playerLib.decoder.DecoderInputBuffer;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.drm.DrmSessionManager;
import com.zj.playerLib.drm.FrameworkMediaCrypto;
import com.zj.playerLib.mediacodec.MediaCodecInfo;
import com.zj.playerLib.mediacodec.MediaCodecRenderer;
import com.zj.playerLib.mediacodec.MediaCodecSelector;
import com.zj.playerLib.mediacodec.MediaCodecUtil;
import com.zj.playerLib.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.zj.playerLib.mediacodec.MediaFormatUtil;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.MimeTypes;
import com.zj.playerLib.util.TraceUtil;
import com.zj.playerLib.util.Util;
import com.zj.playerLib.video.VideoRendererEventListener.EventDispatcher;

import java.nio.ByteBuffer;
import java.util.List;

@TargetApi(16)
public class MediaCodecVideoRenderer extends MediaCodecRenderer {
    private static final String TAG = "MediaCodecVideoRenderer";
    private static final String KEY_CROP_LEFT = "crop-left";
    private static final String KEY_CROP_RIGHT = "crop-right";
    private static final String KEY_CROP_BOTTOM = "crop-bottom";
    private static final String KEY_CROP_TOP = "crop-top";
    private static final int[] STANDARD_LONG_EDGE_VIDEO_PX = new int[]{1920, 1600, 1440, 1280, 960, 854, 640, 540, 480};
    private static final int MAX_PENDING_OUTPUT_STREAM_OFFSET_COUNT = 10;
    private static final float INITIAL_FORMAT_MAX_INPUT_SIZE_SCALE_FACTOR = 1.5F;
    private static boolean evaluatedDeviceNeedsSetOutputSurfaceWorkaround;
    private static boolean deviceNeedsSetOutputSurfaceWorkaround;
    private final Context context;
    private final VideoFrameReleaseTimeHelper frameReleaseTimeHelper;
    private final EventDispatcher eventDispatcher;
    private final long allowedJoiningTimeMs;
    private final int maxDroppedFramesToNotify;
    private final boolean deviceNeedsNoPostProcessWorkaround;
    private final long[] pendingOutputStreamOffsetsUs;
    private final long[] pendingOutputStreamSwitchTimesUs;
    private CodecMaxValues codecMaxValues;
    private boolean codecNeedsSetOutputSurfaceWorkaround;
    private Surface surface;
    private Surface dummySurface;
    private int scalingMode;
    private boolean renderedFirstFrame;
    private long initialPositionUs;
    private long joiningDeadlineMs;
    private long droppedFrameAccumulationStartTimeMs;
    private int droppedFrames;
    private int consecutiveDroppedFrameCount;
    private int buffersInCodecCount;
    private long lastRenderTimeUs;
    private int pendingRotationDegrees;
    private float pendingPixelWidthHeightRatio;
    private int currentWidth;
    private int currentHeight;
    private int currentUnappliedRotationDegrees;
    private float currentPixelWidthHeightRatio;
    private int reportedWidth;
    private int reportedHeight;
    private int reportedUnappliedRotationDegrees;
    private float reportedPixelWidthHeightRatio;
    private boolean tunneling;
    private int tunnelingAudioSessionId;
    OnFrameRenderedListenerV23 tunnelingOnFrameRenderedListener;
    private long lastInputTimeUs;
    private long outputStreamOffsetUs;
    private int pendingOutputStreamOffsetCount;
    @Nullable
    private VideoFrameMetadataListener frameMetadataListener;

    public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector) {
        this(context, mediaCodecSelector, 0L);
    }

    public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs) {
        this(context, mediaCodecSelector, allowedJoiningTimeMs, null, null, -1);
    }

    public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        this(context, mediaCodecSelector, allowedJoiningTimeMs, null, false, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    public MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        super(2, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, 30.0F);
        this.allowedJoiningTimeMs = allowedJoiningTimeMs;
        this.maxDroppedFramesToNotify = maxDroppedFramesToNotify;
        this.context = context.getApplicationContext();
        this.frameReleaseTimeHelper = new VideoFrameReleaseTimeHelper(this.context);
        this.eventDispatcher = new EventDispatcher(eventHandler, eventListener);
        this.deviceNeedsNoPostProcessWorkaround = deviceNeedsNoPostProcessWorkaround();
        this.pendingOutputStreamOffsetsUs = new long[10];
        this.pendingOutputStreamSwitchTimesUs = new long[10];
        this.outputStreamOffsetUs = -Long.MAX_VALUE;
        this.lastInputTimeUs = -Long.MAX_VALUE;
        this.joiningDeadlineMs = -Long.MAX_VALUE;
        this.currentWidth = -1;
        this.currentHeight = -1;
        this.currentPixelWidthHeightRatio = -1.0F;
        this.pendingPixelWidthHeightRatio = -1.0F;
        this.scalingMode = 1;
        this.clearReportedVideoSize();
    }

    protected int supportsFormat(MediaCodecSelector mediaCodecSelector, DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, Format format) throws DecoderQueryException {
        String mimeType = format.sampleMimeType;
        if (!MimeTypes.isVideo(mimeType)) {
            return 0;
        } else {
            boolean requiresSecureDecryption = false;
            DrmInitData drmInitData = format.drmInitData;
            if (drmInitData != null) {
                for(int i = 0; i < drmInitData.schemeDataCount; ++i) {
                    requiresSecureDecryption |= drmInitData.get(i).requiresSecureDecryption;
                }
            }

            List<MediaCodecInfo> decoderInfos = mediaCodecSelector.getDecoderInfos(format.sampleMimeType, requiresSecureDecryption);
            if (!decoderInfos.isEmpty()) {
                if (!supportsFormatDrm(drmSessionManager, drmInitData)) {
                    return 2;
                } else {
                    MediaCodecInfo decoderInfo = decoderInfos.get(0);
                    boolean isFormatSupported = decoderInfo.isFormatSupported(format);
                    int adaptiveSupport = decoderInfo.isSeamlessAdaptationSupported(format) ? 16 : 8;
                    int tunnelingSupport = decoderInfo.tunneling ? 32 : 0;
                    int formatSupport = isFormatSupported ? 4 : 3;
                    return adaptiveSupport | tunnelingSupport | formatSupport;
                }
            } else {
                return requiresSecureDecryption && !mediaCodecSelector.getDecoderInfos(format.sampleMimeType, false).isEmpty() ? 2 : 1;
            }
        }
    }

    protected void onEnabled(boolean joining) throws PlaybackException {
        super.onEnabled(joining);
        this.tunnelingAudioSessionId = this.getConfiguration().tunnelingAudioSessionId;
        this.tunneling = this.tunnelingAudioSessionId != 0;
        this.eventDispatcher.enabled(this.decoderCounters);
        this.frameReleaseTimeHelper.enable();
    }

    protected void onStreamChanged(Format[] formats, long offsetUs) throws PlaybackException {
        if (this.outputStreamOffsetUs == -Long.MAX_VALUE) {
            this.outputStreamOffsetUs = offsetUs;
        } else {
            if (this.pendingOutputStreamOffsetCount == this.pendingOutputStreamOffsetsUs.length) {
                Log.w("MediaCodecVideoRenderer", "Too many stream changes, so dropping offset: " + this.pendingOutputStreamOffsetsUs[this.pendingOutputStreamOffsetCount - 1]);
            } else {
                ++this.pendingOutputStreamOffsetCount;
            }

            this.pendingOutputStreamOffsetsUs[this.pendingOutputStreamOffsetCount - 1] = offsetUs;
            this.pendingOutputStreamSwitchTimesUs[this.pendingOutputStreamOffsetCount - 1] = this.lastInputTimeUs;
        }

        super.onStreamChanged(formats, offsetUs);
    }

    protected void onPositionReset(long positionUs, boolean joining) throws PlaybackException {
        super.onPositionReset(positionUs, joining);
        this.clearRenderedFirstFrame();
        this.initialPositionUs = -Long.MAX_VALUE;
        this.consecutiveDroppedFrameCount = 0;
        this.lastInputTimeUs = -Long.MAX_VALUE;
        if (this.pendingOutputStreamOffsetCount != 0) {
            this.outputStreamOffsetUs = this.pendingOutputStreamOffsetsUs[this.pendingOutputStreamOffsetCount - 1];
            this.pendingOutputStreamOffsetCount = 0;
        }

        if (joining) {
            this.setJoiningDeadlineMs();
        } else {
            this.joiningDeadlineMs = -Long.MAX_VALUE;
        }

    }

    public boolean isReady() {
        if (super.isReady() && (this.renderedFirstFrame || this.dummySurface != null && this.surface == this.dummySurface || this.getCodec() == null || this.tunneling)) {
            this.joiningDeadlineMs = -Long.MAX_VALUE;
            return true;
        } else if (this.joiningDeadlineMs == -Long.MAX_VALUE) {
            return false;
        } else if (SystemClock.elapsedRealtime() < this.joiningDeadlineMs) {
            return true;
        } else {
            this.joiningDeadlineMs = -Long.MAX_VALUE;
            return false;
        }
    }

    protected void onStarted() {
        super.onStarted();
        this.droppedFrames = 0;
        this.droppedFrameAccumulationStartTimeMs = SystemClock.elapsedRealtime();
        this.lastRenderTimeUs = SystemClock.elapsedRealtime() * 1000L;
    }

    protected void onStopped() {
        this.joiningDeadlineMs = -Long.MAX_VALUE;
        this.maybeNotifyDroppedFrames();
        super.onStopped();
    }

    protected void onDisabled() {
        this.currentWidth = -1;
        this.currentHeight = -1;
        this.currentPixelWidthHeightRatio = -1.0F;
        this.pendingPixelWidthHeightRatio = -1.0F;
        this.outputStreamOffsetUs = -Long.MAX_VALUE;
        this.lastInputTimeUs = -Long.MAX_VALUE;
        this.pendingOutputStreamOffsetCount = 0;
        this.clearReportedVideoSize();
        this.clearRenderedFirstFrame();
        this.frameReleaseTimeHelper.disable();
        this.tunnelingOnFrameRenderedListener = null;
        this.tunneling = false;

        try {
            super.onDisabled();
        } finally {
            this.decoderCounters.ensureUpdated();
            this.eventDispatcher.disabled(this.decoderCounters);
        }

    }

    public void handleMessage(int messageType, @Nullable Object message) throws PlaybackException {
        if (messageType == 1) {
            this.setSurface((Surface)message);
        } else if (messageType == 4) {
            this.scalingMode = (Integer)message;
            MediaCodec codec = this.getCodec();
            if (codec != null) {
                codec.setVideoScalingMode(this.scalingMode);
            }
        } else if (messageType == 6) {
            this.frameMetadataListener = (VideoFrameMetadataListener)message;
        } else {
            super.handleMessage(messageType, message);
        }

    }

    private void setSurface(Surface surface) throws PlaybackException {
        if (surface == null) {
            if (this.dummySurface != null) {
                surface = this.dummySurface;
            } else {
                MediaCodecInfo codecInfo = this.getCodecInfo();
                if (codecInfo != null && this.shouldUseDummySurface(codecInfo)) {
                    this.dummySurface = DummySurface.newInstanceV17(this.context, codecInfo.secure);
                    surface = this.dummySurface;
                }
            }
        }

        if (this.surface != surface) {
            this.surface = surface;
            int state = this.getState();
            if (state == 1 || state == 2) {
                MediaCodec codec = this.getCodec();
                if (Util.SDK_INT >= 23 && codec != null && surface != null && !this.codecNeedsSetOutputSurfaceWorkaround) {
                    setOutputSurfaceV23(codec, surface);
                } else {
                    this.releaseCodec();
                    this.maybeInitCodec();
                }
            }

            if (surface != null && surface != this.dummySurface) {
                this.maybeRenotifyVideoSizeChanged();
                this.clearRenderedFirstFrame();
                if (state == 2) {
                    this.setJoiningDeadlineMs();
                }
            } else {
                this.clearReportedVideoSize();
                this.clearRenderedFirstFrame();
            }
        } else if (surface != null && surface != this.dummySurface) {
            this.maybeRenotifyVideoSizeChanged();
            this.maybeRenotifyRenderedFirstFrame();
        }

    }

    protected boolean shouldInitCodec(MediaCodecInfo codecInfo) {
        return this.surface != null || this.shouldUseDummySurface(codecInfo);
    }

    protected boolean getCodecNeedsEosPropagation() {
        return this.tunneling;
    }

    protected void configureCodec(MediaCodecInfo codecInfo, MediaCodec codec, Format format, MediaCrypto crypto, float codecOperatingRate) throws DecoderQueryException {
        this.codecMaxValues = this.getCodecMaxValues(codecInfo, format, this.getStreamFormats());
        MediaFormat mediaFormat = this.getMediaFormat(format, this.codecMaxValues, codecOperatingRate, this.deviceNeedsNoPostProcessWorkaround, this.tunnelingAudioSessionId);
        if (this.surface == null) {
            Assertions.checkState(this.shouldUseDummySurface(codecInfo));
            if (this.dummySurface == null) {
                this.dummySurface = DummySurface.newInstanceV17(this.context, codecInfo.secure);
            }

            this.surface = this.dummySurface;
        }

        codec.configure(mediaFormat, this.surface, crypto, 0);
        if (Util.SDK_INT >= 23 && this.tunneling) {
            this.tunnelingOnFrameRenderedListener = new OnFrameRenderedListenerV23(codec);
        }

    }

    protected int canKeepCodec(MediaCodec codec, MediaCodecInfo codecInfo, Format oldFormat, Format newFormat) {
        if (codecInfo.isSeamlessAdaptationSupported(oldFormat, newFormat, true) && newFormat.width <= this.codecMaxValues.width && newFormat.height <= this.codecMaxValues.height && getMaxInputSize(codecInfo, newFormat) <= this.codecMaxValues.inputSize) {
            return oldFormat.initializationDataEquals(newFormat) ? 1 : 3;
        } else {
            return 0;
        }
    }

    @CallSuper
    protected void releaseCodec() {
        try {
            super.releaseCodec();
        } finally {
            this.buffersInCodecCount = 0;
            if (this.dummySurface != null) {
                if (this.surface == this.dummySurface) {
                    this.surface = null;
                }

                this.dummySurface.release();
                this.dummySurface = null;
            }

        }

    }

    @CallSuper
    protected void flushCodec() throws PlaybackException {
        super.flushCodec();
        this.buffersInCodecCount = 0;
    }

    protected float getCodecOperatingRate(float operatingRate, Format format, Format[] streamFormats) {
        float maxFrameRate = -1.0F;
        Format[] var5 = streamFormats;
        int var6 = streamFormats.length;

        for(int var7 = 0; var7 < var6; ++var7) {
            Format streamFormat = var5[var7];
            float streamFrameRate = streamFormat.frameRate;
            if (streamFrameRate != -1.0F) {
                maxFrameRate = Math.max(maxFrameRate, streamFrameRate);
            }
        }

        return maxFrameRate == -1.0F ? -1.0F : maxFrameRate * operatingRate;
    }

    protected void onCodecInitialized(String name, long initializedTimestampMs, long initializationDurationMs) {
        this.eventDispatcher.decoderInitialized(name, initializedTimestampMs, initializationDurationMs);
        this.codecNeedsSetOutputSurfaceWorkaround = this.codecNeedsSetOutputSurfaceWorkaround(name);
    }

    protected void onInputFormatChanged(Format newFormat) throws PlaybackException {
        super.onInputFormatChanged(newFormat);
        this.eventDispatcher.inputFormatChanged(newFormat);
        this.pendingPixelWidthHeightRatio = newFormat.pixelWidthHeightRatio;
        this.pendingRotationDegrees = newFormat.rotationDegrees;
    }

    @CallSuper
    protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
        ++this.buffersInCodecCount;
        this.lastInputTimeUs = Math.max(buffer.timeUs, this.lastInputTimeUs);
        if (Util.SDK_INT < 23 && this.tunneling) {
            this.onProcessedTunneledBuffer(buffer.timeUs);
        }

    }

    protected void onOutputFormatChanged(MediaCodec codec, MediaFormat outputFormat) {
        boolean hasCrop = outputFormat.containsKey("crop-right") && outputFormat.containsKey("crop-left") && outputFormat.containsKey("crop-bottom") && outputFormat.containsKey("crop-top");
        int width = hasCrop ? outputFormat.getInteger("crop-right") - outputFormat.getInteger("crop-left") + 1 : outputFormat.getInteger("width");
        int height = hasCrop ? outputFormat.getInteger("crop-bottom") - outputFormat.getInteger("crop-top") + 1 : outputFormat.getInteger("height");
        this.processOutputFormat(codec, width, height);
    }

    protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec codec, ByteBuffer buffer, int bufferIndex, int bufferFlags, long bufferPresentationTimeUs, boolean shouldSkip, Format format) throws PlaybackException {
        if (this.initialPositionUs == -Long.MAX_VALUE) {
            this.initialPositionUs = positionUs;
        }

        long presentationTimeUs = bufferPresentationTimeUs - this.outputStreamOffsetUs;
        if (shouldSkip) {
            this.skipOutputBuffer(codec, bufferIndex, presentationTimeUs);
            return true;
        } else {
            long earlyUs = bufferPresentationTimeUs - positionUs;
            if (this.surface == this.dummySurface) {
                if (isBufferLate(earlyUs)) {
                    this.skipOutputBuffer(codec, bufferIndex, presentationTimeUs);
                    return true;
                } else {
                    return false;
                }
            } else {
                long elapsedRealtimeNowUs = SystemClock.elapsedRealtime() * 1000L;
                boolean isStarted = this.getState() == 2;
                long elapsedSinceStartOfLoopUs;
                if (!this.renderedFirstFrame || isStarted && this.shouldForceRenderOutputBuffer(earlyUs, elapsedRealtimeNowUs - this.lastRenderTimeUs)) {
                    elapsedSinceStartOfLoopUs = System.nanoTime();
                    this.notifyFrameMetadataListener(presentationTimeUs, elapsedSinceStartOfLoopUs, format);
                    if (Util.SDK_INT >= 21) {
                        this.renderOutputBufferV21(codec, bufferIndex, presentationTimeUs, elapsedSinceStartOfLoopUs);
                    } else {
                        this.renderOutputBuffer(codec, bufferIndex, presentationTimeUs);
                    }

                    return true;
                } else if (isStarted && positionUs != this.initialPositionUs) {
                    elapsedSinceStartOfLoopUs = elapsedRealtimeNowUs - elapsedRealtimeUs;
                    earlyUs -= elapsedSinceStartOfLoopUs;
                    long systemTimeNs = System.nanoTime();
                    long unadjustedFrameReleaseTimeNs = systemTimeNs + earlyUs * 1000L;
                    long adjustedReleaseTimeNs = this.frameReleaseTimeHelper.adjustReleaseTime(bufferPresentationTimeUs, unadjustedFrameReleaseTimeNs);
                    earlyUs = (adjustedReleaseTimeNs - systemTimeNs) / 1000L;
                    if (this.shouldDropBuffersToKeyframe(earlyUs, elapsedRealtimeUs) && this.maybeDropBuffersToKeyframe(codec, bufferIndex, presentationTimeUs, positionUs)) {
                        return false;
                    } else if (this.shouldDropOutputBuffer(earlyUs, elapsedRealtimeUs)) {
                        this.dropOutputBuffer(codec, bufferIndex, presentationTimeUs);
                        return true;
                    } else {
                        if (Util.SDK_INT >= 21) {
                            if (earlyUs < 50000L) {
                                this.notifyFrameMetadataListener(presentationTimeUs, adjustedReleaseTimeNs, format);
                                this.renderOutputBufferV21(codec, bufferIndex, presentationTimeUs, adjustedReleaseTimeNs);
                                return true;
                            }
                        } else if (earlyUs < 30000L) {
                            if (earlyUs > 11000L) {
                                try {
                                    Thread.sleep((earlyUs - 10000L) / 1000L);
                                } catch (InterruptedException var29) {
                                    Thread.currentThread().interrupt();
                                    return false;
                                }
                            }

                            this.notifyFrameMetadataListener(presentationTimeUs, adjustedReleaseTimeNs, format);
                            this.renderOutputBuffer(codec, bufferIndex, presentationTimeUs);
                            return true;
                        }

                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
    }

    private void processOutputFormat(MediaCodec codec, int width, int height) {
        this.currentWidth = width;
        this.currentHeight = height;
        this.currentPixelWidthHeightRatio = this.pendingPixelWidthHeightRatio;
        if (Util.SDK_INT >= 21) {
            if (this.pendingRotationDegrees == 90 || this.pendingRotationDegrees == 270) {
                int rotatedHeight = this.currentWidth;
                this.currentWidth = this.currentHeight;
                this.currentHeight = rotatedHeight;
                this.currentPixelWidthHeightRatio = 1.0F / this.currentPixelWidthHeightRatio;
            }
        } else {
            this.currentUnappliedRotationDegrees = this.pendingRotationDegrees;
        }

        codec.setVideoScalingMode(this.scalingMode);
    }

    private void notifyFrameMetadataListener(long presentationTimeUs, long releaseTimeNs, Format format) {
        if (this.frameMetadataListener != null) {
            this.frameMetadataListener.onVideoFrameAboutToBeRendered(presentationTimeUs, releaseTimeNs, format);
        }

    }

    protected long getOutputStreamOffsetUs() {
        return this.outputStreamOffsetUs;
    }

    protected void onProcessedTunneledBuffer(long presentationTimeUs) {
        Format format = this.updateOutputFormatForTime(presentationTimeUs);
        if (format != null) {
            this.processOutputFormat(this.getCodec(), format.width, format.height);
        }

        this.maybeNotifyVideoSizeChanged();
        this.maybeNotifyRenderedFirstFrame();
        this.onProcessedOutputBuffer(presentationTimeUs);
    }

    @CallSuper
    protected void onProcessedOutputBuffer(long presentationTimeUs) {
        --this.buffersInCodecCount;

        while(this.pendingOutputStreamOffsetCount != 0 && presentationTimeUs >= this.pendingOutputStreamSwitchTimesUs[0]) {
            this.outputStreamOffsetUs = this.pendingOutputStreamOffsetsUs[0];
            --this.pendingOutputStreamOffsetCount;
            System.arraycopy(this.pendingOutputStreamOffsetsUs, 1, this.pendingOutputStreamOffsetsUs, 0, this.pendingOutputStreamOffsetCount);
            System.arraycopy(this.pendingOutputStreamSwitchTimesUs, 1, this.pendingOutputStreamSwitchTimesUs, 0, this.pendingOutputStreamOffsetCount);
        }

    }

    protected boolean shouldDropOutputBuffer(long earlyUs, long elapsedRealtimeUs) {
        return isBufferLate(earlyUs);
    }

    protected boolean shouldDropBuffersToKeyframe(long earlyUs, long elapsedRealtimeUs) {
        return isBufferVeryLate(earlyUs);
    }

    protected boolean shouldForceRenderOutputBuffer(long earlyUs, long elapsedSinceLastRenderUs) {
        return isBufferLate(earlyUs) && elapsedSinceLastRenderUs > 100000L;
    }

    protected void skipOutputBuffer(MediaCodec codec, int index, long presentationTimeUs) {
        TraceUtil.beginSection("skipVideoBuffer");
        codec.releaseOutputBuffer(index, false);
        TraceUtil.endSection();
        ++this.decoderCounters.skippedOutputBufferCount;
    }

    protected void dropOutputBuffer(MediaCodec codec, int index, long presentationTimeUs) {
        TraceUtil.beginSection("dropVideoBuffer");
        codec.releaseOutputBuffer(index, false);
        TraceUtil.endSection();
        this.updateDroppedBufferCounters(1);
    }

    protected boolean maybeDropBuffersToKeyframe(MediaCodec codec, int index, long presentationTimeUs, long positionUs) throws PlaybackException {
        int droppedSourceBufferCount = this.skipSource(positionUs);
        if (droppedSourceBufferCount == 0) {
            return false;
        } else {
            ++this.decoderCounters.droppedToKeyframeCount;
            this.updateDroppedBufferCounters(this.buffersInCodecCount + droppedSourceBufferCount);
            this.flushCodec();
            return true;
        }
    }

    protected void updateDroppedBufferCounters(int droppedBufferCount) {
        DecoderCounters var10000 = this.decoderCounters;
        var10000.droppedBufferCount += droppedBufferCount;
        this.droppedFrames += droppedBufferCount;
        this.consecutiveDroppedFrameCount += droppedBufferCount;
        this.decoderCounters.maxConsecutiveDroppedBufferCount = Math.max(this.consecutiveDroppedFrameCount, this.decoderCounters.maxConsecutiveDroppedBufferCount);
        if (this.maxDroppedFramesToNotify > 0 && this.droppedFrames >= this.maxDroppedFramesToNotify) {
            this.maybeNotifyDroppedFrames();
        }

    }

    protected void renderOutputBuffer(MediaCodec codec, int index, long presentationTimeUs) {
        this.maybeNotifyVideoSizeChanged();
        TraceUtil.beginSection("releaseOutputBuffer");
        codec.releaseOutputBuffer(index, true);
        TraceUtil.endSection();
        this.lastRenderTimeUs = SystemClock.elapsedRealtime() * 1000L;
        ++this.decoderCounters.renderedOutputBufferCount;
        this.consecutiveDroppedFrameCount = 0;
        this.maybeNotifyRenderedFirstFrame();
    }

    @TargetApi(21)
    protected void renderOutputBufferV21(MediaCodec codec, int index, long presentationTimeUs, long releaseTimeNs) {
        this.maybeNotifyVideoSizeChanged();
        TraceUtil.beginSection("releaseOutputBuffer");
        codec.releaseOutputBuffer(index, releaseTimeNs);
        TraceUtil.endSection();
        this.lastRenderTimeUs = SystemClock.elapsedRealtime() * 1000L;
        ++this.decoderCounters.renderedOutputBufferCount;
        this.consecutiveDroppedFrameCount = 0;
        this.maybeNotifyRenderedFirstFrame();
    }

    private boolean shouldUseDummySurface(MediaCodecInfo codecInfo) {
        return Util.SDK_INT >= 23 && !this.tunneling && !this.codecNeedsSetOutputSurfaceWorkaround(codecInfo.name) && (!codecInfo.secure || DummySurface.isSecureSupported(this.context));
    }

    private void setJoiningDeadlineMs() {
        this.joiningDeadlineMs = this.allowedJoiningTimeMs > 0L ? SystemClock.elapsedRealtime() + this.allowedJoiningTimeMs : -Long.MAX_VALUE;
    }

    private void clearRenderedFirstFrame() {
        this.renderedFirstFrame = false;
        if (Util.SDK_INT >= 23 && this.tunneling) {
            MediaCodec codec = this.getCodec();
            if (codec != null) {
                this.tunnelingOnFrameRenderedListener = new OnFrameRenderedListenerV23(codec);
            }
        }

    }

    void maybeNotifyRenderedFirstFrame() {
        if (!this.renderedFirstFrame) {
            this.renderedFirstFrame = true;
            this.eventDispatcher.renderedFirstFrame(this.surface);
        }

    }

    private void maybeRenotifyRenderedFirstFrame() {
        if (this.renderedFirstFrame) {
            this.eventDispatcher.renderedFirstFrame(this.surface);
        }

    }

    private void clearReportedVideoSize() {
        this.reportedWidth = -1;
        this.reportedHeight = -1;
        this.reportedPixelWidthHeightRatio = -1.0F;
        this.reportedUnappliedRotationDegrees = -1;
    }

    private void maybeNotifyVideoSizeChanged() {
        if ((this.currentWidth != -1 || this.currentHeight != -1) && (this.reportedWidth != this.currentWidth || this.reportedHeight != this.currentHeight || this.reportedUnappliedRotationDegrees != this.currentUnappliedRotationDegrees || this.reportedPixelWidthHeightRatio != this.currentPixelWidthHeightRatio)) {
            this.eventDispatcher.videoSizeChanged(this.currentWidth, this.currentHeight, this.currentUnappliedRotationDegrees, this.currentPixelWidthHeightRatio);
            this.reportedWidth = this.currentWidth;
            this.reportedHeight = this.currentHeight;
            this.reportedUnappliedRotationDegrees = this.currentUnappliedRotationDegrees;
            this.reportedPixelWidthHeightRatio = this.currentPixelWidthHeightRatio;
        }

    }

    private void maybeRenotifyVideoSizeChanged() {
        if (this.reportedWidth != -1 || this.reportedHeight != -1) {
            this.eventDispatcher.videoSizeChanged(this.reportedWidth, this.reportedHeight, this.reportedUnappliedRotationDegrees, this.reportedPixelWidthHeightRatio);
        }

    }

    private void maybeNotifyDroppedFrames() {
        if (this.droppedFrames > 0) {
            long now = SystemClock.elapsedRealtime();
            long elapsedMs = now - this.droppedFrameAccumulationStartTimeMs;
            this.eventDispatcher.droppedFrames(this.droppedFrames, elapsedMs);
            this.droppedFrames = 0;
            this.droppedFrameAccumulationStartTimeMs = now;
        }

    }

    private static boolean isBufferLate(long earlyUs) {
        return earlyUs < -30000L;
    }

    private static boolean isBufferVeryLate(long earlyUs) {
        return earlyUs < -500000L;
    }

    @TargetApi(23)
    private static void setOutputSurfaceV23(MediaCodec codec, Surface surface) {
        codec.setOutputSurface(surface);
    }

    @TargetApi(21)
    private static void configureTunnelingV21(MediaFormat mediaFormat, int tunnelingAudioSessionId) {
        mediaFormat.setFeatureEnabled("tunneled-playback", true);
        mediaFormat.setInteger("audio-session-id", tunnelingAudioSessionId);
    }

    @SuppressLint({"InlinedApi"})
    protected MediaFormat getMediaFormat(Format format, CodecMaxValues codecMaxValues, float codecOperatingRate, boolean deviceNeedsNoPostProcessWorkaround, int tunnelingAudioSessionId) {
        MediaFormat mediaFormat = new MediaFormat();
        mediaFormat.setString("mime", format.sampleMimeType);
        mediaFormat.setInteger("width", format.width);
        mediaFormat.setInteger("height", format.height);
        MediaFormatUtil.setCsdBuffers(mediaFormat, format.initializationData);
        MediaFormatUtil.maybeSetFloat(mediaFormat, "frame-rate", format.frameRate);
        MediaFormatUtil.maybeSetInteger(mediaFormat, "rotation-degrees", format.rotationDegrees);
        MediaFormatUtil.maybeSetColorInfo(mediaFormat, format.colorInfo);
        mediaFormat.setInteger("max-width", codecMaxValues.width);
        mediaFormat.setInteger("max-height", codecMaxValues.height);
        MediaFormatUtil.maybeSetInteger(mediaFormat, "max-input-size", codecMaxValues.inputSize);
        if (Util.SDK_INT >= 23) {
            mediaFormat.setInteger("priority", 0);
            if (codecOperatingRate != -1.0F) {
                mediaFormat.setFloat("operating-rate", codecOperatingRate);
            }
        }

        if (deviceNeedsNoPostProcessWorkaround) {
            mediaFormat.setInteger("no-post-process", 1);
            mediaFormat.setInteger("auto-frc", 0);
        }

        if (tunnelingAudioSessionId != 0) {
            configureTunnelingV21(mediaFormat, tunnelingAudioSessionId);
        }

        return mediaFormat;
    }

    protected CodecMaxValues getCodecMaxValues(MediaCodecInfo codecInfo, Format format, Format[] streamFormats) throws DecoderQueryException {
        int maxWidth = format.width;
        int maxHeight = format.height;
        int maxInputSize = getMaxInputSize(codecInfo, format);
        if (streamFormats.length == 1) {
            if (maxInputSize != -1) {
                int codecMaxInputSize = getCodecMaxInputSize(codecInfo, format.sampleMimeType, format.width, format.height);
                if (codecMaxInputSize != -1) {
                    int scaledMaxInputSize = (int)((float)maxInputSize * 1.5F);
                    maxInputSize = Math.min(scaledMaxInputSize, codecMaxInputSize);
                }
            }

            return new CodecMaxValues(maxWidth, maxHeight, maxInputSize);
        } else {
            boolean haveUnknownDimensions = false;
            Format[] var8 = streamFormats;
            int var9 = streamFormats.length;

            for(int var10 = 0; var10 < var9; ++var10) {
                Format streamFormat = var8[var10];
                if (codecInfo.isSeamlessAdaptationSupported(format, streamFormat, false)) {
                    haveUnknownDimensions |= streamFormat.width == -1 || streamFormat.height == -1;
                    maxWidth = Math.max(maxWidth, streamFormat.width);
                    maxHeight = Math.max(maxHeight, streamFormat.height);
                    maxInputSize = Math.max(maxInputSize, getMaxInputSize(codecInfo, streamFormat));
                }
            }

            if (haveUnknownDimensions) {
                Log.w("MediaCodecVideoRenderer", "Resolutions unknown. Codec max resolution: " + maxWidth + "x" + maxHeight);
                Point codecMaxSize = getCodecMaxSize(codecInfo, format);
                if (codecMaxSize != null) {
                    maxWidth = Math.max(maxWidth, codecMaxSize.x);
                    maxHeight = Math.max(maxHeight, codecMaxSize.y);
                    maxInputSize = Math.max(maxInputSize, getCodecMaxInputSize(codecInfo, format.sampleMimeType, maxWidth, maxHeight));
                    Log.w("MediaCodecVideoRenderer", "Codec max resolution adjusted to: " + maxWidth + "x" + maxHeight);
                }
            }

            return new CodecMaxValues(maxWidth, maxHeight, maxInputSize);
        }
    }

    private static Point getCodecMaxSize(MediaCodecInfo codecInfo, Format format) throws DecoderQueryException {
        boolean isVerticalVideo = format.height > format.width;
        int formatLongEdgePx = isVerticalVideo ? format.height : format.width;
        int formatShortEdgePx = isVerticalVideo ? format.width : format.height;
        float aspectRatio = (float)formatShortEdgePx / (float)formatLongEdgePx;
        int[] var6 = STANDARD_LONG_EDGE_VIDEO_PX;
        int var7 = var6.length;

        for(int var8 = 0; var8 < var7; ++var8) {
            int longEdgePx = var6[var8];
            int shortEdgePx = (int)((float)longEdgePx * aspectRatio);
            if (longEdgePx <= formatLongEdgePx || shortEdgePx <= formatShortEdgePx) {
                return null;
            }

            if (Util.SDK_INT >= 21) {
                Point alignedSize = codecInfo.alignVideoSizeV21(isVerticalVideo ? shortEdgePx : longEdgePx, isVerticalVideo ? longEdgePx : shortEdgePx);
                float frameRate = format.frameRate;
                if (codecInfo.isVideoSizeAndRateSupportedV21(alignedSize.x, alignedSize.y, frameRate)) {
                    return alignedSize;
                }
            } else {
                longEdgePx = Util.ceilDivide(longEdgePx, 16) * 16;
                shortEdgePx = Util.ceilDivide(shortEdgePx, 16) * 16;
                if (longEdgePx * shortEdgePx <= MediaCodecUtil.maxH264DecodAbleFrameSize()) {
                    return new Point(isVerticalVideo ? shortEdgePx : longEdgePx, isVerticalVideo ? longEdgePx : shortEdgePx);
                }
            }
        }

        return null;
    }

    private static int getMaxInputSize(MediaCodecInfo codecInfo, Format format) {
        if (format.maxInputSize == -1) {
            return getCodecMaxInputSize(codecInfo, format.sampleMimeType, format.width, format.height);
        } else {
            int totalInitializationDataSize = 0;
            int initializationDataCount = format.initializationData.size();

            for(int i = 0; i < initializationDataCount; ++i) {
                totalInitializationDataSize += format.initializationData.get(i).length;
            }

            return format.maxInputSize + totalInitializationDataSize;
        }
    }

    private static int getCodecMaxInputSize(MediaCodecInfo codecInfo, String sampleMimeType, int width, int height) {
        if (width != -1 && height != -1) {
            byte var7 = -1;
            switch(sampleMimeType.hashCode()) {
            case -1664118616:
                if (sampleMimeType.equals("video/3gpp")) {
                    var7 = 0;
                }
                break;
            case -1662541442:
                if (sampleMimeType.equals("video/hevc")) {
                    var7 = 4;
                }
                break;
            case 1187890754:
                if (sampleMimeType.equals("video/mp4v-es")) {
                    var7 = 1;
                }
                break;
            case 1331836730:
                if (sampleMimeType.equals("video/avc")) {
                    var7 = 2;
                }
                break;
            case 1599127256:
                if (sampleMimeType.equals("video/x-vnd.on2.vp8")) {
                    var7 = 3;
                }
                break;
            case 1599127257:
                if (sampleMimeType.equals("video/x-vnd.on2.vp9")) {
                    var7 = 5;
                }
            }

            int maxPixels;
            byte minCompressionRatio;
            switch(var7) {
            case 0:
            case 1:
                maxPixels = width * height;
                minCompressionRatio = 2;
                break;
            case 2:
                if ("BRAVIA 4K 2015".equals(Util.MODEL) || "Amazon".equals(Util.MANUFACTURER) && ("KFSOWI".equals(Util.MODEL) || "AFTS".equals(Util.MODEL) && codecInfo.secure)) {
                    return -1;
                }

                maxPixels = Util.ceilDivide(width, 16) * Util.ceilDivide(height, 16) * 16 * 16;
                minCompressionRatio = 2;
                break;
            case 3:
                maxPixels = width * height;
                minCompressionRatio = 2;
                break;
            case 4:
            case 5:
                maxPixels = width * height;
                minCompressionRatio = 4;
                break;
            default:
                return -1;
            }

            return maxPixels * 3 / (2 * minCompressionRatio);
        } else {
            return -1;
        }
    }

    private static boolean deviceNeedsNoPostProcessWorkaround() {
        return "NVIDIA".equals(Util.MANUFACTURER);
    }

    protected boolean codecNeedsSetOutputSurfaceWorkaround(String name) {
        if (name.startsWith("OMX.google")) {
            return false;
        } else {
            Class var2 = MediaCodecVideoRenderer.class;
            synchronized(MediaCodecVideoRenderer.class) {
                if (!evaluatedDeviceNeedsSetOutputSurfaceWorkaround) {
                    if (Util.SDK_INT <= 27 && "dangal".equals(Util.DEVICE)) {
                        deviceNeedsSetOutputSurfaceWorkaround = true;
                    } else if (Util.SDK_INT < 27) {
                        String var3 = Util.DEVICE;
                        byte var4 = -1;
                        switch(var3.hashCode()) {
                        case -2144781245:
                            if (var3.equals("GIONEE_SWW1609")) {
                                var4 = 42;
                            }
                            break;
                        case -2144781185:
                            if (var3.equals("GIONEE_SWW1627")) {
                                var4 = 43;
                            }
                            break;
                        case -2144781160:
                            if (var3.equals("GIONEE_SWW1631")) {
                                var4 = 44;
                            }
                            break;
                        case -2097309513:
                            if (var3.equals("K50a40")) {
                                var4 = 61;
                            }
                            break;
                        case -2022874474:
                            if (var3.equals("CP8676_I02")) {
                                var4 = 18;
                            }
                            break;
                        case -1978993182:
                            if (var3.equals("NX541J")) {
                                var4 = 74;
                            }
                            break;
                        case -1978990237:
                            if (var3.equals("NX573J")) {
                                var4 = 75;
                            }
                            break;
                        case -1936688988:
                            if (var3.equals("PGN528")) {
                                var4 = 85;
                            }
                            break;
                        case -1936688066:
                            if (var3.equals("PGN610")) {
                                var4 = 86;
                            }
                            break;
                        case -1936688065:
                            if (var3.equals("PGN611")) {
                                var4 = 87;
                            }
                            break;
                        case -1931988508:
                            if (var3.equals("AquaPowerM")) {
                                var4 = 10;
                            }
                            break;
                        case -1696512866:
                            if (var3.equals("XT1663")) {
                                var4 = 120;
                            }
                            break;
                        case -1680025915:
                            if (var3.equals("ComioS1")) {
                                var4 = 17;
                            }
                            break;
                        case -1615810839:
                            if (var3.equals("Phantom6")) {
                                var4 = 88;
                            }
                            break;
                        case -1554255044:
                            if (var3.equals("vernee_M5")) {
                                var4 = 113;
                            }
                            break;
                        case -1481772737:
                            if (var3.equals("panell_dl")) {
                                var4 = 81;
                            }
                            break;
                        case -1481772730:
                            if (var3.equals("panell_ds")) {
                                var4 = 82;
                            }
                            break;
                        case -1481772729:
                            if (var3.equals("panell_dt")) {
                                var4 = 83;
                            }
                            break;
                        case -1320080169:
                            if (var3.equals("GiONEE_GBL7319")) {
                                var4 = 40;
                            }
                            break;
                        case -1217592143:
                            if (var3.equals("BRAVIA_ATV2")) {
                                var4 = 14;
                            }
                            break;
                        case -1180384755:
                            if (var3.equals("iris60")) {
                                var4 = 57;
                            }
                            break;
                        case -1139198265:
                            if (var3.equals("Slate_Pro")) {
                                var4 = 101;
                            }
                            break;
                        case -1052835013:
                            if (var3.equals("namath")) {
                                var4 = 72;
                            }
                            break;
                        case -993250464:
                            if (var3.equals("A10-70F")) {
                                var4 = 3;
                            }
                            break;
                        case -965403638:
                            if (var3.equals("s905x018")) {
                                var4 = 103;
                            }
                            break;
                        case -958336948:
                            if (var3.equals("ELUGA_Ray_X")) {
                                var4 = 28;
                            }
                            break;
                        case -879245230:
                            if (var3.equals("tcl_eu")) {
                                var4 = 109;
                            }
                            break;
                        case -842500323:
                            if (var3.equals("nicklaus_f")) {
                                var4 = 73;
                            }
                            break;
                        case -821392978:
                            if (var3.equals("A7000-a")) {
                                var4 = 6;
                            }
                            break;
                        case -797483286:
                            if (var3.equals("SVP-DTV15")) {
                                var4 = 102;
                            }
                            break;
                        case -794946968:
                            if (var3.equals("watson")) {
                                var4 = 114;
                            }
                            break;
                        case -788334647:
                            if (var3.equals("whyred")) {
                                var4 = 115;
                            }
                            break;
                        case -782144577:
                            if (var3.equals("OnePlus5T")) {
                                var4 = 76;
                            }
                            break;
                        case -575125681:
                            if (var3.equals("GiONEE_CBL7513")) {
                                var4 = 39;
                            }
                            break;
                        case -521118391:
                            if (var3.equals("GIONEE_GBL7360")) {
                                var4 = 41;
                            }
                            break;
                        case -430914369:
                            if (var3.equals("Pixi4-7_3G")) {
                                var4 = 89;
                            }
                            break;
                        case -290434366:
                            if (var3.equals("taido_row")) {
                                var4 = 104;
                            }
                            break;
                        case -282781963:
                            if (var3.equals("BLACK-1X")) {
                                var4 = 13;
                            }
                            break;
                        case -277133239:
                            if (var3.equals("Z12_PRO")) {
                                var4 = 121;
                            }
                            break;
                        case -173639913:
                            if (var3.equals("ELUGA_A3_Pro")) {
                                var4 = 25;
                            }
                            break;
                        case -56598463:
                            if (var3.equals("woods_fn")) {
                                var4 = 117;
                            }
                            break;
                        case 2126:
                            if (var3.equals("C1")) {
                                var4 = 16;
                            }
                            break;
                        case 2564:
                            if (var3.equals("Q5")) {
                                var4 = 97;
                            }
                            break;
                        case 2715:
                            if (var3.equals("V1")) {
                                var4 = 110;
                            }
                            break;
                        case 2719:
                            if (var3.equals("V5")) {
                                var4 = 112;
                            }
                            break;
                        case 3483:
                            if (var3.equals("mh")) {
                                var4 = 69;
                            }
                            break;
                        case 73405:
                            if (var3.equals("JGZ")) {
                                var4 = 60;
                            }
                            break;
                        case 75739:
                            if (var3.equals("M5c")) {
                                var4 = 65;
                            }
                            break;
                        case 76779:
                            if (var3.equals("MX6")) {
                                var4 = 71;
                            }
                            break;
                        case 78669:
                            if (var3.equals("P85")) {
                                var4 = 79;
                            }
                            break;
                        case 79305:
                            if (var3.equals("PLE")) {
                                var4 = 91;
                            }
                            break;
                        case 80618:
                            if (var3.equals("QX1")) {
                                var4 = 99;
                            }
                            break;
                        case 88274:
                            if (var3.equals("Z80")) {
                                var4 = 122;
                            }
                            break;
                        case 98846:
                            if (var3.equals("cv1")) {
                                var4 = 21;
                            }
                            break;
                        case 98848:
                            if (var3.equals("cv3")) {
                                var4 = 22;
                            }
                            break;
                        case 99329:
                            if (var3.equals("deb")) {
                                var4 = 23;
                            }
                            break;
                        case 101481:
                            if (var3.equals("flo")) {
                                var4 = 37;
                            }
                            break;
                        case 1513190:
                            if (var3.equals("1601")) {
                                var4 = 0;
                            }
                            break;
                        case 1514184:
                            if (var3.equals("1713")) {
                                var4 = 1;
                            }
                            break;
                        case 1514185:
                            if (var3.equals("1714")) {
                                var4 = 2;
                            }
                            break;
                        case 2436959:
                            if (var3.equals("P681")) {
                                var4 = 78;
                            }
                            break;
                        case 2463773:
                            if (var3.equals("Q350")) {
                                var4 = 93;
                            }
                            break;
                        case 2464648:
                            if (var3.equals("Q427")) {
                                var4 = 95;
                            }
                            break;
                        case 2689555:
                            if (var3.equals("XE2X")) {
                                var4 = 119;
                            }
                            break;
                        case 3154429:
                            if (var3.equals("fugu")) {
                                var4 = 38;
                            }
                            break;
                        case 3284551:
                            if (var3.equals("kate")) {
                                var4 = 62;
                            }
                            break;
                        case 3351335:
                            if (var3.equals("mido")) {
                                var4 = 70;
                            }
                            break;
                        case 3386211:
                            if (var3.equals("p212")) {
                                var4 = 77;
                            }
                            break;
                        case 41325051:
                            if (var3.equals("MEIZU_M5")) {
                                var4 = 68;
                            }
                            break;
                        case 55178625:
                            if (var3.equals("Aura_Note_2")) {
                                var4 = 12;
                            }
                            break;
                        case 61542055:
                            if (var3.equals("A1601")) {
                                var4 = 4;
                            }
                            break;
                        case 65355429:
                            if (var3.equals("E5643")) {
                                var4 = 24;
                            }
                            break;
                        case 66214468:
                            if (var3.equals("F3111")) {
                                var4 = 30;
                            }
                            break;
                        case 66214470:
                            if (var3.equals("F3113")) {
                                var4 = 31;
                            }
                            break;
                        case 66214473:
                            if (var3.equals("F3116")) {
                                var4 = 32;
                            }
                            break;
                        case 66215429:
                            if (var3.equals("F3211")) {
                                var4 = 33;
                            }
                            break;
                        case 66215431:
                            if (var3.equals("F3213")) {
                                var4 = 34;
                            }
                            break;
                        case 66215433:
                            if (var3.equals("F3215")) {
                                var4 = 35;
                            }
                            break;
                        case 66216390:
                            if (var3.equals("F3311")) {
                                var4 = 36;
                            }
                            break;
                        case 76402249:
                            if (var3.equals("PRO7S")) {
                                var4 = 92;
                            }
                            break;
                        case 76404105:
                            if (var3.equals("Q4260")) {
                                var4 = 94;
                            }
                            break;
                        case 76404911:
                            if (var3.equals("Q4310")) {
                                var4 = 96;
                            }
                            break;
                        case 80963634:
                            if (var3.equals("V23GB")) {
                                var4 = 111;
                            }
                            break;
                        case 82882791:
                            if (var3.equals("X3_HK")) {
                                var4 = 118;
                            }
                            break;
                        case 98715550:
                            if (var3.equals("i9031")) {
                                var4 = 54;
                            }
                            break;
                        case 102844228:
                            if (var3.equals("le_x6")) {
                                var4 = 63;
                            }
                            break;
                        case 165221241:
                            if (var3.equals("A2016a40")) {
                                var4 = 5;
                            }
                            break;
                        case 182191441:
                            if (var3.equals("CPY83_I00")) {
                                var4 = 20;
                            }
                            break;
                        case 245388979:
                            if (var3.equals("marino_f")) {
                                var4 = 67;
                            }
                            break;
                        case 287431619:
                            if (var3.equals("griffin")) {
                                var4 = 48;
                            }
                            break;
                        case 307593612:
                            if (var3.equals("A7010a48")) {
                                var4 = 8;
                            }
                            break;
                        case 308517133:
                            if (var3.equals("A7020a48")) {
                                var4 = 9;
                            }
                            break;
                        case 316215098:
                            if (var3.equals("TB3-730F")) {
                                var4 = 105;
                            }
                            break;
                        case 316215116:
                            if (var3.equals("TB3-730X")) {
                                var4 = 106;
                            }
                            break;
                        case 316246811:
                            if (var3.equals("TB3-850F")) {
                                var4 = 107;
                            }
                            break;
                        case 316246818:
                            if (var3.equals("TB3-850M")) {
                                var4 = 108;
                            }
                            break;
                        case 407160593:
                            if (var3.equals("Pixi5-10_4G")) {
                                var4 = 90;
                            }
                            break;
                        case 507412548:
                            if (var3.equals("QM16XE_U")) {
                                var4 = 98;
                            }
                            break;
                        case 793982701:
                            if (var3.equals("GIONEE_WBL5708")) {
                                var4 = 45;
                            }
                            break;
                        case 794038622:
                            if (var3.equals("GIONEE_WBL7365")) {
                                var4 = 46;
                            }
                            break;
                        case 794040393:
                            if (var3.equals("GIONEE_WBL7519")) {
                                var4 = 47;
                            }
                            break;
                        case 835649806:
                            if (var3.equals("manning")) {
                                var4 = 66;
                            }
                            break;
                        case 917340916:
                            if (var3.equals("A7000plus")) {
                                var4 = 7;
                            }
                            break;
                        case 958008161:
                            if (var3.equals("j2xlteins")) {
                                var4 = 59;
                            }
                            break;
                        case 1060579533:
                            if (var3.equals("panell_d")) {
                                var4 = 80;
                            }
                            break;
                        case 1150207623:
                            if (var3.equals("LS-5017")) {
                                var4 = 64;
                            }
                            break;
                        case 1176899427:
                            if (var3.equals("itel_S41")) {
                                var4 = 58;
                            }
                            break;
                        case 1280332038:
                            if (var3.equals("hwALE-H")) {
                                var4 = 50;
                            }
                            break;
                        case 1306947716:
                            if (var3.equals("EverStar_S")) {
                                var4 = 29;
                            }
                            break;
                        case 1349174697:
                            if (var3.equals("htc_e56ml_dtul")) {
                                var4 = 49;
                            }
                            break;
                        case 1522194893:
                            if (var3.equals("woods_f")) {
                                var4 = 116;
                            }
                            break;
                        case 1691543273:
                            if (var3.equals("CPH1609")) {
                                var4 = 19;
                            }
                            break;
                        case 1709443163:
                            if (var3.equals("iball8735_9806")) {
                                var4 = 55;
                            }
                            break;
                        case 1865889110:
                            if (var3.equals("santoni")) {
                                var4 = 100;
                            }
                            break;
                        case 1906253259:
                            if (var3.equals("PB2-670M")) {
                                var4 = 84;
                            }
                            break;
                        case 1977196784:
                            if (var3.equals("Infinix-X572")) {
                                var4 = 56;
                            }
                            break;
                        case 2006372676:
                            if (var3.equals("BRAVIA_ATV3_4K")) {
                                var4 = 15;
                            }
                            break;
                        case 2029784656:
                            if (var3.equals("HWBLN-H")) {
                                var4 = 51;
                            }
                            break;
                        case 2030379515:
                            if (var3.equals("HWCAM-H")) {
                                var4 = 52;
                            }
                            break;
                        case 2033393791:
                            if (var3.equals("ASUS_X00AD_2")) {
                                var4 = 11;
                            }
                            break;
                        case 2047190025:
                            if (var3.equals("ELUGA_Note")) {
                                var4 = 26;
                            }
                            break;
                        case 2047252157:
                            if (var3.equals("ELUGA_Prim")) {
                                var4 = 27;
                            }
                            break;
                        case 2048319463:
                            if (var3.equals("HWVNS-H")) {
                                var4 = 53;
                            }
                        }

                        switch(var4) {
                        case 0:
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                        case 5:
                        case 6:
                        case 7:
                        case 8:
                        case 9:
                        case 10:
                        case 11:
                        case 12:
                        case 13:
                        case 14:
                        case 15:
                        case 16:
                        case 17:
                        case 18:
                        case 19:
                        case 20:
                        case 21:
                        case 22:
                        case 23:
                        case 24:
                        case 25:
                        case 26:
                        case 27:
                        case 28:
                        case 29:
                        case 30:
                        case 31:
                        case 32:
                        case 33:
                        case 34:
                        case 35:
                        case 36:
                        case 37:
                        case 38:
                        case 39:
                        case 40:
                        case 41:
                        case 42:
                        case 43:
                        case 44:
                        case 45:
                        case 46:
                        case 47:
                        case 48:
                        case 49:
                        case 50:
                        case 51:
                        case 52:
                        case 53:
                        case 54:
                        case 55:
                        case 56:
                        case 57:
                        case 58:
                        case 59:
                        case 60:
                        case 61:
                        case 62:
                        case 63:
                        case 64:
                        case 65:
                        case 66:
                        case 67:
                        case 68:
                        case 69:
                        case 70:
                        case 71:
                        case 72:
                        case 73:
                        case 74:
                        case 75:
                        case 76:
                        case 77:
                        case 78:
                        case 79:
                        case 80:
                        case 81:
                        case 82:
                        case 83:
                        case 84:
                        case 85:
                        case 86:
                        case 87:
                        case 88:
                        case 89:
                        case 90:
                        case 91:
                        case 92:
                        case 93:
                        case 94:
                        case 95:
                        case 96:
                        case 97:
                        case 98:
                        case 99:
                        case 100:
                        case 101:
                        case 102:
                        case 103:
                        case 104:
                        case 105:
                        case 106:
                        case 107:
                        case 108:
                        case 109:
                        case 110:
                        case 111:
                        case 112:
                        case 113:
                        case 114:
                        case 115:
                        case 116:
                        case 117:
                        case 118:
                        case 119:
                        case 120:
                        case 121:
                        case 122:
                            deviceNeedsSetOutputSurfaceWorkaround = true;
                        default:
                            var3 = Util.MODEL;
                            var4 = -1;
                            switch(var3.hashCode()) {
                            case 2006354:
                                if (var3.equals("AFTA")) {
                                    var4 = 0;
                                }
                                break;
                            case 2006367:
                                if (var3.equals("AFTN")) {
                                    var4 = 1;
                                }
                            }

                            switch(var4) {
                            case 0:
                            case 1:
                                deviceNeedsSetOutputSurfaceWorkaround = true;
                            }
                        }
                    }

                    evaluatedDeviceNeedsSetOutputSurfaceWorkaround = true;
                }
            }

            return deviceNeedsSetOutputSurfaceWorkaround;
        }
    }

    @TargetApi(23)
    private final class OnFrameRenderedListenerV23 implements OnFrameRenderedListener {
        private OnFrameRenderedListenerV23(MediaCodec codec) {
            codec.setOnFrameRenderedListener(this, new Handler());
        }

        public void onFrameRendered(@NonNull MediaCodec codec, long presentationTimeUs, long nanoTime) {
            if (this == MediaCodecVideoRenderer.this.tunnelingOnFrameRenderedListener) {
                MediaCodecVideoRenderer.this.onProcessedTunneledBuffer(presentationTimeUs);
            }
        }
    }

    protected static final class CodecMaxValues {
        public final int width;
        public final int height;
        public final int inputSize;

        public CodecMaxValues(int width, int height, int inputSize) {
            this.width = width;
            this.height = height;
            this.inputSize = inputSize;
        }
    }
}
