package com.zj.playerLib;

public interface ControlDispatcher {
    boolean dispatchSetPlayWhenReady(Player var1, boolean var2);

    boolean dispatchSeekTo(Player var1, int var2, long var3);

    boolean dispatchSetRepeatMode(Player var1, int var2);

    boolean dispatchSetShuffleModeEnabled(Player var1, boolean var2);

    boolean dispatchStop(Player var1, boolean var2);
}
