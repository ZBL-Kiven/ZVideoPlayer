//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.util;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;

import androidx.annotation.Nullable;

final class SystemClock implements Clock {
    SystemClock() {
    }

    public long elapsedRealtime() {
        return android.os.SystemClock.elapsedRealtime();
    }

    public long uptimeMillis() {
        return android.os.SystemClock.uptimeMillis();
    }

    public void sleep(long sleepTimeMs) {
        android.os.SystemClock.sleep(sleepTimeMs);
    }

    public HandlerWrapper createHandler(Looper looper, @Nullable Callback callback) {
        return new SystemHandlerWrapper(new Handler(looper, callback));
    }
}
