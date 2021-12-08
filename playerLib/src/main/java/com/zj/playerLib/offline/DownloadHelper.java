package com.zj.playerLib.offline;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.zj.playerLib.source.TrackGroupArray;

import java.io.IOException;
import java.util.List;

public abstract class DownloadHelper {
    public DownloadHelper() {
    }

    public void prepare(final Callback callback) {
        final Handler handler = new Handler(Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper());
        (new Thread() {
            public void run() {
                try {
                    DownloadHelper.this.prepareInternal();
                    handler.post(() -> {
                        callback.onPrepared(DownloadHelper.this);
                    });
                } catch (IOException var2) {
                    handler.post(() -> {
                        callback.onPrepareError(DownloadHelper.this, var2);
                    });
                }

            }
        }).start();
    }

    protected abstract void prepareInternal() throws IOException;

    public abstract int getPeriodCount();

    public abstract TrackGroupArray getTrackGroups(int var1);

    public abstract DownloadAction getDownloadAction(@Nullable byte[] var1, List<TrackKey> var2);

    public abstract DownloadAction getRemoveAction(@Nullable byte[] var1);

    public interface Callback {
        void onPrepared(DownloadHelper var1);

        void onPrepareError(DownloadHelper var1, IOException var2);
    }
}
