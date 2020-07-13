package com.zj.player.full

import android.content.Context
import android.hardware.SensorManager
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager

class ScreenUtils(private val context: Context, private val gravityListener: (RotateOrientation) -> Unit) {

    private var mOrientationListener: OrientationEventListener? = null
    private var isPortLock = false
    private var isLandLock = false

    init {
        mOrientationListener = object : OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                if (!checkAccelerometerSystem()) return
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
                        gravityListener(RotateOrientation.L0)
                    }
                    in 261..279 -> {
                        gravityListener(RotateOrientation.L1)
                    }
                    in 171..189 -> {
                        gravityListener(RotateOrientation.P1)
                    }
                    in 350..360, in 0..10 -> {
                        gravityListener(RotateOrientation.P0)
                    }
                }
            }
        }
    }

    private fun checkAccelerometerSystem(): Boolean {
        try {
            val available = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION)
            return available == 1
        } catch (e: SettingNotFoundException) {
            e.printStackTrace()
        }
        return false
    }

    fun disable() {
        mOrientationListener?.disable()
    }

    fun enable(): Boolean {
        (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.let {
            when (it.defaultDisplay.rotation) {
                Surface.ROTATION_0 -> gravityListener(RotateOrientation.P0)
                Surface.ROTATION_90 -> gravityListener(RotateOrientation.L0)
                Surface.ROTATION_180 -> gravityListener(RotateOrientation.P1)
                Surface.ROTATION_270 -> gravityListener(RotateOrientation.L1)
            }
        }
        return if (mOrientationListener?.canDetectOrientation() == true) {
            mOrientationListener?.enable();true
        } else {
            mOrientationListener?.disable();false
        }
    }

    fun setPortLock(lockFlag: Boolean) {
        isPortLock = lockFlag
    }

    fun setLandLock(isLandLock: Boolean) {
        this.isLandLock = isLandLock
    }

    fun lockOrientation(isLock: Boolean) {
        isPortLock = isLock
        isLandLock = isLock
    }
}

enum class RotateOrientation(degree: Int) {
    P0(0), P1(180), L0(90), L1(270)
}