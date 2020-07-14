package com.zj.player.full

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable.Orientation
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.annotation.FloatRange
import androidx.core.view.children
import com.zj.player.R
import com.zj.player.anim.ZFullValueAnimator
import java.lang.IllegalArgumentException
import kotlin.math.roundToInt


class BaseGestureFullScreenDialog private constructor(private val controllerView: View, contentLayout: Int, private val onFullScreenListener: FullScreenListener) : Dialog(controllerView.context, R.style.BaseGestureFullScreenDialogStyle) {

    companion object {
        private const val MAX_DEEP_RATIO = 0.55f
        fun show(view: View, contentLayout: Int = -1, onFullScreenListener: FullScreenListener): BaseGestureFullScreenDialog {
            return BaseGestureFullScreenDialog(view, contentLayout, onFullScreenListener)
        }
    }

    private val systemUiFlags = getActivity()?.window?.decorView?.systemUiVisibility
    private var _width: Float = 0f
    private var _height: Float = 0f
    private val originWidth: Int = getControllerView().measuredWidth
    private val originHeight: Int = getControllerView().measuredHeight
    private var calculateUtils: RectFCalculateUtil? = null
    private var originInScreen: Point? = null
    private var curScaleOffset: Float = 1.0f
    private var isAnimRun = false
    private var scaleAnim: ZFullValueAnimator? = null
    private var touchListener: GestureTouchListener? = null
    private var isDismissing = false
    private val interpolator = DecelerateInterpolator(1.5f)
    private var isMaxFull = false
    private val vp: ViewGroup? = controllerView.parent as? ViewGroup
    private val vlp: ViewGroup.LayoutParams? = controllerView.layoutParams
    private val originViewRectF: RectF
    private var contentLayoutView: View? = null
    private var realWindowSize = Point()

    init {
        this.setCanceledOnTouchOutside(false)
        this.setCancelable(false)
        val cps = getViewPoint(controllerView)
        originViewRectF = RectF(cps.x, cps.y, cps.x + originWidth, cps.y + originHeight)
        (controllerView.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.getRealSize(realWindowSize)
        if (contentLayout > 0) contentLayoutView = View.inflate(controllerView.context, contentLayout, null)
        changeSystemWindowVisibility(true)
        setContent(isMaxFull)
        initListeners()
        showAnim(getControllerView(), isMaxFull)
    }

    private fun setContent(isMaxFull: Boolean, isResizeCalculate: Boolean = false) {
        this.isMaxFull = isMaxFull
        try {
            (getControllerView().parent as? ViewGroup)?.removeView(getControllerView())
            if (isMaxFull || contentLayoutView == null) {
                this@BaseGestureFullScreenDialog.setContentView(getControllerView());return
            }
            contentLayoutView?.let {
                (it as? ViewGroup)?.clipChildren = false
                it.findViewById<ViewGroup>(R.id.player_gesture_full_screen_content)?.let { v ->
                    v.addView(controllerView, vlp)
                    v.clipChildren = false
                } ?: (it as? ViewGroup)?.addView(controllerView, vlp) ?: throw IllegalArgumentException("the content layout view your set is not container a view group that id`s [R.id.playerFullScreenContent] ,and your content layout is not a view parent!")
                this@BaseGestureFullScreenDialog.setContentView(it)
                onFullScreenListener.onContentLayoutInflated(it)
            }
        } finally {
            if (isResizeCalculate) init(isMaxFull)
        }
    }

    private fun showAnim(view: View, isMaxFull: Boolean) {
        fun start() {
            init(isMaxFull)
            isAnimRun = true
            scaleAnim?.start(true)
        }
        if (isShowing || getActivity()?.isFinishing == true) return
        show()
        view.setOnTouchListener(touchListener)
        contentLayoutView?.let {
            it.post {
                start()
            }
        } ?: {
            start()
        }.invoke()
    }

    private fun init(isMaxFull: Boolean) {
        val viewRectF = getWindowSize(isMaxFull)
        _width = viewRectF.right - viewRectF.left
        _height = viewRectF.bottom - viewRectF.top
        calculateUtils = RectFCalculateUtil(viewRectF, originViewRectF)
        updateContent(0f)
        setBackground(1f)
    }


    private fun dismissed() {
        onFullScreenListener.onDisplayChanged(false)
        curScaleOffset = 0f
        getControllerView().setOnTouchListener(null)
        if (getControllerView().parent != null) (getControllerView().parent as? ViewGroup)?.removeView(getControllerView())
        vp?.addView(getControllerView(), vlp)
        isDismissing = false
        super.dismiss()
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
                isAnimRun = false
                originInScreen = null
                if (!isFull) dismissed()
                else onFullScreenListener.onDisplayChanged(true)
            }
        }, false).apply {
            duration = 220
        }
        touchListener = object : GestureTouchListener({ isAnimRun || isDismissing }) {
            override fun onEventEnd(formTrigDuration: Float): Boolean {
                (controllerView.parent as? ViewGroup)?.clipChildren = true
                return isAutoScaleFromTouchEnd(formTrigDuration)
            }

            override fun onTracked(offsetX: Float, offsetY: Float, easeY: Float, orientation: Orientation, formTrigDuration: Float) {
                (controllerView.parent as? ViewGroup)?.clipChildren = false
                setBackground(1f - formTrigDuration)
                followWithFinger(offsetX, offsetY)
                scaleWithOffset(easeY)
            }

            override fun onDoubleClick() {
                contentLayoutView?.let {
                    setContent(!isMaxFull, true)
                    onFullScreenListener.onFullMaxChanged(isMaxFull)
                }
            }
        }
        touchListener?.setPadding(0.15f, 0.13f)
    }

    internal fun getControllerView(): View {
        return controllerView
    }

    private fun updateContent(offset: Float) {
        val rect = calculateUtils?.calculate(offset)
        getFrameLayoutParams(getControllerView(), rect)
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
            setBackground(1f)
        } else {
            isAnimRun = true
            changeSystemWindowVisibility(false)
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
        val flp = FrameLayout.LayoutParams((_width - (pl + pr)).roundToInt(), (_height - (pt + pb)).roundToInt())
        flp.setMargins(pl, pt, pr, pb)
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

    private fun changeSystemWindowVisibility(visible: Boolean) {
        val flag: Int = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        val flagSystem: Int = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        if (visible) {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.addFlags(flag)
            window?.decorView?.systemUiVisibility = flagSystem
        } else {
            window?.decorView?.systemUiVisibility = systemUiFlags ?: flagSystem
            window?.clearFlags(flag)
        }
    }

    private fun getActivity(): Activity? {
        return getControllerView().context as? Activity
    }

    private fun setBackground(@FloatRange(from = 0.0, to = 1.0) duration: Float) {
        val d = interpolator.getInterpolation(duration)
        window?.setDimAmount((duration * 0.85f) + 0.15f)
        (contentLayoutView as? ViewGroup)?.let {
            it.children.forEach { cv ->
                if (cv.id != R.id.player_gesture_full_screen_content) {
                    cv.alpha = d
                }
            }
        }
    }

    private fun getWindowSize(isMaxFull: Boolean): RectF {
        return if (isMaxFull || contentLayoutView == null) {
            val rp = getViewPoint(window?.decorView)
            val w = window?.decorView?.width ?: 0
            val h = window?.decorView?.height ?: 0
            RectF(rp.x, rp.y, rp.x + w, rp.y + h)
        } else {
            contentLayoutView?.let { cv ->
                cv.findViewById<View>(R.id.player_gesture_full_screen_content)?.let { fv ->
                    val pf = getViewPoint(fv)
                    RectF(pf.x, pf.y, pf.x + fv.width, pf.y + fv.height)
                } ?: {
                    val pf = getViewPoint(cv)
                    RectF(pf.x, pf.y, pf.x + cv.width, pf.y + cv.height)
                }.invoke()
            } ?: throw IllegalArgumentException()
        }
    }

    private fun getViewPoint(view: View?): PointF {
        val point = IntArray(2)
        view?.getLocationOnScreen(point)
        val x = point[0] * 1.0f
        val y = point[1] * 1.0f
        return PointF(x, y)
    }

    fun onResume() {
        changeSystemWindowVisibility(true)
    }

    fun onStopped() {
        changeSystemWindowVisibility(false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            dismiss();return true
        }
        return super.onKeyDown(keyCode, event)
    }
}