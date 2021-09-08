//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib;

public interface RendererCapabilities {
    int FORMAT_SUPPORT_MASK = 7;
    int FORMAT_HANDLED = 4;
    int FORMAT_EXCEEDS_CAPABILITIES = 3;
    int FORMAT_UNSUPPORTED_DRM = 2;
    int FORMAT_UNSUPPORTED_SUBTYPE = 1;
    int FORMAT_UNSUPPORTED_TYPE = 0;
    int ADAPTIVE_SUPPORT_MASK = 24;
    int ADAPTIVE_SEAMLESS = 16;
    int ADAPTIVE_NOT_SEAMLESS = 8;
    int ADAPTIVE_NOT_SUPPORTED = 0;
    int TUNNELING_SUPPORT_MASK = 32;
    int TUNNELING_SUPPORTED = 32;
    int TUNNELING_NOT_SUPPORTED = 0;

    int getTrackType();

    int supportsFormat(Format var1) throws PlaybackException;

    int supportsMixedMimeTypeAdaptation() throws PlaybackException;
}
