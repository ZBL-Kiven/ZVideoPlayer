//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.util;

import android.os.Handler.Callback;
import android.os.Looper;

import androidx.annotation.Nullable;

public interface Clock {
    Clock DEFAULT = new SystemClock();

    long elapsedRealtime();

    long uptimeMillis();

    void sleep(long var1);

    HandlerWrapper createHandler(Looper var1, @Nullable Callback var2);
}
