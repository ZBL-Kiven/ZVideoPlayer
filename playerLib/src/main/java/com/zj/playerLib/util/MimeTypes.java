//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.util;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.ArrayList;

public final class MimeTypes {
    public static final String BASE_TYPE_VIDEO = "video";
    public static final String BASE_TYPE_AUDIO = "audio";
    public static final String BASE_TYPE_TEXT = "text";
    public static final String BASE_TYPE_APPLICATION = "application";
    public static final String VIDEO_MP4 = "video/mp4";
    public static final String VIDEO_WEBM = "video/webm";
    public static final String VIDEO_H263 = "video/3gpp";
    public static final String VIDEO_H264 = "video/avc";
    public static final String VIDEO_H265 = "video/hevc";
    public static final String VIDEO_VP8 = "video/x-vnd.on2.vp8";
    public static final String VIDEO_VP9 = "video/x-vnd.on2.vp9";
    public static final String VIDEO_MP4V = "video/mp4v-es";
    public static final String VIDEO_MPEG = "video/mpeg";
    public static final String VIDEO_MPEG2 = "video/mpeg2";
    public static final String VIDEO_VC1 = "video/wvc1";
    public static final String VIDEO_UNKNOWN = "video/x-unknown";
    public static final String AUDIO_MP4 = "audio/mp4";
    public static final String AUDIO_AAC = "audio/mp4a-latm";
    public static final String AUDIO_WEBM = "audio/webm";
    public static final String AUDIO_MPEG = "audio/mpeg";
    public static final String AUDIO_MPEG_L1 = "audio/mpeg-L1";
    public static final String AUDIO_MPEG_L2 = "audio/mpeg-L2";
    public static final String AUDIO_RAW = "audio/raw";
    public static final String AUDIO_ALAW = "audio/g711-alaw";
    public static final String AUDIO_MLAW = "audio/g711-mlaw";
    public static final String AUDIO_AC3 = "audio/ac3";
    public static final String AUDIO_E_AC3 = "audio/eac3";
    public static final String AUDIO_E_AC3_JOC = "audio/eac3-joc";
    public static final String AUDIO_TRUEHD = "audio/true-hd";
    public static final String AUDIO_DTS = "audio/vnd.dts";
    public static final String AUDIO_DTS_HD = "audio/vnd.dts.hd";
    public static final String AUDIO_DTS_EXPRESS = "audio/vnd.dts.hd;profile=lbr";
    public static final String AUDIO_VORBIS = "audio/vorbis";
    public static final String AUDIO_OPUS = "audio/opus";
    public static final String AUDIO_AMR_NB = "audio/3gpp";
    public static final String AUDIO_AMR_WB = "audio/amr-wb";
    public static final String AUDIO_FLAC = "audio/flac";
    public static final String AUDIO_ALAC = "audio/alac";
    public static final String AUDIO_MSGSM = "audio/gsm";
    public static final String AUDIO_UNKNOWN = "audio/x-unknown";
    public static final String TEXT_VTT = "text/vtt";
    public static final String TEXT_SSA = "text/x-ssa";
    public static final String APPLICATION_MP4 = "application/mp4";
    public static final String APPLICATION_WEBM = "application/webm";
    public static final String APPLICATION_MPD = "application/dash+xml";
    public static final String APPLICATION_M3U8 = "application/x-mpegURL";
    public static final String APPLICATION_SS = "application/vnd.ms-sstr+xml";
    public static final String APPLICATION_ID3 = "application/id3";
    public static final String APPLICATION_CEA608 = "application/cea-608";
    public static final String APPLICATION_CEA708 = "application/cea-708";
    public static final String APPLICATION_SUBRIP = "application/x-subrip";
    public static final String APPLICATION_TTML = "application/ttml+xml";
    public static final String APPLICATION_TX3G = "application/x-quicktime-tx3g";
    public static final String APPLICATION_MP4VTT = "application/x-mp4-vtt";
    public static final String APPLICATION_MP4CEA608 = "application/x-mp4-cea-608";
    public static final String APPLICATION_RAWCC = "application/x-rawcc";
    public static final String APPLICATION_VOBSUB = "application/vobsub";
    public static final String APPLICATION_PGS = "application/pgs";
    public static final String APPLICATION_SCTE35 = "application/x-scte35";
    public static final String APPLICATION_CAMERA_MOTION = "application/x-camera-motion";
    public static final String APPLICATION_EMSG = "application/x-emsg";
    public static final String APPLICATION_DVBSUBS = "application/dvbsubs";
    public static final String APPLICATION_EXIF = "application/x-exif";
    private static final ArrayList<CustomMimeType> customMimeTypes = new ArrayList();

    public static void registerCustomMimeType(String mimeType, String codecPrefix, int trackType) {
        CustomMimeType customMimeType = new CustomMimeType(mimeType, codecPrefix, trackType);
        int customMimeTypeCount = customMimeTypes.size();

        for(int i = 0; i < customMimeTypeCount; ++i) {
            if (mimeType.equals(((CustomMimeType)customMimeTypes.get(i)).mimeType)) {
                customMimeTypes.remove(i);
                break;
            }
        }

        customMimeTypes.add(customMimeType);
    }

    public static boolean isAudio(@Nullable String mimeType) {
        return "audio".equals(getTopLevelType(mimeType));
    }

    public static boolean isVideo(@Nullable String mimeType) {
        return "video".equals(getTopLevelType(mimeType));
    }

    public static boolean isText(@Nullable String mimeType) {
        return "text".equals(getTopLevelType(mimeType));
    }

    public static boolean isApplication(@Nullable String mimeType) {
        return "application".equals(getTopLevelType(mimeType));
    }

    @Nullable
    public static String getVideoMediaMimeType(@Nullable String codecs) {
        if (codecs == null) {
            return null;
        } else {
            String[] codecList = Util.splitCodecs(codecs);
            String[] var2 = codecList;
            int var3 = codecList.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                String codec = var2[var4];
                String mimeType = getMediaMimeType(codec);
                if (mimeType != null && isVideo(mimeType)) {
                    return mimeType;
                }
            }

            return null;
        }
    }

    @Nullable
    public static String getAudioMediaMimeType(@Nullable String codecs) {
        if (codecs == null) {
            return null;
        } else {
            String[] codecList = Util.splitCodecs(codecs);
            String[] var2 = codecList;
            int var3 = codecList.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                String codec = var2[var4];
                String mimeType = getMediaMimeType(codec);
                if (mimeType != null && isAudio(mimeType)) {
                    return mimeType;
                }
            }

            return null;
        }
    }

    @Nullable
    public static String getMediaMimeType(@Nullable String codec) {
        if (codec == null) {
            return null;
        } else {
            codec = Util.toLowerInvariant(codec.trim());
            if (!codec.startsWith("avc1") && !codec.startsWith("avc3")) {
                if (!codec.startsWith("hev1") && !codec.startsWith("hvc1")) {
                    if (!codec.startsWith("vp9") && !codec.startsWith("vp09")) {
                        if (!codec.startsWith("vp8") && !codec.startsWith("vp08")) {
                            if (codec.startsWith("mp4a")) {
                                String mimeType = null;
                                if (codec.startsWith("mp4a.")) {
                                    String objectTypeString = codec.substring(5);
                                    if (objectTypeString.length() >= 2) {
                                        try {
                                            String objectTypeHexString = Util.toUpperInvariant(objectTypeString.substring(0, 2));
                                            int objectTypeInt = Integer.parseInt(objectTypeHexString, 16);
                                            mimeType = getMimeTypeFromMp4ObjectType(objectTypeInt);
                                        } catch (NumberFormatException var5) {
                                        }
                                    }
                                }

                                return mimeType == null ? "audio/mp4a-latm" : mimeType;
                            } else if (!codec.startsWith("ac-3") && !codec.startsWith("dac3")) {
                                if (!codec.startsWith("ec-3") && !codec.startsWith("dec3")) {
                                    if (codec.startsWith("ec+3")) {
                                        return "audio/eac3-joc";
                                    } else if (!codec.startsWith("dtsc") && !codec.startsWith("dtse")) {
                                        if (!codec.startsWith("dtsh") && !codec.startsWith("dtsl")) {
                                            if (codec.startsWith("opus")) {
                                                return "audio/opus";
                                            } else if (codec.startsWith("vorbis")) {
                                                return "audio/vorbis";
                                            } else {
                                                return codec.startsWith("flac") ? "audio/flac" : getCustomMimeTypeForCodec(codec);
                                            }
                                        } else {
                                            return "audio/vnd.dts.hd";
                                        }
                                    } else {
                                        return "audio/vnd.dts";
                                    }
                                } else {
                                    return "audio/eac3";
                                }
                            } else {
                                return "audio/ac3";
                            }
                        } else {
                            return "video/x-vnd.on2.vp8";
                        }
                    } else {
                        return "video/x-vnd.on2.vp9";
                    }
                } else {
                    return "video/hevc";
                }
            } else {
                return "video/avc";
            }
        }
    }

    @Nullable
    public static String getMimeTypeFromMp4ObjectType(int objectType) {
        switch(objectType) {
        case 32:
            return "video/mp4v-es";
        case 33:
            return "video/avc";
        case 35:
            return "video/hevc";
        case 64:
        case 102:
        case 103:
        case 104:
            return "audio/mp4a-latm";
        case 96:
        case 97:
        case 98:
        case 99:
        case 100:
        case 101:
            return "video/mpeg2";
        case 105:
        case 107:
            return "audio/mpeg";
        case 106:
            return "video/mpeg";
        case 163:
            return "video/wvc1";
        case 165:
            return "audio/ac3";
        case 166:
            return "audio/eac3";
        case 169:
        case 172:
            return "audio/vnd.dts";
        case 170:
        case 171:
            return "audio/vnd.dts.hd";
        case 173:
            return "audio/opus";
        case 177:
            return "video/x-vnd.on2.vp9";
        default:
            return null;
        }
    }

    public static int getTrackType(@Nullable String mimeType) {
        if (TextUtils.isEmpty(mimeType)) {
            return -1;
        } else if (isAudio(mimeType)) {
            return 1;
        } else if (isVideo(mimeType)) {
            return 2;
        } else if (!isText(mimeType) && !"application/cea-608".equals(mimeType) && !"application/cea-708".equals(mimeType) && !"application/x-mp4-cea-608".equals(mimeType) && !"application/x-subrip".equals(mimeType) && !"application/ttml+xml".equals(mimeType) && !"application/x-quicktime-tx3g".equals(mimeType) && !"application/x-mp4-vtt".equals(mimeType) && !"application/x-rawcc".equals(mimeType) && !"application/vobsub".equals(mimeType) && !"application/pgs".equals(mimeType) && !"application/dvbsubs".equals(mimeType)) {
            if (!"application/id3".equals(mimeType) && !"application/x-emsg".equals(mimeType) && !"application/x-scte35".equals(mimeType)) {
                return "application/x-camera-motion".equals(mimeType) ? 5 : getTrackTypeForCustomMimeType(mimeType);
            } else {
                return 4;
            }
        } else {
            return 3;
        }
    }

    public static int getEncoding(String mimeType) {
        byte var2 = -1;
        switch(mimeType.hashCode()) {
        case -2123537834:
            if (mimeType.equals("audio/eac3-joc")) {
                var2 = 2;
            }
            break;
        case -1095064472:
            if (mimeType.equals("audio/vnd.dts")) {
                var2 = 3;
            }
            break;
        case 187078296:
            if (mimeType.equals("audio/ac3")) {
                var2 = 0;
            }
            break;
        case 1504578661:
            if (mimeType.equals("audio/eac3")) {
                var2 = 1;
            }
            break;
        case 1505942594:
            if (mimeType.equals("audio/vnd.dts.hd")) {
                var2 = 4;
            }
            break;
        case 1556697186:
            if (mimeType.equals("audio/true-hd")) {
                var2 = 5;
            }
        }

        switch(var2) {
        case 0:
            return 5;
        case 1:
        case 2:
            return 6;
        case 3:
            return 7;
        case 4:
            return 8;
        case 5:
            return 14;
        default:
            return 0;
        }
    }

    public static int getTrackTypeOfCodec(String codec) {
        return getTrackType(getMediaMimeType(codec));
    }

    @Nullable
    private static String getTopLevelType(@Nullable String mimeType) {
        if (mimeType == null) {
            return null;
        } else {
            int indexOfSlash = mimeType.indexOf(47);
            if (indexOfSlash == -1) {
                throw new IllegalArgumentException("Invalid mime type: " + mimeType);
            } else {
                return mimeType.substring(0, indexOfSlash);
            }
        }
    }

    @Nullable
    private static String getCustomMimeTypeForCodec(String codec) {
        int customMimeTypeCount = customMimeTypes.size();

        for(int i = 0; i < customMimeTypeCount; ++i) {
            CustomMimeType customMimeType = (CustomMimeType)customMimeTypes.get(i);
            if (codec.startsWith(customMimeType.codecPrefix)) {
                return customMimeType.mimeType;
            }
        }

        return null;
    }

    private static int getTrackTypeForCustomMimeType(String mimeType) {
        int customMimeTypeCount = customMimeTypes.size();

        for(int i = 0; i < customMimeTypeCount; ++i) {
            CustomMimeType customMimeType = (CustomMimeType)customMimeTypes.get(i);
            if (mimeType.equals(customMimeType.mimeType)) {
                return customMimeType.trackType;
            }
        }

        return -1;
    }

    private MimeTypes() {
    }

    private static final class CustomMimeType {
        public final String mimeType;
        public final String codecPrefix;
        public final int trackType;

        public CustomMimeType(String mimeType, String codecPrefix, int trackType) {
            this.mimeType = mimeType;
            this.codecPrefix = codecPrefix;
            this.trackType = trackType;
        }
    }
}
