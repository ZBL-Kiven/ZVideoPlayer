package com.zj.playerLib.audio;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.AudioAttributes.Builder;
import android.os.ConditionVariable;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.zj.playerLib.PlaybackParameters;
import com.zj.playerLib.audio.AudioProcessor.UnhandledFormatException;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.Util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public final class DefaultAudioSink implements AudioSink {
    private static final long MIN_BUFFER_DURATION_US = 250000L;
    private static final long MAX_BUFFER_DURATION_US = 750000L;
    private static final long PASSTHROUGH_BUFFER_DURATION_US = 250000L;
    private static final int BUFFER_MULTIPLICATION_FACTOR = 4;
    private static final int AC3_BUFFER_MULTIPLICATION_FACTOR = 2;
    private static final int ERROR_BAD_VALUE = -2;
    private static final int MODE_STATIC = 0;
    private static final int MODE_STREAM = 1;
    private static final int STATE_INITIALIZED = 1;
    @SuppressLint({"InlinedApi"})
    private static final int WRITE_NON_BLOCKING = 1;
    private static final String TAG = "AudioTrack";
    private static final int START_NOT_SET = 0;
    private static final int START_IN_SYNC = 1;
    private static final int START_NEED_SYNC = 2;
    public static boolean enablePreV21AudioSessionWorkaround = false;
    public static boolean failOnSpuriousAudioTimestamp = false;
    @Nullable
    private final AudioCapabilities audioCapabilities;
    private final AudioProcessorChain audioProcessorChain;
    private final boolean enableConvertHighResIntPcmToFloat;
    private final ChannelMappingAudioProcessor channelMappingAudioProcessor;
    private final TrimmingAudioProcessor trimmingAudioProcessor;
    private final AudioProcessor[] toIntPcmAvailableAudioProcessors;
    private final AudioProcessor[] toFloatPcmAvailableAudioProcessors;
    private final ConditionVariable releasingConditionVariable;
    private final AudioTrackPositionTracker audioTrackPositionTracker;
    private final ArrayDeque<PlaybackParametersCheckpoint> playbackParametersCheckpoints;
    @Nullable
    private Listener listener;
    @Nullable
    private AudioTrack keepSessionIdAudioTrack;
    private AudioTrack audioTrack;
    private boolean isInputPcm;
    private boolean shouldConvertHighResIntPcmToFloat;
    private int inputSampleRate;
    private int outputSampleRate;
    private int outputChannelConfig;
    private int outputEncoding;
    private AudioAttributes audioAttributes;
    private boolean processingEnabled;
    private boolean canApplyPlaybackParameters;
    private int bufferSize;
    @Nullable
    private PlaybackParameters afterDrainPlaybackParameters;
    private PlaybackParameters playbackParameters;
    private long playbackParametersOffsetUs;
    private long playbackParametersPositionUs;
    @Nullable
    private ByteBuffer avSyncHeader;
    private int bytesUntilNextAvSync;
    private int pcmFrameSize;
    private long submittedPcmBytes;
    private long submittedEncodedFrames;
    private int outputPcmFrameSize;
    private long writtenPcmBytes;
    private long writtenEncodedFrames;
    private int framesPerEncodedSample;
    private int startMediaTimeState;
    private long startMediaTimeUs;
    private float volume;
    private AudioProcessor[] activeAudioProcessors;
    private ByteBuffer[] outputBuffers;
    @Nullable
    private ByteBuffer inputBuffer;
    @Nullable
    private ByteBuffer outputBuffer;
    private byte[] preV21OutputBuffer;
    private int preV21OutputBufferOffset;
    private int drainingAudioProcessorIndex;
    private boolean handledEndOfStream;
    private boolean playing;
    private int audioSessionId;
    private AuxEffectInfo auxEffectInfo;
    private boolean tunneling;
    private long lastFeedElapsedRealtimeMs;

    public DefaultAudioSink(@Nullable AudioCapabilities audioCapabilities, AudioProcessor[] audioProcessors) {
        this(audioCapabilities, audioProcessors, false);
    }

    public DefaultAudioSink(@Nullable AudioCapabilities audioCapabilities, AudioProcessor[] audioProcessors, boolean enableConvertHighResIntPcmToFloat) {
        this(audioCapabilities, new DefaultAudioProcessorChain(audioProcessors), enableConvertHighResIntPcmToFloat);
    }

    public DefaultAudioSink(@Nullable AudioCapabilities audioCapabilities, AudioProcessorChain audioProcessorChain, boolean enableConvertHighResIntPcmToFloat) {
        this.audioCapabilities = audioCapabilities;
        this.audioProcessorChain = Assertions.checkNotNull(audioProcessorChain);
        this.enableConvertHighResIntPcmToFloat = enableConvertHighResIntPcmToFloat;
        this.releasingConditionVariable = new ConditionVariable(true);
        this.audioTrackPositionTracker = new AudioTrackPositionTracker(new PositionTrackerListener());
        this.channelMappingAudioProcessor = new ChannelMappingAudioProcessor();
        this.trimmingAudioProcessor = new TrimmingAudioProcessor();
        ArrayList<AudioProcessor> toIntPcmAudioProcessors = new ArrayList();
        Collections.addAll(toIntPcmAudioProcessors, new ResamplingAudioProcessor(), this.channelMappingAudioProcessor, this.trimmingAudioProcessor);
        Collections.addAll(toIntPcmAudioProcessors, audioProcessorChain.getAudioProcessors());
        this.toIntPcmAvailableAudioProcessors = toIntPcmAudioProcessors.toArray(new AudioProcessor[toIntPcmAudioProcessors.size()]);
        this.toFloatPcmAvailableAudioProcessors = new AudioProcessor[]{new FloatResamplingAudioProcessor()};
        this.volume = 1.0F;
        this.startMediaTimeState = 0;
        this.audioAttributes = AudioAttributes.DEFAULT;
        this.audioSessionId = 0;
        this.auxEffectInfo = new AuxEffectInfo(0, 0.0F);
        this.playbackParameters = PlaybackParameters.DEFAULT;
        this.drainingAudioProcessorIndex = -1;
        this.activeAudioProcessors = new AudioProcessor[0];
        this.outputBuffers = new ByteBuffer[0];
        this.playbackParametersCheckpoints = new ArrayDeque();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean supportsOutput(int channelCount, int encoding) {
        if (Util.isEncodingLinearPcm(encoding)) {
            return encoding != 4 || Util.SDK_INT >= 21;
        } else {
            return this.audioCapabilities != null && this.audioCapabilities.supportsEncoding(encoding) && (channelCount == -1 || channelCount <= this.audioCapabilities.getMaxChannelCount());
        }
    }

    public long getCurrentPositionUs(boolean sourceEnded) {
        if (this.isInitialized() && this.startMediaTimeState != 0) {
            long positionUs = this.audioTrackPositionTracker.getCurrentPositionUs(sourceEnded);
            positionUs = Math.min(positionUs, this.framesToDurationUs(this.getWrittenFrames()));
            return this.startMediaTimeUs + this.applySkipping(this.applySpeedup(positionUs));
        } else {
            return -9223372036854775808L;
        }
    }

    public void configure(int inputEncoding, int inputChannelCount, int inputSampleRate, int specifiedBufferSize, @Nullable int[] outputChannels, int trimStartFrames, int trimEndFrames) throws ConfigurationException {
        boolean flush = false;
        this.inputSampleRate = inputSampleRate;
        int channelCount = inputChannelCount;
        int sampleRate = inputSampleRate;
        this.isInputPcm = Util.isEncodingLinearPcm(inputEncoding);
        this.shouldConvertHighResIntPcmToFloat = this.enableConvertHighResIntPcmToFloat && this.supportsOutput(inputChannelCount, 1073741824) && Util.isEncodingHighResolutionIntegerPcm(inputEncoding);
        if (this.isInputPcm) {
            this.pcmFrameSize = Util.getPcmFrameSize(inputEncoding, inputChannelCount);
        }

        int encoding = inputEncoding;
        boolean processingEnabled = this.isInputPcm && inputEncoding != 4;
        this.canApplyPlaybackParameters = processingEnabled && !this.shouldConvertHighResIntPcmToFloat;
        int channelConfig;
        if (Util.SDK_INT < 21 && inputChannelCount == 8 && outputChannels == null) {
            outputChannels = new int[6];

            for (channelConfig = 0; channelConfig < outputChannels.length; outputChannels[channelConfig] = channelConfig++) {
            }
        }

        if (processingEnabled) {
            this.trimmingAudioProcessor.setTrimFrameCount(trimStartFrames, trimEndFrames);
            this.channelMappingAudioProcessor.setChannelMap(outputChannels);
            AudioProcessor[] var19 = this.getAvailableAudioProcessors();
            int var14 = var19.length;

            for (int var15 = 0; var15 < var14; ++var15) {
                AudioProcessor audioProcessor = var19[var15];

                try {
                    flush |= audioProcessor.configure(sampleRate, channelCount, encoding);
                } catch (UnhandledFormatException var18) {
                    throw new ConfigurationException(var18);
                }

                if (audioProcessor.isActive()) {
                    channelCount = audioProcessor.getOutputChannelCount();
                    sampleRate = audioProcessor.getOutputSampleRateHz();
                    encoding = audioProcessor.getOutputEncoding();
                }
            }
        }

        channelConfig = getChannelConfig(channelCount, this.isInputPcm);
        if (channelConfig == 0) {
            throw new ConfigurationException("Unsupported channel count: " + channelCount);
        } else if (flush || !this.isInitialized() || this.outputEncoding != encoding || this.outputSampleRate != sampleRate || this.outputChannelConfig != channelConfig) {
            this.reset();
            this.processingEnabled = processingEnabled;
            this.outputSampleRate = sampleRate;
            this.outputChannelConfig = channelConfig;
            this.outputEncoding = encoding;
            this.outputPcmFrameSize = this.isInputPcm ? Util.getPcmFrameSize(this.outputEncoding, channelCount) : -1;
            this.bufferSize = specifiedBufferSize != 0 ? specifiedBufferSize : this.getDefaultBufferSize();
        }
    }

    private int getDefaultBufferSize() {
        int rate;
        if (this.isInputPcm) {
            rate = AudioTrack.getMinBufferSize(this.outputSampleRate, this.outputChannelConfig, this.outputEncoding);
            Assertions.checkState(rate != -2);
            int multipliedBufferSize = rate * 4;
            int minAppBufferSize = (int) this.durationUsToFrames(250000L) * this.outputPcmFrameSize;
            int maxAppBufferSize = (int) Math.max(rate, this.durationUsToFrames(750000L) * (long) this.outputPcmFrameSize);
            return Util.constrainValue(multipliedBufferSize, minAppBufferSize, maxAppBufferSize);
        } else {
            rate = getMaximumEncodedRateBytesPerSecond(this.outputEncoding);
            if (this.outputEncoding == 5) {
                rate *= 2;
            }

            return (int) (250000L * (long) rate / 1000000L);
        }
    }

    private void setupAudioProcessors() {
        ArrayList<AudioProcessor> newAudioProcessors = new ArrayList();
        AudioProcessor[] var2 = this.getAvailableAudioProcessors();
        int var3 = var2.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            AudioProcessor audioProcessor = var2[var4];
            if (audioProcessor.isActive()) {
                newAudioProcessors.add(audioProcessor);
            } else {
                audioProcessor.flush();
            }
        }

        int count = newAudioProcessors.size();
        this.activeAudioProcessors = newAudioProcessors.toArray(new AudioProcessor[count]);
        this.outputBuffers = new ByteBuffer[count];
        this.flushAudioProcessors();
    }

    private void flushAudioProcessors() {
        for (int i = 0; i < this.activeAudioProcessors.length; ++i) {
            AudioProcessor audioProcessor = this.activeAudioProcessors[i];
            audioProcessor.flush();
            this.outputBuffers[i] = audioProcessor.getOutput();
        }

    }

    private void initialize() throws InitializationException {
        this.releasingConditionVariable.block();
        this.audioTrack = this.initializeAudioTrack();
        int audioSessionId = this.audioTrack.getAudioSessionId();
        if (enablePreV21AudioSessionWorkaround && Util.SDK_INT < 21) {
            if (this.keepSessionIdAudioTrack != null && audioSessionId != this.keepSessionIdAudioTrack.getAudioSessionId()) {
                this.releaseKeepSessionIdAudioTrack();
            }

            if (this.keepSessionIdAudioTrack == null) {
                this.keepSessionIdAudioTrack = this.initializeKeepSessionIdAudioTrack(audioSessionId);
            }
        }

        if (this.audioSessionId != audioSessionId) {
            this.audioSessionId = audioSessionId;
            if (this.listener != null) {
                this.listener.onAudioSessionId(audioSessionId);
            }
        }

        this.playbackParameters = this.canApplyPlaybackParameters ? this.audioProcessorChain.applyPlaybackParameters(this.playbackParameters) : PlaybackParameters.DEFAULT;
        this.setupAudioProcessors();
        this.audioTrackPositionTracker.setAudioTrack(this.audioTrack, this.outputEncoding, this.outputPcmFrameSize, this.bufferSize);
        this.setVolumeInternal();
        if (this.auxEffectInfo.effectId != 0) {
            this.audioTrack.attachAuxEffect(this.auxEffectInfo.effectId);
            this.audioTrack.setAuxEffectSendLevel(this.auxEffectInfo.sendLevel);
        }

    }

    public void play() {
        this.playing = true;
        if (this.isInitialized()) {
            this.audioTrackPositionTracker.start();
            this.audioTrack.play();
        }

    }

    public void handleDiscontinuity() {
        if (this.startMediaTimeState == 1) {
            this.startMediaTimeState = 2;
        }

    }

    public boolean handleBuffer(ByteBuffer buffer, long presentationTimeUs) throws InitializationException, WriteException {
        Assertions.checkArgument(this.inputBuffer == null || buffer == this.inputBuffer);
        if (!this.isInitialized()) {
            this.initialize();
            if (this.playing) {
                this.play();
            }
        }

        if (!this.audioTrackPositionTracker.mayHandleBuffer(this.getWrittenFrames())) {
            return false;
        } else {
            if (this.inputBuffer == null) {
                if (!buffer.hasRemaining()) {
                    return true;
                }

                if (!this.isInputPcm && this.framesPerEncodedSample == 0) {
                    this.framesPerEncodedSample = getFramesPerEncodedSample(this.outputEncoding, buffer);
                    if (this.framesPerEncodedSample == 0) {
                        return true;
                    }
                }

                if (this.afterDrainPlaybackParameters != null) {
                    if (!this.drainAudioProcessorsToEndOfStream()) {
                        return false;
                    }

                    PlaybackParameters newPlaybackParameters = this.afterDrainPlaybackParameters;
                    this.afterDrainPlaybackParameters = null;
                    newPlaybackParameters = this.audioProcessorChain.applyPlaybackParameters(newPlaybackParameters);
                    this.playbackParametersCheckpoints.add(new PlaybackParametersCheckpoint(newPlaybackParameters, Math.max(0L, presentationTimeUs), this.framesToDurationUs(this.getWrittenFrames())));
                    this.setupAudioProcessors();
                }

                if (this.startMediaTimeState == 0) {
                    this.startMediaTimeUs = Math.max(0L, presentationTimeUs);
                    this.startMediaTimeState = 1;
                } else {
                    long expectedPresentationTimeUs = this.startMediaTimeUs + this.inputFramesToDurationUs(this.getSubmittedFrames() - this.trimmingAudioProcessor.getTrimmedFrameCount());
                    if (this.startMediaTimeState == 1 && Math.abs(expectedPresentationTimeUs - presentationTimeUs) > 200000L) {
                        Log.e("AudioTrack", "Discontinuity detected [expected " + expectedPresentationTimeUs + ", got " + presentationTimeUs + "]");
                        this.startMediaTimeState = 2;
                    }

                    if (this.startMediaTimeState == 2) {
                        long adjustmentUs = presentationTimeUs - expectedPresentationTimeUs;
                        this.startMediaTimeUs += adjustmentUs;
                        this.startMediaTimeState = 1;
                        if (this.listener != null && adjustmentUs != 0L) {
                            this.listener.onPositionDiscontinuity();
                        }
                    }
                }

                if (this.isInputPcm) {
                    this.submittedPcmBytes += buffer.remaining();
                } else {
                    this.submittedEncodedFrames += this.framesPerEncodedSample;
                }

                this.inputBuffer = buffer;
            }

            if (this.processingEnabled) {
                this.processBuffers(presentationTimeUs);
            } else {
                this.writeBuffer(this.inputBuffer, presentationTimeUs);
            }

            if (!this.inputBuffer.hasRemaining()) {
                this.inputBuffer = null;
                return true;
            } else if (this.audioTrackPositionTracker.isStalled(this.getWrittenFrames())) {
                Log.w("AudioTrack", "Resetting stalled audio track");
                this.reset();
                return true;
            } else {
                return false;
            }
        }
    }

    private void processBuffers(long avSyncPresentationTimeUs) throws WriteException {
        int count = this.activeAudioProcessors.length;
        int index = count;

        while (true) {
            while (index >= 0) {
                ByteBuffer input = index > 0 ? this.outputBuffers[index - 1] : (this.inputBuffer != null ? this.inputBuffer : AudioProcessor.EMPTY_BUFFER);
                if (index == count) {
                    this.writeBuffer(input, avSyncPresentationTimeUs);
                } else {
                    AudioProcessor audioProcessor = this.activeAudioProcessors[index];
                    audioProcessor.queueInput(input);
                    ByteBuffer output = audioProcessor.getOutput();
                    this.outputBuffers[index] = output;
                    if (output.hasRemaining()) {
                        ++index;
                        continue;
                    }
                }

                if (input.hasRemaining()) {
                    return;
                }

                --index;
            }

            return;
        }
    }

    private void writeBuffer(ByteBuffer buffer, long avSyncPresentationTimeUs) throws WriteException {
        if (buffer.hasRemaining()) {
            int bytesRemaining;
            int bytesWritten;
            if (this.outputBuffer != null) {
                Assertions.checkArgument(this.outputBuffer == buffer);
            } else {
                this.outputBuffer = buffer;
                if (Util.SDK_INT < 21) {
                    bytesRemaining = buffer.remaining();
                    if (this.preV21OutputBuffer == null || this.preV21OutputBuffer.length < bytesRemaining) {
                        this.preV21OutputBuffer = new byte[bytesRemaining];
                    }

                    bytesWritten = buffer.position();
                    buffer.get(this.preV21OutputBuffer, 0, bytesRemaining);
                    buffer.position(bytesWritten);
                    this.preV21OutputBufferOffset = 0;
                }
            }

            bytesRemaining = buffer.remaining();
            bytesWritten = 0;
            if (Util.SDK_INT < 21) {
                int bytesToWrite = this.audioTrackPositionTracker.getAvailableBufferSize(this.writtenPcmBytes);
                if (bytesToWrite > 0) {
                    bytesToWrite = Math.min(bytesRemaining, bytesToWrite);
                    bytesWritten = this.audioTrack.write(this.preV21OutputBuffer, this.preV21OutputBufferOffset, bytesToWrite);
                    if (bytesWritten > 0) {
                        this.preV21OutputBufferOffset += bytesWritten;
                        buffer.position(buffer.position() + bytesWritten);
                    }
                }
            } else if (this.tunneling) {
                Assertions.checkState(avSyncPresentationTimeUs != -Long.MAX_VALUE);
                bytesWritten = this.writeNonBlockingWithAvSyncV21(this.audioTrack, buffer, bytesRemaining, avSyncPresentationTimeUs);
            } else {
                bytesWritten = writeNonBlockingV21(this.audioTrack, buffer, bytesRemaining);
            }

            this.lastFeedElapsedRealtimeMs = SystemClock.elapsedRealtime();
            if (bytesWritten < 0) {
                throw new WriteException(bytesWritten);
            } else {
                if (this.isInputPcm) {
                    this.writtenPcmBytes += bytesWritten;
                }

                if (bytesWritten == bytesRemaining) {
                    if (!this.isInputPcm) {
                        this.writtenEncodedFrames += this.framesPerEncodedSample;
                    }

                    this.outputBuffer = null;
                }

            }
        }
    }

    public void playToEndOfStream() throws WriteException {
        if (!this.handledEndOfStream && this.isInitialized()) {
            if (this.drainAudioProcessorsToEndOfStream()) {
                this.audioTrackPositionTracker.handleEndOfStream(this.getWrittenFrames());
                this.audioTrack.stop();
                this.bytesUntilNextAvSync = 0;
                this.handledEndOfStream = true;
            }

        }
    }

    private boolean drainAudioProcessorsToEndOfStream() throws WriteException {
        boolean audioProcessorNeedsEndOfStream = false;
        if (this.drainingAudioProcessorIndex == -1) {
            this.drainingAudioProcessorIndex = this.processingEnabled ? 0 : this.activeAudioProcessors.length;
            audioProcessorNeedsEndOfStream = true;
        }

        while (this.drainingAudioProcessorIndex < this.activeAudioProcessors.length) {
            AudioProcessor audioProcessor = this.activeAudioProcessors[this.drainingAudioProcessorIndex];
            if (audioProcessorNeedsEndOfStream) {
                audioProcessor.queueEndOfStream();
            }

            this.processBuffers(-Long.MAX_VALUE);
            if (!audioProcessor.isEnded()) {
                return false;
            }

            audioProcessorNeedsEndOfStream = true;
            ++this.drainingAudioProcessorIndex;
        }

        if (this.outputBuffer != null) {
            this.writeBuffer(this.outputBuffer, -Long.MAX_VALUE);
            if (this.outputBuffer != null) {
                return false;
            }
        }

        this.drainingAudioProcessorIndex = -1;
        return true;
    }

    public boolean isEnded() {
        return !this.isInitialized() || this.handledEndOfStream && !this.hasPendingData();
    }

    public boolean hasPendingData() {
        return this.isInitialized() && this.audioTrackPositionTracker.hasPendingData(this.getWrittenFrames());
    }

    public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
        if (this.isInitialized() && !this.canApplyPlaybackParameters) {
            this.playbackParameters = PlaybackParameters.DEFAULT;
            return this.playbackParameters;
        } else {
            PlaybackParameters lastSetPlaybackParameters = this.afterDrainPlaybackParameters != null ? this.afterDrainPlaybackParameters : (!this.playbackParametersCheckpoints.isEmpty() ? this.playbackParametersCheckpoints.getLast().playbackParameters : this.playbackParameters);
            if (!playbackParameters.equals(lastSetPlaybackParameters)) {
                if (this.isInitialized()) {
                    this.afterDrainPlaybackParameters = playbackParameters;
                } else {
                    this.playbackParameters = this.audioProcessorChain.applyPlaybackParameters(playbackParameters);
                }
            }

            return this.playbackParameters;
        }
    }

    public PlaybackParameters getPlaybackParameters() {
        return this.playbackParameters;
    }

    public void setAudioAttributes(AudioAttributes audioAttributes) {
        if (!this.audioAttributes.equals(audioAttributes)) {
            this.audioAttributes = audioAttributes;
            if (!this.tunneling) {
                this.reset();
                this.audioSessionId = 0;
            }
        }
    }

    public void setAudioSessionId(int audioSessionId) {
        if (this.audioSessionId != audioSessionId) {
            this.audioSessionId = audioSessionId;
            this.reset();
        }

    }

    public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
        if (!this.auxEffectInfo.equals(auxEffectInfo)) {
            int effectId = auxEffectInfo.effectId;
            float sendLevel = auxEffectInfo.sendLevel;
            if (this.audioTrack != null) {
                if (this.auxEffectInfo.effectId != effectId) {
                    this.audioTrack.attachAuxEffect(effectId);
                }

                if (effectId != 0) {
                    this.audioTrack.setAuxEffectSendLevel(sendLevel);
                }
            }

            this.auxEffectInfo = auxEffectInfo;
        }
    }

    public void enableTunnelingV21(int tunnelingAudioSessionId) {
        Assertions.checkState(Util.SDK_INT >= 21);
        if (!this.tunneling || this.audioSessionId != tunnelingAudioSessionId) {
            this.tunneling = true;
            this.audioSessionId = tunnelingAudioSessionId;
            this.reset();
        }

    }

    public void disableTunneling() {
        if (this.tunneling) {
            this.tunneling = false;
            this.audioSessionId = 0;
            this.reset();
        }

    }

    public void setVolume(float volume) {
        if (this.volume != volume) {
            this.volume = volume;
            this.setVolumeInternal();
        }

    }

    private void setVolumeInternal() {
        if (this.isInitialized()) {
            if (Util.SDK_INT >= 21) {
                setVolumeInternalV21(this.audioTrack, this.volume);
            } else {
                setVolumeInternalV3(this.audioTrack, this.volume);
            }
        }

    }

    public void pause() {
        this.playing = false;
        if (this.isInitialized() && this.audioTrackPositionTracker.pause()) {
            this.audioTrack.pause();
        }

    }

    public void reset() {
        if (this.isInitialized()) {
            this.submittedPcmBytes = 0L;
            this.submittedEncodedFrames = 0L;
            this.writtenPcmBytes = 0L;
            this.writtenEncodedFrames = 0L;
            this.framesPerEncodedSample = 0;
            if (this.afterDrainPlaybackParameters != null) {
                this.playbackParameters = this.afterDrainPlaybackParameters;
                this.afterDrainPlaybackParameters = null;
            } else if (!this.playbackParametersCheckpoints.isEmpty()) {
                this.playbackParameters = this.playbackParametersCheckpoints.getLast().playbackParameters;
            }

            this.playbackParametersCheckpoints.clear();
            this.playbackParametersOffsetUs = 0L;
            this.playbackParametersPositionUs = 0L;
            this.trimmingAudioProcessor.resetTrimmedFrameCount();
            this.inputBuffer = null;
            this.outputBuffer = null;
            this.flushAudioProcessors();
            this.handledEndOfStream = false;
            this.drainingAudioProcessorIndex = -1;
            this.avSyncHeader = null;
            this.bytesUntilNextAvSync = 0;
            this.startMediaTimeState = 0;
            if (this.audioTrackPositionTracker.isPlaying()) {
                this.audioTrack.pause();
            }

            final AudioTrack toRelease = this.audioTrack;
            this.audioTrack = null;
            this.audioTrackPositionTracker.reset();
            this.releasingConditionVariable.close();
            (new Thread() {
                public void run() {
                    try {
                        toRelease.flush();
                        toRelease.release();
                    } finally {
                        DefaultAudioSink.this.releasingConditionVariable.open();
                    }

                }
            }).start();
        }

    }

    public void release() {
        this.reset();
        this.releaseKeepSessionIdAudioTrack();
        AudioProcessor[] var1 = this.toIntPcmAvailableAudioProcessors;
        int var2 = var1.length;

        int var3;
        AudioProcessor audioProcessor;
        for (var3 = 0; var3 < var2; ++var3) {
            audioProcessor = var1[var3];
            audioProcessor.reset();
        }

        var1 = this.toFloatPcmAvailableAudioProcessors;
        var2 = var1.length;

        for (var3 = 0; var3 < var2; ++var3) {
            audioProcessor = var1[var3];
            audioProcessor.reset();
        }

        this.audioSessionId = 0;
        this.playing = false;
    }

    private void releaseKeepSessionIdAudioTrack() {
        if (this.keepSessionIdAudioTrack != null) {
            final AudioTrack toRelease = this.keepSessionIdAudioTrack;
            this.keepSessionIdAudioTrack = null;
            (new Thread() {
                public void run() {
                    toRelease.release();
                }
            }).start();
        }
    }

    private long applySpeedup(long positionUs) {
        PlaybackParametersCheckpoint checkpoint;
        for (checkpoint = null; !this.playbackParametersCheckpoints.isEmpty() && positionUs >= this.playbackParametersCheckpoints.getFirst().positionUs; checkpoint = this.playbackParametersCheckpoints.remove()) {
        }

        if (checkpoint != null) {
            this.playbackParameters = checkpoint.playbackParameters;
            this.playbackParametersPositionUs = checkpoint.positionUs;
            this.playbackParametersOffsetUs = checkpoint.mediaTimeUs - this.startMediaTimeUs;
        }

        if (this.playbackParameters.speed == 1.0F) {
            return positionUs + this.playbackParametersOffsetUs - this.playbackParametersPositionUs;
        } else {
            return this.playbackParametersCheckpoints.isEmpty() ? this.playbackParametersOffsetUs + this.audioProcessorChain.getMediaDuration(positionUs - this.playbackParametersPositionUs) : this.playbackParametersOffsetUs + Util.getMediaDurationForPlayoutDuration(positionUs - this.playbackParametersPositionUs, this.playbackParameters.speed);
        }
    }

    private long applySkipping(long positionUs) {
        return positionUs + this.framesToDurationUs(this.audioProcessorChain.getSkippedOutputFrameCount());
    }

    private boolean isInitialized() {
        return this.audioTrack != null;
    }

    private long inputFramesToDurationUs(long frameCount) {
        return frameCount * 1000000L / (long) this.inputSampleRate;
    }

    private long framesToDurationUs(long frameCount) {
        return frameCount * 1000000L / (long) this.outputSampleRate;
    }

    private long durationUsToFrames(long durationUs) {
        return durationUs * (long) this.outputSampleRate / 1000000L;
    }

    private long getSubmittedFrames() {
        return this.isInputPcm ? this.submittedPcmBytes / (long) this.pcmFrameSize : this.submittedEncodedFrames;
    }

    private long getWrittenFrames() {
        return this.isInputPcm ? this.writtenPcmBytes / (long) this.outputPcmFrameSize : this.writtenEncodedFrames;
    }

    private AudioTrack initializeAudioTrack() throws InitializationException {
        AudioTrack audioTrack;
        int state;
        if (Util.SDK_INT >= 21) {
            audioTrack = this.createAudioTrackV21();
        } else {
            state = Util.getStreamTypeForAudioUsage(this.audioAttributes.usage);
            if (this.audioSessionId == 0) {
                audioTrack = new AudioTrack(state, this.outputSampleRate, this.outputChannelConfig, this.outputEncoding, this.bufferSize, 1);
            } else {
                audioTrack = new AudioTrack(state, this.outputSampleRate, this.outputChannelConfig, this.outputEncoding, this.bufferSize, 1, this.audioSessionId);
            }
        }

        state = audioTrack.getState();
        if (state != 1) {
            try {
                audioTrack.release();
            } catch (Exception var4) {
            }

            throw new InitializationException(state, this.outputSampleRate, this.outputChannelConfig, this.bufferSize);
        } else {
            return audioTrack;
        }
    }

    @TargetApi(21)
    private AudioTrack createAudioTrackV21() {
        android.media.AudioAttributes attributes;
        if (this.tunneling) {
            attributes = (new Builder()).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).setFlags(16).setUsage(android.media.AudioAttributes.USAGE_MEDIA).build();
        } else {
            attributes = this.audioAttributes.getAudioAttributesV21();
        }

        AudioFormat format = (new android.media.AudioFormat.Builder()).setChannelMask(this.outputChannelConfig).setEncoding(this.outputEncoding).setSampleRate(this.outputSampleRate).build();
        int audioSessionId = this.audioSessionId != 0 ? this.audioSessionId : 0;
        return new AudioTrack(attributes, format, this.bufferSize, 1, audioSessionId);
    }

    private AudioTrack initializeKeepSessionIdAudioTrack(int audioSessionId) {
        int sampleRate = 4000;
        int channelConfig = 4;
        int encoding = 2;
        int bufferSize = 2;
        return new AudioTrack(3, sampleRate, channelConfig, encoding, bufferSize, 0, audioSessionId);
    }

    private AudioProcessor[] getAvailableAudioProcessors() {
        return this.shouldConvertHighResIntPcmToFloat ? this.toFloatPcmAvailableAudioProcessors : this.toIntPcmAvailableAudioProcessors;
    }

    private static int getChannelConfig(int channelCount, boolean isInputPcm) {
        if (Util.SDK_INT <= 28 && !isInputPcm) {
            if (channelCount == 7) {
                channelCount = 8;
            } else if (channelCount == 3 || channelCount == 4 || channelCount == 5) {
                channelCount = 6;
            }
        }

        if (Util.SDK_INT <= 26 && "fugu".equals(Util.DEVICE) && !isInputPcm && channelCount == 1) {
            channelCount = 2;
        }

        return Util.getAudioTrackChannelConfig(channelCount);
    }

    private static int getMaximumEncodedRateBytesPerSecond(int encoding) {
        switch (encoding) {
            case -2147483648:
            case -1:
            case 0:
            case 2:
            case 3:
            case 4:
            case 268435456:
            case 536870912:
            case 1073741824:
            default:
                throw new IllegalArgumentException();
            case 5:
                return 80000;
            case 6:
                return 768000;
            case 7:
                return 192000;
            case 8:
                return 2250000;
            case 14:
                return 3062500;
        }
    }

    private static int getFramesPerEncodedSample(int encoding, ByteBuffer buffer) {
        if (encoding != 7 && encoding != 8) {
            if (encoding == 5) {
                return Ac3Util.getAc3SyncframeAudioSampleCount();
            } else if (encoding == 6) {
                return Ac3Util.parseEAc3SyncframeAudioSampleCount(buffer);
            } else if (encoding == 14) {
                int syncframeOffset = Ac3Util.findTrueHdSyncframeOffset(buffer);
                return syncframeOffset == -1 ? 0 : Ac3Util.parseTrueHdSyncframeAudioSampleCount(buffer, syncframeOffset) * 16;
            } else {
                throw new IllegalStateException("Unexpected audio encoding: " + encoding);
            }
        } else {
            return DtsUtil.parseDtsAudioSampleCount(buffer);
        }
    }

    @TargetApi(21)
    private static int writeNonBlockingV21(AudioTrack audioTrack, ByteBuffer buffer, int size) {
        return audioTrack.write(buffer, size, AudioTrack.WRITE_NON_BLOCKING);
    }

    @TargetApi(21)
    private int writeNonBlockingWithAvSyncV21(AudioTrack audioTrack, ByteBuffer buffer, int size, long presentationTimeUs) {
        if (this.avSyncHeader == null) {
            this.avSyncHeader = ByteBuffer.allocate(16);
            this.avSyncHeader.order(ByteOrder.BIG_ENDIAN);
            this.avSyncHeader.putInt(1431633921);
        }

        if (this.bytesUntilNextAvSync == 0) {
            this.avSyncHeader.putInt(4, size);
            this.avSyncHeader.putLong(8, presentationTimeUs * 1000L);
            this.avSyncHeader.position(0);
            this.bytesUntilNextAvSync = size;
        }

        int avSyncHeaderBytesRemaining = this.avSyncHeader.remaining();
        int result;
        if (avSyncHeaderBytesRemaining > 0) {
            result = audioTrack.write(this.avSyncHeader, avSyncHeaderBytesRemaining, AudioTrack.WRITE_NON_BLOCKING);
            if (result < 0) {
                this.bytesUntilNextAvSync = 0;
                return result;
            }

            if (result < avSyncHeaderBytesRemaining) {
                return 0;
            }
        }

        result = writeNonBlockingV21(audioTrack, buffer, size);
        if (result < 0) {
            this.bytesUntilNextAvSync = 0;
            return result;
        } else {
            this.bytesUntilNextAvSync -= result;
            return result;
        }
    }

    @TargetApi(21)
    private static void setVolumeInternalV21(AudioTrack audioTrack, float volume) {
        audioTrack.setVolume(volume);
    }

    private static void setVolumeInternalV3(AudioTrack audioTrack, float volume) {
        audioTrack.setStereoVolume(volume, volume);
    }

    private final class PositionTrackerListener implements AudioTrackPositionTracker.Listener {
        private PositionTrackerListener() {
        }

        public void onPositionFramesMismatch(long audioTimestampPositionFrames, long audioTimestampSystemTimeUs, long systemTimeUs, long playbackPositionUs) {
            String message = "Spurious audio timestamp (frame position mismatch): " + audioTimestampPositionFrames + ", " + audioTimestampSystemTimeUs + ", " + systemTimeUs + ", " + playbackPositionUs + ", " + DefaultAudioSink.this.getSubmittedFrames() + ", " + DefaultAudioSink.this.getWrittenFrames();
            if (DefaultAudioSink.failOnSpuriousAudioTimestamp) {
                throw new InvalidAudioTrackTimestampException(message);
            } else {
                Log.w("AudioTrack", message);
            }
        }

        public void onSystemTimeUsMismatch(long audioTimestampPositionFrames, long audioTimestampSystemTimeUs, long systemTimeUs, long playbackPositionUs) {
            String message = "Spurious audio timestamp (system clock mismatch): " + audioTimestampPositionFrames + ", " + audioTimestampSystemTimeUs + ", " + systemTimeUs + ", " + playbackPositionUs + ", " + DefaultAudioSink.this.getSubmittedFrames() + ", " + DefaultAudioSink.this.getWrittenFrames();
            if (DefaultAudioSink.failOnSpuriousAudioTimestamp) {
                throw new InvalidAudioTrackTimestampException(message);
            } else {
                Log.w("AudioTrack", message);
            }
        }

        public void onInvalidLatency(long latencyUs) {
            Log.w("AudioTrack", "Ignoring impossibly large audio latency: " + latencyUs);
        }

        public void onUnderrun(int bufferSize, long bufferSizeMs) {
            if (DefaultAudioSink.this.listener != null) {
                long elapsedSinceLastFeedMs = SystemClock.elapsedRealtime() - DefaultAudioSink.this.lastFeedElapsedRealtimeMs;
                DefaultAudioSink.this.listener.onUnderRun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
            }

        }
    }

    private static final class PlaybackParametersCheckpoint {
        private final PlaybackParameters playbackParameters;
        private final long mediaTimeUs;
        private final long positionUs;

        private PlaybackParametersCheckpoint(PlaybackParameters playbackParameters, long mediaTimeUs, long positionUs) {
            this.playbackParameters = playbackParameters;
            this.mediaTimeUs = mediaTimeUs;
            this.positionUs = positionUs;
        }
    }

    public static class DefaultAudioProcessorChain implements AudioProcessorChain {
        private final AudioProcessor[] audioProcessors;
        private final SilenceSkippingAudioProcessor silenceSkippingAudioProcessor;
        private final SonicAudioProcessor sonicAudioProcessor;

        public DefaultAudioProcessorChain(AudioProcessor... audioProcessors) {
            this.audioProcessors = Arrays.copyOf(audioProcessors, audioProcessors.length + 2);
            this.silenceSkippingAudioProcessor = new SilenceSkippingAudioProcessor();
            this.sonicAudioProcessor = new SonicAudioProcessor();
            this.audioProcessors[audioProcessors.length] = this.silenceSkippingAudioProcessor;
            this.audioProcessors[audioProcessors.length + 1] = this.sonicAudioProcessor;
        }

        public AudioProcessor[] getAudioProcessors() {
            return this.audioProcessors;
        }

        public PlaybackParameters applyPlaybackParameters(PlaybackParameters playbackParameters) {
            this.silenceSkippingAudioProcessor.setEnabled(playbackParameters.skipSilence);
            return new PlaybackParameters(this.sonicAudioProcessor.setSpeed(playbackParameters.speed), this.sonicAudioProcessor.setPitch(playbackParameters.pitch), playbackParameters.skipSilence);
        }

        public long getMediaDuration(long playoutDuration) {
            return this.sonicAudioProcessor.scaleDurationForSpeedup(playoutDuration);
        }

        public long getSkippedOutputFrameCount() {
            return this.silenceSkippingAudioProcessor.getSkippedFrames();
        }
    }

    public interface AudioProcessorChain {
        AudioProcessor[] getAudioProcessors();

        PlaybackParameters applyPlaybackParameters(PlaybackParameters var1);

        long getMediaDuration(long var1);

        long getSkippedOutputFrameCount();
    }

    public static final class InvalidAudioTrackTimestampException extends RuntimeException {
        private InvalidAudioTrackTimestampException(String message) {
            super(message);
        }
    }
}
