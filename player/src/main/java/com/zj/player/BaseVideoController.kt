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
import com.zj.player.ut.Constance
import com.zj.player.ut.Controller
import com.zj.player.anim.ZFullValueAnimator
import com.zj.player.base.InflateInfo
import com.zj.player.full.BaseGestureFullScreenDialog
import com.zj.player.full.FullContentListener
import com.zj.player.full.FullScreenListener
import com.zj.player.logs.BehaviorData
import com.zj.player.logs.BehaviorLogsTable
import com.zj.player.logs.ZPlayerLogs
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
open class BaseVideoController @JvmOverloads constructor(context: Context, attributeSet: AttributeSet? = null, def: Int = 0) : FrameLayout(context, attributeSet, def), Controller {

    protected var vPlay: View? = null
    protected var tvStart: TextView? = null
    protected var tvEnd: TextView? = null
    protected var seekBar: SeekBar? = null
    protected var seekBarSmall: SeekBar? = null
    protected var fullScreen: View? = null
    protected var speedView: TextView? = null
    protected var muteView: View? = null
    protected var lockScreen: View? = null
    private var loadingView: BaseLoadingView? = null
    protected var bottomToolsBar: View? = null
    protected var topToolsBar: View? = null
    protected var videoOverrideImageView: ImageView? = null
    protected var videoOverrideImageShaderView: ImageView? = null
    protected var videoRoot: View? = null
    protected var controller: ZController? = null
    protected var fullScreenDialog: BaseGestureFullScreenDialog? = null
    protected var autoPlay = false
    protected var isFull = false
    protected var isStartTrack = false
    protected var isInterruptPlayBtnAnim = true
    protected var isFullingOrDismissing = false
    protected var isTickingSeekBarFromUser: Boolean = false
    protected var fullScreenContentLayoutId: Int = -1
    protected var fullScreenSupported = false
    protected val supportedSpeedList = floatArrayOf(1f, 2f, 4f)
    private var curSpeedIndex = 0
    protected var alphaAnimViewsGroup: MutableList<View?>? = null
    protected var onFullScreenLayoutInflateListener: ((v: View) -> Unit)? = null
    protected var isDefaultMaxScreen: Boolean = false
    protected var fullMaxScreenEnable: Boolean = true
    protected var lockScreenRotation: Int = -1
    protected var isLockScreenRotation: Boolean = false

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
            log("on play btn click", BehaviorLogsTable.onPlayClick())
            onPlayClick(it)
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
            reload(it)
        }

        fullScreen?.setOnClickListener {
            onFullScreenClick(it)
        }

        speedView?.setOnClickListener {
            log("on speed btn click", BehaviorLogsTable.onSpeedClick())
            onSpeedClick(it)
        }

        muteView?.setOnClickListener {
            log("on mute btn click", BehaviorLogsTable.onMuteClick())
            onMuteClick(it)
        }

        lockScreen?.setOnClickListener {
            log("on lock screen btn click", BehaviorLogsTable.onLockScreenClick())
            if (!lockScreenRotate(!it.isSelected)) Toast.makeText(context, R.string.z_player_str_screen_locked_tint, Toast.LENGTH_SHORT).show()
        }
    }

    private fun initVolume(isMute: Boolean) {
        val volume = if (isMute) 0f else {
            val audioManager = context.getSystemService(Service.AUDIO_SERVICE) as AudioManager
            audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM) * 1.0f
        }
        controller?.setVolume(volume)
    }

    @SuppressLint("ClickableViewAccessibility")
    protected fun initSeekBar() {
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
        setOverlayViews(isShowThumb = false, isShowBackground = true, isSinkBottomShader = true)
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
        reset(false, isRegulate)
        updateCurPlayerInfo(1f, supportedSpeedList[0])
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

    open fun clickPlayBtn() {
        vPlay?.callOnClick()
    }

    open fun getThumbView(): ImageView? {
        return videoOverrideImageView
    }

    open fun getBackgroundView(): ImageView? {
        return videoOverrideImageShaderView
    }

    open fun setScreenContentLayout(@LayoutRes layoutId: Int, onFullScreenLayoutInflateListener: ((v: View) -> Unit)? = null) {
        this.fullScreenContentLayoutId = layoutId
        this.onFullScreenLayoutInflateListener = onFullScreenLayoutInflateListener
    }

    open fun onFullScreenListener(isFull: Boolean) {}

    open fun onFullMaxChangedListener(isFull: Boolean) {}

    open fun onPlayClick(v: View) {
        v.isEnabled = false
        if (!v.isSelected) {
            controller?.playOrResume()
        } else {
            controller?.pause()
        }
    }

    open fun reload(v: View) {
        val path = controller?.getPath()
        if (path.isNullOrEmpty()) {
            loadingView?.setMode(BaseLoadingView.DisplayMode.LOADING)
            v.postDelayed({
                onError(NullPointerException("video path is null"))
            }, 300)
        } else controller?.playOrResume(path)
    }

    open fun onFullScreenClick(v: View) {
        if (!isFullingOrDismissing) {
            isFullingOrDismissing = true
            onFullScreen(!v.isSelected)
        }
    }

    open fun onSpeedClick(v: View) {
        if (controller?.isReady() == true) {
            val curSpeed = supportedSpeedList[++curSpeedIndex % supportedSpeedList.size]
            controller?.setSpeed(curSpeed)
        }
    }

    open fun onMuteClick(v: View) {
        val nextState = !v.isSelected
        initVolume(nextState)
        v.isSelected = nextState
    }

    /**
     * even though you called it and set the lock to FALSE ,
     * it also merge with the system screen rotate settings.
     * @return the status form this operation
     * */
    open fun lockScreenRotate(isLock: Boolean): Boolean {
        return if (fullScreenDialog?.lockScreenRotation(isLock) == true) {
            isLockScreenRotation = isLock
            lockScreen?.isSelected = isLock
            true
        } else false
    }

    protected fun getDuration(mediaDuration: Long): String {
        val duration = mediaDuration / 1000
        val minute = duration / 60
        val second = duration % 60
        return String.format(Locale.getDefault(), "${if (minute < 10) "0%d" else "%d"}:${if (second < 10) "0%d" else "%d"}", minute, second)
    }

    protected fun showOrHidePlayBtn(isShow: Boolean, withState: Boolean = false) {
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

    protected fun full(isFull: Boolean, isSetNow: Boolean = false) {
        if (isSetNow) {
            anim?.end()
            bottomToolsBar?.let {
                it.clearAnimation()
                it.visibility = if (isFull) VISIBLE else GONE
            }
            topToolsBar?.let {
                it.alpha = 1.0f
                it.visibility = if (isFull) VISIBLE else GONE
            }
            seekBarSmall?.visibility = GONE
        } else {
            bottomToolsBar?.let {
                if (anim?.isRunning == true) return
                if (isFull == this.isFull) return
                this.isFull = isFull
                it.clearAnimation()
                anim?.start(isFull)
                log("on tools bar hidden ${!isFull}", BehaviorLogsTable.onToolsBarShow(isFull))
            }
            if (isFull) seekBarSmall?.visibility = View.GONE
        }
    }

    protected fun setOverlayViews(isShowThumb: Boolean, isShowBackground: Boolean, isSinkBottomShader: Boolean) {
        log("on overlay views visibility  thumb = $isShowThumb  bg = $isShowBackground", BehaviorLogsTable.thumbImgVisible(isShowThumb, isShowBackground))
        videoOverrideImageView?.visibility = if (isShowThumb) View.VISIBLE else View.GONE
        videoOverrideImageShaderView?.visibility = if (isShowBackground) View.VISIBLE else View.GONE
        videoOverrideImageShaderView?.z = if (isSinkBottomShader) 0f else Resources.getSystem().displayMetrics.density * 3 + 0.5f
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
                if (!isFull && !isStartTrack && (controller?.isPlaying() == true || controller?.isPause(true) == true)) seekBarSmall?.visibility = View.VISIBLE
            }
            topToolsBar?.let {
                it.alpha = if (isFull) 1f else 0f
                if (!isFull) it.visibility = View.GONE
            }
        }
    }

    protected val fullScreenListener = object : FullScreenListener {
        override fun onDisplayChanged(dialog: BaseGestureFullScreenDialog, isShow: Boolean) {
            onDisplayChanged(fullScreen, isShow)
        }

        override fun onFocusChange(dialog: BaseGestureFullScreenDialog, isMax: Boolean) {
            onFocusChanged(dialog, isMax)
        }

        override fun onTrack(isStart: Boolean, isEnd: Boolean, formTrigDuration: Float) {
            onTracked(isStart, isEnd, formTrigDuration)
        }
    }

    protected val fullContentListener = object : FullContentListener {
        override fun onDisplayChanged(dialog: BaseGestureFullScreenDialog, isShow: Boolean) {
            onDisplayChanged(fullScreen, isShow)
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

        override fun onTrack(isStart: Boolean, isEnd: Boolean, formTrigDuration: Float) {
            onTracked(isStart, isEnd, formTrigDuration)
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    protected fun onFullScreen(full: Boolean) {
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
                        d.showFull(it, lockScreenRotation, fullScreenListener)
                    } else d.showInContent(it, fullScreenContentLayoutId, fullMaxScreenEnable, lockScreenRotation, fullContentListener)
                }
                lockScreenRotate(isLockScreenRotation)
            }
        }
    }

    open fun onTracked(isStart: Boolean, isEnd: Boolean, formTrigDuration: Float) {
        if (isStart && this.isFull) {
            isStartTrack = true;full(false)
            if (!isInterruptPlayBtnAnim) {
                showOrHidePlayBtn(isShow = false, withState = true)
            }
        }
        if (isEnd && !this.isFull) {
            isStartTrack = false;full(true)
            if (!isInterruptPlayBtnAnim) {
                showOrHidePlayBtn(isShow = true, withState = true)
            }
        }
    }

    open fun reset(isNow: Boolean, isRegulate: Boolean, isShowThumb: Boolean = true, isShowBackground: Boolean = true, isSinkBottomShader: Boolean = false) {
        vPlay?.isEnabled = true
        loadingView?.setMode(BaseLoadingView.DisplayMode.DISMISS, "", false, setNow = true)
        setOverlayViews(isShowThumb, isShowBackground, isSinkBottomShader)
        seekBar?.isSelected = false
        seekBar?.isEnabled = false
        isInterruptPlayBtnAnim = true
        seekBarSmall?.visibility = View.GONE
        onSeekChanged(0, 0, false, 0)
        if (isRegulate) showOrHidePlayBtn(true, withState = false)
        full(false, isSetNow = isNow)
    }

    protected fun onDisplayChanged(v: View?, isShow: Boolean) {
        log("on full screen $isShow", BehaviorLogsTable.onFullscreen(isShow))
        v?.isSelected = isShow
        if (!isShow) {
            fullScreenDialog = null
            lockScreen?.isSelected = false
        }
        isFullingOrDismissing = false
        onFullScreenListener(isShow)
    }

    protected fun onFocusChanged(dialog: BaseGestureFullScreenDialog, isMax: Boolean) {
        log("on full max screen $isMax", BehaviorLogsTable.onFullMaxScreen(isMax))
        if (isMax) lockScreen?.isSelected = dialog.isLockedCurrent()
        onFullMaxChangedListener(isMax)
    }

    protected fun setChildZ(zIn: Float) {
        videoOverrideImageShaderView?.z = zIn
    }

    protected fun checkActIsFinished(): Boolean {
        return (context as? Activity)?.isFinishing == true
    }

    private fun log(s: String, bd: BehaviorData? = null) {
        controller?.recordLogs(s, "BaseViewController", bd)
    }

    protected fun recordLogs(s: String, modeName: String, vararg params: Pair<String, Any>) {
        if (Constance.CORE_LOG_ABLE) ZPlayerLogs.onLog(s, controller?.getPath() ?: "", "", modeName, *params)
    }
}