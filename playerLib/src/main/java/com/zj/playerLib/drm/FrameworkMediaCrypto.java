package com.zj.playerLib.drm;

import android.annotation.TargetApi;

import com.zj.playerLib.util.Assertions;

@TargetApi(16)
public final class FrameworkMediaCrypto implements MediaCrypto {
    private final android.media.MediaCrypto mediaCrypto;
    private final boolean forceAllowInsecureDecoderComponents;

    public FrameworkMediaCrypto(android.media.MediaCrypto mediaCrypto) {
        this(mediaCrypto, false);
    }

    public FrameworkMediaCrypto(android.media.MediaCrypto mediaCrypto, boolean forceAllowInsecureDecoderComponents) {
        this.mediaCrypto = Assertions.checkNotNull(mediaCrypto);
        this.forceAllowInsecureDecoderComponents = forceAllowInsecureDecoderComponents;
    }

    public android.media.MediaCrypto getWrappedMediaCrypto() {
        return this.mediaCrypto;
    }

    public boolean requiresSecureDecoderComponent(String mimeType) {
        return !this.forceAllowInsecureDecoderComponents && this.mediaCrypto.requiresSecureDecoderComponent(mimeType);
    }
}
