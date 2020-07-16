package com.zj.player

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Resources
import android.media.AudioManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.annotation.LayoutRes
import com.zj.player.UT.Constance
import com.zj.player.UT.Controller
import com.zj.player.anim.ZFullValueAnimator
import com.zj.player.base.InflateInfo
import com.zj.player.full.BaseGestureFullScreenDialog
import com.zj.player.full.FullContentListener
import com.zj.player.full.FullScreenListener
import com.zj.player.view.BaseLoadingView
import java.lang.NullPointerException
import java.util.*
import kotlin.math.roundToInt

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
    private var speedView: TextView? = null
    private var muteView: View? = null
    private var lockScreen: View? = null
    private var loadingView: BaseLoadingView? = null
    private var bottomToolsBar: View? = null
    private var topToolsBar: View? = null
    private var videoOverrideImageView: ImageView? = null
    private var videoOverrideImageShaderView: ImageView? = null
    private var videoRoot: View? = null
    private var controller: ZController? = null
    private var fullScreenDialog: BaseGestureFullScreenDialog? = null
    private var autoPlay = false
    private var isFull = false
    private var isInterruptPlayBtnAnim = true
    private var isFullingOrDismissing = false
    private var isTickingSeekBarFromUser: Boolean = false
    private var fullScreenContentLayoutId: Int = -1
    private var fullScreenSupported = false
    private val supportedSpeedList = floatArrayOf(1f, 2f, 4f)
    private var curSpeedIndex = 0
    private var alphaAnimViewsGroup: MutableList<View?>? = null
    private var onFullScreenLayoutInflateListener: ((v: View) -> Unit)? = null
    private var isDefaultMaxScreen: Boolean = false
    private var fullMaxScreenEnable: Boolean = true
    private var lockScreenRotation: Int = -1
    private var isLockScreenRotation: Boolean = false

    init {
        initView(context, attributeSet)
        initListener()
        initSeekBar()
    }

    /**
     * config it in [R.styleable.BaseVideoController]
     * */
    private fun initView(context: Context, attributeSet: AttributeSet?) {
        val ta = context.obtainStyledAttributes(attributeSet, R.styleable.BaseVideoController)
        fun <T : View> setDefaultControllerStyle(id: Int, mode: Int): T? {
            return when (mode) {
                1, 2 -> {
                    val v = videoRoot?.findViewById<T>(id)
                    v?.visibility = if (mode == 2) View.VISIBLE else View.GONE
                    v
                }
                else -> null
            }
        }
        try {
            val defaultControllerVisibility = ta.getInt(R.styleable.BaseVideoController_defaultControllerVisibility, Constance.defaultControllerVisibility)
            val muteIconEnable = ta.getInt(R.styleable.BaseVideoController_muteIconEnable, Constance.muteIconEnable)
            val speedIconEnable = ta.getInt(R.styleable.BaseVideoController_speedIconEnable, Constance.speedIconEnable)
            val secondarySeekBarEnable = ta.getInt(R.styleable.BaseVideoController_secondarySeekBarEnable, Constance.secondarySeekBarEnable)
            val fullScreenEnable = ta.getInt(R.styleable.BaseVideoController_fullScreenEnable, Constance.fullScreenEnAble)
            fullMaxScreenEnable = ta.getBoolean(R.styleable.BaseVideoController_fullMaxScreenEnable, Constance.fullMaxScreenEnable)
            isDefaultMaxScreen = ta.getBoolean(R.styleable.BaseVideoController_isDefaultMaxScreen, Constance.isDefaultMaxScreen)
            lockScreenRotation = ta.getInt(R.styleable.BaseVideoController_lockScreenRotation, -1)
            videoRoot = LayoutInflater.from(context).inflate(R.layout.z_player_video_view, null, false)
            addView(videoRoot, LayoutParams(MATCH_PARENT, MATCH_PARENT))
            vPlay = videoRoot?.findViewById(R.id.z_player_video_preview_iv_play)
            muteView = setDefaultControllerStyle(R.id.z_player_video_preview_iv_mute, muteIconEnable)
            speedView = setDefaultControllerStyle(R.id.z_player_video_preview_tv_speed, speedIconEnable)
            fullScreen = setDefaultControllerStyle(R.id.z_player_video_preview_iv_full_screen, fullScreenEnable)
            seekBarSmall = setDefaultControllerStyle(R.id.z_player_video_preview_sb_small, secondarySeekBarEnable)
            bottomToolsBar = setDefaultControllerStyle(R.id.z_player_video_preview_tools_bar, defaultControllerVisibility)
            lockScreen = videoRoot?.findViewById(R.id.z_player_video_preview_iv_lock_screen)
            tvStart = videoRoot?.findViewById(R.id.z_player_video_preview_tv_start)
            tvEnd = videoRoot?.findViewById(R.id.z_player_video_preview_tv_end)
            loadingView = videoRoot?.findViewById(R.id.z_player_video_preview_loading)
            topToolsBar = videoRoot?.findViewById(R.id.z_player_video_preview_top_bar)
            videoOverrideImageView = videoRoot?.findViewById(R.id.z_player_video_thumb)
            videoOverrideImageShaderView = videoRoot?.findViewById(R.id.z_player_video_background)
            seekBar = videoRoot?.findViewById(R.id.z_player_video_preview_sb)
            speedView?.text = context.getString(R.string.z_player_str_speed, 1)
            isLockScreenRotation = lockScreenRotation != -1
            isFull = bottomToolsBar?.visibility == View.VISIBLE
        } finally {
            ta.recycle()
        }
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
            if (!isFullingOrDismissing) {
                isFullingOrDismissing = true
                onFullScreen(it, !it.isSelected)
            }
        }
        speedView?.setOnClickListener {
            if (controller?.isReady() == true) {
                val curSpeed = supportedSpeedList[++curSpeedIndex % supportedSpeedList.size]
                controller?.setSpeed(curSpeed)
            }
        }

        muteView?.setOnClickListener {
            val nextState = !it.isSelected
            initVolume(nextState)
            it.isSelected = nextState
        }

        lockScreen?.setOnClickListener {
            if (!lockScreenRotate(!it.isSelected)) Toast.makeText(context, R.string.z_player_str_screen_locked_tint, Toast.LENGTH_SHORT).show()
        }
    }

    private fun initVolume(isMute: Boolean) {
        if (isMute) {
            controller?.setVolume(0f)
        } else {
            val audioManager = context.getSystemService(Service.AUDIO_SERVICE) as AudioManager
            controller?.setVolume(audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM) * 1.0f)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSeekBar() {
        seekBar?.isEnabled = false
        seekBarSmall?.setOnTouchListener(null)
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

    override fun onControllerBind(controller: ZController?) {
        this.controller = controller
    }

    override fun onDestroy(path: String?, isRegulate: Boolean) {
        fullScreenDialog?.let {
            if (it.isShowing) it.dismiss()
        }
        controller = null
    }

    override fun getControllerInfo(): InflateInfo {
        val vpThis = (this.getChildAt(0) as? ViewGroup) ?: fullScreenDialog?.getControllerView() as? ViewGroup
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
        initVolume(muteView?.isSelected == true)
    }

    override fun onPlay(path: String, isRegulate: Boolean) {
        setOverlayViews(false)
        seekBar?.isSelected = true
        seekBar?.isEnabled = true
        isInterruptPlayBtnAnim = false
        showOrHidePlayBtn(false)
        loadingView?.setMode(BaseLoadingView.DisplayMode.DISMISS)
        full(false)
        seekBarSmall?.visibility = View.VISIBLE
    }

    override fun onPause(path: String, isRegulate: Boolean) {
        seekBar?.isSelected = false
        showOrHidePlayBtn(true)
    }

    override fun updateCurPlayerInfo(volume: Float, speed: Float) {
        muteView?.isSelected = volume <= 0
        curSpeedIndex = supportedSpeedList.indexOfLast { it in (speed - 0.4f)..(speed + 0.5f) }
        speedView?.text = context.getString(R.string.z_player_str_speed, supportedSpeedList[curSpeedIndex].roundToInt())
    }

    override fun onStop(path: String, isRegulate: Boolean) {
        setOverlayViews(true)
        seekBar?.isSelected = false
        seekBar?.isEnabled = false
        isInterruptPlayBtnAnim = true
        seekBarSmall?.visibility = View.GONE
        updateCurPlayerInfo(1f, supportedSpeedList[0])
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
        if (loadingView?.visibility == View.VISIBLE) {
            loadingView?.setMode(BaseLoadingView.DisplayMode.NONE)
            completing(path, isRegulate)
        }
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

    override fun onLifecycleResume() {
        fullScreenDialog?.onResume()
    }

    override fun onLifecycleStop() {
        fullScreenDialog?.onStopped()
    }

    override fun onLifecyclePause() {
        //use off in extends
    }

    fun getThumbView(): ImageView? {
        return videoOverrideImageView
    }

    fun getBackgroundView(): ImageView? {
        return videoOverrideImageShaderView
    }

    fun setScreenContentLayout(@LayoutRes layoutId: Int, onFullScreenLayoutInflateListener: ((v: View) -> Unit)? = null) {
        this.fullScreenContentLayoutId = layoutId
        this.onFullScreenLayoutInflateListener = onFullScreenLayoutInflateListener
    }

    /**
     * even though you called it and set the lock to FALSE ,
     * it also merge with the system screen rotate settings.
     * @return the status form this operation
     * */
    fun lockScreenRotate(isLock: Boolean): Boolean {
        return if (fullScreenDialog?.lockScreenRotation(isLock) == true) {
            isLockScreenRotation = isLock
            lockScreen?.isSelected = isLock
            true
        } else false
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
            if (checkActIsFinished()) return
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
            topToolsBar?.let {
                if (isFull && it.alpha == 1.0f) it.alpha = 0f
                val d = if (isFull) duration else -duration
                it.alpha += d
                if (isFull && it.visibility != View.VISIBLE) it.visibility = View.VISIBLE
            }
        }

        override fun onAnimEnd(animation: Animator, isFull: Boolean) {
            if (checkActIsFinished()) return
            bottomToolsBar?.let {
                val toolsBottomHeight = (it.measuredHeight) * 1.0f
                it.translationY = if (isFull) 0f else toolsBottomHeight
                it.alpha = if (isFull) 1f else 0f
                if (!isFull) it.visibility = View.GONE
                if (!isFull && (controller?.isPlaying() == true || controller?.isPause(true) == true)) seekBarSmall?.visibility = View.VISIBLE
            }
            topToolsBar?.let {
                it.alpha = if (isFull) 1f else 0f
                if (!isFull) it.visibility = View.GONE
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun onFullScreen(v: View, full: Boolean) {
        videoRoot?.let {
            if (!full) {
                lockScreen?.visibility = GONE
                fullScreenDialog?.dismiss()
            } else {
                if (!isDefaultMaxScreen) (context as? Activity)?.let {
                    if (it.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                        it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
                if (fullScreenDialog == null) fullScreenDialog = BaseGestureFullScreenDialog.let { d ->
                    if (isDefaultMaxScreen) {
                        lockScreen?.visibility = View.VISIBLE
                        d.showFull(it, lockScreenRotation, object : FullScreenListener {
                            override fun onDisplayChanged(dialog: BaseGestureFullScreenDialog, isShow: Boolean) {
                                onDisplayChanged(v, isShow)
                            }

                            override fun onFocusChange(dialog: BaseGestureFullScreenDialog, isMax: Boolean) {
                                onFocusChanged(dialog, isMax)
                            }
                        })
                    } else d.showInContent(it, fullScreenContentLayoutId, fullMaxScreenEnable, lockScreenRotation, object : FullContentListener {
                        override fun onDisplayChanged(dialog: BaseGestureFullScreenDialog, isShow: Boolean) {
                            onDisplayChanged(v, isShow)
                        }

                        override fun onContentLayoutInflated(dialog: BaseGestureFullScreenDialog, content: View) {
                            onFullScreenLayoutInflateListener?.invoke(content)
                        }

                        override fun onFullMaxChanged(dialog: BaseGestureFullScreenDialog, isMax: Boolean) {
                            lockScreen?.visibility = if (isMax) View.VISIBLE else GONE
                            onFocusChanged(dialog, isMax)
                        }

                        override fun onFocusChange(dialog: BaseGestureFullScreenDialog, isMax: Boolean) {
                            onFocusChanged(dialog, isMax)
                        }
                    })
                }
                lockScreenRotate(isLockScreenRotation)
            }
        }
    }

    private fun onDisplayChanged(v: View, isShow: Boolean) {
        v.isSelected = isShow
        if (!isShow) {
            fullScreenDialog = null
            lockScreen?.isSelected = false
        }
        isFullingOrDismissing = false
    }

    private fun onFocusChanged(dialog: BaseGestureFullScreenDialog, isMax: Boolean) {
        if (isMax) lockScreen?.isSelected = dialog.isLockedCurrent()
    }

    private fun setChildZ(zIn: Float) {
        videoOverrideImageShaderView?.z = zIn
    }

    private fun checkActIsFinished(): Boolean {
        return (context as? Activity)?.isFinishing == true
    }
}