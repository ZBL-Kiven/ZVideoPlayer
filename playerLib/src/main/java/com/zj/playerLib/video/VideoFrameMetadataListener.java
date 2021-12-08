package com.zj.playerLib.video;

import com.zj.playerLib.Format;

public interface VideoFrameMetadataListener {
    void onVideoFrameAboutToBeRendered(long var1, long var3, Format var5);
}
