package com.zj.playerLib;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.metadata.Metadata;
import com.zj.playerLib.util.MimeTypes;
import com.zj.playerLib.util.Util;
import com.zj.playerLib.video.ColorInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Format implements Parcelable {
    @Nullable
    public final String id;
    @Nullable
    public final String label;
    public final int bitrate;
    @Nullable
    public final String codecs;
    @Nullable
    public final Metadata metadata;
    @Nullable
    public final String containerMimeType;
    @Nullable
    public final String sampleMimeType;
    public final int maxInputSize;
    public final List<byte[]> initializationData;
    @Nullable
    public final DrmInitData drmInitData;
    public final long subSampleOffsetUs;
    public final int width;
    public final int height;
    public final float frameRate;
    public final int rotationDegrees;
    public final float pixelWidthHeightRatio;
    public final int stereoMode;
    @Nullable
    public final byte[] projectionData;
    @Nullable
    public final ColorInfo colorInfo;
    public final int channelCount;
    public final int sampleRate;
    public final int pcmEncoding;
    public final int encoderDelay;
    public final int encoderPadding;
    public final int selectionFlags;
    @Nullable
    public final String language;
    public final int accessibilityChannel;
    private int hashCode;
    public static final Creator<Format> CREATOR = new Creator<Format>() {
        public Format createFromParcel(Parcel in) {
            return new Format(in);
        }

        public Format[] newArray(int size) {
            return new Format[size];
        }
    };
    
    public static Format createVideoContainerFormat(@Nullable String id, @Nullable String label, @Nullable String containerMimeType, String sampleMimeType, String codecs, int bitrate, int width, int height, float frameRate, @Nullable List<byte[]> initializationData, int selectionFlags) {
        return new Format(id, label, containerMimeType, sampleMimeType, codecs, bitrate, -1, width, height, frameRate, -1, -1.0F, null, -1, null, -1, -1, -1, -1, -1, selectionFlags, null, -1, Long.MAX_VALUE, initializationData, null, null);
    }

    public static Format createVideoSampleFormat(@Nullable String id, @Nullable String sampleMimeType, @Nullable String codecs, int bitrate, int maxInputSize, int width, int height, float frameRate, @Nullable List<byte[]> initializationData, @Nullable DrmInitData drmInitData) {
        return createVideoSampleFormat(id, sampleMimeType, codecs, bitrate, maxInputSize, width, height, frameRate, initializationData, -1, -1.0F, drmInitData);
    }

    public static Format createVideoSampleFormat(@Nullable String id, @Nullable String sampleMimeType, @Nullable String codecs, int bitrate, int maxInputSize, int width, int height, float frameRate, @Nullable List<byte[]> initializationData, int rotationDegrees, float pixelWidthHeightRatio, @Nullable DrmInitData drmInitData) {
        return createVideoSampleFormat(id, sampleMimeType, codecs, bitrate, maxInputSize, width, height, frameRate, initializationData, rotationDegrees, pixelWidthHeightRatio, null, -1, null, drmInitData);
    }

    public static Format createVideoSampleFormat(@Nullable String id, @Nullable String sampleMimeType, @Nullable String codecs, int bitrate, int maxInputSize, int width, int height, float frameRate, @Nullable List<byte[]> initializationData, int rotationDegrees, float pixelWidthHeightRatio, byte[] projectionData, int stereoMode, @Nullable ColorInfo colorInfo, @Nullable DrmInitData drmInitData) {
        return new Format(id, null, null, sampleMimeType, codecs, bitrate, maxInputSize, width, height, frameRate, rotationDegrees, pixelWidthHeightRatio, projectionData, stereoMode, colorInfo, -1, -1, -1, -1, -1, 0, null, -1, Long.MAX_VALUE, initializationData, drmInitData, null);
    }
    
    public static Format createAudioContainerFormat(@Nullable String id, @Nullable String label, @Nullable String containerMimeType, @Nullable String sampleMimeType, @Nullable String codecs, int bitrate, int channelCount, int sampleRate, @Nullable List<byte[]> initializationData, int selectionFlags, @Nullable String language) {
        return new Format(id, label, containerMimeType, sampleMimeType, codecs, bitrate, -1, -1, -1, -1.0F, -1, -1.0F, null, -1, null, channelCount, sampleRate, -1, -1, -1, selectionFlags, language, -1, Long.MAX_VALUE, initializationData, null, null);
    }

    public static Format createAudioSampleFormat(@Nullable String id, @Nullable String sampleMimeType, @Nullable String codecs, int bitrate, int maxInputSize, int channelCount, int sampleRate, @Nullable List<byte[]> initializationData, @Nullable DrmInitData drmInitData, int selectionFlags, @Nullable String language) {
        return createAudioSampleFormat(id, sampleMimeType, codecs, bitrate, maxInputSize, channelCount, sampleRate, -1, initializationData, drmInitData, selectionFlags, language);
    }

    public static Format createAudioSampleFormat(@Nullable String id, @Nullable String sampleMimeType, @Nullable String codecs, int bitrate, int maxInputSize, int channelCount, int sampleRate, int pcmEncoding, @Nullable List<byte[]> initializationData, @Nullable DrmInitData drmInitData, int selectionFlags, @Nullable String language) {
        return createAudioSampleFormat(id, sampleMimeType, codecs, bitrate, maxInputSize, channelCount, sampleRate, pcmEncoding, -1, -1, initializationData, drmInitData, selectionFlags, language, null);
    }

    public static Format createAudioSampleFormat(@Nullable String id, @Nullable String sampleMimeType, @Nullable String codecs, int bitrate, int maxInputSize, int channelCount, int sampleRate, int pcmEncoding, int encoderDelay, int encoderPadding, @Nullable List<byte[]> initializationData, @Nullable DrmInitData drmInitData, int selectionFlags, @Nullable String language, @Nullable Metadata metadata) {
        return new Format(id, null, null, sampleMimeType, codecs, bitrate, maxInputSize, -1, -1, -1.0F, -1, -1.0F, null, -1, null, channelCount, sampleRate, pcmEncoding, encoderDelay, encoderPadding, selectionFlags, language, -1, Long.MAX_VALUE, initializationData, drmInitData, metadata);
    }
    
    public static Format createTextSampleFormat(@Nullable String id, String sampleMimeType, int selectionFlags, @Nullable String language) {
        return createTextSampleFormat(id, sampleMimeType, selectionFlags, language, null);
    }

    public static Format createTextSampleFormat(@Nullable String id, String sampleMimeType, int selectionFlags, @Nullable String language, @Nullable DrmInitData drmInitData) {
        return createTextSampleFormat(id, sampleMimeType, null, -1, selectionFlags, language, -1, drmInitData, Long.MAX_VALUE, Collections.emptyList());
    }

    public static Format createTextSampleFormat(@Nullable String id, @Nullable String sampleMimeType, @Nullable String codecs, int bitrate, int selectionFlags, @Nullable String language, int accessibilityChannel, @Nullable DrmInitData drmInitData) {
        return createTextSampleFormat(id, sampleMimeType, codecs, bitrate, selectionFlags, language, accessibilityChannel, drmInitData, Long.MAX_VALUE, Collections.emptyList());
    }

    public static Format createTextSampleFormat(@Nullable String id, @Nullable String sampleMimeType, @Nullable String codecs, int bitrate, int selectionFlags, @Nullable String language, @Nullable DrmInitData drmInitData, long subSampleOffsetUs) {
        return createTextSampleFormat(id, sampleMimeType, codecs, bitrate, selectionFlags, language, -1, drmInitData, subSampleOffsetUs, Collections.emptyList());
    }

    public static Format createTextSampleFormat(@Nullable String id, @Nullable String sampleMimeType, @Nullable String codecs, int bitrate, int selectionFlags, @Nullable String language, int accessibilityChannel, @Nullable DrmInitData drmInitData, long subSampleOffsetUs, List<byte[]> initializationData) {
        return new Format(id, null, null, sampleMimeType, codecs, bitrate, -1, -1, -1, -1.0F, -1, -1.0F, null, -1, null, -1, -1, -1, -1, -1, selectionFlags, language, accessibilityChannel, subSampleOffsetUs, initializationData, drmInitData, null);
    }

    public static Format createImageSampleFormat(@Nullable String id, @Nullable String sampleMimeType, @Nullable String codecs, int bitrate, int selectionFlags, @Nullable List<byte[]> initializationData, @Nullable String language, @Nullable DrmInitData drmInitData) {
        return new Format(id, null, null, sampleMimeType, codecs, bitrate, -1, -1, -1, -1.0F, -1, -1.0F, null, -1, null, -1, -1, -1, -1, -1, selectionFlags, language, -1, Long.MAX_VALUE, initializationData, drmInitData, null);
    }
    
    public static Format createSampleFormat(@Nullable String id, @Nullable String sampleMimeType, long subSampleOffsetUs) {
        return new Format(id, null, null, sampleMimeType, null, -1, -1, -1, -1, -1.0F, -1, -1.0F, null, -1, null, -1, -1, -1, -1, -1, 0, null, -1, subSampleOffsetUs, null, null, null);
    }

    public static Format createSampleFormat(@Nullable String id, @Nullable String sampleMimeType, @Nullable String codecs, int bitrate, @Nullable DrmInitData drmInitData) {
        return new Format(id, null, null, sampleMimeType, codecs, bitrate, -1, -1, -1, -1.0F, -1, -1.0F, null, -1, null, -1, -1, -1, -1, -1, 0, null, -1, Long.MAX_VALUE, null, drmInitData, null);
    }

    Format(@Nullable String id, @Nullable String label, @Nullable String containerMimeType, @Nullable String sampleMimeType, @Nullable String codecs, int bitrate, int maxInputSize, int width, int height, float frameRate, int rotationDegrees, float pixelWidthHeightRatio, @Nullable byte[] projectionData, int stereoMode, @Nullable ColorInfo colorInfo, int channelCount, int sampleRate, int pcmEncoding, int encoderDelay, int encoderPadding, int selectionFlags, @Nullable String language, int accessibilityChannel, long subSampleOffsetUs, @Nullable List<byte[]> initializationData, @Nullable DrmInitData drmInitData, @Nullable Metadata metadata) {
        this.id = id;
        this.label = label;
        this.containerMimeType = containerMimeType;
        this.sampleMimeType = sampleMimeType;
        this.codecs = codecs;
        this.bitrate = bitrate;
        this.maxInputSize = maxInputSize;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.rotationDegrees = rotationDegrees == -1 ? 0 : rotationDegrees;
        this.pixelWidthHeightRatio = pixelWidthHeightRatio == -1.0F ? 1.0F : pixelWidthHeightRatio;
        this.projectionData = projectionData;
        this.stereoMode = stereoMode;
        this.colorInfo = colorInfo;
        this.channelCount = channelCount;
        this.sampleRate = sampleRate;
        this.pcmEncoding = pcmEncoding;
        this.encoderDelay = encoderDelay == -1 ? 0 : encoderDelay;
        this.encoderPadding = encoderPadding == -1 ? 0 : encoderPadding;
        this.selectionFlags = selectionFlags;
        this.language = language;
        this.accessibilityChannel = accessibilityChannel;
        this.subSampleOffsetUs = subSampleOffsetUs;
        this.initializationData = initializationData == null ? Collections.emptyList() : initializationData;
        this.drmInitData = drmInitData;
        this.metadata = metadata;
    }

    Format(Parcel in) {
        this.id = in.readString();
        this.label = in.readString();
        this.containerMimeType = in.readString();
        this.sampleMimeType = in.readString();
        this.codecs = in.readString();
        this.bitrate = in.readInt();
        this.maxInputSize = in.readInt();
        this.width = in.readInt();
        this.height = in.readInt();
        this.frameRate = in.readFloat();
        this.rotationDegrees = in.readInt();
        this.pixelWidthHeightRatio = in.readFloat();
        boolean hasProjectionData = Util.readBoolean(in);
        this.projectionData = hasProjectionData ? in.createByteArray() : null;
        this.stereoMode = in.readInt();
        this.colorInfo = in.readParcelable(ColorInfo.class.getClassLoader());
        this.channelCount = in.readInt();
        this.sampleRate = in.readInt();
        this.pcmEncoding = in.readInt();
        this.encoderDelay = in.readInt();
        this.encoderPadding = in.readInt();
        this.selectionFlags = in.readInt();
        this.language = in.readString();
        this.accessibilityChannel = in.readInt();
        this.subSampleOffsetUs = in.readLong();
        int initializationDataSize = in.readInt();
        this.initializationData = new ArrayList<>(initializationDataSize);

        for(int i = 0; i < initializationDataSize; ++i) {
            this.initializationData.add(in.createByteArray());
        }

        this.drmInitData = in.readParcelable(DrmInitData.class.getClassLoader());
        this.metadata = in.readParcelable(Metadata.class.getClassLoader());
    }

    public Format copyWithMaxInputSize(int maxInputSize) {
        return new Format(this.id, this.label, this.containerMimeType, this.sampleMimeType, this.codecs, this.bitrate, maxInputSize, this.width, this.height, this.frameRate, this.rotationDegrees, this.pixelWidthHeightRatio, this.projectionData, this.stereoMode, this.colorInfo, this.channelCount, this.sampleRate, this.pcmEncoding, this.encoderDelay, this.encoderPadding, this.selectionFlags, this.language, this.accessibilityChannel, this.subSampleOffsetUs, this.initializationData, this.drmInitData, this.metadata);
    }

    public Format copyWithSubSampleOffsetUs(long subSampleOffsetUs) {
        return new Format(this.id, this.label, this.containerMimeType, this.sampleMimeType, this.codecs, this.bitrate, this.maxInputSize, this.width, this.height, this.frameRate, this.rotationDegrees, this.pixelWidthHeightRatio, this.projectionData, this.stereoMode, this.colorInfo, this.channelCount, this.sampleRate, this.pcmEncoding, this.encoderDelay, this.encoderPadding, this.selectionFlags, this.language, this.accessibilityChannel, subSampleOffsetUs, this.initializationData, this.drmInitData, this.metadata);
    }

    public Format copyWithManifestFormatInfo(Format manifestFormat) {
        if (this == manifestFormat) {
            return this;
        } else {
            int trackType = MimeTypes.getTrackType(this.sampleMimeType);
            String id = manifestFormat.id;
            String label = manifestFormat.label != null ? manifestFormat.label : this.label;
            String language = this.language;
            if ((trackType == 3 || trackType == 1) && manifestFormat.language != null) {
                language = manifestFormat.language;
            }

            int bitrate = this.bitrate == -1 ? manifestFormat.bitrate : this.bitrate;
            String codecs = this.codecs;
            if (codecs == null) {
                String codecsOfType = Util.getCodecsOfType(manifestFormat.codecs, trackType);
                if (Util.splitCodecs(codecsOfType).length == 1) {
                    codecs = codecsOfType;
                }
            }

            float frameRate = this.frameRate;
            if (frameRate == -1.0F && trackType == 2) {
                frameRate = manifestFormat.frameRate;
            }

            int selectionFlags = this.selectionFlags | manifestFormat.selectionFlags;
            DrmInitData drmInitData = DrmInitData.createSessionCreationData(manifestFormat.drmInitData, this.drmInitData);
            return new Format(id, label, this.containerMimeType, this.sampleMimeType, codecs, bitrate, this.maxInputSize, this.width, this.height, frameRate, this.rotationDegrees, this.pixelWidthHeightRatio, this.projectionData, this.stereoMode, this.colorInfo, this.channelCount, this.sampleRate, this.pcmEncoding, this.encoderDelay, this.encoderPadding, selectionFlags, language, this.accessibilityChannel, this.subSampleOffsetUs, this.initializationData, drmInitData, this.metadata);
        }
    }

    public Format copyWithGapLessInfo(int encoderDelay, int encoderPadding) {
        return new Format(this.id, this.label, this.containerMimeType, this.sampleMimeType, this.codecs, this.bitrate, this.maxInputSize, this.width, this.height, this.frameRate, this.rotationDegrees, this.pixelWidthHeightRatio, this.projectionData, this.stereoMode, this.colorInfo, this.channelCount, this.sampleRate, this.pcmEncoding, encoderDelay, encoderPadding, this.selectionFlags, this.language, this.accessibilityChannel, this.subSampleOffsetUs, this.initializationData, this.drmInitData, this.metadata);
    }

    public Format copyWithDrmInitData(@Nullable DrmInitData drmInitData) {
        return new Format(this.id, this.label, this.containerMimeType, this.sampleMimeType, this.codecs, this.bitrate, this.maxInputSize, this.width, this.height, this.frameRate, this.rotationDegrees, this.pixelWidthHeightRatio, this.projectionData, this.stereoMode, this.colorInfo, this.channelCount, this.sampleRate, this.pcmEncoding, this.encoderDelay, this.encoderPadding, this.selectionFlags, this.language, this.accessibilityChannel, this.subSampleOffsetUs, this.initializationData, drmInitData, this.metadata);
    }

    public Format copyWithMetadata(@Nullable Metadata metadata) {
        return new Format(this.id, this.label, this.containerMimeType, this.sampleMimeType, this.codecs, this.bitrate, this.maxInputSize, this.width, this.height, this.frameRate, this.rotationDegrees, this.pixelWidthHeightRatio, this.projectionData, this.stereoMode, this.colorInfo, this.channelCount, this.sampleRate, this.pcmEncoding, this.encoderDelay, this.encoderPadding, this.selectionFlags, this.language, this.accessibilityChannel, this.subSampleOffsetUs, this.initializationData, this.drmInitData, metadata);
    }

    public Format copyWithRotationDegrees(int rotationDegrees) {
        return new Format(this.id, this.label, this.containerMimeType, this.sampleMimeType, this.codecs, this.bitrate, this.maxInputSize, this.width, this.height, this.frameRate, rotationDegrees, this.pixelWidthHeightRatio, this.projectionData, this.stereoMode, this.colorInfo, this.channelCount, this.sampleRate, this.pcmEncoding, this.encoderDelay, this.encoderPadding, this.selectionFlags, this.language, this.accessibilityChannel, this.subSampleOffsetUs, this.initializationData, this.drmInitData, this.metadata);
    }

    public int getPixelCount() {
        return this.width != -1 && this.height != -1 ? this.width * this.height : -1;
    }

    public String toString() {
        return "Format(" + this.id + ", " + this.label + ", " + this.containerMimeType + ", " + this.sampleMimeType + ", " + this.codecs + ", " + this.bitrate + ", " + this.language + ", [" + this.width + ", " + this.height + ", " + this.frameRate + "], [" + this.channelCount + ", " + this.sampleRate + "])";
    }

    public int hashCode() {
        if (this.hashCode == 0) {
            int result = 17;
            result = 31 * result + (this.id == null ? 0 : this.id.hashCode());
            result = 31 * result + (this.containerMimeType == null ? 0 : this.containerMimeType.hashCode());
            result = 31 * result + (this.sampleMimeType == null ? 0 : this.sampleMimeType.hashCode());
            result = 31 * result + (this.codecs == null ? 0 : this.codecs.hashCode());
            result = 31 * result + this.bitrate;
            result = 31 * result + this.width;
            result = 31 * result + this.height;
            result = 31 * result + this.channelCount;
            result = 31 * result + this.sampleRate;
            result = 31 * result + (this.language == null ? 0 : this.language.hashCode());
            result = 31 * result + this.accessibilityChannel;
            result = 31 * result + (this.drmInitData == null ? 0 : this.drmInitData.hashCode());
            result = 31 * result + (this.metadata == null ? 0 : this.metadata.hashCode());
            result = 31 * result + (this.label != null ? this.label.hashCode() : 0);
            result = 31 * result + this.maxInputSize;
            result = 31 * result + (int)this.subSampleOffsetUs;
            result = 31 * result + Float.floatToIntBits(this.frameRate);
            result = 31 * result + Float.floatToIntBits(this.pixelWidthHeightRatio);
            result = 31 * result + this.rotationDegrees;
            result = 31 * result + this.stereoMode;
            result = 31 * result + this.pcmEncoding;
            result = 31 * result + this.encoderDelay;
            result = 31 * result + this.encoderPadding;
            result = 31 * result + this.selectionFlags;
            this.hashCode = result;
        }

        return this.hashCode;
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            Format other = (Format)obj;
            if (this.hashCode != 0 && other.hashCode != 0 && this.hashCode != other.hashCode) {
                return false;
            } else {
                return this.bitrate == other.bitrate && this.maxInputSize == other.maxInputSize && this.width == other.width && this.height == other.height && Float.compare(this.frameRate, other.frameRate) == 0 && this.rotationDegrees == other.rotationDegrees && Float.compare(this.pixelWidthHeightRatio, other.pixelWidthHeightRatio) == 0 && this.stereoMode == other.stereoMode && this.channelCount == other.channelCount && this.sampleRate == other.sampleRate && this.pcmEncoding == other.pcmEncoding && this.encoderDelay == other.encoderDelay && this.encoderPadding == other.encoderPadding && this.subSampleOffsetUs == other.subSampleOffsetUs && this.selectionFlags == other.selectionFlags && Util.areEqual(this.id, other.id) && Util.areEqual(this.label, other.label) && Util.areEqual(this.language, other.language) && this.accessibilityChannel == other.accessibilityChannel && Util.areEqual(this.containerMimeType, other.containerMimeType) && Util.areEqual(this.sampleMimeType, other.sampleMimeType) && Util.areEqual(this.codecs, other.codecs) && Util.areEqual(this.drmInitData, other.drmInitData) && Util.areEqual(this.metadata, other.metadata) && Util.areEqual(this.colorInfo, other.colorInfo) && Arrays.equals(this.projectionData, other.projectionData) && this.initializationDataEquals(other);
            }
        } else {
            return false;
        }
    }

    public boolean initializationDataEquals(Format other) {
        if (this.initializationData.size() != other.initializationData.size()) {
            return false;
        } else {
            for(int i = 0; i < this.initializationData.size(); ++i) {
                if (!Arrays.equals(this.initializationData.get(i), other.initializationData.get(i))) {
                    return false;
                }
            }

            return true;
        }
    }

    public static String toLogString(@Nullable Format format) {
        if (format == null) {
            return "null";
        } else {
            StringBuilder builder = new StringBuilder();
            builder.append("id=").append(format.id).append(", mimeType=").append(format.sampleMimeType);
            if (format.bitrate != -1) {
                builder.append(", bitrate=").append(format.bitrate);
            }

            if (format.codecs != null) {
                builder.append(", codecs=").append(format.codecs);
            }

            if (format.width != -1 && format.height != -1) {
                builder.append(", res=").append(format.width).append("x").append(format.height);
            }

            if (format.frameRate != -1.0F) {
                builder.append(", fps=").append(format.frameRate);
            }

            if (format.channelCount != -1) {
                builder.append(", channels=").append(format.channelCount);
            }

            if (format.sampleRate != -1) {
                builder.append(", sample_rate=").append(format.sampleRate);
            }

            if (format.language != null) {
                builder.append(", language=").append(format.language);
            }

            if (format.label != null) {
                builder.append(", label=").append(format.label);
            }

            return builder.toString();
        }
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.id);
        dest.writeString(this.label);
        dest.writeString(this.containerMimeType);
        dest.writeString(this.sampleMimeType);
        dest.writeString(this.codecs);
        dest.writeInt(this.bitrate);
        dest.writeInt(this.maxInputSize);
        dest.writeInt(this.width);
        dest.writeInt(this.height);
        dest.writeFloat(this.frameRate);
        dest.writeInt(this.rotationDegrees);
        dest.writeFloat(this.pixelWidthHeightRatio);
        Util.writeBoolean(dest, this.projectionData != null);
        if (this.projectionData != null) {
            dest.writeByteArray(this.projectionData);
        }

        dest.writeInt(this.stereoMode);
        dest.writeParcelable(this.colorInfo, flags);
        dest.writeInt(this.channelCount);
        dest.writeInt(this.sampleRate);
        dest.writeInt(this.pcmEncoding);
        dest.writeInt(this.encoderDelay);
        dest.writeInt(this.encoderPadding);
        dest.writeInt(this.selectionFlags);
        dest.writeString(this.language);
        dest.writeInt(this.accessibilityChannel);
        dest.writeLong(this.subSampleOffsetUs);
        int initializationDataSize = this.initializationData.size();
        dest.writeInt(initializationDataSize);

        for(int i = 0; i < initializationDataSize; ++i) {
            dest.writeByteArray(this.initializationData.get(i));
        }

        dest.writeParcelable(this.drmInitData, 0);
        dest.writeParcelable(this.metadata, 0);
    }
}
