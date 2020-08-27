package com.zj.player

import android.content.Context
import android.content.res.Resources
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.RelativeLayout.CENTER_IN_PARENT
import androidx.annotation.IntRange
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.zj.player.ut.Constance.CORE_LOG_ABLE
import com.zj.player.ut.Controller
import com.zj.player.ut.PlayerEventController
import com.zj.player.config.VideoConfig
import com.zj.player.logs.BehaviorData
import com.zj.player.logs.BehaviorLogsTable
import com.zj.player.logs.ZPlayerLogs
import java.lang.NullPointerException

/**
 * @author ZJJ on 2020/6/22.
 *
 * A controller that interacts with the user interface, player, and renderer.
 * */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class ZController private constructor(private var player: ZPlayer?, viewController: Controller?) : PlayerEventController, LifecycleObserver {

    private var seekProgressInterval: Long = 16
    private var render: ZRender? = null
    private var curAccessKey: String = ""
    private var isPausedByLifecycle = false
    private var isIgnoreNullControllerGlobal = false
    private var viewController: Controller? = null
        set(value) {
            if (field != null) {
                field?.onControllerBind(null)
            }
            field = value
            field?.onControllerBind(this)
            isIgnoreNullControllerGlobal = value == null
        }

    init {
        this.viewController = viewController
        isBindLifecycle(true)
        curAccessKey = runWithPlayer { it.setViewController(this) } ?: ""
    }

    /**
     * build a video controller.
     * require a viewController and a player ex [ZPlayer]
     * the uniqueId is required and it also binding with a viewController, changed if recreate or viewController [updateViewController] updated.
     * */
    companion object {

        private const val releaseKey = " - released - "

        fun build(viewController: Controller): ZController {
            return build(viewController, ZPlayer(VideoConfig.create()))
        }

        fun build(viewController: Controller, config: VideoConfig): ZController {
            return build(viewController, ZPlayer(config))
        }

        fun <T : ZPlayer> build(viewController: Controller, player: T): ZController {
            return ZController(player, viewController)
        }
    }

    private fun checkRenderToken(needed: Boolean): Controller? {
        val ignore = render?.parent == null && !needed
        val c = getController()
        addRenderAndControllerView(c, !needed, ignore)
        return c
    }

    // Add a renderer to the video ,or remove only
    private fun addRenderAndControllerView(c: Controller?, removeOnly: Boolean = false, ignoredNullController: Boolean = false) {
        if (render == null) render = ZRender(context ?: return)
        val info = c?.controllerInfo ?: if (isIgnoreNullControllerGlobal || ignoredNullController) return else throw NullPointerException("the controller view is required")
        val ctr = info.container
        (render?.parent as? ViewGroup)?.let {
            if (ctr != it || removeOnly) {
                it.removeView(render)
            }
            if (ctr == it && !removeOnly) return
        }
        if (ctr == null || removeOnly) return
        if (ctr.measuredWidth <= 0 || ctr.measuredHeight <= 0) log("the controller view size is 0 , render may not to display")
        val rlp = info.layoutParams ?: getSuitParentLayoutParams(ctr)
        render?.z = Resources.getSystem().displayMetrics.density * info.zHeightDp + 0.5f
        ctr.addView(render, 0, rlp)
        log("the render view added in $ctr")
    }

    /**
     * Set an address to be played, the player will create the necessary components but will not actively play the video,
     * you need to manually call [playOrResume] or #{@link autoPlayWhenReady(true) } to play, of course, the path parameter will no longer be necessary.
     * @param autoPlay When this value is true, the video will play automatically after loading is completed.
     * */
    fun setData(url: String, autoPlay: Boolean = false, callId: Any? = null) {
        log("user set new data", BehaviorLogsTable.setNewData(url, callId, autoPlay))
        runWithPlayer { it.setData(url, autoPlay, callId) }
    }

    /**
     * Set the minimum time difference of the automatic retrieval progress during video playback, usually in ms.
     * */
    fun setSeekInterval(interval: Long) {
        log("user set seek interval to $interval")
        this.seekProgressInterval = interval
    }

    /**
     * Get the current video address of the player. Local or remote.
     * */
    fun getPath(): String {
        return runWithPlayer { it.currentPlayPath() } ?: ""
    }

    /**
     * Get the current video playing back call id.
     * */
    fun getCallId(): Any? {
        return runWithPlayer { it.currentCallId() }
    }

    /**
     * Retrieve the video playback position, [i] can only between 0-100.
     * */
    fun seekTo(@IntRange(from = 0, to = 100) i: Int) {
        log("user seek the video on $i%")
        runWithPlayer { it.seekTo(i, true) }
    }

    /**
     * On/Off No matter what the current state of the player is, when the first frame of the video is loaded, it will start to play automatically.
     * */
    fun autoPlayWhenReady(autoPlay: Boolean) {
        log("user set auto play when ready")
        runWithPlayer { it.autoPlay(autoPlay) }
    }

    /**
     * Call this method to start automatic playback after the player processes playable frames.
     * */
    fun playOrResume(path: String = getPath(), callId: Any? = null) {
        log("user call play or resume")
        if (path != getPath()) setData(path, false, callId)
        runWithPlayer { it.play() }
    }

    fun pause() {
        log("user call pause")
        runWithPlayer { it.pause() }
    }

    fun stop() {
        log("user call stop")
        runWithPlayer { it.stop() }
    }

    fun stopNow(withNotify: Boolean = false, isRegulate: Boolean = false) {
        log("user call stop --now")
        runWithPlayer { it.stopNow(withNotify, isRegulate) }
    }

    fun setSpeed(s: Float) {
        log("user set speed to $s")
        runWithPlayer { it.setSpeed(s) }
    }

    fun setVolume(volume: Float) {
        log("user set volume to $volume")
        runWithPlayer { it.setVolume(volume) }
    }

    fun isPause(accurate: Boolean = false): Boolean {
        log("user query cur state is pause or not")
        return runWithPlayer { it.isPause(accurate) } ?: false
    }

    fun isStop(accurate: Boolean = false): Boolean {
        log("user query cur state is stop or not")
        return runWithPlayer { it.isStop(accurate) } ?: true
    }

    fun isPlaying(accurate: Boolean = false): Boolean {
        log("user query cur state is playing or not")
        return runWithPlayer { it.isPlaying(accurate) } ?: false
    }

    fun isReady(accurate: Boolean = false): Boolean {
        log("user query cur state is ready or not")
        return runWithPlayer { it.isReady(accurate) } ?: false
    }

    fun isLoading(accurate: Boolean = false): Boolean {
        log("user query cur state is loading or not")
        return runWithPlayer { it.isLoading(accurate) } ?: false
    }

    fun isLoadData(): Boolean {
        log("user query cur state is  loaded data")
        return runWithPlayer { it.isLoadData() } ?: false
    }

    fun isDestroyed(accurate: Boolean = false): Boolean {
        log("user query cur state is destroy or not")
        return runWithPlayer { it.isDestroyed(accurate) } ?: true
    }

    fun getCurVolume(): Float {
        return player?.getVolume() ?: 0f
    }

    fun getCurSpeed(): Float {
        return player?.getSpeed() ?: 1f
    }

    /**
     * Use another View to bind to the Controller. The bound ViewController will take effect immediately and receive the method callback from the player.
     * */
    fun updateViewController(viewController: Controller?) {
        if (viewController != null) {
            if (this.viewController != viewController) {
                addRenderAndControllerView(viewController, false)
                log("user update the view controller names ${viewController::class.java.simpleName}")
                syncPlayerState()
            }
        } else {
            addRenderAndControllerView(this.viewController, false)
        }
        this.viewController = viewController
    }

    fun syncPlayerState() {
        if (viewController != null) {
            runWithPlayer { it.updateControllerState() }
        }
    }

    /**
     * recycle a Controller in Completely, after which this instance will be invalid.
     * */
    fun release() {
        isPausedByLifecycle = false
        (render?.parent as? ViewGroup)?.removeView(render)
        render?.release()
        render = null
        player?.release()
        viewController?.let {
            it.onStop("", true)
            it.onDestroy("", true)
        }
        viewController = null
        player = null
        isBindLifecycle(false)
        seekProgressInterval = -1
        curAccessKey = releaseKey
    }


    private fun <T> runWithPlayer(throwMust: Boolean = true, block: (ZPlayer) -> T): T? {
        return try {
            player?.let {
                block(it) ?: return@runWithPlayer null
            } ?: {
                if (curAccessKey != releaseKey) {
                    throw NullPointerException("are you forgot setting a Player in to the video view controller? ,now it used the default player.")
                } else null
            }.invoke()
        } catch (e: java.lang.Exception) {
            if (throwMust) ZPlayerLogs.onError("in VideoViewController.runWithPlayer error case: - ${e.message}")
            null
        }
    }

    override fun getProgressInterval(): Long {
        return seekProgressInterval
    }

    override fun onError(e: Exception?) {
        checkRenderToken(true)?.onError(e)
        ZPlayerLogs.onError(e, true)
    }

    override fun getPlayerView(): ZRender? {
        return render
    }

    override fun onLoading(path: String?, isRegulate: Boolean) {
        log("on video loading ...", BehaviorLogsTable.controllerState("loading", getCallId(), getPath()))
        checkRenderToken(true)?.onLoading(path, isRegulate)
    }

    override fun onPause(path: String?, isRegulate: Boolean) {
        log("on video loading ...", BehaviorLogsTable.controllerState("onPause", getCallId(), getPath()))
        checkRenderToken(true)?.onPause(path, isRegulate)
    }

    override fun onFirstFrameRender() {
        log("the video had rendered a first frame !")
    }

    override fun onSeekChanged(seek: Int, buffered: Int, fromUser: Boolean, videoSize: Long) {
        if (fromUser) log("on seek changed to $seek")
        checkRenderToken(true)?.onSeekChanged(seek, buffered, fromUser, videoSize)
    }

    override fun onSeekingLoading(path: String?, isRegulate: Boolean) {
        log("on video seek loading ...", BehaviorLogsTable.controllerState("onSeekLoading", getCallId(), getPath()))
        checkRenderToken(true)?.onSeekingLoading(path)
    }

    override fun onPrepare(path: String?, videoSize: Long, isRegulate: Boolean) {
        log("on video prepare ...", BehaviorLogsTable.controllerState("onPrepare", getCallId(), getPath()))
        checkRenderToken(true)?.onPrepare(path, videoSize, isRegulate)
    }

    override fun getContext(): Context? {
        return getController()?.context
    }

    override fun onPlay(path: String?, isRegulate: Boolean) {
        log("on video playing ...", BehaviorLogsTable.controllerState("onPlay", getCallId(), getPath()))
        checkRenderToken(true)?.onPlay(path, isRegulate)
    }

    override fun onStop(notifyStop: Boolean, path: String?, isRegulate: Boolean) {
        log("on video stop ...", BehaviorLogsTable.controllerState("onStop", getCallId(), getPath()))
        val c = checkRenderToken(false)
        if (notifyStop) c?.onStop(path, isRegulate)
    }

    override fun onCompleted(path: String?, isRegulate: Boolean) {
        log("on video completed ...", BehaviorLogsTable.controllerState("onCompleted", getCallId(), getPath()))
        checkRenderToken(false)?.onCompleted(path, isRegulate)
    }

    override fun completing(path: String?, isRegulate: Boolean) {
        log("on video completing ...", BehaviorLogsTable.controllerState("completing", getCallId(), getPath()))
        checkRenderToken(true)?.completing(path, isRegulate)
    }

    override fun onPlayerInfo(volume: Float, speed: Float) {
        log("on video upload player info ...", BehaviorLogsTable.controllerState("onUploadPlayerInfo", getCallId(), getPath()))
        checkRenderToken(false)?.updateCurPlayerInfo(volume, speed)
    }

    private fun getSuitParentLayoutParams(v: ViewGroup): ViewGroup.LayoutParams {
        return when (v) {
            is FrameLayout -> FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                this.gravity = Gravity.CENTER
            }
            is RelativeLayout -> RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT).apply {
                this.addRule(CENTER_IN_PARENT)
            }
            is LinearLayout -> LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                this.gravity = Gravity.CENTER
            }
            else -> ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    private fun isBindLifecycle(isBind: Boolean) {
        (getController()?.context as? LifecycleOwner)?.let {
            if (isBind) it.lifecycle.addObserver(this)
            else it.lifecycle.removeObserver(this)
        }
    }

    fun getController(): Controller? {
        return viewController
    }

    @OnLifecycleEvent(value = Lifecycle.Event.ON_RESUME)
    private fun onResumed() {
        if (isPausedByLifecycle) {
            isPausedByLifecycle = false
            playOrResume()
        }
        getController()?.onLifecycleResume()
    }

    @OnLifecycleEvent(value = Lifecycle.Event.ON_STOP)
    private fun onStopped() {
        getController()?.onLifecycleStop()
    }

    @OnLifecycleEvent(value = Lifecycle.Event.ON_PAUSE)
    private fun onPaused() {
        if (isPlaying()) {
            isPausedByLifecycle = true
            pause()
        }
        getController()?.onLifecyclePause()
    }

    @OnLifecycleEvent(value = Lifecycle.Event.ON_DESTROY)
    private fun onDestroyed() {
        release()
    }

    private fun log(s: String, bd: BehaviorData? = null) {
        recordLogs(s, "ZController", bd)
    }

    internal fun recordLogs(s: String, modeName: String, bd: BehaviorData? = null) {
        if (CORE_LOG_ABLE) ZPlayerLogs.onLog(s, getPath(), curAccessKey, modeName, bd)
    }
}