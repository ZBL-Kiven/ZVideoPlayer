@file:Suppress("unused")

package com.zj.player.view

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.zj.player.R
import com.zj.player.logs.ZPlayerLogs
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * @author ZJJ on 2018/7/3.
 */
internal class VideoLoadingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {

    private var oldMode = DisplayMode.NONE
    private var disPlayViews: MutableMap<DisplayMode, Float>? = null
    private var contentView: View? = null
    private var vLoading: ProgressBar? = null
    private var vNoData: View? = null
    private var tvHint: TextView? = null
    private var tvRefresh: TextView? = null
    private var refresh: ((View) -> Unit)? = null
    private var bgColor: Int = 0
    private var needBackgroundColor: Int = 0
    private var oldBackgroundColor: Int = 0
    private var noDataRes = -1
    private var loadingRes = -1
    private var hintTextColor: Int = 0
    private var refreshTextColor: Int = 0
    private var loadingHint: String = ""
    private var noDataHint: String = ""
    private var refreshHint: String? = ""
    private var argbEvaluator: ArgbEvaluator? = null
    private var refreshEnableWithView = false
    private var valueAnimator: BaseLoadingValueAnimator? = null
    private val listener = object : BaseLoadingAnimatorListener {

        override fun onDurationChange(animation: ValueAnimator, duration: Float, mode: DisplayMode?) {
            synchronized(this@VideoLoadingView) {
                onAnimationFraction(animation.animatedFraction, duration, mode)
            }
        }

        override fun onAnimEnd(animation: Animator, mode: DisplayMode?) {
            synchronized(this@VideoLoadingView) {
                onAnimationFraction(1.0f, 1.0f, mode)
            }
        }
    }

    init {
        init(context, attrs)
        initView(context)
    }

    enum class DisplayMode(internal val value: Int) {
        NONE(0), LOADING(1), NO_DATA(2)
    }

    /**
     * when you set mode as NO_DATA/NET_ERROR ,
     * you can get the event when this view was clicked
     * and you can refresh content  when the  "onCallRefresh()" callback
     */
    fun setRefreshListener(refresh: (v: View) -> Unit) {
        this.refresh = refresh
        setOnClickListener {
            if (refreshEnableWithView && this@VideoLoadingView.refresh != null) {
                this@VideoLoadingView.refresh?.invoke(it)
            }
        }
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        if (attrs != null) {
            val array = context.obtainStyledAttributes(attrs, R.styleable.VideoLoadingView)
            try {
                bgColor = array.getColor(R.styleable.VideoLoadingView_zPlayer_backgroundFill, -1)
                noDataRes = array.getResourceId(R.styleable.VideoLoadingView_zPlayer_noDataRes, -1)
                loadingRes = array.getResourceId(R.styleable.VideoLoadingView_zPlayer_loadingRes, -1)
                hintTextColor = array.getColor(R.styleable.VideoLoadingView_zPlayer_hintColor, -1)
                refreshTextColor = array.getColor(R.styleable.VideoLoadingView_zPlayer_refreshTextColor, -1)
                loadingHint = array.getString(R.styleable.VideoLoadingView_zPlayer_loadingText) ?: ""
                noDataHint = array.getString(R.styleable.VideoLoadingView_zPlayer_noDataText) ?: ""
                refreshHint = array.getString(R.styleable.VideoLoadingView_zPlayer_refreshText)
            } catch (e: Exception) {
                ZPlayerLogs.onError(e)
            } finally {
                array.recycle()
            }
        }
        initView(context)
    }

    private fun initView(context: Context) {
        contentView = View.inflate(context, R.layout.z_player_loading_view, this)
        vNoData = f(R.id.blv_vNoData)
        vLoading = f(R.id.blv_pb)
        tvHint = f(R.id.blv_tvHint)
        tvRefresh = f(R.id.blv_tvRefresh)
        if (refreshHint != null && !refreshHint.isNullOrEmpty()) tvRefresh?.text = refreshHint
        if (hintTextColor != 0) tvHint?.setTextColor(hintTextColor)
        if (refreshTextColor != 0) tvRefresh?.setTextColor(refreshTextColor)
        argbEvaluator = ArgbEvaluator()
        disPlayViews = EnumMap(DisplayMode::class.java)
        disPlayViews?.put(DisplayMode.LOADING, 0.0f)
        tvHint?.text = loadingHint
    }

    private fun resetBackground() {
        setBackgroundColor(bgColor)
    }

    /**
     * @param drawableRes must be an animatorDrawable in progressBar;
     * @link call resetUi() after set this
     */
    fun setLoadingDrawable(drawableRes: Int): VideoLoadingView {
        this.loadingRes = drawableRes
        return this
    }

    //call resetUi() after set this
    fun setNoDataDrawable(drawableRes: Int): VideoLoadingView {
        this.noDataRes = drawableRes
        return this
    }

    //reset LOADING/NO_DATA/NET_ERROR drawable
    private fun resetUi() {
        if (loadingRes > 0) {
            val drawable = context.getDrawable(loadingRes)
            if (drawable != null) {
                val rect = vLoading?.indeterminateDrawable?.bounds
                if (rect != null) drawable.bounds = rect
                vLoading?.indeterminateDrawable = drawable
            }
        }
        if (noDataRes > 0) {
            vNoData?.setBackgroundResource(noDataRes)
        }
    }

    /**
     * just call setMode after this View got,
     * @param m      the current display mode you need;
     */
    @JvmOverloads
    fun setMode(m: DisplayMode, setNow: Boolean = false) {
        oldMode = m
        val isSameMode = m.value == oldMode.value
        tvHint?.text = getHintString(m)
        refreshEnableWithView = m == DisplayMode.NO_DATA
        tvRefresh?.visibility = if (refreshEnableWithView) View.VISIBLE else View.INVISIBLE
        if (setNow) {
            valueAnimator?.end()
            setViews(1f, m)
            setBackground(1f, m)
        } else {
            if (valueAnimator == null) {
                valueAnimator = BaseLoadingValueAnimator(listener)
                valueAnimator?.duration = DEFAULT_ANIM_DURATION
            } else {
                valueAnimator?.end()
            }
            disPlayViews?.put(m, 0.0f)
            if (!isSameMode) {
                resetBackground()
                needBackgroundColor = bgColor
                valueAnimator?.start(m)
            }
        }
    }

    private fun getHintString(mode: DisplayMode): String {
        return when (mode) {
            DisplayMode.LOADING -> if (loadingHint.isEmpty()) "LOADING" else loadingHint
            DisplayMode.NO_DATA -> if (noDataHint.isEmpty()) "no data found" else noDataHint
            else -> ""
        }
    }

    fun hideDelay(delayDismissTime: Int) {
        postDelayed({ setMode(DisplayMode.NONE, false) }, delayDismissTime.toLong())
    }

    @Synchronized
    private fun onAnimationFraction(duration: Float, offset: Float, curMode: DisplayMode?) {
        setViews(offset, curMode)
        setBackground(duration, curMode)
    }


    private fun setViews(offset: Float, curMode: DisplayMode?) {
        disPlayViews?.forEach { (key, curAlpha) ->
            val curSetView = getDisplayView(key)
            if (curSetView != null) {
                val newAlpha: Float
                if (key == curMode) {
                    //need show
                    if (curSetView.visibility != View.VISIBLE) {
                        curSetView.visibility = View.VISIBLE
                        curSetView.alpha = 0f
                    }
                    newAlpha = min(1.0f, max(0.0f, curAlpha) + offset)
                    curSetView.alpha = newAlpha
                } else {
                    //need hide
                    newAlpha = max(min(1.0f, curAlpha) - offset, 0f)
                    curSetView.alpha = newAlpha
                    if (newAlpha == 0f && curSetView.visibility != View.GONE) curSetView.visibility = View.GONE
                }
                disPlayViews?.put(key, newAlpha)
            }
        }
    }

    private fun setBackground(duration: Float, curMode: DisplayMode?) {
        if (curMode != DisplayMode.NONE) {
            if (visibility != View.VISIBLE) {
                alpha = 0f
                visibility = View.VISIBLE
            }
            if (alpha >= 1.0f) {
                if (oldBackgroundColor != needBackgroundColor) {
                    setBackgroundColor(needBackgroundColor)
                    oldBackgroundColor = needBackgroundColor
                }
            } else {
                alpha = min(1.0f, duration)
                if (oldBackgroundColor != needBackgroundColor) {
                    val curBackgroundColor = argbEvaluator?.evaluate(duration, oldBackgroundColor, needBackgroundColor) as Int
                    oldBackgroundColor = curBackgroundColor
                    setBackgroundColor(curBackgroundColor)
                }
            }
        } else {
            alpha = 1.0f - duration
            if (alpha <= 0.05f) {
                alpha = 0f
                oldBackgroundColor = 0
                setBackgroundColor(oldBackgroundColor)
                visibility = View.GONE
            }
        }
    }

    private fun getDisplayView(mode: DisplayMode): View? {
        return when (mode) {
            DisplayMode.NO_DATA -> vNoData
            DisplayMode.LOADING -> vLoading
            else -> null
        }
    }

    private fun <T : View> f(id: Int): T? {
        return contentView?.findViewById<T>(id)
    }

    private class BaseLoadingValueAnimator constructor(private var listener: BaseLoadingAnimatorListener?) : ValueAnimator() {

        private var curMode: DisplayMode? = null
        private var curDuration: Float = 0.toFloat()
        private var isCancel: Boolean = false

        fun start(mode: DisplayMode) {
            if (isRunning) cancel()
            this.curMode = mode
            super.start()
        }

        override fun cancel() {
            removeAllListeners()
            if (listener != null) listener = null
            isCancel = true
            super.cancel()
        }

        init {
            setFloatValues(0.0f, 1.0f)
            addListener(object : AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    if (curDuration != 0f) curDuration = 0f
                }

                override fun onAnimationEnd(animation: Animator) {
                    curDuration = 0f
                    if (isCancel) return
                    if (listener != null) listener?.onAnimEnd(animation, curMode)
                }

                override fun onAnimationCancel(animation: Animator) {
                    curDuration = 0f
                }

                override fun onAnimationRepeat(animation: Animator) {
                    curDuration = 0f
                }
            })

            addUpdateListener(AnimatorUpdateListener { animation ->
                if (isCancel) return@AnimatorUpdateListener
                if (listener != null) {
                    val duration = animation.animatedValue as Float
                    val offset = duration - curDuration
                    listener?.onDurationChange(animation, offset, curMode)
                    curDuration = duration
                }
            })
        }

        fun setAnimatorListener(listener: BaseLoadingAnimatorListener) {
            this.listener = listener
        }
    }

    interface BaseLoadingAnimatorListener {

        fun onDurationChange(animation: ValueAnimator, duration: Float, mode: DisplayMode?)

        fun onAnimEnd(animation: Animator, mode: DisplayMode?)
    }

    companion object {

        private const val DEFAULT_ANIM_DURATION = 400L
    }
}
