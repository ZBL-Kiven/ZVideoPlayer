package com.zj.player.full

import android.content.Context
import android.hardware.SensorManager
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.view.OrientationEventListener

@Suppress("unused")
class ScreenUtils(private val context: Context, private val gravityListener: (RotateOrientation?) -> Unit) {

    private var mOrientationListener: OrientationEventListener? = null
    private var isPortLock = false
    private var isLandLock = false
    private var lastOrientation: RotateOrientation? = null
    private var isEventLock = false

    init {
        mOrientationListener = object : OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
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
                gravityListener(lastOrientation)
            }
        }
    }

    fun checkAccelerometerSystem(): Boolean {
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

    fun getLastOrientation(): RotateOrientation? {
        return lastOrientation
    }

    fun enable(): Boolean {
        gravityListener(lastOrientation)
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
        isEventLock = isLock
    }
}

enum class RotateOrientation(val degree: Float) {
    P0(0f), P1(180f), L0(270f), L1(90f)
}