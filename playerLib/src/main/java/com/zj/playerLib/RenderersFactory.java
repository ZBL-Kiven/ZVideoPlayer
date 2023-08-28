package com.zj.playerLib;

import android.os.Handler;

import androidx.annotation.Nullable;

import com.zj.playerLib.audio.AudioRendererEventListener;
import com.zj.playerLib.drm.DrmSessionManager;
import com.zj.playerLib.drm.FrameworkMediaCrypto;
import com.zj.playerLib.metadata.MetadataOutput;
import com.zj.playerLib.text.TextOutput;
import com.zj.playerLib.video.VideoRendererEventListener;

public interface RenderersFactory {
    Renderer[] createRenderers(Handler var1, VideoRendererEventListener var2, AudioRendererEventListener var3, TextOutput var4, MetadataOutput var5, @Nullable DrmSessionManager<FrameworkMediaCrypto> var6);
}
