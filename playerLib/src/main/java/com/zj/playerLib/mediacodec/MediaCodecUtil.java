package com.zj.playerLib.mediacodec;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecList;
import android.text.TextUtils;
import android.util.Pair;
import android.util.SparseIntArray;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.MimeTypes;
import com.zj.playerLib.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
@TargetApi(16)
@SuppressLint({"InlinedApi"})
public final class MediaCodecUtil {
    private static final Pattern PROFILE_PATTERN = Pattern.compile("^\\D?(\\d+)$");
    private static final RawAudioCodecComparator RAW_AUDIO_CODEC_COMPARATOR = new RawAudioCodecComparator();
    private static final HashMap<CodecKey, List<MediaCodecInfo>> decoderInfosCache = new HashMap<>();
    private static final SparseIntArray AVC_PROFILE_NUMBER_TO_CONST = new SparseIntArray();
    private static final SparseIntArray AVC_LEVEL_NUMBER_TO_CONST;
    private static final String CODEC_ID_AVC1 = "avc1";
    private static final String CODEC_ID_AVC2 = "avc2";
    private static final Map<String, Integer> HEVC_CODEC_STRING_TO_PROFILE_LEVEL;
    private static final String CODEC_ID_HEV1 = "hev1";
    private static final String CODEC_ID_HVC1 = "hvc1";
    private static final SparseIntArray MP4A_AUDIO_OBJECT_TYPE_TO_PROFILE;
    private static final String CODEC_ID_MP4A = "mp4a";
    private static int maxH264DecodableFrameSize = -1;

    private MediaCodecUtil() {
    }

    public static void warmDecoderInfoCache(String mimeType, boolean secure) {
        try {
            getDecoderInfos(mimeType, secure);
        } catch (DecoderQueryException var3) {
            Log.e("MediaCodecUtil", "Codec warming failed", var3);
        }

    }

    @Nullable
    public static MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException {
        MediaCodecInfo decoderInfo = getDecoderInfo("audio/raw", false);
        return decoderInfo == null ? null : MediaCodecInfo.newPassthroughInstance(decoderInfo.name);
    }

    @Nullable
    public static MediaCodecInfo getDecoderInfo(String mimeType, boolean secure) throws DecoderQueryException {
        List<MediaCodecInfo> decoderInfos = getDecoderInfos(mimeType, secure);
        return decoderInfos.isEmpty() ? null : decoderInfos.get(0);
    }

    public static synchronized List<MediaCodecInfo> getDecoderInfos(String mimeType, boolean secure) throws DecoderQueryException {
        CodecKey key = new CodecKey(mimeType, secure);
        List<MediaCodecInfo> cachedDecoderInfos = decoderInfosCache.get(key);
        if (cachedDecoderInfos != null) {
            return cachedDecoderInfos;
        } else {
            MediaCodecListCompat mediaCodecList = Util.SDK_INT >= 21 ? new MediaCodecListCompatV21(secure) : new MediaCodecListCompatV16();
            ArrayList<MediaCodecInfo> decoderInfos = getDecoderInfosInternal(key, mediaCodecList, mimeType);
            if (secure && decoderInfos.isEmpty() && 21 <= Util.SDK_INT && Util.SDK_INT <= 23) {
                mediaCodecList = new MediaCodecListCompatV16();
                decoderInfos = getDecoderInfosInternal(key, mediaCodecList, mimeType);
                if (!decoderInfos.isEmpty()) {
                    Log.w("MediaCodecUtil", "MediaCodecList API didn't list secure decoder for: " + mimeType + ". Assuming: " + decoderInfos.get(0).name);
                }
            }

            if ("audio/eac3-joc".equals(mimeType)) {
                CodecKey eac3Key = new CodecKey("audio/eac3", key.secure);
                ArrayList<MediaCodecInfo> eac3DecoderInfos = getDecoderInfosInternal(eac3Key, mediaCodecList, mimeType);
                decoderInfos.addAll(eac3DecoderInfos);
            }

            applyWorkarounds(mimeType, decoderInfos);
            List<MediaCodecInfo> unmodifiableDecoderInfos = Collections.unmodifiableList(decoderInfos);
            decoderInfosCache.put(key, unmodifiableDecoderInfos);
            return unmodifiableDecoderInfos;
        }
    }

    public static int maxH264DecodAbleFrameSize() throws DecoderQueryException {
        if (maxH264DecodableFrameSize == -1) {
            int result = 0;
            MediaCodecInfo decoderInfo = getDecoderInfo("video/avc", false);
            if (decoderInfo != null) {
                for (CodecProfileLevel profileLevel : decoderInfo.getProfileLevels()) {
                    result = Math.max(avcLevelToMaxFrameSize(profileLevel.level), result);
                }

                result = Math.max(result, Util.SDK_INT >= 21 ? 345600 : 172800);
            }

            maxH264DecodableFrameSize = result;
        }

        return maxH264DecodableFrameSize;
    }

    @Nullable
    public static Pair<Integer, Integer> getCodecProfileAndLevel(String codec) {
        if (codec == null) {
            return null;
        } else {
            String[] parts = codec.split("\\.");
            String var2 = parts[0];
            byte var3 = -1;
            switch (var2.hashCode()) {
                case 3006243:
                    if (var2.equals("avc1")) {
                        var3 = 2;
                    }
                    break;
                case 3006244:
                    if (var2.equals("avc2")) {
                        var3 = 3;
                    }
                    break;
                case 3199032:
                    if (var2.equals("hev1")) {
                        var3 = 0;
                    }
                    break;
                case 3214780:
                    if (var2.equals("hvc1")) {
                        var3 = 1;
                    }
                    break;
                case 3356560:
                    if (var2.equals("mp4a")) {
                        var3 = 4;
                    }
            }

            switch (var3) {
                case 0:
                case 1:
                    return getHevcProfileAndLevel(codec, parts);
                case 2:
                case 3:
                    return getAvcProfileAndLevel(codec, parts);
                case 4:
                    return getAacCodecProfileAndLevel(codec, parts);
                default:
                    return null;
            }
        }
    }

    private static ArrayList<MediaCodecInfo> getDecoderInfosInternal(CodecKey key, MediaCodecListCompat mediaCodecList, String requestedMimeType) throws DecoderQueryException {
        try {
            ArrayList<MediaCodecInfo> decoderInfos = new ArrayList<>();
            String mimeType = key.mimeType;
            int numberOfCodecs = mediaCodecList.getCodecCount();
            boolean secureDecodersExplicit = mediaCodecList.secureDecodersExplicit();

            for (int i = 0; i < numberOfCodecs; ++i) {
                android.media.MediaCodecInfo codecInfo = mediaCodecList.getCodecInfoAt(i);
                String codecName = codecInfo.getName();
                if (isCodecUsableDecoder(codecInfo, codecName, secureDecodersExplicit, requestedMimeType)) {
                    for (String supportedType : codecInfo.getSupportedTypes()) {
                        if (supportedType.equalsIgnoreCase(mimeType)) {
                            try {
                                CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(supportedType);
                                boolean secure = mediaCodecList.isSecurePlaybackSupported(mimeType, capabilities);
                                boolean forceDisableAdaptive = codecNeedsDisableAdaptationWorkaround(codecName);
                                if (secureDecodersExplicit && key.secure == secure || !secureDecodersExplicit && !key.secure) {
                                    decoderInfos.add(MediaCodecInfo.newInstance(codecName, mimeType, capabilities, forceDisableAdaptive, false));
                                } else if (!secureDecodersExplicit && secure) {
                                    decoderInfos.add(MediaCodecInfo.newInstance(codecName + ".secure", mimeType, capabilities, forceDisableAdaptive, true));
                                    return decoderInfos;
                                }
                            } catch (Exception var17) {
                                if (Util.SDK_INT > 23 || decoderInfos.isEmpty()) {
                                    Log.e("MediaCodecUtil", "Failed to query codec " + codecName + " (" + supportedType + ")");
                                    throw var17;
                                }

                                Log.e("MediaCodecUtil", "Skipping codec " + codecName + " (failed to query capabilities)");
                            }
                        }
                    }
                }
            }

            return decoderInfos;
        } catch (Exception var18) {
            throw new DecoderQueryException(var18);
        }
    }

    private static boolean isCodecUsableDecoder(android.media.MediaCodecInfo info, String name, boolean secureDecodersExplicit, String requestedMimeType) {
        if (info.isEncoder() || !secureDecodersExplicit && name.endsWith(".secure")) {
            return false;
        } else if (Util.SDK_INT < 21 && ("CIPAACDecoder".equals(name) || "CIPMP3Decoder".equals(name) || "CIPVorbisDecoder".equals(name) || "CIPAMRNBDecoder".equals(name) || "AACDecoder".equals(name) || "MP3Decoder".equals(name))) {
            return false;
        } else if (Util.SDK_INT < 18 && "OMX.SEC.MP3.Decoder".equals(name)) {
            return false;
        } else if ("OMX.SEC.mp3.dec".equals(name) && (Util.MODEL.startsWith("GT-I9152") || Util.MODEL.startsWith("GT-I9515") || Util.MODEL.startsWith("GT-P5220") || Util.MODEL.startsWith("GT-S7580") || Util.MODEL.startsWith("SM-G350") || Util.MODEL.startsWith("SM-G386") || Util.MODEL.startsWith("SM-T231") || Util.MODEL.startsWith("SM-T530"))) {
            return false;
        } else if ("OMX.brcm.audio.mp3.decoder".equals(name) && (Util.MODEL.startsWith("GT-I9152") || Util.MODEL.startsWith("GT-S7580") || Util.MODEL.startsWith("SM-G350"))) {
            return false;
        } else if (Util.SDK_INT < 18 && "OMX.MTK.AUDIO.DECODER.AAC".equals(name) && ("a70".equals(Util.DEVICE) || "Xiaomi".equals(Util.MANUFACTURER) && Util.DEVICE.startsWith("HM"))) {
            return false;
        } else if (Util.SDK_INT != 16 || !"OMX.qcom.audio.decoder.mp3".equals(name) || !"dlxu".equals(Util.DEVICE) && !"protou".equals(Util.DEVICE) && !"ville".equals(Util.DEVICE) && !"villeplus".equals(Util.DEVICE) && !"villec2".equals(Util.DEVICE) && !Util.DEVICE.startsWith("gee") && !"C6602".equals(Util.DEVICE) && !"C6603".equals(Util.DEVICE) && !"C6606".equals(Util.DEVICE) && !"C6616".equals(Util.DEVICE) && !"L36h".equals(Util.DEVICE) && !"SO-02E".equals(Util.DEVICE)) {
            if (Util.SDK_INT == 16 && "OMX.qcom.audio.decoder.aac".equals(name) && ("C1504".equals(Util.DEVICE) || "C1505".equals(Util.DEVICE) || "C1604".equals(Util.DEVICE) || "C1605".equals(Util.DEVICE))) {
                return false;
            } else if (Util.SDK_INT < 24 && ("OMX.SEC.aac.dec".equals(name) || "OMX.Exynos.AAC.Decoder".equals(name)) && "samsung".equals(Util.MANUFACTURER) && (Util.DEVICE.startsWith("zeroflte") || Util.DEVICE.startsWith("zerolte") || Util.DEVICE.startsWith("zenlte") || "SC-05G".equals(Util.DEVICE) || "marinelteatt".equals(Util.DEVICE) || "404SC".equals(Util.DEVICE) || "SC-04G".equals(Util.DEVICE) || "SCV31".equals(Util.DEVICE))) {
                return false;
            } else if (Util.SDK_INT > 19 || !"OMX.SEC.vp8.dec".equals(name) || !"samsung".equals(Util.MANUFACTURER) || !Util.DEVICE.startsWith("d2") && !Util.DEVICE.startsWith("serrano") && !Util.DEVICE.startsWith("jflte") && !Util.DEVICE.startsWith("santos") && !Util.DEVICE.startsWith("t0")) {
                if (Util.SDK_INT <= 19 && Util.DEVICE.startsWith("jflte") && "OMX.qcom.video.decoder.vp8".equals(name)) {
                    return false;
                } else {
                    return !"audio/eac3-joc".equals(requestedMimeType) || !"OMX.MTK.AUDIO.DECODER.DSPAC3".equals(name);
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private static void applyWorkarounds(String mimeType, List<MediaCodecInfo> decoderInfos) {
        if ("audio/raw".equals(mimeType)) {
            Collections.sort(decoderInfos, RAW_AUDIO_CODEC_COMPARATOR);
        }

    }

    private static boolean codecNeedsDisableAdaptationWorkaround(String name) {
        return Util.SDK_INT <= 22 && ("ODROID-XU3".equals(Util.MODEL) || "Nexus 10".equals(Util.MODEL)) && ("OMX.Exynos.AVC.Decoder".equals(name) || "OMX.Exynos.AVC.Decoder.secure".equals(name));
    }

    private static Pair<Integer, Integer> getHevcProfileAndLevel(String codec, String[] parts) {
        if (parts.length < 4) {
            Log.w("MediaCodecUtil", "Ignoring malformed HEVC codec string: " + codec);
            return null;
        } else {
            Matcher matcher = PROFILE_PATTERN.matcher(parts[1]);
            if (!matcher.matches()) {
                Log.w("MediaCodecUtil", "Ignoring malformed HEVC codec string: " + codec);
                return null;
            } else {
                String profileString = matcher.group(1);
                byte profile;
                if ("1".equals(profileString)) {
                    profile = 1;
                } else {
                    if (!"2".equals(profileString)) {
                        Log.w("MediaCodecUtil", "Unknown HEVC profile string: " + profileString);
                        return null;
                    }

                    profile = 2;
                }

                Integer level = HEVC_CODEC_STRING_TO_PROFILE_LEVEL.get(parts[3]);
                if (level == null) {
                    Log.w("MediaCodecUtil", "Unknown HEVC level string: " + matcher.group(1));
                    return null;
                } else {
                    return new Pair<>((int) profile, level);
                }
            }
        }
    }

    private static Pair<Integer, Integer> getAvcProfileAndLevel(String codec, String[] parts) {
        if (parts.length < 2) {
            Log.w("MediaCodecUtil", "Ignoring malformed AVC codec string: " + codec);
            return null;
        } else {
            int profileInteger;
            int levelInteger;
            try {
                if (parts[1].length() == 6) {
                    profileInteger = Integer.parseInt(parts[1].substring(0, 2), 16);
                    levelInteger = Integer.parseInt(parts[1].substring(4), 16);
                } else {
                    if (parts.length < 3) {
                        Log.w("MediaCodecUtil", "Ignoring malformed AVC codec string: " + codec);
                        return null;
                    }

                    profileInteger = Integer.parseInt(parts[1]);
                    levelInteger = Integer.parseInt(parts[2]);
                }
            } catch (NumberFormatException var6) {
                Log.w("MediaCodecUtil", "Ignoring malformed AVC codec string: " + codec);
                return null;
            }

            int profile = AVC_PROFILE_NUMBER_TO_CONST.get(profileInteger, -1);
            if (profile == -1) {
                Log.w("MediaCodecUtil", "Unknown AVC profile: " + profileInteger);
                return null;
            } else {
                int level = AVC_LEVEL_NUMBER_TO_CONST.get(levelInteger, -1);
                if (level == -1) {
                    Log.w("MediaCodecUtil", "Unknown AVC level: " + levelInteger);
                    return null;
                } else {
                    return new Pair<>(profile, level);
                }
            }
        }
    }

    private static int avcLevelToMaxFrameSize(int avcLevel) {
        switch (avcLevel) {
            case 1:
            case 2:
                return 25344;
            case 8:
            case 16:
            case 32:
                return 101376;
            case 64:
                return 202752;
            case 128:
            case 256:
                return 414720;
            case 512:
                return 921600;
            case 1024:
                return 1310720;
            case 2048:
            case 4096:
                return 2097152;
            case 8192:
                return 2228224;
            case 16384:
                return 5652480;
            case 32768:
            case 65536:
                return 9437184;
            default:
                return -1;
        }
    }

    @Nullable
    private static Pair<Integer, Integer> getAacCodecProfileAndLevel(String codec, String[] parts) {
        if (parts.length != 3) {
            Log.w("MediaCodecUtil", "Ignoring malformed MP4A codec string: " + codec);
        } else {
            try {
                int objectTypeIndication = Integer.parseInt(parts[1], 16);
                String mimeType = MimeTypes.getMimeTypeFromMp4ObjectType(objectTypeIndication);
                if ("audio/mp4a-latm".equals(mimeType)) {
                    int audioObjectTypeIndication = Integer.parseInt(parts[2]);
                    int profile = MP4A_AUDIO_OBJECT_TYPE_TO_PROFILE.get(audioObjectTypeIndication, -1);
                    if (profile != -1) {
                        return new Pair<>(profile, 0);
                    }
                }
            } catch (NumberFormatException var6) {
                Log.w("MediaCodecUtil", "Ignoring malformed MP4A codec string: " + codec);
            }

        }
        return null;
    }

    static {
        AVC_PROFILE_NUMBER_TO_CONST.put(66, 1);
        AVC_PROFILE_NUMBER_TO_CONST.put(77, 2);
        AVC_PROFILE_NUMBER_TO_CONST.put(88, 4);
        AVC_PROFILE_NUMBER_TO_CONST.put(100, 8);
        AVC_PROFILE_NUMBER_TO_CONST.put(110, 16);
        AVC_PROFILE_NUMBER_TO_CONST.put(122, 32);
        AVC_PROFILE_NUMBER_TO_CONST.put(244, 64);
        AVC_LEVEL_NUMBER_TO_CONST = new SparseIntArray();
        AVC_LEVEL_NUMBER_TO_CONST.put(10, 1);
        AVC_LEVEL_NUMBER_TO_CONST.put(11, 4);
        AVC_LEVEL_NUMBER_TO_CONST.put(12, 8);
        AVC_LEVEL_NUMBER_TO_CONST.put(13, 16);
        AVC_LEVEL_NUMBER_TO_CONST.put(20, 32);
        AVC_LEVEL_NUMBER_TO_CONST.put(21, 64);
        AVC_LEVEL_NUMBER_TO_CONST.put(22, 128);
        AVC_LEVEL_NUMBER_TO_CONST.put(30, 256);
        AVC_LEVEL_NUMBER_TO_CONST.put(31, 512);
        AVC_LEVEL_NUMBER_TO_CONST.put(32, 1024);
        AVC_LEVEL_NUMBER_TO_CONST.put(40, 2048);
        AVC_LEVEL_NUMBER_TO_CONST.put(41, 4096);
        AVC_LEVEL_NUMBER_TO_CONST.put(42, 8192);
        AVC_LEVEL_NUMBER_TO_CONST.put(50, 16384);
        AVC_LEVEL_NUMBER_TO_CONST.put(51, 32768);
        AVC_LEVEL_NUMBER_TO_CONST.put(52, 65536);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL = new HashMap<>();
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L30", 1);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L60", 4);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L63", 16);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L90", 64);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L93", 256);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L120", 1024);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L123", 4096);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L150", 16384);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L153", 65536);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L156", 262144);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L180", 1048576);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L183", 4194304);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L186", 16777216);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H30", 2);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H60", 8);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H63", 32);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H90", 128);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H93", 512);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H120", 2048);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H123", 8192);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H150", 32768);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H153", 131072);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H156", 524288);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H180", 2097152);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H183", 8388608);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H186", 33554432);
        MP4A_AUDIO_OBJECT_TYPE_TO_PROFILE = new SparseIntArray();
        MP4A_AUDIO_OBJECT_TYPE_TO_PROFILE.put(1, 1);
        MP4A_AUDIO_OBJECT_TYPE_TO_PROFILE.put(2, 2);
        MP4A_AUDIO_OBJECT_TYPE_TO_PROFILE.put(3, 3);
        MP4A_AUDIO_OBJECT_TYPE_TO_PROFILE.put(4, 4);
        MP4A_AUDIO_OBJECT_TYPE_TO_PROFILE.put(5, 5);
        MP4A_AUDIO_OBJECT_TYPE_TO_PROFILE.put(6, 6);
        MP4A_AUDIO_OBJECT_TYPE_TO_PROFILE.put(17, 17);
        MP4A_AUDIO_OBJECT_TYPE_TO_PROFILE.put(20, 20);
        MP4A_AUDIO_OBJECT_TYPE_TO_PROFILE.put(23, 23);
        MP4A_AUDIO_OBJECT_TYPE_TO_PROFILE.put(29, 29);
        MP4A_AUDIO_OBJECT_TYPE_TO_PROFILE.put(39, 39);
        MP4A_AUDIO_OBJECT_TYPE_TO_PROFILE.put(42, 42);
    }

    private static final class RawAudioCodecComparator implements Comparator<MediaCodecInfo> {
        private RawAudioCodecComparator() {
        }

        public int compare(MediaCodecInfo a, MediaCodecInfo b) {
            return scoreMediaCodecInfo(a) - scoreMediaCodecInfo(b);
        }

        private static int scoreMediaCodecInfo(MediaCodecInfo mediaCodecInfo) {
            String name = mediaCodecInfo.name;
            if (!name.startsWith("OMX.google") && !name.startsWith("c2.android")) {
                return Util.SDK_INT < 26 && name.equals("OMX.MTK.AUDIO.DECODER.RAW") ? 1 : 0;
            } else {
                return -1;
            }
        }
    }

    private static final class CodecKey {
        public final String mimeType;
        public final boolean secure;

        public CodecKey(String mimeType, boolean secure) {
            this.mimeType = mimeType;
            this.secure = secure;
        }

        public int hashCode() {
            int result = 1;
            result = 31 * result + (this.mimeType == null ? 0 : this.mimeType.hashCode());
            result = 31 * result + (this.secure ? 1231 : 1237);
            return result;
        }

        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            } else if (obj != null && obj.getClass() == CodecKey.class) {
                CodecKey other = (CodecKey) obj;
                return TextUtils.equals(this.mimeType, other.mimeType) && this.secure == other.secure;
            } else {
                return false;
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static final class MediaCodecListCompatV16 implements MediaCodecListCompat {
        private MediaCodecListCompatV16() {
        }

        public int getCodecCount() {
            return MediaCodecList.getCodecCount();
        }

        public android.media.MediaCodecInfo getCodecInfoAt(int index) {
            return MediaCodecList.getCodecInfoAt(index);
        }

        public boolean secureDecodersExplicit() {
            return false;
        }

        public boolean isSecurePlaybackSupported(String mimeType, CodecCapabilities capabilities) {
            return "video/avc".equals(mimeType);
        }
    }

    @TargetApi(21)
    private static final class MediaCodecListCompatV21 implements MediaCodecListCompat {
        private final int codecKind;
        private android.media.MediaCodecInfo[] mediaCodecInfos;

        public MediaCodecListCompatV21(boolean includeSecure) {
            this.codecKind = includeSecure ? 1 : 0;
        }

        public int getCodecCount() {
            this.ensureMediaCodecInfosInitialized();
            return this.mediaCodecInfos.length;
        }

        public android.media.MediaCodecInfo getCodecInfoAt(int index) {
            this.ensureMediaCodecInfosInitialized();
            return this.mediaCodecInfos[index];
        }

        public boolean secureDecodersExplicit() {
            return true;
        }

        public boolean isSecurePlaybackSupported(String mimeType, CodecCapabilities capabilities) {
            return capabilities.isFeatureSupported("secure-playback");
        }

        private void ensureMediaCodecInfosInitialized() {
            if (this.mediaCodecInfos == null) {
                this.mediaCodecInfos = (new MediaCodecList(this.codecKind)).getCodecInfos();
            }

        }
    }

    private interface MediaCodecListCompat {
        int getCodecCount();

        android.media.MediaCodecInfo getCodecInfoAt(int var1);

        boolean secureDecodersExplicit();

        boolean isSecurePlaybackSupported(String var1, CodecCapabilities var2);
    }

    public static class DecoderQueryException extends Exception {
        private DecoderQueryException(Throwable cause) {
            super("Failed to query underlying media codecs", cause);
        }
    }
}
