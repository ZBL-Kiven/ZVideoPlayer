package com.zj.playerLib.scheduler;

public interface Scheduler {
    boolean DEBUG = false;

    boolean schedule(Requirements var1, String var2, String var3);

    boolean cancel();
}
