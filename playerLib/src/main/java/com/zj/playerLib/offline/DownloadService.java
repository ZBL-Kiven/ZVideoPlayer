package com.zj.playerLib.offline;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.zj.playerLib.offline.DownloadManager.TaskState;
import com.zj.playerLib.scheduler.Requirements;
import com.zj.playerLib.scheduler.RequirementsWatcher;
import com.zj.playerLib.scheduler.RequirementsWatcher.Listener;
import com.zj.playerLib.scheduler.Scheduler;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.NotificationUtil;
import com.zj.playerLib.util.Util;

import java.io.IOException;
import java.util.HashMap;

public abstract class DownloadService extends Service {
    public static final String ACTION_INIT = "player_download_service.action.INIT";
    public static final String ACTION_ADD = "player_download_service.action.ADD";
    public static final String ACTION_RELOAD_REQUIREMENTS = "player_download_service.action.RELOAD_REQUIREMENTS";
    private static final String ACTION_RESTART = "player_download_service.action.RESTART";
    public static final String KEY_DOWNLOAD_ACTION = "download_action";
    public static final int FOREGROUND_NOTIFICATION_ID_NONE = 0;
    public static final String KEY_FOREGROUND = "foreground";
    public static final long DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL = 1000L;
    private static final String TAG = "DownloadService";
    private static final boolean DEBUG = false;
    private static final HashMap<Class<? extends DownloadService>, RequirementsHelper> requirementsHelpers = new HashMap();
    private static final Requirements DEFAULT_REQUIREMENTS = new Requirements(1, false, false);
    @Nullable
    private final DownloadService.ForegroundNotificationUpdater foregroundNotificationUpdater;
    @Nullable
    private final String channelId;
    @StringRes
    private final int channelName;
    private DownloadManager downloadManager;
    private DownloadManagerListener downloadManagerListener;
    private int lastStartId;
    private boolean startedInForeground;
    private boolean taskRemoved;

    protected DownloadService(int foregroundNotificationId) {
        this(foregroundNotificationId, 1000L);
    }

    protected DownloadService(int foregroundNotificationId, long foregroundNotificationUpdateInterval) {
        this(foregroundNotificationId, foregroundNotificationUpdateInterval, null, 0);
    }

    protected DownloadService(int foregroundNotificationId, long foregroundNotificationUpdateInterval, @Nullable String channelId, @StringRes int channelName) {
        this.foregroundNotificationUpdater = foregroundNotificationId == 0 ? null : new ForegroundNotificationUpdater(foregroundNotificationId, foregroundNotificationUpdateInterval);
        this.channelId = channelId;
        this.channelName = channelName;
    }

    public static Intent buildAddActionIntent(Context context, Class<? extends DownloadService> clazz, DownloadAction downloadAction, boolean foreground) {
        return getIntent(context, clazz, "player_download_service.action.ADD").putExtra("download_action", downloadAction.toByteArray()).putExtra("foreground", foreground);
    }

    public static void startWithAction(Context context, Class<? extends DownloadService> clazz, DownloadAction downloadAction, boolean foreground) {
        Intent intent = buildAddActionIntent(context, clazz, downloadAction, foreground);
        if (foreground) {
            Util.startForegroundService(context, intent);
        } else {
            context.startService(intent);
        }

    }

    public static void start(Context context, Class<? extends DownloadService> clazz) {
        context.startService(getIntent(context, clazz, "player_download_service.action.INIT"));
    }

    public static void startForeground(Context context, Class<? extends DownloadService> clazz) {
        Intent intent = getIntent(context, clazz, "player_download_service.action.INIT").putExtra("foreground", true);
        Util.startForegroundService(context, intent);
    }

    public void onCreate() {
        this.logd("onCreate");
        if (this.channelId != null) {
            NotificationUtil.createNotificationChannel(this, this.channelId, this.channelName, 2);
        }

        this.downloadManager = this.getDownloadManager();
        this.downloadManagerListener = new DownloadManagerListener();
        this.downloadManager.addListener(this.downloadManagerListener);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        this.lastStartId = startId;
        this.taskRemoved = false;
        String intentAction = null;
        if (intent != null) {
            intentAction = intent.getAction();
            this.startedInForeground |= intent.getBooleanExtra("foreground", false) || "player_download_service.action.RESTART".equals(intentAction);
        }

        if (intentAction == null) {
            intentAction = "player_download_service.action.INIT";
        }

        this.logd("onStartCommand action: " + intentAction + " startId: " + startId);
        byte var6 = -1;
        switch(intentAction.hashCode()) {
        case -871181424:
            if (intentAction.equals("player_download_service.action.RESTART")) {
                var6 = 1;
            }
            break;
        case -608867945:
            if (intentAction.equals("player_download_service.action.RELOAD_REQUIREMENTS")) {
                var6 = 3;
            }
            break;
        case -382886238:
            if (intentAction.equals("player_download_service.action.ADD")) {
                var6 = 2;
            }
            break;
        case 1015676687:
            if (intentAction.equals("player_download_service.action.INIT")) {
                var6 = 0;
            }
        }

        switch(var6) {
        case 0:
        case 1:
            break;
        case 2:
            byte[] actionData = intent.getByteArrayExtra("download_action");
            if (actionData == null) {
                Log.e("DownloadService", "Ignoring ADD action with no action data");
            } else {
                try {
                    this.downloadManager.handleAction(actionData);
                } catch (IOException var9) {
                    Log.e("DownloadService", "Failed to handle ADD action", var9);
                }
            }
            break;
        case 3:
            this.stopWatchingRequirements();
            break;
        default:
            Log.e("DownloadService", "Ignoring unrecognized action: " + intentAction);
        }

        Requirements requirements = this.getRequirements();
        if (requirements.checkRequirements(this)) {
            this.downloadManager.startDownloads();
        } else {
            this.downloadManager.stopDownloads();
        }

        this.maybeStartWatchingRequirements(requirements);
        if (this.downloadManager.isIdle()) {
            this.stop();
        }

        return 1;
    }

    public void onTaskRemoved(Intent rootIntent) {
        this.logd("onTaskRemoved rootIntent: " + rootIntent);
        this.taskRemoved = true;
    }

    public void onDestroy() {
        this.logd("onDestroy");
        if (this.foregroundNotificationUpdater != null) {
            this.foregroundNotificationUpdater.stopPeriodicUpdates();
        }

        this.downloadManager.removeListener(this.downloadManagerListener);
        this.maybeStopWatchingRequirements();
    }

    @Nullable
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected abstract DownloadManager getDownloadManager();

    @Nullable
    protected abstract Scheduler getScheduler();

    protected Requirements getRequirements() {
        return DEFAULT_REQUIREMENTS;
    }

    protected Notification getForegroundNotification(TaskState[] taskStates) {
        throw new IllegalStateException(this.getClass().getName() + " is started in the foreground but getForegroundNotification() is not implemented.");
    }

    protected void onTaskStateChanged(TaskState taskState) {
    }

    private void maybeStartWatchingRequirements(Requirements requirements) {
        if (this.downloadManager.getDownloadCount() != 0) {
            Class<? extends DownloadService> clazz = this.getClass();
            RequirementsHelper requirementsHelper = requirementsHelpers.get(clazz);
            if (requirementsHelper == null) {
                requirementsHelper = new RequirementsHelper(this, requirements, this.getScheduler(), clazz);
                requirementsHelpers.put(clazz, requirementsHelper);
                requirementsHelper.start();
                this.logd("started watching requirements");
            }

        }
    }

    private void maybeStopWatchingRequirements() {
        if (this.downloadManager.getDownloadCount() <= 0) {
            this.stopWatchingRequirements();
        }
    }

    private void stopWatchingRequirements() {
        RequirementsHelper requirementsHelper = requirementsHelpers.remove(this.getClass());
        if (requirementsHelper != null) {
            requirementsHelper.stop();
            this.logd("stopped watching requirements");
        }

    }

    private void stop() {
        if (this.foregroundNotificationUpdater != null) {
            this.foregroundNotificationUpdater.stopPeriodicUpdates();
            if (this.startedInForeground && Util.SDK_INT >= 26) {
                this.foregroundNotificationUpdater.showNotificationIfNotAlready();
            }
        }

        if (Util.SDK_INT < 28 && this.taskRemoved) {
            this.stopSelf();
            this.logd("stopSelf()");
        } else {
            boolean stopSelfResult = this.stopSelfResult(this.lastStartId);
            this.logd("stopSelf(" + this.lastStartId + ") result: " + stopSelfResult);
        }

    }

    private void logd(String message) {
    }

    private static Intent getIntent(Context context, Class<? extends DownloadService> clazz, String action) {
        return (new Intent(context, clazz)).setAction(action);
    }

    private static final class RequirementsHelper implements Listener {
        private final Context context;
        private final Requirements requirements;
        @Nullable
        private final Scheduler scheduler;
        private final Class<? extends DownloadService> serviceClass;
        private final RequirementsWatcher requirementsWatcher;

        private RequirementsHelper(Context context, Requirements requirements, @Nullable Scheduler scheduler, Class<? extends DownloadService> serviceClass) {
            this.context = context;
            this.requirements = requirements;
            this.scheduler = scheduler;
            this.serviceClass = serviceClass;
            this.requirementsWatcher = new RequirementsWatcher(context, this, requirements);
        }

        public void start() {
            this.requirementsWatcher.start();
        }

        public void stop() {
            this.requirementsWatcher.stop();
            if (this.scheduler != null) {
                this.scheduler.cancel();
            }

        }

        public void requirementsMet(RequirementsWatcher requirementsWatcher) {
            try {
                this.notifyService();
            } catch (Exception var3) {
                return;
            }

            if (this.scheduler != null) {
                this.scheduler.cancel();
            }

        }

        public void requirementsNotMet(RequirementsWatcher requirementsWatcher) {
            try {
                this.notifyService();
            } catch (Exception var4) {
            }

            if (this.scheduler != null) {
                String servicePackage = this.context.getPackageName();
                boolean success = this.scheduler.schedule(this.requirements, servicePackage, "player_download_service.action.RESTART");
                if (!success) {
                    Log.e("DownloadService", "Scheduling downloads failed.");
                }
            }

        }

        private void notifyService() throws Exception {
            Intent intent = DownloadService.getIntent(this.context, this.serviceClass, "player_download_service.action.INIT");

            try {
                this.context.startService(intent);
            } catch (IllegalStateException var3) {
                throw new Exception(var3);
            }
        }
    }

    private final class ForegroundNotificationUpdater implements Runnable {
        private final int notificationId;
        private final long updateInterval;
        private final Handler handler;
        private boolean periodicUpdatesStarted;
        private boolean notificationDisplayed;

        public ForegroundNotificationUpdater(int notificationId, long updateInterval) {
            this.notificationId = notificationId;
            this.updateInterval = updateInterval;
            this.handler = new Handler(Looper.getMainLooper());
        }

        public void startPeriodicUpdates() {
            this.periodicUpdatesStarted = true;
            this.update();
        }

        public void stopPeriodicUpdates() {
            this.periodicUpdatesStarted = false;
            this.handler.removeCallbacks(this);
        }

        public void update() {
            TaskState[] taskStates = DownloadService.this.downloadManager.getAllTaskStates();
            DownloadService.this.startForeground(this.notificationId, DownloadService.this.getForegroundNotification(taskStates));
            this.notificationDisplayed = true;
            if (this.periodicUpdatesStarted) {
                this.handler.removeCallbacks(this);
                this.handler.postDelayed(this, this.updateInterval);
            }

        }

        public void showNotificationIfNotAlready() {
            if (!this.notificationDisplayed) {
                this.update();
            }

        }

        public void run() {
            this.update();
        }
    }

    private final class DownloadManagerListener implements DownloadManager.Listener {
        private DownloadManagerListener() {
        }

        public void onInitialized(DownloadManager downloadManager) {
            DownloadService.this.maybeStartWatchingRequirements(DownloadService.this.getRequirements());
        }

        public void onTaskStateChanged(DownloadManager downloadManager, TaskState taskState) {
            DownloadService.this.onTaskStateChanged(taskState);
            if (DownloadService.this.foregroundNotificationUpdater != null) {
                if (taskState.state == 1) {
                    DownloadService.this.foregroundNotificationUpdater.startPeriodicUpdates();
                } else {
                    DownloadService.this.foregroundNotificationUpdater.update();
                }
            }

        }

        public final void onIdle(DownloadManager downloadManager) {
            DownloadService.this.stop();
        }
    }
}
