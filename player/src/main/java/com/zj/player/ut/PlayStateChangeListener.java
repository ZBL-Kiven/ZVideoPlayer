package com.zj.player.ut;

import com.zj.player.z.ZController;

public interface PlayStateChangeListener {
    void onState(boolean isPlaying, String desc, ZController controller);
}
