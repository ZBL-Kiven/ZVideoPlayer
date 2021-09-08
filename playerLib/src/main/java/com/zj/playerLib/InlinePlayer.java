//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib;

import android.os.Looper;

import androidx.annotation.Nullable;

import com.zj.playerLib.PlayerMessage.Target;
import com.zj.playerLib.source.MediaSource;

public interface InlinePlayer extends Player {

    Looper getPlaybackLooper();

    void retry();

    void prepare(MediaSource var1);

    void prepare(MediaSource var1, boolean var2, boolean var3);

    PlayerMessage createMessage(Target var1);

    void setSeekParameters(@Nullable SeekParameters var1);

    SeekParameters getSeekParameters();
}
