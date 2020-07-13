package com.zj.videotest;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.hardware.SensorManager;
import android.view.OrientationEventListener;

import java.lang.ref.WeakReference;

public class TEST {

    private OrientationEventListener mOrientationListener;
    private boolean isPortLock = false;
    private boolean isLandLock = false;

    public TEST(final Activity activity) {
        this.mOrientationListener = new OrientationEventListener(activity, SensorManager.SENSOR_DELAY_NORMAL) {
            public void onOrientationChanged(int orientation) {
                if (orientation < 100 && orientation > 80 || orientation < 280 && orientation > 260) {
                    if (!isLandLock) {
                        //land
                        isLandLock = true;
                        isPortLock = false;
                    }
                }

                if (orientation < 10 || orientation > 350 || orientation < 190 && orientation > 170) {
                    if (!isPortLock) {
                        //port
                        isPortLock = true;
                        isLandLock = false;
                    }
                }
            }
        };
    }

    //禁用切换屏幕的开关
    public void disable() {
        this.mOrientationListener.disable();
    }

    //开启横竖屏切换的开关
    public void enable() {
        this.mOrientationListener.enable();
    }

    //设置竖屏是否上锁，true锁定屏幕,false解锁
    public void setPortLock(boolean lockFlag) {
        this.isPortLock = lockFlag;
    }

    //设置横屏是否锁定，true锁定，false解锁
    public void setLandLock(boolean isLandLock) {
        this.isLandLock = isLandLock;
    }




}
