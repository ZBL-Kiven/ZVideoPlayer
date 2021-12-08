package com.zj.playerLib.mediacodec;

import androidx.annotation.Nullable;

import com.zj.playerLib.mediacodec.MediaCodecUtil.DecoderQueryException;

import java.util.Collections;
import java.util.List;

public interface MediaCodecSelector {
    MediaCodecSelector DEFAULT = new MediaCodecSelector() {
        public List<MediaCodecInfo> getDecoderInfos(String mimeType, boolean requiresSecureDecoder) throws DecoderQueryException {
            List<MediaCodecInfo> decoderInfos = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder);
            return decoderInfos.isEmpty() ? Collections.emptyList() : Collections.singletonList(decoderInfos.get(0));
        }

        @Nullable
        public MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException {
            return MediaCodecUtil.getPassthroughDecoderInfo();
        }
    };
    MediaCodecSelector DEFAULT_WITH_FALLBACK = new MediaCodecSelector() {
        public List<MediaCodecInfo> getDecoderInfos(String mimeType, boolean requiresSecureDecoder) throws DecoderQueryException {
            return MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder);
        }

        @Nullable
        public MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException {
            return MediaCodecUtil.getPassthroughDecoderInfo();
        }
    };

    List<MediaCodecInfo> getDecoderInfos(String var1, boolean var2) throws DecoderQueryException;

    @Nullable
    MediaCodecInfo getPassthroughDecoderInfo() throws DecoderQueryException;
}
