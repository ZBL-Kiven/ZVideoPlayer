package com.zj.player

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.zj.player.UT.Constance
import com.zj.player.UT.Controller
import com.zj.player.anim.ZFullValueAnimator
import com.zj.player.base.InflateInfo
import com.zj.player.full.BaseGestureFullScreenPopWindow
import com.zj.player.view.BaseLoadingView
import java.lang.NullPointerException
import java.util.*

/**
 * @author ZJJ on 2020.6.16
 *
 * A user operation interface based on an instance of the framework model.
 * During use, the operation interface only needs to be concerned about what behavior events are received and make corresponding interactive responses.
 * The operation interface does not need to carry the playback controller, renderer, decoder and other components at any time, which means that it is just a simple View when you are not playing.
 * At the same time, any operation interface you define based on the Controller interface is It can be used as a container for video reception and display at any time without interruption.
 * */
@Suppress("unused", "MemberVisibilityCanBePrivate", "InflateParams")
class BaseVideoController @JvmOverloads constructor(context: Context, attributeSet: AttributeSet? = null, def: Int = 0) : FrameLayout(context, attributeSet, def), Controller {

    private var vPlay: View? = null
    private var tvStart: TextView? = null
    private var tvEnd: TextView? = null
    private var seekBar: SeekBar? = null
    private var seekBarSmall: SeekBar? = null
    private var fullScreen: View? = null
    private var loadingView: BaseLoadingView? = null
    private var bottomToolsBar: View? = null
    private var videoOverrideImageView: ImageView? = null
    private var videoOverrideImageShaderView: ImageView? = null
    private var isTickingSeekBarFromUser: Boolean = false
    private var autoPlay = false
    private var videoRoot: View? = null
    private var controller: ZController? = null
    private var isFull = false
    private var isInterruptPlayBtnAnim = true
    private var fullScreenPopWindow: BaseGestureFullScreenPopWindow? = null

    init {
        videoRoot = LayoutInflater.from(context).inflate(R.layout.z_player_video_view, null, false)
        addView(videoRoot, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        vPlay = videoRoot?.findViewById(R.id.z_player_video_preview_iv_play)
        tvStart = videoRoot?.findViewById(R.id.z_player_video_preview_tv_start)
        tvEnd = videoRoot?.findViewById(R.id.z_player_video_preview_tv_end)
        loadingView = videoRoot?.findViewById(R.id.z_player_video_preview_loading)
        bottomToolsBar = videoRoot?.findViewById(R.id.z_player_video_preview_tools_bar)
        videoOverrideImageView = videoRoot?.findViewById(R.id.z_player_video_thumb)
        videoOverrideImageShaderView = videoRoot?.findViewById(R.id.z_player_video_background)
        seekBar = videoRoot?.findViewById(R.id.z_player_video_preview_sb)
        fullScreen = videoRoot?.findViewById(R.id.z_player_video_preview_iv_full_screen)
        seekBarSmall = videoRoot?.findViewById(R.id.z_player_video_preview_sb_small)
        initListener()
        initSeekBar()
    }

    private fun initListener() {
        vPlay?.setOnClickListener {
            it.isEnabled = false
            if (!it.isSelected) {
                controller?.playOrResume()
            } else {
                controller?.pause()
            }
        }
        videoRoot?.setOnClickListener {
            controller?.let {
                val full = !isFull
                if (!isInterruptPlayBtnAnim) {
                    showOrHidePlayBtn(full, true)
                }
                full(full)
            }
        }

        loadingView?.setRefreshListener {
            val path = controller?.getPath()
            if (path.isNullOrEmpty()) {
                onError(NullPointerException("video path is null"))
            } else controller?.playOrResume(path)
        }

        fullScreen?.setOnClickListener {
            onFullScreen(it, !it.isSelected)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSeekBar() {
        seekBar?.isEnabled = false
        seekBarSmall?.setOnTouchListener { _, _ ->
            return@setOnTouchListener true
        }
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                if (isTickingSeekBarFromUser && p2) {
                    controller?.seekTo(p0?.progress ?: 0)
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                isTickingSeekBarFromUser = true
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                isTickingSeekBarFromUser = false
                controller?.autoPlayWhenReady(true)
            }
        })
    }

    fun getThumbView(): ImageView? {
        return videoOverrideImageView
    }

    fun getBackgroundView(): ImageView? {
        return videoOverrideImageShaderView
    }

    override fun onControllerBind(controller: ZController?) {
        this.controller = controller
    }

    override fun getControllerInfo(): InflateInfo {
        val vpThis = (this.getChildAt(0) as? ViewGroup) ?: ((fullScreenPopWindow?.contentView as? ViewGroup)?.getChildAt(0) as? ViewGroup)
        return InflateInfo(vpThis, 2)
    }

    override fun onLoading(path: String, isRegulate: Boolean) {
        seekBar?.isEnabled = false
        showOrHidePlayBtn(false)
        loadingView?.setMode(BaseLoadingView.DisplayMode.LOADING)
    }

    override fun onPrepare(path: String, videoSize: Long, isRegulate: Boolean) {
        seekBar?.isEnabled = true
        tvEnd?.text = getDuration(videoSize)
    }

    override fun onPlay(path: String, isRegulate: Boolean) {
        setOverlayViews(false)
        seekBar?.isSelected = true
        seekBar?.isEnabled = true
        isInterruptPlayBtnAnim = false
        showOrHidePlayBtn(false)
        loadingView?.setMode(BaseLoadingView.DisplayMode.DISMISS)
        full(true)
    }

    override fun onPause(path: String, isRegulate: Boolean) {
        seekBar?.isSelected = false
        showOrHidePlayBtn(true)
    }

    override fun onStop(path: String, isRegulate: Boolean) {
        setOverlayViews(true)
        seekBar?.isSelected = false
        seekBar?.isEnabled = false
        isInterruptPlayBtnAnim = true
        seekBarSmall?.visibility = View.GONE
        onSeekChanged(0, 0, false, 0)
        if (isRegulate) showOrHidePlayBtn(true, withState = false)
        full(false)
    }

    override fun completing(path: String, isRegulate: Boolean) {
        isInterruptPlayBtnAnim = true
        showOrHidePlayBtn(true, withState = false)
        if (isRegulate) full(false)
    }

    override fun onCompleted(path: String, isRegulate: Boolean) {
        seekBar?.isSelected = false
        seekBar?.isEnabled = false
        isInterruptPlayBtnAnim = true
        if (isRegulate) {
            showOrHidePlayBtn(true, withState = false)
            full(false)
        }
        onSeekChanged(0, 0, false, 0)
    }

    override fun onSeekChanged(seek: Int, buffered: Int, fromUser: Boolean, videoSize: Long) {
        if (!fromUser) {
            seekBar?.progress = seek
            seekBar?.secondaryProgress = buffered
            seekBarSmall?.progress = seek
        }
        val startProgress = videoSize / 100f * seek
        tvStart?.text = getDuration(startProgress.toLong())
    }

    override fun onSeekingLoading(path: String?) {
        isInterruptPlayBtnAnim = true
        loadingView?.setMode(BaseLoadingView.DisplayMode.LOADING)
        showOrHidePlayBtn(false)
    }

    override fun onError(e: Exception?) {
        seekBar?.isSelected = false
        isInterruptPlayBtnAnim = true
        seekBarSmall?.visibility = View.GONE
        onSeekChanged(0, 0, false, 0)
        showOrHidePlayBtn(false)
        loadingView?.setMode(BaseLoadingView.DisplayMode.NO_DATA)
    }

    private fun getDuration(mediaDuration: Long): String {
        val duration = mediaDuration / 1000
        val minute = duration / 60
        val second = duration % 60
        return String.format(Locale.getDefault(), "${if (minute < 10) "0%d" else "%d"}:${if (second < 10) "0%d" else "%d"}", minute, second)
    }

    private fun showOrHidePlayBtn(isShow: Boolean, withState: Boolean = false) {
        vPlay?.let {
            var isNeedSetFreePlayBtn = true
            try {
                if (!withState) {
                    it.isSelected = !isShow
                    if (isShow && it.visibility == View.VISIBLE && it.tag == null) return
                }
                if (withState && it.tag != null) return
                if (isShow && it.tag == 0) return
                if (!isShow && it.tag == 1) return
                isNeedSetFreePlayBtn = false
                it.tag = if (isShow) 0 else 1
                val start = if (isShow) 0.0f else 1.0f
                val end = if (isShow) 1.0f else 0.0f
                it.alpha = start
                if (isShow) it.visibility = View.VISIBLE
                it.animation?.cancel()
                it.clearAnimation()
                it.animate()?.alpha(end)?.setDuration(Constance.ANIMATE_DURATION)?.withEndAction {
                    it.alpha = end
                    it.visibility = if (isShow) View.VISIBLE else View.GONE
                    it.animation = null
                    it.tag = null
                    it.isEnabled = true
                }?.start()
            } finally {
                if (isNeedSetFreePlayBtn) it.isEnabled = true
            }
        }
    }

    private fun full(isFull: Boolean) {
        bottomToolsBar?.let {
            if (anim?.isRunning == true) return
            if (isFull == this.isFull) return
            this.isFull = isFull
            it.clearAnimation()
            anim?.start(isFull)
        }
        if (isFull) seekBarSmall?.visibility = View.GONE
    }

    private fun setOverlayViews(isShow: Boolean) {
        videoOverrideImageView?.visibility = if (isShow) View.VISIBLE else View.GONE
        videoOverrideImageShaderView?.z = if (isShow) Resources.getSystem().displayMetrics.density * 3 + 0.5f else 0f
    }

    private var anim: ZFullValueAnimator? = null
        get() {
            if (field == null) field = ZFullValueAnimator(fullListener)
            field?.duration = Constance.ANIMATE_DURATION
            field?.interpolator = AccelerateDecelerateInterpolator()
            return field
        }

    private val fullListener = object : ZFullValueAnimator.FullAnimatorListener {

        override fun onDurationChange(animation: ValueAnimator, duration: Float, isFull: Boolean) {
            bottomToolsBar?.let {
                val toolsBottomHeight = it.measuredHeight * 1.0f
                if (isFull && it.translationY == 0f) {
                    it.alpha = 0f
                    it.translationY = toolsBottomHeight
                }
                val d = if (isFull) duration else -duration
                val bottomTrans = d * toolsBottomHeight
                var bTranslateY = it.translationY
                bTranslateY -= bottomTrans
                it.translationY = bTranslateY
                it.alpha += d
                if (isFull && it.visibility != View.VISIBLE) it.visibility = View.VISIBLE
            }
        }

        override fun onAnimEnd(animation: Animator, isFull: Boolean) {
            bottomToolsBar?.let {
                val toolsBottomHeight = (it.measuredHeight) * 1.0f
                it.translationY = if (isFull) 0f else toolsBottomHeight
                it.alpha = if (isFull) 1f else 0f
                if (!isFull) it.visibility = View.GONE
                if (!isFull) seekBarSmall?.visibility = View.VISIBLE
            }
        }
    }

    private fun onFullScreen(v: View, full: Boolean) {
        videoRoot?.let {
            if (fullScreenPopWindow == null && full) fullScreenPopWindow = BaseGestureFullScreenPopWindow.show(this, it, 0.25f) { b ->
                v.isSelected = b
                fullScreenPopWindow = null
            }
            if (!full) {
                fullScreenPopWindow?.dismiss();fullScreenPopWindow = null
            }
        }
    }

    private fun setChildZ(zIn: Float) {
        videoOverrideImageShaderView?.z = zIn
    }
}