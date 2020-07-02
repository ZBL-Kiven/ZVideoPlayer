package com.zj.player.full

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.view.*
import android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.PopupWindow
import com.zj.player.anim.ZFullValueAnimator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BaseGestureFullScreenPopWindow private constructor(private val vp: ViewGroup, private val view: View, private val vlp: ViewGroup.LayoutParams, maxDeepRate: Float, private val onDisplayChanged: (Boolean) -> Unit) : PopupWindow(), View.OnTouchListener {

    companion object {
        fun show(vp: ViewGroup, view: View, maxDeepRate: Float, onDisplayChanged: (Boolean) -> Unit): BaseGestureFullScreenPopWindow {
            return BaseGestureFullScreenPopWindow(vp, view, view.layoutParams, maxDeepRate, onDisplayChanged)
        }
    }

    private val _width: Int
    private val _height: Int
    private val originWidth: Int
    private val originHeight: Int
    private val originViewRectF: RectF
    private val viewRectF: RectF
    private val systemUiFlags: Int
    private val curYDeepInterval: Int
    private val curXDeepInterval: Int
    private val calculateUtils: RectFCalculateUtil
    private val originCenterInScreen: PointF = PointF()
    private var curScaleOffset: Float = 1.0f
    private val scaleAnim: ZFullValueAnimator = ZFullValueAnimator(object : ZFullValueAnimator.FullAnimatorListener {
        override fun onDurationChange(animation: ValueAnimator, duration: Float, isFull: Boolean) {
            
        }

        override fun onAnimEnd(animation: Animator, isFull: Boolean) {

        }
    })

    init {
        isOutsideTouchable = false
        isTouchable = true
        systemUiFlags = view.systemUiVisibility
        val windowSize = Point()
        (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealSize(windowSize)
        originWidth = view.measuredWidth
        originHeight = view.measuredHeight
        _width = windowSize.x
        _height = windowSize.y
        val point = IntArray(2)
        view.getLocationOnScreen(point)
        val x = point[0] * 1.0f
        val y = point[1] * 1.0f
        originCenterInScreen.x = point[0] + originWidth / 2f
        originCenterInScreen.y = point[1] + originHeight / 2f
        originViewRectF = RectF(x, y, x + originWidth, y + originHeight)
        viewRectF = RectF(0f, 0f, _width * 1.0f, _height * 1.0f)
        curXDeepInterval = (_width * maxDeepRate).toInt()
        curYDeepInterval = (_height * maxDeepRate).toInt()
        calculateUtils = RectFCalculateUtil(viewRectF, originViewRectF)
        (view.parent as? ViewGroup)?.removeView(view)
        contentView = FrameLayout(view.context).apply {
            val ref = calculateUtils.calculate(1f)
            getFrameLayoutParams(view, ref)
            clipChildren = false
            addView(view)
        }
        view.setOnTouchListener(this@BaseGestureFullScreenPopWindow)
        show(view)
    }

    private fun show(view: View) {
        if (isShowing) return
        this.width = _width
        this.height = _height
        this.isClippingEnabled = false
        val parent = ((view.context as? Activity)?.window?.decorView) as? ViewGroup
        showAtLocation(parent, Gravity.CENTER, 0, 0)
        changeSystemWindowVisibility(false)
        onDisplayChanged(true)

    }

    override fun dismiss() {
        changeSystemWindowVisibility(true)
        onDisplayChanged(false)
        super.dismiss()
        view.setOnTouchListener(null)
        if (view.parent != null) (view.parent as? ViewGroup)?.removeView(view)
        vlp.width = originWidth
        vlp.height = originHeight
        vp.addView(view, vlp)
    }

    private var curYOffset = 0.0f
    private var lstX: Float = 0.0f
    private var lstY: Float = 0.0f
    private var _x = 0f
    private var _y = 0f

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                _x = event.rawX;_y = event.rawY;lstX = event.rawX;lstY = event.rawY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                try {
                    if (isAutoScaleFromTouchEnd() && (lstX != 0f && lstX - event.rawX < 20f) && (lstY != 0f && lstY - event.rawY < 20f)) {
                        v?.performClick()
                    }
                } finally {
                    lstY = 0f;lstX = 0f;_x = 0f;_y = 0f;curYOffset = 0f
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val y = max(event.rawY, _y)
                try {
                    val offsetY = y - _y
                    if ((lstX != 0f && lstY != 0f) || (abs(offsetY) > 50f && curYOffset != 0.0f)) {
                        curYOffset = min(offsetY / curYDeepInterval, 1f)
                        followWithFinger(lstX - event.rawX, if (curYOffset < 1) lstY - event.rawY else 0f)
                        scaleWithOffset(curYOffset, true)
                    }
                } finally {
                    lstX = event.rawX;lstY = event.rawY
                }
            }
        }
        return true
    }

    private fun followWithFinger(x: Float, y: Float) {
        view.scrollBy(x.toInt(), y.toInt())
    }

    private fun scaleWithOffset(curYOffset: Float, fromUser: Boolean = false) {
        val off = AccelerateInterpolator().getInterpolation(curYOffset)
        if (fromUser) {
            if (off <= 0.25f) {
                val ref = calculateUtils.calculate(off)
                getFrameLayoutParams(view, ref)
            }
        } else {
            val ref = calculateUtils.calculate(off)
            getFrameLayoutParams(view, ref)
        }
        curScaleOffset = off
    }

    private fun isAutoScaleFromTouchEnd(): Boolean {
        view.scrollTo(0, 0)
        if (curYOffset < 0.5f) {
            scaleWithOffset(0f)
        } else {
            scaleWithOffset(1f)
            dismiss()
        }
        return curYOffset == 0f
    }

    private fun getFrameLayoutParams(view: View, ref: RectF) {
        val pl: Int = ref.left.toInt()
        val pt: Int = ref.top.toInt()
        val pr: Int = ref.right.toInt()
        val pb: Int = ref.bottom.toInt()
        val flp = FrameLayout.LayoutParams(originWidth, originHeight)
        flp.setMargins(pl, pt, pr, pb)
        flp.width = _width - (pl + pr)
        flp.height = _height - (pt + pb)
        view.layoutParams = flp
    }

    private fun changeSystemWindowVisibility(visible: Boolean) {
        val flag = SYSTEM_UI_FLAG_FULLSCREEN
        if (visible) {
            contentView?.systemUiVisibility = flag
        } else {
            contentView?.systemUiVisibility = systemUiFlags
        }
    }
}