package com.zj.player.z

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Resources
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.*
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import com.zj.player.R
import com.zj.player.anim.ZFullValueAnimator
import com.zj.player.base.InflateInfo
import com.zj.player.base.LoadingMode
import com.zj.player.full.*
import com.zj.player.full.ZPlayerFullScreenView
import com.zj.player.full.FullContentListener
import com.zj.player.full.FullScreenListener
import com.zj.player.full.GestureTouchListener
import com.zj.player.img.scale.ImageViewTouchEnableIn
import com.zj.player.img.scale.TouchScaleImageView
import com.zj.player.logs.BehaviorData
import com.zj.player.logs.BehaviorLogsTable
import com.zj.player.logs.ZPlayerLogs
import com.zj.player.ut.Constance
import com.zj.player.ut.Controller
import com.zj.player.ut.PlayQualityLevel
import com.zj.player.view.QualityMenuView
import com.zj.player.view.VideoLoadingView
import com.zj.player.view.VideoRootView
import java.lang.ref.WeakReference
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * @author ZJJ on 2020.6.16
 *
 * A user operation interface based on an instance of the framework model.
 * During use, the operation interface only needs to be concerned about what behavior events are received and make corresponding interactive responses.
 * The operation interface does not need to carry the playback controller, renderer, decoder and other components at any time, which means that it is just a simple View when you are not playing.
 * At the same time, any operation interface you define based on the Controller interface is It can be used as a container for video reception and display at any time without interruption.
 * */

@Suppress("unused", "MemberVisibilityCanBePrivate", "InflateParams", "ClickableViewAccessibility")
open class ZVideoView @JvmOverloads constructor(context: Context, attributeSet: AttributeSet? = null, def: Int = 0) : FrameLayout(context, attributeSet, def), Controller {

    companion object {
        private const val dismissFullTools = 7817
        private const val loadingModeDelay = 7716
        private var muteGlobalDefault: Boolean = false

        /**
         * After setting this property, all ViewController instances configured with app:useMuteGlobal in xml take effect。
         * @see muteIsUseGlobal  bind to [muteGlobalDefault]
         * */
        internal fun setGlobalMuteDefault(isMute: Boolean) {
            muteGlobalDefault = isMute
        }
    }

    private val mHandler = Handler(Looper.getMainLooper()) {
        when (it.what) {
            dismissFullTools -> onFullTools(false)
            loadingModeDelay -> if (controller?.isLoading(true) == true) loadingView?.setMode(VideoLoadingView.DisplayMode.LOADING)
        }
        return@Handler false
    }
    protected var autoFullTools = true
    protected var autoFullInterval = 3000
    protected var loadingIgnoreInterval = 0
    protected var vPlay: View? = null
    protected var tvStart: TextView? = null
    protected var tvEnd: TextView? = null
    protected var seekBar: SeekBar? = null
    protected var seekBarSmall: SeekBar? = null
    protected var fullScreen: View? = null
    protected var qualityView: TextView? = null
    protected var speedView: TextView? = null
    protected var muteView: View? = null
    protected var lockScreen: View? = null
    private var loadingView: VideoLoadingView? = null
    protected var bottomToolsBar: View? = null
    protected var topToolsBar: View? = null
    protected var videoOverrideImageView: TouchScaleImageView? = null
    protected var videoOverrideImageShaderView: ImageView? = null
    protected var videoRoot: VideoRootView? = null
    protected var controller: ZController<*, *>? = null
    protected var autoPlay = false
    protected var isFull = false
    protected var isStartTrack = false
    protected var isInterruptPlayBtnAnim = true
    protected var isFullingOrDismissing = false
    protected var isTickingSeekBarFromUser: Boolean = false
    protected var fullScreenContentLayoutId: Int = -1
    protected var fullScreenSupported = false
    protected var alphaAnimViewsGroup: MutableList<View?>? = null
    protected var onFullScreenLayoutInflateListener: ((v: View) -> Unit)? = null
    protected var isDefaultMaxScreen: Boolean = false
    protected var fullMaxScreenEnable: Boolean = true
    protected var scrollXEnabled: Boolean = true
    protected var qualityEnable = 0
    protected var lockScreenRotation: Int = -1
    protected var isLockScreenRotation: Boolean = false
    protected var keepScreenOnWhenPlaying: Boolean = false
    protected var enablePlayAnimation: Boolean = true
    private var curSpeedIndex = 0
    private var fullScreenTransactionTime = 250
    private var fullScreenDialog: ZPlayerFullScreenView? = null
    private var muteDefault: Boolean = false
    private var muteIsUseGlobal: Boolean = false
    private var isTransactionNavigation: Boolean = false
    open val supportedSpeedList = floatArrayOf(1.0f, 1.5f, 2f)
    private var menuView: QualityMenuView? = null
    protected var isFullScreen: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            fullScreen?.isSelected = value
        }
    var isPlayable = true
        set(value) {
            if (value != field) {
                vPlay?.visibility = if (value) View.VISIBLE else GONE
                field = value
            }
        }

    private val isPlaying: Boolean; get() = controller?.isPlaying() ?: false

    private var touchListener: GestureTouchListener? = object : GestureTouchListener({ isInterrupted() }) {

        override fun onEventEnd(formTrigDuration: Float, parseAutoScale: Boolean): Boolean {
            return fullScreenDialog?.onEventEnd(formTrigDuration, parseAutoScale) ?: true
        }

        override fun onClick() {
            if (isPlayable) onRootClick() else onRootDoubleClick()
        }

        override fun onDoubleClick() {
            if (isPlayable) if (isFullScreen) {
                fullScreenDialog?.onDoubleClick()
            } else {
                onRootDoubleClick()
            }
        }

        override fun onTracked(isStart: Boolean, offsetX: Float, offsetY: Float, easeY: Float, orientation: TrackOrientation, formTrigDuration: Float) {
            fullScreenDialog?.let {
                if (isFullScreen && it.parent != null) it.onTracked(isStart, if (scrollXEnabled) offsetX else 0f, offsetY, easeY, formTrigDuration)
            }
        }

        override fun onTouchActionEvent(event: MotionEvent, lastX: Float, lastY: Float, orientation: TrackOrientation?): Boolean {
            return fullScreenDialog?.let {
                if (isFullScreen && it.parent != null && !it.isMaxFull()) this@ZVideoView.onTouchActionEvent(videoRoot, event, lastX, lastY, orientation) else false
            } ?: false
        }
    }

    fun isInterrupted(): Boolean {
        return if ((menuView?.visibility ?: View.GONE) != View.GONE) {
            menuView?.visibility = View.GONE;true
        } else fullScreenDialog?.isInterruptTouchEvent() ?: false
    }

    init {
        initView(context, attributeSet)
        initListener()
        initSeekBar()
    }

    /**
     * config it in [R.styleable.ZVideoView]
     * */
    private fun initView(context: Context, attributeSet: AttributeSet?) {
        val ta = context.obtainStyledAttributes(attributeSet, R.styleable.ZVideoView)
        try {
            val defaultControllerVisibility = ta.getInt(R.styleable.ZVideoView_defaultControllerVisibility, Constance.defaultControllerVisibility)
            val muteIconEnable = ta.getInt(R.styleable.ZVideoView_muteIconEnable, Constance.muteIconEnable)
            qualityEnable = ta.getInt(R.styleable.ZVideoView_qualityEnable, Constance.qualityEnable)
            val speedIconEnable = ta.getInt(R.styleable.ZVideoView_speedIconEnable, Constance.speedIconEnable)
            val secondarySeekBarEnable = ta.getInt(R.styleable.ZVideoView_secondarySeekBarEnable, Constance.secondarySeekBarEnable)
            val fullScreenEnable = ta.getInt(R.styleable.ZVideoView_fullScreenEnable, Constance.fullScreenEnAble)
            autoFullTools = ta.getBoolean(R.styleable.ZVideoView_autoFullTools, false)
            autoFullInterval = ta.getInt(R.styleable.ZVideoView_autoFullInterval, 3000)
            fullScreenTransactionTime = ta.getInt(R.styleable.ZVideoView_fullScreenTransactionTime, fullScreenTransactionTime)
            fullMaxScreenEnable = ta.getBoolean(R.styleable.ZVideoView_fullMaxScreenEnable, Constance.fullMaxScreenEnable)
            isDefaultMaxScreen = ta.getBoolean(R.styleable.ZVideoView_isDefaultMaxScreen, Constance.isDefaultMaxScreen)
            lockScreenRotation = ta.getInt(R.styleable.ZVideoView_lockScreenRotation, -1)
            scrollXEnabled = ta.getBoolean(R.styleable.ZVideoView_scrollXEnabled, true)
            muteIsUseGlobal = ta.getBoolean(R.styleable.ZVideoView_useMuteGlobal, false)
            muteDefault = ta.getBoolean(R.styleable.ZVideoView_muteDefault, false)
            keepScreenOnWhenPlaying = ta.getBoolean(R.styleable.ZVideoView_keepScreenOnWhenPlaying, false)
            isTransactionNavigation = ta.getBoolean(R.styleable.ZVideoView_isTransactionNavigation, false)
            enablePlayAnimation = ta.getBoolean(R.styleable.ZVideoView_enablePlayAnimation, true)
            val view = LayoutInflater.from(context).inflate(R.layout.z_player_video_view, null, false)
            addView(view, LayoutParams(MATCH_PARENT, MATCH_PARENT))
            videoRoot = view?.findViewById(R.id.z_player_video_root)
            vPlay = view?.findViewById(R.id.z_player_video_preview_iv_play)
            muteView = getViewByDefaultConfig(R.id.z_player_video_preview_iv_mute, muteIconEnable)
            speedView = getViewByDefaultConfig(R.id.z_player_video_preview_tv_speed, speedIconEnable)
            fullScreen = getViewByDefaultConfig(R.id.z_player_video_preview_iv_full_screen, fullScreenEnable)
            seekBarSmall = getViewByDefaultConfig(R.id.z_player_video_preview_sb_small, secondarySeekBarEnable)
            bottomToolsBar = getViewByDefaultConfig(R.id.z_player_video_preview_tools_bar, defaultControllerVisibility)
            lockScreen = view?.findViewById(R.id.z_player_video_preview_iv_lock_screen)
            tvStart = view?.findViewById(R.id.z_player_video_preview_tv_start)
            tvEnd = view?.findViewById(R.id.z_player_video_preview_tv_end)
            topToolsBar = view?.findViewById(R.id.z_player_video_preview_top_bar)
            videoOverrideImageView = view?.findViewById(R.id.z_player_video_thumb)
            videoOverrideImageShaderView = view?.findViewById(R.id.z_player_video_background)
            loadingView = view?.findViewById(R.id.z_player_video_preview_vs_loading)
            seekBar = view?.findViewById(R.id.z_player_video_preview_sb)
            menuView = view?.findViewById(R.id.z_player_video_preview_v_menu)
            isLockScreenRotation = lockScreenRotation != -1
            isFull = bottomToolsBar?.visibility == View.VISIBLE
            speedView?.text = context.getString(R.string.z_player_str_speed, "1")
            touchListener?.setPadding(0.05f, 0.08f)

            loadingView?.let {
                loadingIgnoreInterval = ta.getInt(R.styleable.ZVideoView_loadingIgnoreTs, 0)
                val loadingBackgroundColor = ta.getColor(R.styleable.ZVideoView_loadingBackgroundColor, ContextCompat.getColor(context, R.color.z_player_color_trans_50))
                val loadingRes = ta.getResourceId(R.styleable.ZVideoView_loadingRes, R.drawable.z_player_loading_progressbar)
                val failRes = ta.getResourceId(R.styleable.ZVideoView_failRes, R.mipmap.z_player_video_loading_fail)
                val loadingIconWidth = ta.getDimension(R.styleable.ZVideoView_loadingIconWidth, 0f)
                val loadingIconHeight = ta.getDimension(R.styleable.ZVideoView_loadingIconHeight, 0f)
                val failIconWidth = ta.getDimension(R.styleable.ZVideoView_failIconWidth, 0f)
                val failIconHeight = ta.getDimension(R.styleable.ZVideoView_failIconHeight, 0f)
                val hintTextColor = ta.getColor(R.styleable.ZVideoView_hintTextColor, ContextCompat.getColor(context, R.color.z_player_color_loading))
                val refreshTextColor = ta.getColor(R.styleable.ZVideoView_refreshTextColor, ContextCompat.getColor(context, R.color.z_player_color_gray))
                val loadingText = ta.getString(R.styleable.ZVideoView_loadingText)
                val failText = ta.getString(R.styleable.ZVideoView_failText)
                val refreshHintText = ta.getString(R.styleable.ZVideoView_refreshHintText)
                val density = context.resources.displayMetrics.density
                val hintTextSize = ta.getDimension(R.styleable.ZVideoView_hintTextSize, density * 14f)
                val refreshTextSize = ta.getDimension(R.styleable.ZVideoView_refreshTextSize, density * 14f)
                it.setBackground(loadingBackgroundColor)
                it.setLoadingDrawable(loadingRes)
                it.setNoDataDrawable(failRes)
                it.setHintTextColor(hintTextColor)
                it.setRefreshTextColor(refreshTextColor)
                it.setLoadingText(if (!loadingText.isNullOrEmpty()) loadingText else context.getString(R.string.z_player_str_loading_video_progress))
                it.setFailText(if (!failText.isNullOrEmpty()) failText else context.getString(R.string.z_player_str_loading_video_error))
                it.setRefreshText(if (!refreshHintText.isNullOrEmpty()) refreshHintText else context.getString(R.string.z_player_str_loading_video_error_tint))
                if (hintTextSize > 0) it.setHintTextSize(hintTextSize)
                if (refreshTextSize > 0) it.setHintTextSize(refreshTextSize)
                it.setDrawableSize(loadingIconWidth, loadingIconHeight, failIconWidth, failIconHeight)
            }
        } catch (e: Exception) {
            if (e is RuntimeException) throw e
            else e.printStackTrace()
        } finally {
            ta.recycle()
        }
    }

    private fun <T : View> getViewByDefaultConfig(id: Int, mode: Int): T? {
        return if (mode == 1 || mode == 2) {
            val v = videoRoot?.findViewById<T>(id)
            v?.visibility = if (mode == 2) View.VISIBLE else View.GONE
            v
        } else null
    }

    private fun initListener() {
        vPlay?.setOnClickListener {
            log("on play btn click", BehaviorLogsTable.onPlayClick())
            onPlayClick(it, true)
        }

        videoRoot?.setTargetChangeListener { v, e ->
            touchListener?.updateTargetXY(v, e)
        }
        videoRoot?.setTouchInterceptor {
            (!isPlayable && (videoOverrideImageView?.scrollAble() ?: false)) && isFullScreen
        }
        videoRoot?.setOnTouchListener(touchListener)

        loadingView?.setRefreshListener {
            reload(it)
        }

        fullScreen?.setOnClickListener {
            onFullScreenClick(it, true)
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
            onLockScreenClick(it)
        }

        videoOverrideImageView?.setSingleTapListener {
            fullScreenDialog?.onDoubleClick()
        }

        videoOverrideImageView?.setTouchEnabled(ImageViewTouchEnableIn {
            return@ImageViewTouchEnableIn !isPlayable && isFullScreen
        })
    }

    private fun initVolume(isMute: Boolean) {
        val audioManager = context.getSystemService(Service.AUDIO_SERVICE) as AudioManager
        val volume = if (isMute) 0 else {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
        var max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            max -= audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        }
        controller?.setVolume(volume, max)
    }

    @SuppressLint("ClickableViewAccessibility")
    protected fun initSeekBar() {
        seekBar?.isEnabled = false
        seekBarSmall?.setOnTouchListener(null)
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                if (isTickingSeekBarFromUser && p2) {
                    controller?.seekTo(p0?.progress ?: 0, true)
                    if (autoFullTools) mHandler.removeMessages(dismissFullTools)
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                isTickingSeekBarFromUser = true
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                isTickingSeekBarFromUser = false
                controller?.autoPlayWhenReady(true)
                if (autoFullTools && isFull) mHandler.sendEmptyMessageDelayed(dismissFullTools, autoFullInterval.toLong())
            }
        })
    }

    override fun keepScreenOnWhenPlaying(): Boolean {
        return keepScreenOnWhenPlaying
    }

    override fun getKeepScreenOn(): Boolean {
        return keepScreenOnWhenPlaying
    }

    override fun onControllerBind(controller: ZController<*, *>?) {
        this.controller = controller
    }

    override fun onDestroy(path: String?, isRegulate: Boolean) {
        fullScreenDialog?.let {
            if (it.parent != null) it.dismiss()
        }
        controller = null
    }

    override fun getControllerInfo(): InflateInfo {
        return InflateInfo(getVideoRootView(), 2)
    }

    override fun onLoading(path: String, isRegulate: Boolean) {
        seekBar?.isEnabled = false
        showOrHidePlayBtn(false)
        onLoadingEvent(LoadingMode.Loading)
    }

    override fun onPrepare(path: String, videoSize: Long, isRegulate: Boolean) {
        seekBar?.isEnabled = true
        tvEnd?.text = getDuration(videoSize)
        if (muteIsUseGlobal) {
            if (muteView?.isSelected != muteGlobalDefault) muteView?.isSelected = muteGlobalDefault;initVolume(muteGlobalDefault)
        } else {
            if (muteView?.isSelected != muteDefault) muteView?.isSelected = muteDefault;initVolume(muteDefault)
        }
    }

    override fun onPlay(path: String, isRegulate: Boolean) {
        setOverlayViews(isShowThumb = false, isShowBackground = true, isSinkBottomShader = true)
        seekBar?.isSelected = true
        seekBar?.isEnabled = true
        isInterruptPlayBtnAnim = false
        onLoadingEvent(LoadingMode.None)
        if (!isTickingSeekBarFromUser) {
            showOrHidePlayBtn(isShow = false, withState = false, byAnim = true)
            full(false)
            seekBarSmall?.visibility = View.VISIBLE
        }
    }

    override fun onPause(path: String, isRegulate: Boolean) {
        seekBar?.isSelected = false
        showOrHidePlayBtn(true)
    }

    @CallSuper
    override fun updateCurPlayerInfo(volume: Int, speed: Float) {
        val nextState = volume <= 0
        if (muteIsUseGlobal) muteGlobalDefault = nextState
        muteDefault = nextState
        muteView?.isSelected = nextState
        curSpeedIndex = 0
        var lastMinValue = 100000f
        supportedSpeedList.forEachIndexed { index, fl ->
            val of = abs(speed - fl)
            if (of == 0f) {
                lastMinValue = 0f
                curSpeedIndex = index
                return@forEachIndexed
            }
            if (of < lastMinValue) {
                lastMinValue = of
                curSpeedIndex = index
            }
        }
        val curSpeed = supportedSpeedList[curSpeedIndex]
        val t = if (curSpeed == curSpeed.roundToInt().toFloat()) "${curSpeed.roundToInt()}" else DecimalFormat("#.0").format(curSpeed)
        speedView?.text = context.getString(R.string.z_player_str_speed, t)
    }

    override fun updateCurPlayingQuality(level: PlayQualityLevel, supportedQualities: MutableList<PlayQualityLevel>?) {
        if (supportedQualities.isNullOrEmpty()) {
            qualityView?.visibility = View.GONE
            qualityView = null
        } else {
            if (qualityView == null) qualityView = getViewByDefaultConfig(R.id.z_player_video_preview_tv_quality, qualityEnable)
            qualityView?.let {
                it.text = context.getText(level.textId)
                it.visibility = if (isDefaultMaxScreen) View.VISIBLE else View.GONE
                it.setOnClickListener {
                    menuView?.setSupportedMenusAndShow(level, supportedQualities) { l ->
                        controller?.requirePlayQuality(l)
                    }
                }
            }
        }
    }

    override fun onStop(path: String, isRegulate: Boolean) {
        reset(false, isRegulate = isRegulate, isShowPlayBtn = true)
    }

    override fun completing(path: String, isRegulate: Boolean) {
        isInterruptPlayBtnAnim = true
        showOrHidePlayBtn(true, withState = false)
        full(false)
    }

    override fun onCompleted(path: String, isRegulate: Boolean) {
        if (loadingView?.visibility != View.GONE) {
            onLoadingEvent(LoadingMode.None)
            completing(path, isRegulate)
        }
        seekBar?.isSelected = false
        seekBar?.isEnabled = false
        isInterruptPlayBtnAnim = true
        if (isRegulate) {
            showOrHidePlayBtn(true, withState = false)
            full(false)
        }
        onSeekChanged(0, 0, false, 0, 0)
    }

    override fun onSeekChanged(seek: Int, buffered: Int, fromUser: Boolean, played: Long, videoSize: Long) {
        if (isTickingSeekBarFromUser) return
        if (!fromUser) {
            seekBar?.progress = seek
            seekBar?.secondaryProgress = buffered
            seekBarSmall?.progress = seek
            onLoadingEvent(LoadingMode.None, true)
        }
        tvStart?.text = getDuration(played)
        tvEnd?.text = getDuration(videoSize)
    }

    override fun onSeekingLoading(path: String?) {
        isInterruptPlayBtnAnim = true
        onLoadingEvent(LoadingMode.Loading)
        showOrHidePlayBtn(false)
    }

    override fun onError(e: Exception?) {
        seekBar?.isSelected = false
        isInterruptPlayBtnAnim = true
        seekBarSmall?.visibility = View.GONE
        onSeekChanged(0, 0, false, 0, 0)
        showOrHidePlayBtn(false)
        onLoadingEvent(LoadingMode.Fail)
    }

    internal fun clickPlayBtn() {
        onPlayClick(vPlay ?: return, false)
    }

    open fun playIfReady() {
        controller?.playOrResume()
    }

    protected fun getVideoRootView(): RelativeLayout? {
        return videoRoot
    }

    open fun getThumbView(): TouchScaleImageView? {
        return videoOverrideImageView
    }

    open fun getBackgroundView(): ImageView? {
        return videoOverrideImageShaderView
    }

    open fun setScreenContentLayout(@LayoutRes layoutId: Int, onFullScreenLayoutInflateListener: ((v: View) -> Unit)? = null) {
        this.fullScreenContentLayoutId = layoutId
        this.onFullScreenLayoutInflateListener = onFullScreenLayoutInflateListener
    }

    open fun onFullScreenChanged(isFull: Boolean, payloads: Map<String, Any?>?) {}

    open fun onFullMaxScreenChanged(isFull: Boolean) {}

    open fun onTrack(playAble: Boolean, start: Boolean, end: Boolean, formTrigDuration: Float) {}

    open fun onTouchActionEvent(videoRoot: View?, event: MotionEvent, lastX: Float, lastY: Float, orientation: TrackOrientation?): Boolean {
        return false
    }

    open fun onPlayClick(v: View, fromUser: Boolean) {
        if (!isPlayable) return
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
            onLoadingEvent(LoadingMode.Loading, isSetInNow = false, ignoreInterval = true)
            postDelayed({
                onError(NullPointerException("video path is null"))
            }, 300)
        } else controller?.playOrResume(path)
    }

    open fun onRootClick() {
        if (!isPlayable) {
            ZPlayerLogs.debug("on video root click , cur status is disable play")
            return
        }
        if (controller?.isLoadData() != true) {
            ZPlayerLogs.debug("on video root click , and controller is not load data")
            return
        }
        log("on root view click", BehaviorLogsTable.onRootClick())
        onFullTools(!isFull)
    }

    open fun onRootDoubleClick() {
        log("on root view double click", BehaviorLogsTable.onRootDoubleClick())
        fullScreen?.let { onFullScreenClick(it, false) }
    }

    private fun onFullScreenClick(v: View, fromUser: Boolean, payloads: Map<String, Any?>? = null) {
        log("on full screen", BehaviorLogsTable.onFullScreen())
        if (!isFullingOrDismissing) {
            isFullingOrDismissing = true
            onFullScreen(!v.isSelected, this.onFullScreenClick(Transaction(fromUser, fullScreenTransactionTime, true, payloads)))
        }
    }

    fun fullScreen(isFull: Boolean, fromUser: Boolean, payloads: Map<String, Any?>? = null) {
        this.fullScreen(isFull, fromUser, fullScreenTransactionTime, true, payloads)
    }

    fun fullScreen(isFull: Boolean, fromUser: Boolean, transactionTime: Int, isStartOnly: Boolean, payloads: Map<String, Any?>? = null) {
        log("on override method full screen", BehaviorLogsTable.onChildFullScreen())
        if (!isFullingOrDismissing) {
            isFullingOrDismissing = true
            onFullScreen(isFull, this.onFullScreenClick(Transaction(fromUser, transactionTime, isStartOnly, payloads)))
        }
    }

    open fun onFullScreenClick(transaction: Transaction): Transaction {
        return transaction
    }

    open fun onSpeedClick(v: View) {
        resendAutoFullScreenAction()
        if (controller?.isReady() == true) {
            val next = supportedSpeedList[++curSpeedIndex % supportedSpeedList.size]
            requestNewSpeed(next)
        }
    }

    open fun requestNewSpeed(nextSpeed: Float) {
        controller?.setSpeed(nextSpeed)
    }

    open fun onMuteClick(v: View) {
        resendAutoFullScreenAction()
        val nextState = !v.isSelected
        try {
            initVolume(nextState)
        } catch (e: Exception) {
            error("the mute click to $nextState failed , trying to check the system audio manager as device type: ${Build.MANUFACTURER}")
        }
    }

    open fun onLockScreenClick(v: View) {
        resendAutoFullScreenAction()
        if (!lockScreenRotate(!v.isSelected)) Toast.makeText(context, R.string.z_player_str_screen_locked_tint, Toast.LENGTH_SHORT).show()
    }

    @CallSuper
    open fun removeView(tag: Any?, nullAbleView: WeakReference<View?>? = null) {
        getVideoRootView()?.let { v ->
            v.post {
                val view = v.findViewWithTag<View>(tag)
                if (view != null) getVideoRootView()?.removeView(view)
                else try {
                    getVideoRootView()?.removeView(nullAbleView?.get())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    open fun containsOverlayView(tag: Any?, view: WeakReference<View?>?): Boolean {
        getVideoRootView()?.let { rv ->
            return rv.findViewWithTag<View>(tag) != null || (view?.get()?.parent == rv)
        }
        return false
    }

    open fun addOverlayView(tag: Any, view: WeakReference<View?>?, paramsBuilder: ((RelativeLayout.LayoutParams) -> RelativeLayout.LayoutParams)? = null) {
        addOverlayView(tag, (getVideoRootView()?.childCount ?: 0) * 1.0f, view, paramsBuilder)
    }

    /**
     * For information about [zPoint], see [ZVideoView.addViewWithZPoint].
     * The difference is that calling this method provides a stable width and height under various conditions,
     * suitable for covering the full layout of the covered view, and can follow the full screen animation zoom
     * */
    @CallSuper
    open fun addOverlayView(tag: Any, zPoint: Float, view: WeakReference<View?>?, paramsBuilder: ((RelativeLayout.LayoutParams) -> RelativeLayout.LayoutParams)? = null) {

        fun generateLp(invoke: (RelativeLayout.LayoutParams) -> Unit) {
            getThumbView()?.let {
                it.post {
                    val match = MATCH_PARENT
                    var ivW: Int? = null
                    var ivH: Int? = null
                    try {
                        val bounds = it.getRealBounds()
                        ivW = bounds[0]
                        ivH = bounds[1]
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    if ((ivW ?: 0) <= 0 || (ivH ?: 0) <= 0) {
                        ivW = it.width
                        ivH = it.height
                    }
                    val width = if (isFullScreen) ivW ?: match else width
                    val height = if (isFullScreen) ivH ?: match else height
                    val lp = RelativeLayout.LayoutParams(width, height)
                    lp.addRule(RelativeLayout.CENTER_IN_PARENT)
                    invoke(lp)
                }
            }
        }
        generateLp {
            val nlp = paramsBuilder?.invoke(it) ?: it
            addViewWithZPoint(tag, view, zPoint, nlp)
        }
    }

    /**
     * @param zPoint This value will determine the Z-axis position after trying to be added to the Controller,that is, the height.
     * [zPoint] can be passed in one A larger value to ensure that the View is always at the top of the control,
     * of course, additional calls [RelativeLayout.bringChildToFront] or [View.bringToFront] will only take effect when the layout is stable
     * if current [zPoint] less tha 6 ,the adding view may below of the video controller children.
     * */
    @CallSuper
    open fun addViewWithZPoint(tag: Any?, view: WeakReference<View?>?, zPoint: Float, nlp: RelativeLayout.LayoutParams? = null) {
        getVideoRootView()?.let { rv ->
            rv.post {
                var isAdd = true
                val vp = view?.get()?.parent as? ViewGroup
                rv.findViewWithTag<View>(tag)?.let { exi ->
                    if (vp != rv) vp?.removeView(exi)
                    else isAdd = false
                } ?: vp?.let {
                    if (it != rv) it.removeView(view.get())
                    else isAdd = false
                }
                if (isAdd) {
                    view?.get()?.tag = tag
                    view?.get()?.setTag(R.id.tag_view_point_z, zPoint)
                    if (zPoint <= 0 || rv.childCount <= 0) {
                        rv.addView(view?.get() ?: return@post, 0, nlp)
                    } else {
                        view?.get()?.let { v ->
                            v.translationZ = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, zPoint, resources.displayMetrics)
                            rv.addView(v, nlp)
                        }
                    }
                }
            }
        }
    }

    /**
     * even though you called it and set the lock to FALSE ,
     * it also merge with the system screen rotate settings.
     * @return the status form this operation
     * */
    @CallSuper
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
        return String.format("${if (minute < 10) "0%d" else "%d"}:${if (second < 10) "0%d" else "%d"}", minute, second)
    }

    protected fun showOrHidePlayBtn(isShow: Boolean, withState: Boolean = false, byAnim: Boolean = false) {
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
                if (!byAnim || enablePlayAnimation) {
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
                } else {
                    it.visibility = if (isShow) View.VISIBLE else View.GONE
                    it.isEnabled = true
                }
            } finally {
                if (isNeedSetFreePlayBtn) it.isEnabled = true
            }
        }
    }

    protected fun full(isFull: Boolean, isSetNow: Boolean = false) {
        if (autoFullTools) mHandler.removeMessages(dismissFullTools)
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
        onToolsBarChanged(isFull, isSetNow)
        if (autoFullTools && isFull) mHandler.sendEmptyMessageDelayed(dismissFullTools, autoFullInterval.toLong())
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

    private val fullListener = object : ZFullValueAnimator.FullAnimatorListener() {

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

    private val fullScreenListener = object : FullScreenListener {
        override fun onDisplayChanged(isShow: Boolean, payloads: Map<String, Any?>?) {
            this@ZVideoView.onDisplayChanged(isShow, payloads)
        }

        override fun onFocusChange(dialog: ZPlayerFullScreenView, isMax: Boolean) {
            onFocusChanged(dialog, isMax)
        }

        override fun onTrack(isStart: Boolean, isEnd: Boolean, formTrigDuration: Float) {
            onTracked(isStart, isEnd, formTrigDuration)
        }

        override fun onKeyEvent(code: Int, event: KeyEvent): Boolean {
            return onFullKeyEvent(code, event)
        }
    }

    private val fullContentListener = object : FullContentListener {
        override fun onDisplayChanged(isShow: Boolean, payloads: Map<String, Any?>?) {
            onTrack(isPlayable, start = false, end = true, formTrigDuration = 1.0f)
            this@ZVideoView.onDisplayChanged(isShow, payloads)
        }

        override fun onContentLayoutInflated(content: View) {
            onFullScreenLayoutInflateListener?.invoke(content)
        }

        override fun onFullMaxChanged(dialog: ZPlayerFullScreenView, isMax: Boolean) {
            lockScreen?.visibility = if (isPlayable && isMax) View.VISIBLE else GONE
            qualityView?.visibility = if (isPlayable && isMax) View.VISIBLE else GONE
            onFocusChanged(dialog, isMax)
        }

        override fun onFocusChange(dialog: ZPlayerFullScreenView, isMax: Boolean) {
            onFocusChanged(dialog, isMax)
        }

        override fun onTrack(isStart: Boolean, isEnd: Boolean, formTrigDuration: Float) {
            onTracked(isStart, isEnd, formTrigDuration)
        }

        override fun onKeyEvent(code: Int, event: KeyEvent): Boolean {
            return onFullKeyEvent(code, event)
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun onFullScreen(full: Boolean, transaction: Transaction) {
        getVideoRootView()?.let { root ->
            if (!full) {
                lockScreen?.visibility = GONE
                qualityView?.visibility = GONE
                fullScreenDialog?.dismiss()
            } else {
                if (!isDefaultMaxScreen) (context as? Activity)?.let {
                    if (it.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                        it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
                if (fullScreenDialog == null) fullScreenDialog = ZPlayerFullScreenView.let { d ->
                    d.open(root).defaultOrientation(lockScreenRotation).transactionNavigation(isTransactionNavigation).transactionAnimDuration(transaction.transactionTime, transaction.isStartOnly, fullScreenTransactionTime).payLoads(transaction.payloads).let { config ->
                        if (isDefaultMaxScreen) {
                            lockScreen?.visibility = View.VISIBLE
                            qualityView?.visibility = View.VISIBLE
                            config.withFullMaxScreen(fullScreenListener)
                        } else {
                            config.withFullContentScreen(fullScreenContentLayoutId, fullMaxScreenEnable, fullContentListener)
                        }
                        config.start(context)
                    }
                }
                lockScreenRotate(isLockScreenRotation)
            }
        }
    }

    private var lastIsFull = false
    private fun onTracked(isStart: Boolean, isEnd: Boolean, formTrigDuration: Float) {
        if (isPlayable) {
            if (isStart) lastIsFull = this.isFull
            if (isStart && this.isFull) {
                isStartTrack = true;full(false)
                showOrHidePlayBtn(isShow = false, withState = true)
            }
            if (isEnd && !this.isFull) {
                isStartTrack = false
                if (lastIsFull && controller?.isLoadData() == true) {
                    full(true)
                    if (!isInterruptPlayBtnAnim) showOrHidePlayBtn(isShow = true, withState = true)
                }
            }
        }
        onTrack(isPlayable, isStart, isEnd, formTrigDuration)
    }


    @CallSuper
    open fun reset(isNow: Boolean, isRegulate: Boolean, isShowPlayBtn: Boolean, isShowThumb: Boolean = true, isShowBackground: Boolean = true, isSinkBottomShader: Boolean = false) {
        vPlay?.isEnabled = isShowPlayBtn
        onLoadingEvent(LoadingMode.None, true)
        setOverlayViews(isShowThumb, isShowBackground, isSinkBottomShader)
        seekBar?.isSelected = false
        seekBar?.isEnabled = false
        isInterruptPlayBtnAnim = true
        seekBarSmall?.visibility = View.GONE
        onSeekChanged(0, 0, false, 0, 0)
        if (isRegulate) showOrHidePlayBtn(isShowPlayBtn, withState = false)
        full(false, isSetNow = isNow)
        muteView?.isSelected = if (muteIsUseGlobal) muteGlobalDefault else muteDefault
        qualityView?.visibility = View.GONE
    }

    protected fun onDisplayChanged(isShow: Boolean, payloads: Map<String, Any?>?) {
        log("on full screen $isShow", BehaviorLogsTable.onFullscreen(isShow))
        isFullScreen = isShow
        if (!isShow) {
            fullScreenDialog = null
            lockScreen?.isSelected = false
        }
        isFullingOrDismissing = false
        onFullScreenChanged(isShow, payloads)
    }

    /**
     * If the event is consumed, return true, otherwise false
     * */
    open fun onFullKeyEvent(code: Int, event: KeyEvent): Boolean {
        return false
    }

    open fun onToolsBarChanged(isFullExpand: Boolean, isResetNow: Boolean) {}

    private fun onFocusChanged(dialog: ZPlayerFullScreenView, isMax: Boolean) {
        log("on full max screen $isMax", BehaviorLogsTable.onFullMaxScreen(isMax))
        if (isMax) lockScreen?.isSelected = dialog.isLockedCurrent()
        onFullMaxScreenChanged(isMax)
    }

    protected fun setMuteIsGlobal(isGlobal: Boolean) {
        muteIsUseGlobal = isGlobal
    }

    protected fun checkActIsFinished(): Boolean {
        return (context as? Activity)?.isFinishing == true
    }

    private fun onLoadingEvent(loadingMode: LoadingMode, isSetInNow: Boolean = false, ignoreInterval: Boolean = false) {
        mHandler.removeMessages(loadingModeDelay)
        loadingView?.let {
            when (loadingMode) {
                LoadingMode.None -> it.setMode(VideoLoadingView.DisplayMode.NONE, isSetInNow)
                LoadingMode.Loading -> if (isSetInNow || loadingIgnoreInterval <= 0 || ignoreInterval) it.setMode(VideoLoadingView.DisplayMode.LOADING, isSetInNow)
                else mHandler.sendEmptyMessageDelayed(loadingModeDelay, loadingIgnoreInterval.toLong())
                LoadingMode.Fail -> it.setMode(VideoLoadingView.DisplayMode.NO_DATA, isSetInNow)
            }
        }
    }

    private fun onFullTools(isFull: Boolean) {
        if (isTickingSeekBarFromUser) return
        controller?.let {
            if (!isInterruptPlayBtnAnim) {
                showOrHidePlayBtn(isFull, true)
            }
            full(isFull)
        }
    }

    private fun resendAutoFullScreenAction() {
        if (autoFullTools) mHandler.removeMessages(dismissFullTools)
        if (autoFullTools && isFull) mHandler.sendEmptyMessageDelayed(dismissFullTools, autoFullInterval.toLong())
    }

    private fun log(s: String, bd: BehaviorData? = null) {
        controller?.recordLogs(s, "BaseViewController", bd)
    }

    protected fun recordLogs(s: String, modeName: String, vararg params: Pair<String, Any>) {
        if (Constance.CORE_LOG_ABLE) ZPlayerLogs.onLog(s, controller?.getPath() ?: "", "", modeName, *params)
    }

    fun getCurSpeed(): Float {
        return controller?.getCurSpeed() ?: 1.0f
    }

    fun getCurVolume(): Int {
        return controller?.getCurVolume() ?: 0
    }

    fun isPause(accurate: Boolean = false): Boolean {
        return controller?.isPause(accurate) ?: false
    }

    fun isStop(accurate: Boolean = false): Boolean {
        return controller?.isStop(accurate) ?: true
    }

    fun isPlaying(accurate: Boolean = false): Boolean {
        return controller?.isPlaying(accurate) ?: false
    }

    fun isReady(accurate: Boolean = false): Boolean {
        return controller?.isReady(accurate) ?: false
    }

    fun isLoading(accurate: Boolean = false): Boolean {
        return controller?.isLoading(accurate) ?: false
    }

    fun isLoadData(): Boolean {
        return controller?.isLoadData() ?: false
    }

    fun isDestroyed(accurate: Boolean = false): Boolean {
        return controller?.isDestroyed(accurate) ?: true
    }
}