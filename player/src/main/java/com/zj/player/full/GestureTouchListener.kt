package com.zj.player.full

import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import android.view.View
import androidx.annotation.FloatRange
import kotlin.math.*

@Suppress("unused")
internal abstract class GestureTouchListener(private val intercepted: () -> Boolean) : View.OnTouchListener {

    private var curOrientation: TrackOrientation? = null
    private var realOrientation: TrackOrientation? = null
    private var lastOrientation: TrackOrientation? = null
    private var _x = 0f
    private var _y = 0f
    private var lstX: Float = 0.0f
    private var lstY: Float = 0.0f
    private var startX = 0f
    private var startY = 0f
    private var paddingX: Float = 0.0f
    private var paddingY: Float = 0.0f
    private var inTouching = false
    private var startTrack = false
    private var interpolator = 58f
    private var triggerX = 230f
    private var triggerY = 400f
    private var toleranceClick = 20f
    private var noPaddingClickPointStart: PointF? = null
    private var isOnceTap = false
    private var handler: Handler? = Handler(Looper.getMainLooper()) {
        if (it.what == 127924) {
            isOnceTap = false
            onClick(it.arg1 * 1.0f, it.arg2 * 1.0f)
        }
        return@Handler false
    }

    fun setPadding(@FloatRange(from = .0, to = .5) paddingX: Float, @FloatRange(from = .0, to = .5) paddingY: Float) {
        this.paddingX = paddingX
        this.paddingY = paddingY
    }

    fun setTorque(interpolator: Float, tx: Float, ty: Float, toleranceClick: Float) {
        triggerX = tx;triggerY = ty;this.interpolator = interpolator;this.toleranceClick = toleranceClick
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (intercepted()) return true
        var paddingX = 0f
        var paddingY = 0f
        v?.let {
            paddingX = it.measuredWidth * this.paddingX
            paddingY = it.measuredHeight * this.paddingY
        }
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                noPaddingClickPointStart = PointF(event.rawX, event.rawY);init(v, event)
                onTouchActionEvent(event, lstX, lstY, null)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                try {
                    val isTap = (abs((noPaddingClickPointStart?.x ?: _x) - max(event.rawX, paddingX)) < toleranceClick && abs((noPaddingClickPointStart?.y ?: _y) - event.rawY) < toleranceClick)
                    val isDisInterrupted = if (!isTap) !onTouchActionEvent(event, lstX, lstY, null) else true
                    val isParseEnd = if (isDisInterrupted) onEventEnd(min(triggerY, event.rawY - _y) / triggerY, !isTap && isDisInterrupted) else false
                    if (isTap && (isDisInterrupted || isParseEnd)) {
                        if (isOnceTap) {
                            handler?.removeMessages(127924)
                            isOnceTap = false
                            onDoubleClick(event.x, event.y)
                        } else {
                            isOnceTap = true
                            handler?.removeMessages(127924)
                            handler?.sendMessageDelayed(Message.obtain().apply {
                                this.what = 127924
                                this.arg1 = event.x.roundToInt()
                                this.arg2 = event.y.roundToInt()
                            }, 200)
                        }
                    }
                } finally {
                    reset();noPaddingClickPointStart = null
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTouchInPadding(v, event.rawX, event.rawY, paddingX, paddingY) && !inTouching) {
                    init(v, event);return true
                }
                inTouching = true
                val x = event.rawX
                val y = max(event.rawY, _y)
                try {
                    val xMoved = abs((noPaddingClickPointStart?.x ?: _x) - x) !in 0f..toleranceClick
                    val yMoved = abs((noPaddingClickPointStart?.y ?: _y) - event.rawY) !in 0f..toleranceClick
                    if (xMoved || yMoved) {
                        val orientation = parseCurOrientation(x, y)
                        val interrupt = onTouchActionEvent(event, lstX, lstY, realOrientation)
                        if (orientation || interrupt) {
                            init(v, event); return true
                        }
                        parseCurTouchOffset(x, y)
                    }
                } finally {
                    lstX = event.rawX;lstY = max(event.rawY, _y)
                }
            }
        }
        return true
    }

    private fun reset() {
        lstY = 0f;lstX = 0f;_x = 0f;_y = 0f;startX = 0f;startY = 0f;curOrientation = null;lastOrientation = null;realOrientation = null;inTouching = false
    }

    private fun init(v: View?, event: MotionEvent) {
        _x = max(event.rawX, paddingX);_y = max(event.rawY, paddingY);lstX = _x;lstY = _y;startX = _x - (v?.left ?: 0);startY = _y - (v?.top ?: 0);startTrack = true
    }

    private fun parseCurOrientation(x: Float, y: Float): Boolean {
        val stepX = x - lstX
        val stepY = y - lstY
        if (lstX != 0f && lstY != 0f) {
            val absX = abs(stepX)
            val absY = abs(stepY)
            val orientation = if (absX > absY && stepX > 0) TrackOrientation.LEFT_RIGHT
            else if (absX > absY && stepX < 0) TrackOrientation.RIGHT_LEFT
            else if (absX < absY && stepY > 0) TrackOrientation.TOP_BOTTOM
            else TrackOrientation.BOTTOM_TOP
            realOrientation = orientation
            if (lastOrientation == null && orientation != TrackOrientation.TOP_BOTTOM) return true
            lastOrientation = orientation
            if (orientation != curOrientation) {
                curOrientation = orientation
            }
        }
        return false
    }

    private fun parseCurTouchOffset(x: Float, y: Float) {
        val offsetX = max(x, _x) - min(x, _x)
        val isXEasing = triggerX > offsetX
        val isYEasing = triggerY > y - _y
        var ry = min(0f, -if (isYEasing) y - startY else max(0f, elasticStep(max(y - startY, triggerY), triggerY)))
        if (ry == -0.0f) ry = 0f
        val rx: Float = if (x == _x) 0f else if (x > _x) {
            if (isXEasing) -(x - startX)
            else -max(0f, elasticStep(max(x - startX, triggerX), triggerX))
        } else {
            if (isXEasing) _x - x
            else max(0f, elasticStep(max(_x - x, triggerX), triggerX))
        }
        val easeY = easingInterpolator(1f - _y / y, 9.8f, 1f)
        curOrientation?.let {
            onTracked(startTrack, rx, ry, easeY, it, min(triggerY, y - _y) / triggerY);startTrack = false
        }
    }

    private fun isTouchInPadding(v: View?, x: Float, y: Float, px: Float, py: Float): Boolean {
        return v?.let {
            x < px || y < py || x > v.measuredWidth - px || y > v.measuredHeight - py
        } ?: false
    }

    private fun elasticStep(cur: Float, tr: Float): Float {
        if (cur < tr) throw IllegalArgumentException("trigger $tr  must be greater than cur $cur")
        val a = cur - tr
        return tr + easingInterpolator(a, interpolator)
    }

    private fun easingInterpolator(`in`: Float, interpolator: Float, base: Float = 0.765f): Float {
        if (`in` == 0f) return 0f
        return `in` * (1f - (interpolator * 0.06f) / (interpolator * 0.06f + 1f)) * base
    }

    fun updateTargetXY(v: View, event: MotionEvent): Boolean {
        val e = MotionEvent.obtain(event)
        e.action = MotionEvent.ACTION_DOWN
        return onTouch(v, e)
    }

    fun release() {
        handler?.removeCallbacksAndMessages(null)
        handler = null
    }

    abstract fun onEventEnd(formTrigDuration: Float, parseAutoScale: Boolean): Boolean

    abstract fun onDoubleClick(x: Float, y: Float)

    abstract fun onClick(x: Float, y: Float)

    /**
     * Called only after the default gesture returns successfully triggered，
     * @param formTrigDuration Out of bounds coefficient after exceeding the maximum return distance
     * @param orientation see [TrackOrientation]
     * @param easeY Current damping coefficient based on Y direction
     * */
    abstract fun onTracked(isStart: Boolean, offsetX: Float, offsetY: Float, easeY: Float, orientation: TrackOrientation, formTrigDuration: Float)

    /**
     * @param event [MotionEvent]
     * @param lastX X coordinate of recent valid record
     * @param lastY Y coordinate of recent valid record
     * @param orientation see [TrackOrientation] ,return null if it has never been recorded
     * @return true to indicate that the event is consumed, and the default gesture will be intercepted.
     * */
    abstract fun onTouchActionEvent(event: MotionEvent, lastX: Float, lastY: Float, orientation: TrackOrientation?): Boolean
}