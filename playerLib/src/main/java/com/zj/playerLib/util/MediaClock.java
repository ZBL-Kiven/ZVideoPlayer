//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.util;

import com.zj.playerLib.PlaybackParameters;

public interface MediaClock {
    long getPositionUs();

    PlaybackParameters setPlaybackParameters(PlaybackParameters var1);

    PlaybackParameters getPlaybackParameters();
}
