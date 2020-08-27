package com.zj.player.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.abs

class VideoRootView @JvmOverloads constructor(context: Context, attributeSet: AttributeSet? = null, def: Int = 0) : FrameLayout(context, attributeSet, def) {

    private var isInterceptOtherTouch: (() -> Boolean)? = null
    private var onTargetChanged: ((v: View, e: MotionEvent) -> Boolean?)? = null

    internal fun setTouchInterceptor(isInterceptOtherTouch: (() -> Boolean)? = null) {
        this.isInterceptOtherTouch = isInterceptOtherTouch
    }

    internal fun setTargetChangeListener(onTargetChanged: ((v: View, e: MotionEvent) -> Boolean?)? = null) {
        this.onTargetChanged = onTargetChanged
    }

    private var rawX = 0f
    private var rawY: Float = 0f
    private var aX: Float = 0f
    private var aY: Float = 0f
    private var changeWithNotDown = false
    private var oldPointerCount = 0
    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                rawX = event.rawX
                aX = rawX
                rawY = event.rawY
                aY = rawY
                changeWithNotDown = false
                oldPointerCount = event.pointerCount
            }
            MotionEvent.ACTION_MOVE -> {
                aX = event.rawX
                aY = event.rawY
                val pc = event.pointerCount
                if (oldPointerCount != pc) {
                    rawX = aX;rawY = aY
                    oldPointerCount = pc
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                aY = 0f
                aX = aY
                rawY = aX
                rawX = rawY
                changeWithNotDown = false
                oldPointerCount = 0
                return false
            }
        }
        val xOffset: Float = abs(rawX - aX)
        val yOffset: Float = abs(rawY - aY)
        val sa: Boolean = isInterceptOtherTouch?.invoke() ?: true
        if (event?.pointerCount == 1 && !sa && (xOffset > 100 || yOffset > 100)) {
            if (!changeWithNotDown) {
                changeWithNotDown = true
                event.action = MotionEvent.ACTION_CANCEL
                return onTargetChanged?.invoke(this@VideoRootView, event) ?: false
            }
            return true
        }
        changeWithNotDown = false
        return false
    }
}