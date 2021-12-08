package com.zj.playerLib.extractor.ts;

import android.util.SparseArray;

import com.zj.playerLib.Format;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.ts.TsPayloadReader.EsInfo;
import com.zj.playerLib.extractor.ts.TsPayloadReader.Factory;
import com.zj.playerLib.text.cea.Cea708InitializationData;
import com.zj.playerLib.util.ParsableByteArray;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DefaultTsPayloadReaderFactory implements Factory {
    public static final int FLAG_ALLOW_NON_IDR_KEYFRAMES = 1;
    public static final int FLAG_IGNORE_AAC_STREAM = 2;
    public static final int FLAG_IGNORE_H264_STREAM = 4;
    public static final int FLAG_DETECT_ACCESS_UNITS = 8;
    public static final int FLAG_IGNORE_SPLICE_INFO_STREAM = 16;
    public static final int FLAG_OVERRIDE_CAPTION_DESCRIPTORS = 32;
    private static final int DESCRIPTOR_TAG_CAPTION_SERVICE = 134;
    private final int flags;
    private final List<Format> closedCaptionFormats;

    public DefaultTsPayloadReaderFactory() {
        this(0);
    }

    public DefaultTsPayloadReaderFactory(int flags) {
        this(flags, Collections.singletonList(Format.createTextSampleFormat(null, "application/cea-608", 0, null)));
    }

    public DefaultTsPayloadReaderFactory(int flags, List<Format> closedCaptionFormats) {
        this.flags = flags;
        this.closedCaptionFormats = closedCaptionFormats;
    }

    public SparseArray<TsPayloadReader> createInitialPayloadReaders() {
        return new SparseArray();
    }

    public TsPayloadReader createPayloadReader(int streamType, EsInfo esInfo) {
        switch(streamType) {
        case 2:
            return new PesReader(new H262Reader(this.buildUserDataReader(esInfo)));
        case 3:
        case 4:
            return new PesReader(new MpegAudioReader(esInfo.language));
        case 15:
            return this.isSet(2) ? null : new PesReader(new AdtsReader(false, esInfo.language));
        case 17:
            return this.isSet(2) ? null : new PesReader(new LatmReader(esInfo.language));
        case 21:
            return new PesReader(new Id3Reader());
        case 27:
            return this.isSet(4) ? null : new PesReader(new H264Reader(this.buildSeiReader(esInfo), this.isSet(1), this.isSet(8)));
        case 36:
            return new PesReader(new H265Reader(this.buildSeiReader(esInfo)));
        case 89:
            return new PesReader(new DvbSubtitleReader(esInfo.dvbSubtitleInfos));
        case 129:
        case 135:
            return new PesReader(new Ac3Reader(esInfo.language));
        case 130:
        case 138:
            return new PesReader(new DtsReader(esInfo.language));
        case 134:
            return this.isSet(16) ? null : new SectionReader(new SpliceInfoSectionReader());
        default:
            return null;
        }
    }

    private SeiReader buildSeiReader(EsInfo esInfo) {
        return new SeiReader(this.getClosedCaptionFormats(esInfo));
    }

    private UserDataReader buildUserDataReader(EsInfo esInfo) {
        return new UserDataReader(this.getClosedCaptionFormats(esInfo));
    }

    private List<Format> getClosedCaptionFormats(EsInfo esInfo) {
        if (this.isSet(32)) {
            return this.closedCaptionFormats;
        } else {
            ParsableByteArray scratchDescriptorData = new ParsableByteArray(esInfo.descriptorBytes);

            Object closedCaptionFormats;
            int nextDescriptorPosition;
            for(closedCaptionFormats = this.closedCaptionFormats; scratchDescriptorData.bytesLeft() > 0; scratchDescriptorData.setPosition(nextDescriptorPosition)) {
                int descriptorTag = scratchDescriptorData.readUnsignedByte();
                int descriptorLength = scratchDescriptorData.readUnsignedByte();
                nextDescriptorPosition = scratchDescriptorData.getPosition() + descriptorLength;
                if (descriptorTag == 134) {
                    closedCaptionFormats = new ArrayList();
                    int numberOfServices = scratchDescriptorData.readUnsignedByte() & 31;

                    for(int i = 0; i < numberOfServices; ++i) {
                        String language = scratchDescriptorData.readString(3);
                        int captionTypeByte = scratchDescriptorData.readUnsignedByte();
                        boolean isDigital = (captionTypeByte & 128) != 0;
                        String mimeType;
                        int accessibilityChannel;
                        if (isDigital) {
                            mimeType = "application/cea-708";
                            accessibilityChannel = captionTypeByte & 63;
                        } else {
                            mimeType = "application/cea-608";
                            accessibilityChannel = 1;
                        }

                        byte flags = (byte)scratchDescriptorData.readUnsignedByte();
                        scratchDescriptorData.skipBytes(1);
                        List<byte[]> initializationData = null;
                        if (isDigital) {
                            boolean isWideAspectRatio = (flags & 64) != 0;
                            initializationData = Cea708InitializationData.buildData(isWideAspectRatio);
                        }

                        ((List)closedCaptionFormats).add(Format.createTextSampleFormat(null, mimeType, null, -1, 0, language, accessibilityChannel, null, Long.MAX_VALUE, initializationData));
                    }
                }
            }

            return (List)closedCaptionFormats;
        }
    }

    private boolean isSet(int flag) {
        return (this.flags & flag) != 0;
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface Flags {
    }
}
