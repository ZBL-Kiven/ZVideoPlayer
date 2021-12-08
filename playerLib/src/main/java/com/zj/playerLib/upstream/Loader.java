package com.zj.playerLib.upstream;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.TraceUtil;
import com.zj.playerLib.util.Util;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public final class Loader implements LoaderErrorThrower {
    private static final int ACTION_TYPE_RETRY = 0;
    private static final int ACTION_TYPE_RETRY_AND_RESET_ERROR_COUNT = 1;
    private static final int ACTION_TYPE_DONT_RETRY = 2;
    private static final int ACTION_TYPE_DONT_RETRY_FATAL = 3;
    public static final LoadErrorAction RETRY = createRetryAction(false, -Long.MAX_VALUE);
    public static final LoadErrorAction RETRY_RESET_ERROR_COUNT = createRetryAction(true, -Long.MAX_VALUE);
    public static final LoadErrorAction DONT_RETRY = new LoadErrorAction(2, -Long.MAX_VALUE);
    public static final LoadErrorAction DONT_RETRY_FATAL = new LoadErrorAction(3, -Long.MAX_VALUE);
    private final ExecutorService downloadExecutorService;
    private LoadTask<? extends Loadable> currentTask;
    private IOException fatalError;

    public Loader(String threadName) {
        this.downloadExecutorService = Util.newSingleThreadExecutor(threadName);
    }

    public static LoadErrorAction createRetryAction(boolean resetErrorCount, long retryDelayMillis) {
        return new LoadErrorAction(resetErrorCount ? 1 : 0, retryDelayMillis);
    }

    public <T extends Loadable> long startLoading(T loadable, Callback<T> callback, int defaultMinRetryCount) {
        Looper looper = Looper.myLooper();
        Assertions.checkState(looper != null);
        this.fatalError = null;
        long startTimeMs = SystemClock.elapsedRealtime();
        (new LoadTask(looper, loadable, callback, defaultMinRetryCount, startTimeMs)).start(0L);
        return startTimeMs;
    }

    public boolean isLoading() {
        return this.currentTask != null;
    }

    public void cancelLoading() {
        this.currentTask.cancel(false);
    }

    public void release() {
        this.release(null);
    }

    public void release(@Nullable Loader.ReleaseCallback callback) {
        if (this.currentTask != null) {
            this.currentTask.cancel(true);
        }

        if (callback != null) {
            this.downloadExecutorService.execute(new ReleaseTask(callback));
        }

        this.downloadExecutorService.shutdown();
    }

    public void maybeThrowError() throws IOException {
        this.maybeThrowError(-2147483648);
    }

    public void maybeThrowError(int minRetryCount) throws IOException {
        if (this.fatalError != null) {
            throw this.fatalError;
        } else {
            if (this.currentTask != null) {
                this.currentTask.maybeThrowError(minRetryCount == -2147483648 ? this.currentTask.defaultMinRetryCount : minRetryCount);
            }

        }
    }

    private static final class ReleaseTask implements Runnable {
        private final ReleaseCallback callback;

        public ReleaseTask(ReleaseCallback callback) {
            this.callback = callback;
        }

        public void run() {
            this.callback.onLoaderReleased();
        }
    }

    @SuppressLint({"HandlerLeak"})
    private final class LoadTask<T extends Loadable> extends Handler implements Runnable {
        private static final String TAG = "LoadTask";
        private static final int MSG_START = 0;
        private static final int MSG_CANCEL = 1;
        private static final int MSG_END_OF_SOURCE = 2;
        private static final int MSG_IO_EXCEPTION = 3;
        private static final int MSG_FATAL_ERROR = 4;
        public final int defaultMinRetryCount;
        private final T loadable;
        private final long startTimeMs;
        @Nullable
        private Loader.Callback<T> callback;
        private IOException currentError;
        private int errorCount;
        private volatile Thread executorThread;
        private volatile boolean canceled;
        private volatile boolean released;

        public LoadTask(Looper looper, T loadable, Loader.Callback<T> callback, int defaultMinRetryCount, long startTimeMs) {
            super(looper);
            this.loadable = loadable;
            this.callback = callback;
            this.defaultMinRetryCount = defaultMinRetryCount;
            this.startTimeMs = startTimeMs;
        }

        public void maybeThrowError(int minRetryCount) throws IOException {
            if (this.currentError != null && this.errorCount > minRetryCount) {
                throw this.currentError;
            }
        }

        public void start(long delayMillis) {
            Assertions.checkState(Loader.this.currentTask == null);
            Loader.this.currentTask = this;
            if (delayMillis > 0L) {
                this.sendEmptyMessageDelayed(0, delayMillis);
            } else {
                this.execute();
            }

        }

        public void cancel(boolean released) {
            this.released = released;
            this.currentError = null;
            if (this.hasMessages(0)) {
                this.removeMessages(0);
                if (!released) {
                    this.sendEmptyMessage(1);
                }
            } else {
                this.canceled = true;
                this.loadable.cancelLoad();
                if (this.executorThread != null) {
                    this.executorThread.interrupt();
                }
            }

            if (released) {
                this.finish();
                long nowMs = SystemClock.elapsedRealtime();
                this.callback.onLoadCanceled(this.loadable, nowMs, nowMs - this.startTimeMs, true);
                this.callback = null;
            }

        }

        public void run() {
            try {
                this.executorThread = Thread.currentThread();
                if (!this.canceled) {
                    TraceUtil.beginSection("load:" + this.loadable.getClass().getSimpleName());

                    try {
                        this.loadable.load();
                    } finally {
                        TraceUtil.endSection();
                    }
                }

                if (!this.released) {
                    this.sendEmptyMessage(2);
                }
            } catch (IOException var9) {
                if (!this.released) {
                    this.obtainMessage(3, var9).sendToTarget();
                }
            } catch (InterruptedException var10) {
                Assertions.checkState(this.canceled);
                if (!this.released) {
                    this.sendEmptyMessage(2);
                }
            } catch (Exception var11) {
                Log.e("LoadTask", "Unexpected exception loading stream", var11);
                if (!this.released) {
                    this.obtainMessage(3, new UnexpectedLoaderException(var11)).sendToTarget();
                }
            } catch (OutOfMemoryError var12) {
                Log.e("LoadTask", "OutOfMemory error loading stream", var12);
                if (!this.released) {
                    this.obtainMessage(3, new UnexpectedLoaderException(var12)).sendToTarget();
                }
            } catch (Error var13) {
                Log.e("LoadTask", "Unexpected error loading stream", var13);
                if (!this.released) {
                    this.obtainMessage(4, var13).sendToTarget();
                }

                throw var13;
            }

        }

        public void handleMessage(Message msg) {
            if (!this.released) {
                if (msg.what == 0) {
                    this.execute();
                } else if (msg.what == 4) {
                    throw (Error)msg.obj;
                } else {
                    this.finish();
                    long nowMs = SystemClock.elapsedRealtime();
                    long durationMs = nowMs - this.startTimeMs;
                    if (this.canceled) {
                        this.callback.onLoadCanceled(this.loadable, nowMs, durationMs, false);
                    } else {
                        switch(msg.what) {
                        case 1:
                            this.callback.onLoadCanceled(this.loadable, nowMs, durationMs, false);
                            break;
                        case 2:
                            try {
                                this.callback.onLoadCompleted(this.loadable, nowMs, durationMs);
                            } catch (RuntimeException var7) {
                                Log.e("LoadTask", "Unexpected exception handling load completed", var7);
                                Loader.this.fatalError = new UnexpectedLoaderException(var7);
                            }
                            break;
                        case 3:
                            this.currentError = (IOException)msg.obj;
                            ++this.errorCount;
                            LoadErrorAction action = this.callback.onLoadError(this.loadable, nowMs, durationMs, this.currentError, this.errorCount);
                            if (action.type == 3) {
                                Loader.this.fatalError = this.currentError;
                            } else if (action.type != 2) {
                                if (action.type == 1) {
                                    this.errorCount = 1;
                                }

                                this.start(action.retryDelayMillis != -Long.MAX_VALUE ? action.retryDelayMillis : this.getRetryDelayMillis());
                            }
                        }

                    }
                }
            }
        }

        private void execute() {
            this.currentError = null;
            Loader.this.downloadExecutorService.execute(Loader.this.currentTask);
        }

        private void finish() {
            Loader.this.currentTask = null;
        }

        private long getRetryDelayMillis() {
            return Math.min((this.errorCount - 1) * 1000, 5000);
        }
    }

    public static final class LoadErrorAction {
        private final int type;
        private final long retryDelayMillis;

        private LoadErrorAction(int type, long retryDelayMillis) {
            this.type = type;
            this.retryDelayMillis = retryDelayMillis;
        }

        public boolean isRetry() {
            return this.type == 0 || this.type == 1;
        }
    }

    public interface ReleaseCallback {
        void onLoaderReleased();
    }

    public interface Callback<T extends Loadable> {
        void onLoadCompleted(T var1, long var2, long var4);

        void onLoadCanceled(T var1, long var2, long var4, boolean var6);

        LoadErrorAction onLoadError(T var1, long var2, long var4, IOException var6, int var7);
    }

    public interface Loadable {
        void cancelLoad();

        void load() throws IOException, InterruptedException;
    }

    public static final class UnexpectedLoaderException extends IOException {
        public UnexpectedLoaderException(Throwable cause) {
            super("Unexpected " + cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
        }
    }
}
