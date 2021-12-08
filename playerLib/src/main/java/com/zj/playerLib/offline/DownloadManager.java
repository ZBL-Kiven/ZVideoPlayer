package com.zj.playerLib.offline;

import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.zj.playerLib.offline.DownloadAction.Deserializer;
import com.zj.playerLib.upstream.DataSource.Factory;
import com.zj.playerLib.upstream.cache.Cache;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public final class DownloadManager {
    public static final int DEFAULT_MAX_SIMULTANEOUS_DOWNLOADS = 1;
    public static final int DEFAULT_MIN_RETRY_COUNT = 5;
    private static final String TAG = "DownloadManager";
    private static final boolean DEBUG = false;
    private final DownloaderConstructorHelper downloaderConstructorHelper;
    private final int maxActiveDownloadTasks;
    private final int minRetryCount;
    private final ActionFile actionFile;
    private final Deserializer[] deserializers;
    private final ArrayList<Task> tasks;
    private final ArrayList<Task> activeDownloadTasks;
    private final Handler handler;
    private final HandlerThread fileIOThread;
    private final Handler fileIOHandler;
    private final CopyOnWriteArraySet<Listener> listeners;
    private int nextTaskId;
    private boolean initialized;
    private boolean released;
    private boolean downloadsStopped;

    public DownloadManager(Cache cache, Factory upstreamDataSourceFactory, File actionSaveFile, Deserializer... deserializers) {
        this(new DownloaderConstructorHelper(cache, upstreamDataSourceFactory), actionSaveFile, deserializers);
    }

    public DownloadManager(DownloaderConstructorHelper constructorHelper, File actionFile, Deserializer... deserializers) {
        this(constructorHelper, 1, 5, actionFile, deserializers);
    }

    public DownloadManager(DownloaderConstructorHelper constructorHelper, int maxSimultaneousDownloads, int minRetryCount, File actionFile, Deserializer... deserializers) {
        this.downloaderConstructorHelper = constructorHelper;
        this.maxActiveDownloadTasks = maxSimultaneousDownloads;
        this.minRetryCount = minRetryCount;
        this.actionFile = new ActionFile(actionFile);
        this.deserializers = deserializers.length > 0 ? deserializers : DownloadAction.getDefaultDeserializers();
        this.downloadsStopped = true;
        this.tasks = new ArrayList<>();
        this.activeDownloadTasks = new ArrayList<>();
        Looper looper = Looper.myLooper();
        if (looper == null) {
            looper = Looper.getMainLooper();
        }

        this.handler = new Handler(looper);
        this.fileIOThread = new HandlerThread("DownloadManager file i/o");
        this.fileIOThread.start();
        this.fileIOHandler = new Handler(this.fileIOThread.getLooper());
        this.listeners = new CopyOnWriteArraySet<>();
        this.loadActions();
    }

    public void addListener(Listener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

    public void startDownloads() {
        Assertions.checkState(!this.released);
        if (this.downloadsStopped) {
            this.downloadsStopped = false;
            this.maybeStartTasks();
        }
    }

    public void stopDownloads() {
        Assertions.checkState(!this.released);
        if (!this.downloadsStopped) {
            this.downloadsStopped = true;

            for(int i = 0; i < this.activeDownloadTasks.size(); ++i) {
                this.activeDownloadTasks.get(i).stop();
            }
        }
    }

    public int handleAction(byte[] actionData) throws IOException {
        Assertions.checkState(!this.released);
        ByteArrayInputStream input = new ByteArrayInputStream(actionData);
        DownloadAction action = DownloadAction.deserializeFromStream(this.deserializers, input);
        return this.handleAction(action);
    }

    public int handleAction(DownloadAction action) {
        Assertions.checkState(!this.released);
        Task task = this.addTaskForAction(action);
        if (this.initialized) {
            this.saveActions();
            this.maybeStartTasks();
            if (task.currentState == 0) {
                this.notifyListenersTaskStateChange(task);
            }
        }

        return task.id;
    }

    public int getTaskCount() {
        Assertions.checkState(!this.released);
        return this.tasks.size();
    }

    public int getDownloadCount() {
        int count = 0;

        for(int i = 0; i < this.tasks.size(); ++i) {
            if (!this.tasks.get(i).action.isRemoveAction) {
                ++count;
            }
        }

        return count;
    }

    @Nullable
    public DownloadManager.TaskState getTaskState(int taskId) {
        Assertions.checkState(!this.released);

        for(int i = 0; i < this.tasks.size(); ++i) {
            Task task = this.tasks.get(i);
            if (task.id == taskId) {
                return task.getDownloadState();
            }
        }

        return null;
    }

    public TaskState[] getAllTaskStates() {
        Assertions.checkState(!this.released);
        TaskState[] states = new TaskState[this.tasks.size()];

        for(int i = 0; i < states.length; ++i) {
            states[i] = this.tasks.get(i).getDownloadState();
        }

        return states;
    }

    public boolean isInitialized() {
        Assertions.checkState(!this.released);
        return this.initialized;
    }

    public boolean isIdle() {
        Assertions.checkState(!this.released);
        if (!this.initialized) {
            return false;
        } else {
            for(int i = 0; i < this.tasks.size(); ++i) {
                if (this.tasks.get(i).isActive()) {
                    return false;
                }
            }
            return true;
        }
    }

    public void release() {
        if (!this.released) {
            this.released = true;
            for(int i = 0; i < this.tasks.size(); ++i) {
                this.tasks.get(i).stop();
            }

            ConditionVariable fileIOFinishedCondition = new ConditionVariable();
            this.fileIOHandler.post(fileIOFinishedCondition::open);
            fileIOFinishedCondition.block();
            this.fileIOThread.quit();
        }
    }

    private Task addTaskForAction(DownloadAction action) {
        Task task = new Task(this.nextTaskId++, this, action, this.minRetryCount);
        this.tasks.add(task);
        return task;
    }

    private void maybeStartTasks() {
        if (this.initialized && !this.released) {
            boolean skipDownloadActions = this.downloadsStopped || this.activeDownloadTasks.size() == this.maxActiveDownloadTasks;

            for(int i = 0; i < this.tasks.size(); ++i) {
                Task task = this.tasks.get(i);
                if (task.canStart()) {
                    DownloadAction action = task.action;
                    boolean isRemoveAction = action.isRemoveAction;
                    if (isRemoveAction || !skipDownloadActions) {
                        boolean canStartTask = true;

                        for(int j = 0; j < i; ++j) {
                            Task otherTask = this.tasks.get(j);
                            if (otherTask.action.isSameMedia(action)) {
                                if (isRemoveAction) {
                                    canStartTask = false;
                                    otherTask.cancel();
                                } else if (otherTask.action.isRemoveAction) {
                                    canStartTask = false;
                                    skipDownloadActions = true;
                                    break;
                                }
                            }
                        }

                        if (canStartTask) {
                            task.start();
                            if (!isRemoveAction) {
                                this.activeDownloadTasks.add(task);
                                skipDownloadActions = this.activeDownloadTasks.size() == this.maxActiveDownloadTasks;
                            }
                        }
                    }
                }
            }
        }
    }

    private void maybeNotifyListenersIdle() {
        if (this.isIdle()) {
            for (Listener listener : this.listeners) {
                listener.onIdle(this);
            }

        }
    }

    private void onTaskStateChange(Task task) {
        if (!this.released) {
            boolean stopped = !task.isActive();
            if (stopped) {
                this.activeDownloadTasks.remove(task);
            }

            this.notifyListenersTaskStateChange(task);
            if (task.isFinished()) {
                this.tasks.remove(task);
                this.saveActions();
            }

            if (stopped) {
                this.maybeStartTasks();
                this.maybeNotifyListenersIdle();
            }

        }
    }

    private void notifyListenersTaskStateChange(Task task) {
        TaskState taskState = task.getDownloadState();
        for (Listener listener : this.listeners) {
            listener.onTaskStateChanged(this, taskState);
        }
    }

    private void loadActions() {
        this.fileIOHandler.post(() -> {
            DownloadAction[] loadedActions;
            try {
                loadedActions = this.actionFile.load(this.deserializers);
            } catch (Throwable var3) {
                Log.e("DownloadManager", "Action file loading failed.", var3);
                loadedActions = new DownloadAction[0];
            }
            final DownloadAction[] la = loadedActions;
            this.handler.post(() -> {
                if (!this.released) {
                    List<Task> pendingTasks = new ArrayList<>(this.tasks);
                    this.tasks.clear();
                    for (DownloadAction action : la) {
                        this.addTaskForAction(action);
                    }
                    this.initialized = true;
                    for (Listener listener : this.listeners) {
                        listener.onInitialized(this);
                    }

                    if (!pendingTasks.isEmpty()) {
                        this.tasks.addAll(pendingTasks);
                        this.saveActions();
                    }
                    this.maybeStartTasks();
                    for(int i = 0; i < this.tasks.size(); ++i) {
                        Task task = this.tasks.get(i);
                        if (task.currentState == 0) {
                            this.notifyListenersTaskStateChange(task);
                        }
                    }
                }
            });
        });
    }

    private void saveActions() {
        if (!this.released) {
            DownloadAction[] actions = new DownloadAction[this.tasks.size()];

            for(int i = 0; i < this.tasks.size(); ++i) {
                actions[i] = this.tasks.get(i).action;
            }

            this.fileIOHandler.post(() -> {
                try {
                    this.actionFile.store(actions);
                } catch (IOException var3) {
                    Log.e("DownloadManager", "Persisting actions failed.", var3);
                }

            });
        }
    }

    private static final class Task implements Runnable {
        public static final int STATE_QUEUED_CANCELING = 5;
        public static final int STATE_STARTED_CANCELING = 6;
        public static final int STATE_STARTED_STOPPING = 7;
        private final int id;
        private final DownloadManager downloadManager;
        private final DownloadAction action;
        private final int minRetryCount;
        private volatile int currentState;
        private volatile Downloader downloader;
        private Thread thread;
        private Throwable error;

        private Task(int id, DownloadManager downloadManager, DownloadAction action, int minRetryCount) {
            this.id = id;
            this.downloadManager = downloadManager;
            this.action = action;
            this.currentState = 0;
            this.minRetryCount = minRetryCount;
        }

        public TaskState getDownloadState() {
            int externalState = this.getExternalState();
            return new TaskState(this.id, this.action, externalState, this.getDownloadPercentage(), this.getDownloadedBytes(), this.error);
        }

        public boolean isFinished() {
            return this.currentState == 4 || this.currentState == 2 || this.currentState == 3;
        }

        public boolean isActive() {
            return this.currentState == 5 || this.currentState == 1 || this.currentState == 7 || this.currentState == 6;
        }

        public float getDownloadPercentage() {
            return this.downloader != null ? this.downloader.getDownloadPercentage() : -1.0F;
        }

        public long getDownloadedBytes() {
            return this.downloader != null ? this.downloader.getDownloadedBytes() : 0L;
        }

        public String toString() {
            return super.toString();
        }

        private int getExternalState() {
            switch(this.currentState) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            default:
                return this.currentState;
            case 5:
                return 0;
            case 6:
            case 7:
                return 1;
            }
        }

        private void start() {
            if (this.changeStateAndNotify(0, 1)) {
                this.thread = new Thread(this);
                this.thread.start();
            }

        }

        private boolean canStart() {
            return this.currentState == 0;
        }

        private void cancel() {
            if (this.changeStateAndNotify(0, 5)) {
                this.downloadManager.handler.post(() -> {
                    this.changeStateAndNotify(5, 3);
                });
            } else if (this.changeStateAndNotify(1, 6)) {
                this.cancelDownload();
            }
        }

        private void stop() {
            if (this.changeStateAndNotify(1, 7)) {
                this.cancelDownload();
            }
        }

        private boolean changeStateAndNotify(int oldState, int newState) {
            return this.changeStateAndNotify(oldState, newState, null);
        }

        private boolean changeStateAndNotify(int oldState, int newState, Throwable error) {
            if (this.currentState != oldState) {
                return false;
            } else {
                this.currentState = newState;
                this.error = error;
                boolean isInternalState = this.currentState != this.getExternalState();
                if (!isInternalState) {
                    this.downloadManager.onTaskStateChange(this);
                }

                return true;
            }
        }

        private void cancelDownload() {
            if (this.downloader != null) {
                this.downloader.cancel();
            }

            this.thread.interrupt();
        }

        public void run() {
            Throwable error = null;

            try {
                this.downloader = this.action.createDownloader(this.downloadManager.downloaderConstructorHelper);
                if (this.action.isRemoveAction) {
                    this.downloader.remove();
                } else {
                    int errorCount = 0;
                    long errorPosition = -1L;

                    while(!Thread.interrupted()) {
                        try {
                            this.downloader.download();
                            break;
                        } catch (IOException var8) {
                            long downloadedBytes = this.downloader.getDownloadedBytes();
                            if (downloadedBytes != errorPosition) {
                                errorPosition = downloadedBytes;
                                errorCount = 0;
                            }

                            if (this.currentState == 1) {
                                ++errorCount;
                                if (errorCount <= this.minRetryCount) {
                                    Thread.sleep(this.getRetryDelayMillis(errorCount));
                                    continue;
                                }
                            }

                            throw var8;
                        }
                    }
                }
            } catch (Throwable var9) {
                error = var9;
            }
            final Throwable e = error;
            this.downloadManager.handler.post(() -> {
                if (!this.changeStateAndNotify(1, e != null ? 4 : 2, e) && !this.changeStateAndNotify(6, 3) && !this.changeStateAndNotify(7, 0)) {
                    throw new IllegalStateException();
                }
            });
        }

        private int getRetryDelayMillis(int errorCount) {
            return Math.min((errorCount - 1) * 1000, 5000);
        }

        @Documented
        @Retention(RetentionPolicy.SOURCE)
        public @interface InternalState {
        }
    }

    public static final class TaskState {
        public static final int STATE_QUEUED = 0;
        public static final int STATE_STARTED = 1;
        public static final int STATE_COMPLETED = 2;
        public static final int STATE_CANCELED = 3;
        public static final int STATE_FAILED = 4;
        public final int taskId;
        public final DownloadAction action;
        public final int state;
        public final float downloadPercentage;
        public final long downloadedBytes;
        public final Throwable error;

        public static String getStateString(int state) {
            switch(state) {
            case 0:
                return "QUEUED";
            case 1:
                return "STARTED";
            case 2:
                return "COMPLETED";
            case 3:
                return "CANCELED";
            case 4:
                return "FAILED";
            default:
                throw new IllegalStateException();
            }
        }

        private TaskState(int taskId, DownloadAction action, int state, float downloadPercentage, long downloadedBytes, Throwable error) {
            this.taskId = taskId;
            this.action = action;
            this.state = state;
            this.downloadPercentage = downloadPercentage;
            this.downloadedBytes = downloadedBytes;
            this.error = error;
        }

        @Documented
        @Retention(RetentionPolicy.SOURCE)
        public @interface State {
        }
    }

    public interface Listener {
        void onInitialized(DownloadManager var1);

        void onTaskStateChanged(DownloadManager var1, TaskState var2);

        void onIdle(DownloadManager var1);
    }
}
