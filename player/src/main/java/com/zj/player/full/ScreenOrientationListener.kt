package com.zj.player.full

import android.content.Context
import android.hardware.SensorManager
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.view.OrientationEventListener
import com.zj.player.logs.ZPlayerLogs
import java.lang.ref.WeakReference

@Suppress("unused")
class ScreenOrientationListener(private var c: WeakReference<Context>?, private var gravityListener: ((RotateOrientation?) -> Unit)?) : OrientationEventListener(c?.get(), SensorManager.SENSOR_DELAY_NORMAL) {

    private var isPortLock = false
    private var isLandLock = false
    private var lastOrientation: RotateOrientation? = null
    private var isEventLock = false


    override fun onOrientationChanged(orientation: Int) {
        if (isEventLock) {
            return
        }
        if (orientation in 81..99 || orientation in 261..279) {
            if (isLandLock) return
            isLandLock = true
            isPortLock = false
        } else if (orientation < 10 || orientation > 350 || orientation in 171..189) {
            if (isPortLock) return
            isPortLock = true
            isLandLock = false
        }
        when (orientation) {
            in 81..99 -> {
                lastOrientation = RotateOrientation.L0
            }
            in 261..279 -> {
                lastOrientation = RotateOrientation.L1
            }
            in 171..189 -> {
                lastOrientation = RotateOrientation.P1
            }
            in 350..360, in 0..10 -> {
                lastOrientation = RotateOrientation.P0
            }
        }
        gravityListener?.invoke(lastOrientation)
    }

    fun checkAccelerometerSystem(): Boolean {
        try {
            val available = Settings.System.getInt(c?.get()?.contentResolver, Settings.System.ACCELEROMETER_ROTATION)
            return available == 1
        } catch (e: SettingNotFoundException) {
            ZPlayerLogs.onError(e)
        }
        return false
    }

    fun release() {
        disable()
        c = null
        lastOrientation = null
        gravityListener = null
    }

    fun getLastOrientation(): RotateOrientation? {
        return lastOrientation
    }

    override fun enable() {
        gravityListener?.invoke(lastOrientation)
        if (canDetectOrientation()) {
            super.enable()
        } else {
            super.disable()
        }
    }

    fun lockOrientation(isLock: Boolean) {
        isEventLock = isLock
    }
}