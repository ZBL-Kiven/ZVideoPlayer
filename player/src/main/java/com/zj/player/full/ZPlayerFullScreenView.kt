package com.zj.player.full

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.annotation.FloatRange
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import com.zj.player.R
import com.zj.player.anim.ZFullValueAnimator
import com.zj.player.config.forEach
import com.zj.player.logs.ZPlayerLogs
import com.zj.player.z.ZVideoView
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
internal class ZPlayerFullScreenView constructor(context: Context) : FrameLayout(context) {

    companion object {
        private const val MAX_DEEP_RATIO = 0.55f
        private const val HANDLE_ORIENTATION_CHANGE = 19285
        internal var fullScreenViews = HashMap<Int, ZPlayerFullScreenView>()

        fun start(context: Context, config: FullScreenConfig) {
            val cachedFullScreenView = fullScreenViews[context.hashCode()] ?: ZPlayerFullScreenView(context)
            cachedFullScreenView.let {
                it.config = config
                it.isMaxFull = config.isDefaultMaxScreen
                it.originWidth = config.getControllerView()?.measuredWidth ?: 0
                it.originHeight = config.getControllerView()?.measuredHeight ?: 0
                it.vp = config.getControllerView()?.parent as? ViewGroup
                it.vlp = config.getControllerView()?.layoutParams
                it.mDecorView = (context as? Activity)?.findViewById(android.R.id.content) as? ViewGroup
                if (it.mDecorView == null) {
                    ZPlayerLogs.onError("the full screen view open failed ,case the [context $context] was not an Activity!", true);return@let
                }
                it.startFullScreen()
            }
        }
    }

    private fun startFullScreen() {
        fullScreenViews[context.hashCode()] = this
        (this.parent as? ViewGroup?)?.let {
            it.removeView(this)
            mDecorView?.removeView(this)
        }
        mDecorView?.addView(this, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        runWithControllerView {
            val wm = (it.context?.applicationContext?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm?.currentWindowMetrics?.bounds ?: Rect()
                realWindowSize.set(bounds.width(), bounds.height())
                Log.e("----- ", "11111 ${realWindowSize.x} , ${realWindowSize.y}")
            } else {
                val dm = Resources.getSystem().displayMetrics
                realWindowSize.set(dm.widthPixels, dm.heightPixels)
            }
            if (realWindowSize.x <= 0 || realWindowSize.y <= 0) {
                @Suppress("DEPRECATION") wm?.defaultDisplay?.getRealSize(realWindowSize)
                Log.e("----- ", "33333 ${realWindowSize.x} , ${realWindowSize.y}")
            }
            if (!config.isDefaultMaxScreen && config.contentLayout > 0) contentLayoutView = View.inflate(it.context, config.contentLayout, null)
            val actionViews = mutableMapOf<Int, View>()
            hiddenAllChildIfNotScreenContent(contentLayoutView, actionViews)
            actionViews.forEach { (_, u) ->
                u.alpha = 0.0f
            }
            if (childCount > 0) removeAllViews()
            backgroundView = View(context)
            backgroundView?.setBackgroundColor(Color.BLACK)
            backgroundView?.alpha = 0f
            backgroundView?.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            this.addView(backgroundView, 0)
            config.preToFullMaxChange {
                post { setContent(it, isMaxFull) }
            }
        }
        screenUtil = ScreenOrientationListener(WeakReference(context)) {
            fullHandler.removeMessages(HANDLE_ORIENTATION_CHANGE)
            if (!isScreenRotateLocked && isMaxFull) {
                fullHandler.sendMessageDelayed(Message.obtain().apply { what = HANDLE_ORIENTATION_CHANGE;obj = it }, 100)
            }
        }
        screenRotationsChanged(true)
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
    }

    private lateinit var config: FullScreenConfig
    private var mDecorView: ViewGroup? = null
    private var _width: Float = 0f
    private var _height: Float = 0f
    private var originWidth: Int = 0
    private var originHeight: Int = 0
    private var calculateUtils: RectFCalculateUtil? = null
    private var originInScreen: Point? = null
    private var curScaleOffset: Float = 1.0f
    private var isAnimRun = false
    private var scaleAnim: ZFullValueAnimator? = null
    private var isDismissing = false
    private val interpolator = DecelerateInterpolator(1.5f)
    private var isMaxFull: Boolean = false
    private var vp: ViewGroup? = null
    private var vlp: ViewGroup.LayoutParams? = null
    private var originViewRectF: RectF? = null
    private var contentLayoutView: View? = null
    private var backgroundView: View? = null
    private var realWindowSize = Point()
    private var screenUtil: ScreenOrientationListener? = null
    private var isScreenRotateLocked: Boolean = false
    private var fullHandler = Handler(Looper.getMainLooper()) {
        when (it.what) {
            HANDLE_ORIENTATION_CHANGE -> {
                (it.obj as? RotateOrientation)?.let { o -> curScreenRotation = o }
            }
        }
        return@Handler false
    }

    private var curScreenRotation: RotateOrientation? = null
        set(value) {
            if (field == value) return
            field = value
            if (value == null) return
            if (config.allowReversePortrait || value != RotateOrientation.P1) {
                (context as? Activity)?.let { act ->
                    act.requestedOrientation = when (value) {
                        RotateOrientation.L0 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                        RotateOrientation.L1 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        RotateOrientation.P0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        RotateOrientation.P1 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    }
                }
            }
        }

    private fun initCalculate() {
        val cps = getViewPoint(vp)
        originViewRectF = RectF(cps.x, cps.y, cps.x + originWidth, cps.y + originHeight)
        val viewRectF = getWindowSize(isMaxFull)
        _width = viewRectF.right - viewRectF.left
        _height = viewRectF.bottom - viewRectF.top
        calculateUtils = RectFCalculateUtil(viewRectF, originViewRectF ?: return)
    }

    private fun setContent(controller: View, isMaxFull: Boolean, isInit: Boolean = true) {
        if (this.isMaxFull && !isMaxFull && curScreenRotation?.isLandSpace() != false) curScreenRotation = RotateOrientation.P0
        this.isMaxFull = isMaxFull
        changeSystemWindowVisibility(true)
        try {
            val controllerNeedAdd = (controller.parent as? ViewGroup)?.let { parent ->
                if (isMaxFull || contentLayoutView == null) {
                    if (parent != this) {
                        parent.removeView(controller); true
                    } else false
                } else if (parent != contentLayoutView) {
                    parent.removeView(controller); true
                } else false
            } ?: if (controller.parent !is ViewGroup) throw IllegalArgumentException("controllers parent is not a viewGroup ,so what's that?") else true
            if (isMaxFull || contentLayoutView == null) {
                if (contentLayoutView != null) (contentLayoutView?.parent as? ViewGroup)?.removeView(contentLayoutView)
                if (controllerNeedAdd) this@ZPlayerFullScreenView.addView(controller);return
            }
            contentLayoutView?.let {
                val contentNeedAdd = (it.parent as? ViewGroup)?.let { parent ->
                    if (parent != this) {
                        parent.removeView(it);true
                    } else false
                } ?: true
                if (contentNeedAdd) this@ZPlayerFullScreenView.addView(it)
                it.findViewById<ViewGroup>(R.id.player_gesture_full_screen_content)?.let { v ->
                    if (controllerNeedAdd) v.addView(controller, vlp)
                } ?: if (controllerNeedAdd) (it as? ViewGroup)?.addView(controller, vlp) ?: throw IllegalArgumentException("the content layout view your set is not container a view group that id`s [R.id.playerFullScreenContent] ,and your content layout is not a view group!")
            }
        } finally {
            try {
                notifyContentViewChanged(null)
            } catch (e: Exception) {
                ZPlayerLogs.onError("player error with fullscreen notify changed ! case: ${e.message}")
            }
            initCalculate()
            updateContent(1.0f)
            if (isInit) {
                initListeners()
                showAnim()
            }
        }
    }

    private fun showAnim() {
        post {
            if (config.transactionAnimDuration <= 0) {
                setBackground(1f, true)
                onDisplayChange(true)
                if (config.isAnimDurationOnlyStart) config.resetDurationWithDefault()
            } else {
                isAnimRun = true
                startScaleAnim(true)
            }
        }
    }

    private fun dismissed(fromAnimEnd: Boolean = false) {
        scaleAnim?.let {
            if (!fromAnimEnd && it.isRunning) {
                val toEnd = !it.isFull
                it.end()
                if (toEnd) return
            }
        }
        fullHandler.removeCallbacksAndMessages(null)
        config.preToDismiss {
            onDisplayChange(false)
            runWithControllerView {
                if (it.parent != null) (it.parent as? ViewGroup)?.removeView(it)
                vp?.addView(it, vlp)
            }
            curScaleOffset = 0f
            screenUtil?.release()
            scaleAnim?.cancel()
            scaleAnim = null
            screenUtil = null
            isDismissing = false
            calculateUtils = null
            backgroundView = null
            config.clear()
            fullScreenViews.remove(context.hashCode())
            mDecorView?.removeView(this)
        }
    }

    private fun startScaleAnim(isFull: Boolean) {
        if (scaleAnim?.isRunning == true) scaleAnim?.end()
        scaleAnim?.duration = (config.transactionAnimDuration.coerceAtLeast(0)).toLong()
        scaleAnim?.start(isFull)
    }

    private fun initListeners() {
        scaleAnim = ZFullValueAnimator(object : ZFullValueAnimator.FullAnimatorListener() {

            override fun onStart() {
                initCalculate()
            }

            override fun onDurationChange(animation: ValueAnimator, duration: Float, isFull: Boolean, obj: Any?) {
                if (isFull) {
                    updateContent(1 - duration)
                    setBackground(duration, true)
                } else {
                    clipChildren = false
                    setBackground(1 - duration, isFromStart = true, isDownTo = true)
                    runWithControllerView { cv ->
                        (cv.parent as? ViewGroup)?.clipChildren = false
                        if (originInScreen == null) originInScreen = Point(cv.scrollX, cv.scrollY)
                        originInScreen?.let {
                            val sx = (it.x * (1f - duration)).roundToInt()
                            val sy = (it.y * (1f - duration)).roundToInt()
                            cv.scrollTo(sx, sy)
                        }
                    }
                    val curOff = if (curScaleOffset <= 0f) 1f else curScaleOffset
                    val offset = (duration * curOff) + (1f - curOff)
                    updateContent(offset)
                }
            }

            override fun onAnimEnd(animation: Animator, isFull: Boolean, obj: Any?) {
                isAnimRun = false
                originInScreen = null
                if (!isFull) dismissed(true)
                else onDisplayChange(true)
            }
        }, false)
    }

    fun isMaxFull(): Boolean {
        return isMaxFull
    }

    fun isInterruptTouchEvent(): Boolean {
        return isAnimRun || isDismissing
    }

    fun onEventEnd(formTrigDuration: Float, parseAutoScale: Boolean): Boolean {
        return runWithControllerView {
            (it.parent as? ViewGroup)?.clipChildren = true
            if (parseAutoScale) isAutoScaleFromTouchEnd(formTrigDuration, true) else false
        } ?: false
    }

    fun onDoubleClick() {
        if (config.isDefaultMaxScreen || !config.fullMaxScreenEnable) return
        config.preToFullMaxChange {
            contentLayoutView?.let { _ ->
                runWithControllerView {
                    if (!isMaxFull) {
                        isScreenRotateLocked = config.defaultScreenOrientation != ZVideoView.LOCK_SCREEN_UNSPECIFIED
                        checkSelfScreenLockAvailable(isScreenRotateLocked)
                    }
                    setContent(it, !isMaxFull, isInit = false)
                    config.onFullContentListener?.onFullMaxChanged(this@ZPlayerFullScreenView, isMaxFull)
                    if (isMaxFull) {
                        curScreenRotation = when (config.defaultScreenOrientation) {
                            0 -> RotateOrientation.L1
                            1 -> RotateOrientation.P0
                            else -> null
                        }
                    }
                }
            }
        }
    }

    fun onTracked(isStart: Boolean, offsetX: Float, offsetY: Float, easeY: Float, formTrigDuration: Float) {
        if (isStart) initCalculate()
        runWithControllerView {
            (it.parent as? ViewGroup)?.clipChildren = false
            setBackground(1f - formTrigDuration)
            followWithFinger(offsetX, offsetY)
            scaleWithOffset(easeY)
            onTracked(isStart, false, formTrigDuration)
        }
    }

    private fun <T> runWithControllerView(i: (View) -> T): T? {
        val cv = config.getControllerView()
        if (cv == null) {
            ZPlayerLogs.debug("case the controller view is null ,so what`s your displaying wishes")
        } else {
            return i(cv)
        }
        return null
    }

    private fun updateContent(offset: Float) {
        runWithControllerView {
            val rect = calculateUtils?.calculate(offset)
            getFrameLayoutParams(it, rect)
            it.post { it.requestLayout() }
        }
    }

    private fun followWithFinger(x: Float, y: Float) {
        if (isMaxFull) return
        runWithControllerView { it.scrollTo(x.roundToInt(), y.roundToInt()) }
    }

    private fun scaleWithOffset(curYOffset: Float) {
        if (isMaxFull) return
        updateContent(curYOffset)
        curScaleOffset = 1 - curYOffset
    }

    private fun isAutoScaleFromTouchEnd(curYOffset: Float, fromUser: Boolean): Boolean {
        if (isMaxFull && fromUser) return true
        isDismissing = true
        val isScaleAuto = curYOffset <= MAX_DEEP_RATIO
        if (isScaleAuto) {
            runWithControllerView { it.scrollTo(0, 0) }
            scaleWithOffset(0f)
            setBackground(1f, true)
            onTracked(false, isEnd = true, formTrigDuration = 0f)
            isDismissing = false
        } else {
            config.preToDismiss {
                isAnimRun = true
                changeSystemWindowVisibility(false)
                startScaleAnim(false)
            }
        }
        return isScaleAuto
    }

    private fun getFrameLayoutParams(view: View, ref: RectF?) {
        if (ref == null) return
        val pl: Int = ref.left.roundToInt()
        val pt: Int = ref.top.roundToInt()
        val pr: Int = ref.right.roundToInt()
        val pb: Int = ref.bottom.roundToInt()
        val w = (_width - (pl + pr)).roundToInt()
        val h = (_height - (pt + pb)).roundToInt()
        val flp = LayoutParams(w, h)
        flp.setMargins(pl, pt, pr, pb)
        view.layoutParams = flp
    }

    fun dismiss() {
        if (curScreenRotation?.isLandSpace() != false) curScreenRotation = RotateOrientation.P0
        screenRotationsChanged(false)
        if (getActivity()?.isFinishing == true) {
            dismissed();mDecorView?.removeView(this)
        } else {
            if (isDismissing) return
            isAutoScaleFromTouchEnd(1f, false)
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun changeSystemWindowVisibility(visible: Boolean) {
        getActivity()?.let {
            it.window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
            val ime = ImmersionBar.with(it)
            if (visible) {
                ime.transparentStatusBar()
                if (isMaxFull || config.translateNavigation) {
                    ime.transparentNavigationBar().hideBar(BarHide.FLAG_HIDE_BAR)
                } else {
                    ime.fullScreen(false).hideBar(BarHide.FLAG_HIDE_STATUS_BAR).navigationBarEnable(true).navigationBarColorInt(Color.BLACK)
                }
            } else {
                ime.hideBar(BarHide.FLAG_SHOW_BAR).autoDarkModeEnable(true).navigationBarEnable(false)
            }
            ime.init()
            it.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun getActivity(): Activity? {
        return (context as? Activity)
    }

    /**
     * Reduce the transparency of background and other unimportant (non-playing components) views when sliding
     * */
    private fun setBackground(@FloatRange(from = 0.0, to = 1.0) duration: Float, isFromStart: Boolean = false, isDownTo: Boolean = false) {
        if (isMaxFull) return
        val d = interpolator.getInterpolation(duration)
        if (isDownTo) {
            var cur = backgroundView?.alpha ?: 0f
            if (duration < cur) cur = duration
            backgroundView?.alpha = cur
        } else {
            backgroundView?.alpha = if (isFromStart) duration else ((duration * 0.75f) + 0.25f)
        }
        val actionViews = mutableMapOf<Int, View>()
        hiddenAllChildIfNotScreenContent(contentLayoutView, actionViews)
        actionViews.forEach { (_, u) ->
            if (d <= 0 || u.alpha >= d || !isDownTo) u.alpha = max(0f, d)
        }
    }

    /**
     * Return all other views and their parents that are not player views to [views],
     * which needs to be noted that this View is not included when view.id is not set.
     * The function considers that the View without ID is not used as a display estimate, and improves performance.
     * */
    private fun hiddenAllChildIfNotScreenContent(child: View?, views: MutableMap<Int, View>) {
        (child as? ViewGroup)?.forEach { cv ->
            if (cv.id == R.id.player_gesture_full_screen_content) {
                removeSelfActionParent(cv, views)
            } else {
                views[cv.id] = cv
                hiddenAllChildIfNotScreenContent(cv, views)
            }
        }
    }

    /**
     * only used by [hiddenAllChildIfNotScreenContent]
     * */
    private fun removeSelfActionParent(view: View, views: MutableMap<Int, View>) {
        if (view == contentLayoutView) return
        (view.parent as? ViewGroup)?.let {
            views.remove(it.id)
            removeSelfActionParent(it, views)
        }
    }

    private fun getWindowSize(isMaxFull: Boolean): RectF {
        return if (isMaxFull || contentLayoutView == null) {
            val rp = getViewPoint(mDecorView)
            val w = mDecorView?.width ?: 0
            val h = mDecorView?.height ?: 0
            RectF(rp.x, rp.y, rp.x + w, rp.y + h)
        } else {
            contentLayoutView?.let { cv ->
                cv.findViewById<View>(R.id.player_gesture_full_screen_content)?.let { fv ->
                    val pf = getViewPoint(fv)
                    RectF(pf.x, pf.y, pf.x + fv.width, pf.y + fv.height)
                } ?: run {
                    val pf = getViewPoint(cv)
                    RectF(pf.x, pf.y, pf.x + cv.width, pf.y + cv.height)
                }
            } ?: throw IllegalArgumentException()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        _width = w * 1.0f
        _height = h * 1.0f
        super.onSizeChanged(w, h, ow, oh)
        initCalculate()
        updateContent(0f)
    }

    private fun getViewPoint(view: View?): PointF {
        val point = IntArray(2)
        view?.getLocationOnScreen(point)
        val x = point[0] * 1.0f
        val y = point[1] * 1.0f
        return PointF(x, y)
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun screenRotationsChanged(isRotateEnable: Boolean) {
        if (isRotateEnable) screenUtil?.enable() else {
            screenUtil?.disable();curScreenRotation = null
        }
    }

    fun lockScreenRotation(isLock: Boolean): Boolean {
        isScreenRotateLocked = isLock
        return checkSelfScreenLockAvailable(isLock)
    }

    fun isLockedCurrent(): Boolean {
        return screenUtil?.let {
            if (!it.checkAccelerometerSystem()) true else isScreenRotateLocked
        } ?: false
    }

    fun notifyContentViewChanged(pl: Any?) {
        contentLayoutView?.let { config.onFullContentListener?.onContentLayoutInflated(it, pl) }
    }

    private fun onTracked(isStart: Boolean, isEnd: Boolean, formTrigDuration: Float) {
        if (!isMaxFull) config.onFullContentListener?.onTrack(isStart, isEnd, formTrigDuration) ?: config.onFullScreenListener?.onTrack(isStart, isEnd, formTrigDuration)
    }

    private fun onDisplayChange(isShow: Boolean) {
        config.onFullContentListener?.onDisplayChanged(isShow, config.payloads) ?: config.onFullScreenListener?.onDisplayChanged(isShow, config.payloads)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        event?.let {
            if (config.onFullContentListener?.onKeyEvent(event.keyCode, event) == true) return true
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (!isAnimRun) {
                    dismiss();return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        isScreenRotateLocked = screenUtil?.checkAccelerometerSystem() != false || !hasFocus
        if (hasFocus) changeSystemWindowVisibility(true)
        checkSelfScreenLockAvailable(isScreenRotateLocked)
        config.onFullContentListener?.onFocusChange(this, isMaxFull)
        if (hasFocus) {
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }
    }

    private fun checkSelfScreenLockAvailable(newState: Boolean): Boolean {
        val b = screenUtil?.checkAccelerometerSystem() ?: false
        var isLock = false
        return try {
            if (!b) {
                isLock = true;false
            } else {
                isLock = newState;true
            }
        } finally {
            screenUtil?.lockOrientation(isLock)
        }
    }
}