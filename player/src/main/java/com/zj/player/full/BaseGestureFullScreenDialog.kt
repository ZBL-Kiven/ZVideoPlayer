package com.zj.player.full

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable.Orientation
import android.view.*
import android.widget.FrameLayout
import com.zj.player.anim.ZFullValueAnimator
import android.widget.PopupWindow
import androidx.annotation.FloatRange
import kotlin.math.min
import kotlin.math.roundToInt

class BaseGestureFullScreenDialog private constructor(private val vp: ViewGroup, private val view: View, private val vlp: ViewGroup.LayoutParams, private val onDisplayChanged: (Boolean) -> Unit) : PopupWindow() {

    companion object {
        private const val MAX_DEEP_RATIO = 0.55f
        fun show(vp: ViewGroup, view: View, onDisplayChanged: (Boolean) -> Unit): BaseGestureFullScreenDialog {
            return BaseGestureFullScreenDialog(vp, view, view.layoutParams, onDisplayChanged)
        }
    }

    private val systemUiFlags = view.systemUiVisibility
    private val _width: Int
    private val _height: Int
    private val originWidth: Int = view.measuredWidth
    private val originHeight: Int = view.measuredHeight
    private val originViewRectF: RectF
    private val viewRectF: RectF
    private val calculateUtils: RectFCalculateUtil
    private var originInScreen: Point? = null
    private var curScaleOffset: Float = 1.0f
    private var isAnimRun = false
    private val scaleAnim: ZFullValueAnimator
    private val touchListener: GestureTouchListener
    private val onKeyListener: View.OnKeyListener
    private var isDismissing = false

    init {
        this.isOutsideTouchable = false
        this.isTouchable = true
        this.isClippingEnabled = false
        this.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        val screenSize = getWindowSize()
        _width = screenSize.x
        _height = screenSize.y
        width = _width
        height = _height
        val point = IntArray(2)
        view.getLocationOnScreen(point)
        val x = point[0] * 1.0f
        val y = point[1] * 1.0f
        originViewRectF = RectF(x, y, x + originWidth, y + originHeight)
        viewRectF = RectF(0f, 0f, _width * 1.0f, _height * 1.0f)
        calculateUtils = RectFCalculateUtil(viewRectF, originViewRectF)
        (view.parent as? ViewGroup)?.removeView(view)
        val ref = calculateUtils.calculate(1f)
        getFrameLayoutParams(view, ref)
        scaleAnim = ZFullValueAnimator(object : ZFullValueAnimator.FullAnimatorListener {
            override fun onDurationChange(animation: ValueAnimator, duration: Float, isFull: Boolean) {
                if (isFull) {
                    val rect = calculateUtils.calculate(1 - duration)
                    getFrameLayoutParams(view, rect)
                } else {
                    if (originInScreen == null) originInScreen = Point(view.scrollX, view.scrollY)
                    originInScreen?.let {
                        val sx = (it.x * (1f - duration)).toInt()
                        val sy = (it.y * (1f - duration)).toInt()
                        view.scrollTo(sx, sy)
                    }
                    val curOff = if (curScaleOffset <= 0f) 1f else curScaleOffset
                    val offset = (duration * curOff) + (1f - curOff)
                    val rect = calculateUtils.calculate(offset)
                    getFrameLayoutParams(view, rect)
                }
            }

            override fun onAnimEnd(animation: Animator, isFull: Boolean) {
                val rect = calculateUtils.calculate(0.0f)
                getFrameLayoutParams(view, rect)
                isAnimRun = false
                originInScreen = null
                if (!isFull) dismissed()
                else onDisplayChanged(true)
            }
        }, false).apply {
            duration = 240
        }
        touchListener = object : GestureTouchListener({ isAnimRun || isDismissing }) {
            override fun isPerformTouchClick(formTrigDuration: Float): Boolean {
                setBackground(0f)
                return isAutoScaleFromTouchEnd(formTrigDuration)
            }

            override fun onTracked(offsetX: Float, offsetY: Float, easeY: Float, orientation: Orientation, formTrigDuration: Float) {
                setBackground(1f - formTrigDuration)
                followWithFinger(offsetX, offsetY)
                scaleWithOffset(easeY, true)
            }
        }
        touchListener.setPadding(0.15f, 0.13f)
        onKeyListener = View.OnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                dismiss()
                return@OnKeyListener false
            }
            true
        }
        contentView = FrameLayout(view.context).apply {
            val cal = calculateUtils.calculate(1f)
            getFrameLayoutParams(view, cal)
            fitsSystemWindows = false
            clipChildren = false
            addView(view)
        }
        show(view)
    }

    private fun show(view: View) {
        changeSystemWindowVisibility(true)
        if (isShowing || (view.context as? Activity)?.isFinishing == true) return
        val parent = (getActivity()?.window?.decorView) as? ViewGroup
        showAtLocation(parent, Gravity.NO_GRAVITY, 0, 0)
        view.setOnTouchListener(touchListener)
        view.setOnKeyListener(onKeyListener)
        isAnimRun = true
        scaleAnim.start(true)
    }

    private fun dismissed() {
        onDisplayChanged(false)
        curScaleOffset = 0f
        view.setOnTouchListener(null)
        if (view.parent != null) (view.parent as? ViewGroup)?.removeView(view)
        vlp.width = originWidth
        vlp.height = originHeight
        vp.addView(view, vlp)
        isDismissing = false
        super.dismiss()
    }

    internal fun getRootView(): View {
        return view
    }

    private fun followWithFinger(x: Float, y: Float) {
        view.scrollTo(x.toInt(), y.toInt())
    }

    private fun scaleWithOffset(curYOffset: Float, fromUser: Boolean = false) {
        val ref = calculateUtils.calculate(curYOffset)
        if (fromUser) {
            getFrameLayoutParams(view, ref)
        } else {
            getFrameLayoutParams(view, ref)
        }
        curScaleOffset = 1 - curYOffset
    }

    private fun isAutoScaleFromTouchEnd(curYOffset: Float): Boolean {
        val isScaleAuto = curYOffset <= MAX_DEEP_RATIO
        if (isScaleAuto) {
            view.scrollTo(0, 0)
            scaleWithOffset(0f)
        } else {
            isAnimRun = true
            changeSystemWindowVisibility(false)
            scaleAnim.start(false)
        }
        return isScaleAuto
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

    override fun dismiss() {
        if (getActivity()?.isFinishing == true) {
            dismissed();super.dismiss()
        } else {
            if (isDismissing) return
            isDismissing = true
            isAutoScaleFromTouchEnd(1f)
        }
    }

    private fun changeSystemWindowVisibility(visible: Boolean) {
        val flag = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        if (visible) {
            getDecorView()?.systemUiVisibility = flag
            getActivity()?.window?.attributes?.flags?.or(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            getDecorView()?.systemUiVisibility = systemUiFlags
            getActivity()?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun getDecorView(): View? {
        return getActivity()?.window?.decorView
    }

    private fun getActivity(): Activity? {
        return view.context as? Activity
    }

    private fun setBackground(@FloatRange(from = 0.0, to = 1.0) duration: Float) {
        contentView?.setBackgroundColor(Color.argb((min(1f, duration) * 255f).roundToInt(), 0, 0, 0))
    }

    private fun getWindowSize(): Point {
        val windowSize = Point()
        (view.context?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.getRealSize(windowSize)
        return windowSize
    }
}