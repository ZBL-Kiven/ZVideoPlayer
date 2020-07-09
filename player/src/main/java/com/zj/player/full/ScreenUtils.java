package com.zj.player.full;

import android.content.Context;
import android.provider.Settings;

public class ScreenUtils {


    private boolean checkAccelerometerSystem(Context context) {
        try {
            int available = Settings.System.getInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION);
            return available == 1;
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }
}
