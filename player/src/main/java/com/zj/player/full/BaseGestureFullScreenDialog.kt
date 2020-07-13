package com.zj.player.full

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable.Orientation
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.annotation.FloatRange
import com.zj.player.R
import com.zj.player.anim.ZFullValueAnimator
import kotlin.math.roundToInt


class BaseGestureFullScreenDialog private constructor(private val vp: ViewGroup, private val controllerView: View, private val vlp: ViewGroup.LayoutParams, private val onDisplayChanged: (Boolean) -> Unit) : Dialog(controllerView.context, R.style.BaseGestureFullScreenDialogStyle) {

    companion object {
        private const val MAX_DEEP_RATIO = 0.55f
        fun show(vp: ViewGroup, view: View, onDisplayChanged: (Boolean) -> Unit): BaseGestureFullScreenDialog {
            return BaseGestureFullScreenDialog(vp, view, view.layoutParams, onDisplayChanged)
        }
    }

    private val systemUiFlags = getActivity()?.window?.decorView?.systemUiVisibility
    private var _width: Int = 0
    private var _height: Int = 0
    private val originWidth: Int = getControllerView().measuredWidth
    private val originHeight: Int = getControllerView().measuredHeight
    private var calculateUtils: RectFCalculateUtil? = null
    private var originInScreen: Point? = null
    private var curScaleOffset: Float = 1.0f
    private var isAnimRun = false
    private var scaleAnim: ZFullValueAnimator? = null
    private var touchListener: GestureTouchListener? = null
    private var onKeyListener: View.OnKeyListener? = null
    private var isDismissing = false
    private val interpolator = DecelerateInterpolator(1.5f)
    private var scrolled: Point = Point()
    private var isMaxFull = false
    private val originViewRectF = getControllerViewRect()

    init {
        init(isMaxFull)
        setContent()
        initListeners()
        show(getControllerView())
    }

    private fun init(isMaxFull: Boolean) {
        this.isMaxFull = isMaxFull
        this.setCanceledOnTouchOutside(false)
        this.setCancelable(false)
        val screenSize = getWindowSize(isMaxFull)
        _width = screenSize.x
        _height = screenSize.y
        scrolled.x = vp.bottom
        val viewRectF = RectF(0f, 0f, _width * 1.0f, _height * 1.0f)
        calculateUtils = RectFCalculateUtil(viewRectF, originViewRectF)
        changeSystemWindowVisibility(true, isMaxFull)
        updateContent(0f)
        setBackground(1f)
    }

    private fun setContent() {
        (getControllerView().parent as? ViewGroup)?.removeView(getControllerView())
        this@BaseGestureFullScreenDialog.setContentView(getControllerView())
    }

    private fun initListeners() {
        scaleAnim = ZFullValueAnimator(object : ZFullValueAnimator.FullAnimatorListener {
            override fun onDurationChange(animation: ValueAnimator, duration: Float, isFull: Boolean) {
                if (isFull) {
                    updateContent(1 - duration)
                } else {
                    if (originInScreen == null) originInScreen = Point(getControllerView().scrollX, getControllerView().scrollY)
                    originInScreen?.let {
                        val sx = (it.x * (1f - duration)).roundToInt()
                        val sy = (it.y * (1f - duration)).roundToInt()
                        getControllerView().scrollTo(sx, sy)
                    }
                    val curOff = if (curScaleOffset <= 0f) 1f else curScaleOffset
                    val offset = (duration * curOff) + (1f - curOff)
                    updateContent(offset)
                }
            }

            override fun onAnimEnd(animation: Animator, isFull: Boolean) {
                updateContent(0.0f)
                isAnimRun = false
                originInScreen = null
                if (!isFull) dismissed()
                else onDisplayChanged(true)
                //                scrolled.y = originViewRectF?.bottom?.roundToInt()
                getControllerView().scrollTo(0, 0)
            }
        }, false).apply {
            duration = 220
        }
        touchListener = object : GestureTouchListener({ isAnimRun || isDismissing }) {
            override fun onEventEnd(formTrigDuration: Float): Boolean {
                return isAutoScaleFromTouchEnd(formTrigDuration)
            }

            override fun onTracked(offsetX: Float, offsetY: Float, easeY: Float, orientation: Orientation, formTrigDuration: Float) {
                setBackground(1f - formTrigDuration)
                followWithFinger(offsetX, offsetY)
                scaleWithOffset(easeY)
            }

            override fun onDoubleClick() {
                init(!isMaxFull)
            }
        }
        touchListener?.setPadding(0.15f, 0.13f)
        onKeyListener = View.OnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                dismiss()
                return@OnKeyListener false
            }
            true
        }
    }

    internal fun getControllerView(): View {
        return controllerView
    }

    private fun updateContent(offset: Float) {
        val rect = calculateUtils?.calculate(offset)
        getFrameLayoutParams(getControllerView(), rect)
    }

    private fun show(view: View) {
        if (isShowing || (view.context as? Activity)?.isFinishing == true) return
        view.setOnTouchListener(touchListener)
        view.setOnKeyListener(onKeyListener)
        show()
        isAnimRun = true
        scaleAnim?.start(true)
    }

    private fun dismissed() {
        onDisplayChanged(false)
        curScaleOffset = 0f
        getControllerView().setOnTouchListener(null)
        if (getControllerView().parent != null) (getControllerView().parent as? ViewGroup)?.removeView(getControllerView())
        vp.addView(getControllerView(), vlp)
        isDismissing = false
        super.dismiss()
    }

    private fun followWithFinger(x: Float, y: Float) {
        getControllerView().scrollTo(x.roundToInt(), y.roundToInt())
    }

    private fun scaleWithOffset(curYOffset: Float) {
        updateContent(curYOffset)
        curScaleOffset = 1 - curYOffset
    }

    private fun isAutoScaleFromTouchEnd(curYOffset: Float): Boolean {
        val isScaleAuto = curYOffset <= MAX_DEEP_RATIO
        if (isScaleAuto) {
            getControllerView().scrollTo(0, 0)
            scaleWithOffset(0f)
        } else {
            isAnimRun = true
            changeSystemWindowVisibility(false, isMaxFull = false)
            scaleAnim?.start(false)
        }
        return isScaleAuto
    }

    private fun getFrameLayoutParams(view: View, ref: RectF?) {
        if (ref == null) return
        val pl: Int = ref.left.roundToInt()
        val pt: Int = ref.top.roundToInt()
        val pr: Int = ref.right.roundToInt()
        val pb: Int = ref.bottom.roundToInt()
        val flp = FrameLayout.LayoutParams(originWidth, originHeight)
        flp.setMargins(pl, pt, pr, pb)
        flp.width = _width - (pl + pr)
        flp.height = _height - (pt + pb)
        view.layoutParams = flp
    }

    override fun dismiss() {
        if ((context as? Activity)?.isFinishing == true) {
            dismissed();super.dismiss()
        } else {
            if (isDismissing) return
            isDismissing = true
            isAutoScaleFromTouchEnd(1f)
        }
    }

    private fun changeSystemWindowVisibility(visible: Boolean, isMaxFull: Boolean) {
        val flag: Int = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val flagSystem: Int = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (visible) {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (isMaxFull) {
                window?.addFlags(flag)
                window?.decorView?.systemUiVisibility = flagSystem
            } else {
                window?.addFlags(flag or WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                window?.decorView?.systemUiVisibility = flagSystem or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
        } else {
            window?.decorView?.systemUiVisibility = systemUiFlags ?: flagSystem
            window?.clearFlags(flag)
        }
    }

    private fun getActivity(): Activity? {
        return getControllerView().context as? Activity
    }

    private fun getControllerViewRect(): RectF {
        val point = IntArray(2)
        getControllerView().getLocationInWindow(point)
        val x = point[0] * 1.0f
        val y = point[1] * 1.0f
        return RectF(x, y, x + originWidth, y + originHeight)
    }

    private fun setBackground(@FloatRange(from = 0.0, to = 1.0) duration: Float) {
        window?.setDimAmount(interpolator.getInterpolation(duration))
    }

    private fun getWindowSize(isMaxFull: Boolean): Point {
        return if (isMaxFull) {
            val windowSize = Point()
            (getActivity()?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.getRealSize(windowSize)
            windowSize
        } else {
            val rect = Rect()
            window?.windowManager?.defaultDisplay?.getRectSize(rect)
            Point(rect.right - rect.left, rect.bottom - rect.top)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            dismiss();return true
        }
        return super.onKeyDown(keyCode, event)
    }
}