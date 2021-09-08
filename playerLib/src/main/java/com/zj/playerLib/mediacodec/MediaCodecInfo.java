//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.mediacodec;

import android.annotation.TargetApi;
import android.graphics.Point;
import android.media.MediaCodecInfo.AudioCapabilities;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.zj.playerLib.Format;
import com.zj.playerLib.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.MimeTypes;
import com.zj.playerLib.util.Util;

@TargetApi(16)
public final class MediaCodecInfo {
    public static final String TAG = "MediaCodecInfo";
    public final String name;
    @Nullable
    public final String mimeType;
    @Nullable
    public final CodecCapabilities capabilities;
    public final boolean adaptive;
    public final boolean tunneling;
    public final boolean secure;
    public final boolean passThrough;
    private final boolean isVideo;

    public static MediaCodecInfo newPassthroughInstance(String name) {
        return new MediaCodecInfo(name, (String) null, (CodecCapabilities) null, true, false, false);
    }

    public static MediaCodecInfo newInstance(String name, String mimeType, CodecCapabilities capabilities) {
        return new MediaCodecInfo(name, mimeType, capabilities, false, false, false);
    }

    public static MediaCodecInfo newInstance(String name, String mimeType, CodecCapabilities capabilities, boolean forceDisableAdaptive, boolean forceSecure) {
        return new MediaCodecInfo(name, mimeType, capabilities, false, forceDisableAdaptive, forceSecure);
    }

    private MediaCodecInfo(String name, @Nullable String mimeType, @Nullable CodecCapabilities capabilities, boolean passthrough, boolean forceDisableAdaptive, boolean forceSecure) {
        this.name = Assertions.checkNotNull(name);
        this.mimeType = mimeType;
        this.capabilities = capabilities;
        this.passThrough = passthrough;
        this.adaptive = !forceDisableAdaptive && capabilities != null && isAdaptive(capabilities);
        this.tunneling = capabilities != null && isTunneling(capabilities);
        this.secure = forceSecure || capabilities != null && isSecure(capabilities);
        this.isVideo = MimeTypes.isVideo(mimeType);
    }

    public String toString() {
        return this.name;
    }

    public CodecProfileLevel[] getProfileLevels() {
        return this.capabilities != null && this.capabilities.profileLevels != null ? this.capabilities.profileLevels : new CodecProfileLevel[0];
    }

    public int getMaxSupportedInstances() {
        return Util.SDK_INT >= 23 && this.capabilities != null ? getMaxSupportedInstancesV23(this.capabilities) : -1;
    }

    public boolean isFormatSupported(Format format) throws DecoderQueryException {
        if (!this.isCodecSupported(format.codecs)) {
            return false;
        } else if (this.isVideo) {
            if (format.width > 0 && format.height > 0) {
                if (Util.SDK_INT >= 21) {
                    return this.isVideoSizeAndRateSupportedV21(format.width, format.height, format.frameRate);
                } else {
                    boolean isFormatSupported = format.width * format.height <= MediaCodecUtil.maxH264DecodAbleFrameSize();
                    if (!isFormatSupported) {
                        this.logNoSupport("legacyFrameSize, " + format.width + "x" + format.height);
                    }

                    return isFormatSupported;
                }
            } else {
                return true;
            }
        } else {
            return (format.sampleRate == -1 || this.isAudioSampleRateSupportedV21(format.sampleRate)) && (format.channelCount == -1 || this.isAudioChannelCountSupportedV21(format.channelCount));
        }
    }

    public boolean isCodecSupported(String codec) {
        if (codec != null && this.mimeType != null) {
            String codecMimeType = MimeTypes.getMediaMimeType(codec);
            if (codecMimeType == null) {
                return true;
            } else if (!this.mimeType.equals(codecMimeType)) {
                this.logNoSupport("codec.mime " + codec + ", " + codecMimeType);
                return false;
            } else {
                Pair<Integer, Integer> codecProfileAndLevel = MediaCodecUtil.getCodecProfileAndLevel(codec);
                if (codecProfileAndLevel == null) {
                    return true;
                } else {
                    int profile = codecProfileAndLevel.first;
                    int level = codecProfileAndLevel.second;
                    if (!this.isVideo && profile != 42) {
                        return true;
                    } else {
                        CodecProfileLevel[] var6 = this.getProfileLevels();
                        for (CodecProfileLevel capabilities : var6) {
                            if (capabilities.profile == profile && capabilities.level >= level) {
                                return true;
                            }
                        }

                        this.logNoSupport("codec.profileLevel, " + codec + ", " + codecMimeType);
                        return false;
                    }
                }
            }
        } else {
            return true;
        }
    }

    public boolean isSeamlessAdaptationSupported(Format format) {
        if (this.isVideo) {
            return this.adaptive;
        } else {
            Pair<Integer, Integer> codecProfileLevel = MediaCodecUtil.getCodecProfileAndLevel(format.codecs);
            return codecProfileLevel != null && codecProfileLevel.first == 42;
        }
    }

    public boolean isSeamlessAdaptationSupported(Format oldFormat, Format newFormat, boolean isNewFormatComplete) {
        if (this.isVideo) {
            return oldFormat.sampleMimeType.equals(newFormat.sampleMimeType) && oldFormat.rotationDegrees == newFormat.rotationDegrees && (this.adaptive || oldFormat.width == newFormat.width && oldFormat.height == newFormat.height) && (!isNewFormatComplete && newFormat.colorInfo == null || Util.areEqual(oldFormat.colorInfo, newFormat.colorInfo));
        } else if ("audio/mp4a-latm".equals(this.mimeType) && oldFormat.sampleMimeType.equals(newFormat.sampleMimeType) && oldFormat.channelCount == newFormat.channelCount && oldFormat.sampleRate == newFormat.sampleRate) {
            Pair<Integer, Integer> oldCodecProfileLevel = MediaCodecUtil.getCodecProfileAndLevel(oldFormat.codecs);
            Pair<Integer, Integer> newCodecProfileLevel = MediaCodecUtil.getCodecProfileAndLevel(newFormat.codecs);
            if (oldCodecProfileLevel != null && newCodecProfileLevel != null) {
                int oldProfile = oldCodecProfileLevel.first;
                int newProfile = newCodecProfileLevel.first;
                return oldProfile == 42 && newProfile == 42;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @TargetApi(21)
    public boolean isVideoSizeAndRateSupportedV21(int width, int height, double frameRate) {
        if (this.capabilities == null) {
            this.logNoSupport("sizeAndRate.caps");
            return false;
        } else {
            VideoCapabilities videoCapabilities = this.capabilities.getVideoCapabilities();
            if (videoCapabilities == null) {
                this.logNoSupport("sizeAndRate.vCaps");
                return false;
            } else {
                if (areSizeAndRateSupportedV21(videoCapabilities, width, height, frameRate)) {
                    if (width >= height || areSizeAndRateSupportedV21(videoCapabilities, height, width, frameRate)) {
                        this.logNoSupport("sizeAndRate.support, " + width + "x" + height + "x" + frameRate);
                        return false;
                    }

                    this.logAssumedSupport("sizeAndRate.rotated, " + width + "x" + height + "x" + frameRate);
                }

                return true;
            }
        }
    }

    @TargetApi(21)
    public Point alignVideoSizeV21(int width, int height) {
        if (this.capabilities == null) {
            this.logNoSupport("align.caps");
            return null;
        } else {
            VideoCapabilities videoCapabilities = this.capabilities.getVideoCapabilities();
            if (videoCapabilities == null) {
                this.logNoSupport("align.vCaps");
                return null;
            } else {
                int widthAlignment = videoCapabilities.getWidthAlignment();
                int heightAlignment = videoCapabilities.getHeightAlignment();
                return new Point(Util.ceilDivide(width, widthAlignment) * widthAlignment, Util.ceilDivide(height, heightAlignment) * heightAlignment);
            }
        }
    }

    @TargetApi(21)
    public boolean isAudioSampleRateSupportedV21(int sampleRate) {
        if (this.capabilities == null) {
            this.logNoSupport("sampleRate.caps");
            return false;
        } else {
            AudioCapabilities audioCapabilities = this.capabilities.getAudioCapabilities();
            if (audioCapabilities == null) {
                this.logNoSupport("sampleRate.aCaps");
                return false;
            } else if (!audioCapabilities.isSampleRateSupported(sampleRate)) {
                this.logNoSupport("sampleRate.support, " + sampleRate);
                return false;
            } else {
                return true;
            }
        }
    }

    @TargetApi(21)
    public boolean isAudioChannelCountSupportedV21(int channelCount) {
        if (this.capabilities == null) {
            this.logNoSupport("channelCount.caps");
            return false;
        } else {
            AudioCapabilities audioCapabilities = this.capabilities.getAudioCapabilities();
            if (audioCapabilities == null) {
                this.logNoSupport("channelCount.aCaps");
                return false;
            } else {
                int maxInputChannelCount = adjustMaxInputChannelCount(this.name, this.mimeType, audioCapabilities.getMaxInputChannelCount());
                if (maxInputChannelCount < channelCount) {
                    this.logNoSupport("channelCount.support, " + channelCount);
                    return false;
                } else {
                    return true;
                }
            }
        }
    }

    private void logNoSupport(String message) {
        Log.d("MediaCodecInfo", "NoSupport [" + message + "] [" + this.name + ", " + this.mimeType + "] [" + Util.DEVICE_DEBUG_INFO + "]");
    }

    private void logAssumedSupport(String message) {
        Log.d("MediaCodecInfo", "AssumedSupport [" + message + "] [" + this.name + ", " + this.mimeType + "] [" + Util.DEVICE_DEBUG_INFO + "]");
    }

    private static int adjustMaxInputChannelCount(String name, String mimeType, int maxChannelCount) {
        if (maxChannelCount <= 1 && (Util.SDK_INT < 26 || maxChannelCount <= 0)) {
            if (!"audio/mpeg".equals(mimeType) && !"audio/3gpp".equals(mimeType) && !"audio/amr-wb".equals(mimeType) && !"audio/mp4a-latm".equals(mimeType) && !"audio/vorbis".equals(mimeType) && !"audio/opus".equals(mimeType) && !"audio/raw".equals(mimeType) && !"audio/flac".equals(mimeType) && !"audio/g711-alaw".equals(mimeType) && !"audio/g711-mlaw".equals(mimeType) && !"audio/gsm".equals(mimeType)) {
                byte assumedMaxChannelCount;
                if ("audio/ac3".equals(mimeType)) {
                    assumedMaxChannelCount = 6;
                } else if ("audio/eac3".equals(mimeType)) {
                    assumedMaxChannelCount = 16;
                } else {
                    assumedMaxChannelCount = 30;
                }

                Log.w("MediaCodecInfo", "AssumedMaxChannelAdjustment: " + name + ", [" + maxChannelCount + " to " + assumedMaxChannelCount + "]");
                return assumedMaxChannelCount;
            } else {
                return maxChannelCount;
            }
        } else {
            return maxChannelCount;
        }
    }

    private static boolean isAdaptive(CodecCapabilities capabilities) {
        return isAdaptiveV19(capabilities);
    }

    @TargetApi(19)
    private static boolean isAdaptiveV19(CodecCapabilities capabilities) {
        return capabilities.isFeatureSupported("adaptive-playback");
    }

    private static boolean isTunneling(CodecCapabilities capabilities) {
        return Util.SDK_INT >= 21 && isTunnelingV21(capabilities);
    }

    @TargetApi(21)
    private static boolean isTunnelingV21(CodecCapabilities capabilities) {
        return capabilities.isFeatureSupported("tunneled-playback");
    }

    private static boolean isSecure(CodecCapabilities capabilities) {
        return Util.SDK_INT >= 21 && isSecureV21(capabilities);
    }

    @TargetApi(21)
    private static boolean isSecureV21(CodecCapabilities capabilities) {
        return capabilities.isFeatureSupported("secure-playback");
    }

    @TargetApi(21)
    private static boolean areSizeAndRateSupportedV21(VideoCapabilities capabilities, int width, int height, double frameRate) {
        return frameRate != -1.0D && frameRate > 0.0D ? !capabilities.areSizeAndRateSupported(width, height, frameRate) : !capabilities.isSizeSupported(width, height);
    }

    @TargetApi(23)
    private static int getMaxSupportedInstancesV23(CodecCapabilities capabilities) {
        return capabilities.getMaxSupportedInstances();
    }
}
