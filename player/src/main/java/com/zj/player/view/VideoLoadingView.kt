@file:Suppress("unused")

package com.zj.player.view

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.zj.player.R
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * @author ZJJ on 2018/7/3.
 */
internal class VideoLoadingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {

    private var contentView: View? = null
    private var vLoading: ProgressBar? = null
    private var vNoData: View? = null
    private var tvHint: TextView? = null
    private var tvRefresh: TextView? = null

    private var oldMode = DisplayMode.INIT
    private var disPlayViews: MutableMap<DisplayMode, Float>? = null
    private var refresh: ((View) -> Unit)? = null

    private var bgColor: Int = 0
    private var needBackgroundColor: Int = 0
    private var oldBackgroundColor: Int = 0
    private var loadingDrawableRes: Int = 0
    private var noDataDrawableRes: Int = 0

    private var loadingHint: String = ""
    private var noDataHint: String = ""
    private var refreshHint: String? = ""

    private var argbEvaluator: ArgbEvaluator? = null
    private var refreshEnableWithView = false
    private var valueAnimator: BaseLoadingValueAnimator? = null

    private var loadingWidth = 0f
    private var loadingHeight = 0f
    private var noDataWidth = 0f
    private var noDataHeight = 0f

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
        initView(context)
    }

    enum class DisplayMode(internal val value: Int) {
        INIT(-1), NONE(0), LOADING(1), NO_DATA(2), DISMISS(3)
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

    private fun initView(context: Context) {
        contentView = View.inflate(context, R.layout.z_player_loading_view, this)
        vNoData = f(R.id.blv_vNoData)
        vLoading = f(R.id.blv_pb)
        tvHint = f(R.id.blv_tvHint)
        tvRefresh = f(R.id.blv_tvRefresh)
        argbEvaluator = ArgbEvaluator()
        disPlayViews = EnumMap(DisplayMode::class.java)
        disPlayViews?.put(DisplayMode.LOADING, 0.0f)
    }

    fun setBackground(c: Int) {
        val color = try {
            ContextCompat.getColor(context, c)
        } catch (e: Exception) {
            c
        }
        bgColor = color
        setBackgroundColor(bgColor)
    }

    fun setHintTextColor(c: Int) {
        val color = try {
            ContextCompat.getColor(context, c)
        } catch (e: Exception) {
            c
        }
        if (color != -1) tvHint?.setTextColor(color)
    }

    fun setRefreshTextColor(c: Int) {
        val color = try {
            ContextCompat.getColor(context, c)
        } catch (e: Exception) {
            c
        }
        if (color != -1) tvRefresh?.setTextColor(color)
    }

    fun setLoadingText(txt: String) {
        this.loadingHint = txt
    }

    fun setFailText(txt: String) {
        this.noDataHint = txt
    }

    fun setHintTextSize(size: Float) {
        tvHint?.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
    }

    fun setRefreshText(txt: String) {
        this.refreshHint = txt
    }

    fun setRefreshTextSize(size: Float) {
        tvRefresh?.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
    }

    fun setDrawableSize(loadingWidth: Float, loadingHeight: Float, noDataWith: Float, noDataHeight: Float) {
        val dst = context.resources.displayMetrics.density
        this.loadingWidth = (dst * if (loadingWidth > 0) loadingWidth else 45f) + 0.5f
        this.loadingHeight = (dst * (if (loadingHeight > 0) loadingHeight else 45f)) + 0.5f
        this.noDataWidth = (dst * if (noDataWith > 0) noDataWith else 45f) + 0.5f
        this.noDataHeight = (dst * if (noDataHeight > 0) noDataHeight else 45f) + 0.5f
        resetBg()
    }

    fun setLoadingDrawable(loadingDrawableRes: Int) {
        this.loadingDrawableRes = loadingDrawableRes
    }

    fun setNoDataDrawable(noDataDrawableRes: Int) {
        this.noDataDrawableRes = noDataDrawableRes
    }

    private fun resetBg() {
        if (noDataDrawableRes > 0) {
            val lp = vNoData?.layoutParams
            lp?.width = noDataWidth.toInt()
            lp?.height = noDataHeight.toInt()
            vNoData?.layoutParams = lp
            vNoData?.setBackgroundResource(noDataDrawableRes)
        }
        if (loadingDrawableRes > 0) {
            val lp = vLoading?.layoutParams
            lp?.width = loadingWidth.toInt()
            lp?.height = loadingHeight.toInt()
            vLoading?.layoutParams = lp
            val drawable = context.getDrawable(loadingDrawableRes)
            vLoading?.indeterminateDrawable = drawable
        }
    }

    /**
     * just call setMode after this View got,
     * @param m      the current display mode you need;
     */
    @JvmOverloads
    fun setMode(m: DisplayMode, setNow: Boolean = false) {
        if (m == DisplayMode.INIT) return
        var mode = m
        if (m == DisplayMode.NONE) mode = DisplayMode.DISMISS
        val isSameMode = mode.value == oldMode.value
        if (isSameMode) return
        oldMode = m
        tvHint?.text = getHintString(mode)
        refreshEnableWithView = mode == DisplayMode.NO_DATA
        tvRefresh?.visibility = if (refreshEnableWithView) View.VISIBLE else View.GONE
        if (refreshEnableWithView) tvRefresh?.text = refreshHint
        if (setNow) {
            valueAnimator?.end()
            setViews(1f, mode)
            setBackground(1f, mode)
        } else {
            if (valueAnimator == null) {
                valueAnimator = BaseLoadingValueAnimator(listener)
                valueAnimator?.duration = DEFAULT_ANIM_DURATION
            } else {
                valueAnimator?.end()
            }
            disPlayViews?.put(mode, 0.0f)
            if (!isSameMode) {
                needBackgroundColor = bgColor
                valueAnimator?.start(mode)
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
        if (curMode != DisplayMode.DISMISS) {
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
                setBackgroundColor({ oldBackgroundColor = 0;oldBackgroundColor }.invoke())
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
