package com.zj.playerLib.upstream;

import java.io.IOException;

public interface LoadErrorHandlingPolicy {
    long getBlacklistDurationMsFor(int var1, long var2, IOException var4, int var5);

    long getRetryDelayMsFor(int var1, long var2, IOException var4, int var5);

    int getMinimumLoadableRetryCount(int var1);
}
