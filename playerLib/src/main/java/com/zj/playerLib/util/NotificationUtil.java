package com.zj.playerLib.util;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@SuppressLint({"InlinedApi"})
public final class NotificationUtil {
    public static final int IMPORTANCE_UNSPECIFIED = -1000;
    public static final int IMPORTANCE_NONE = 0;
    public static final int IMPORTANCE_MIN = 1;
    public static final int IMPORTANCE_LOW = 2;
    public static final int IMPORTANCE_DEFAULT = 3;
    public static final int IMPORTANCE_HIGH = 4;

    public static void createNotificationChannel(Context context, String id, @StringRes int name, int importance) {
        if (Util.SDK_INT >= 26) {
            NotificationManager notificationManager = (NotificationManager)context.getSystemService("notification");
            NotificationChannel channel = new NotificationChannel(id, context.getString(name), importance);
            notificationManager.createNotificationChannel(channel);
        }

    }

    public static void setNotification(Context context, int id, @Nullable Notification notification) {
        NotificationManager notificationManager = (NotificationManager)context.getSystemService("notification");
        if (notification != null) {
            notificationManager.notify(id, notification);
        } else {
            notificationManager.cancel(id);
        }

    }

    private NotificationUtil() {
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface Importance {
    }
}
