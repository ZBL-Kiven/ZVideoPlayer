package com.zj.youtube

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.CallSuper
import com.zj.youtube.YouTubePlayerBridge.Companion.STATE_UNSTARTED
import com.zj.youtube.constance.PlayerConstants
import com.zj.youtube.options.IFramePlayerOptions
import com.zj.youtube.proctol.YouTubePlayerListener
import com.zj.youtube.utils.PendingLoadTask
import com.zj.youtube.utils.Utils
import java.lang.Exception

@Suppress("MemberVisibilityCanBePrivate")
abstract class YoutubeDelegate(debugAble: Boolean) : YouTubePlayerListener {

    var curPath: String = ""
    var curBuffering = 0.0f
    var curPlayingDuration = 0L
    var totalDuration = 0L
    var curPlayingRate = 0f
    var curVolume = 1
    var isPageReady = false
    private var inLoading = false

    companion object {
        private const val HANDLE_SEEK = 0x165771
    }

    private var pendingIfNotReady: PendingLoadTask? = null
    private var playerState: PlayerConstants.PlayerState = PlayerConstants.PlayerState.UNKNOWN

    open fun onPlayStateChange(curState: PlayerConstants.PlayerState, oldState: PlayerConstants.PlayerState, fromSync: Boolean) {}
    open fun onPlayQualityChanged(quality: PlayerConstants.PlaybackQuality, supports: List<PlayerConstants.PlaybackQuality>?) {}

    init {
        if (debugAble) Utils.openDebug()
    }

    abstract fun onSeekChanged(fromUser: Boolean)

    abstract fun onSeekParsed(progress: Long, fromUser: Boolean)

    abstract fun getWebView(): WebView?

    private val mainThreadHandler: Handler = Handler(Looper.getMainLooper()) {
        when (it.what) {
            HANDLE_SEEK -> onSeekParsed(it.obj as Long, it.arg2 == 0)
        }
        return@Handler false
    }

    private fun runWithWebView(func: (WebView) -> Unit) {
        try {
            mainThreadHandler.post {
                getWebView()?.let {
                    func(it)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun initYoutubeScript(transparentWebBackground: Boolean, playerOptions: IFramePlayerOptions) {
        val ctx = getWebView()?.context ?: return
        val htmlPage = Utils.readHTMLFromUTF8File(ctx.resources.openRawResource(R.raw.youtube_player_bridge)).replace("<<injectedPlayerVars>>", playerOptions.toString())
        Utils.log(htmlPage)
        runWithWebView {
            it.settings?.mediaPlaybackRequiresUserGesture = false
            it.settings?.cacheMode = WebSettings.LOAD_NO_CACHE
            if (transparentWebBackground) it.setBackgroundColor(Color.TRANSPARENT)
            it.loadDataWithBaseURL(playerOptions.getOrigin(), htmlPage, "text/html", "utf-8", null)
        }
    }

    fun loadVideoById(id: String, startSeconds: Float, suggestionQuality: String, pendingIfNotReady: PendingLoadTask, fromPending: Boolean) {
        if (id == curPath && inLoading) return
        if (!fromPending) playerState = PlayerConstants.PlayerState.UNKNOWN
        if (isPageReady) {
            inLoading = true
            this.pendingIfNotReady = null
            onStateChange(PlayerConstants.PlayerState.LOADING)
            runWithWebView {
                this.curPath = id
                it.loadUrl("javascript:loadVideoById(\'$id\', $startSeconds, \'$suggestionQuality\')")
            }
        } else {
            if (this.pendingIfNotReady?.path != pendingIfNotReady.path) {
                this.pendingIfNotReady?.let { r -> mainThreadHandler.removeCallbacks(r) }
                this.pendingIfNotReady = pendingIfNotReady
            }
        }
    }

    fun play(): Boolean {
        if (!isPageReady) Utils.log("call play error ! please wait to page ready!")
        if (curPath.isEmpty() || !isPageReady) return false
        pendingIfNotReady = null
        runWithWebView { it.loadUrl("javascript:playVideo()") }
        return true
    }

    fun pause() {
        if (!isPageReady) Utils.log("call pause error ! please wait to page ready!")
        runWithWebView {
            pendingIfNotReady = null
            if (isPageReady && isPlaying(false)) it.loadUrl("javascript:pauseVideo()")
            playerState = PlayerConstants.PlayerState.PAUSED
        }
    }

    fun stop() {
        curPath = ""
        pendingIfNotReady = null
        curPlayingDuration = 0
        getWebView()?.visibility = View.INVISIBLE
        if (!isPageReady) Utils.log("call stop error ! please wait to page ready!")
        if (isPageReady && !isStop(false)) runWithWebView {
            it.loadUrl("javascript:stop()")
        }
        playerState = PlayerConstants.PlayerState.STOP
    }

    fun setVolume(volumePercent: Int, isFollowToDevice: Boolean) {
        Utils.log("user set the volume : new volume = $volumePercent , isFollowToDevice =  $isFollowToDevice")
        curVolume = volumePercent
        runWithWebView {
            if (isFollowToDevice && volumePercent in 0..100) it.loadUrl("javascript:setVolume($volumePercent)")
            else it.loadUrl("javascript:setVolume(${if (volumePercent <= 0) "0" else "100"})")
            if (volumePercent <= 0) it.loadUrl("javascript:mute()") else {
                it.loadUrl("javascript:unMute()")
            }
        }
    }

    fun setPlaybackRate(rate: Float) {
        if (isPageReady) runWithWebView { it.loadUrl("javascript:setPlaybackRate($rate)") }
    }

    fun setPlaybackQuality(quality: String) {
        if (isPageReady) runWithWebView { it.loadUrl("javascript:setPlaybackQuality(\'$quality\')") }
    }

    fun destroy() {
        pendingIfNotReady = null
        mainThreadHandler.removeCallbacksAndMessages(null)
        runWithWebView {
            playerState = PlayerConstants.PlayerState.STOP
            it.clearHistory()
            it.clearFormData()
            it.removeAllViews()
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        curPath = ""
    }

    fun seekTo(progress: Long, fromUser: Boolean) {
        mainThreadHandler.removeMessages(HANDLE_SEEK)
        mainThreadHandler.sendMessageDelayed(Message.obtain().apply {
            what = HANDLE_SEEK
            obj = progress
            arg2 = if (fromUser) 0 else 1
        }, 200)
    }

    fun seekNow(progress: Long, fromUser: Boolean) {
        curPlayingDuration = progress / 1000L
        runWithWebView {
            if (isPageReady) it.loadUrl("javascript:seekTo($curPlayingDuration,$fromUser)")
            onSeekChanged(fromUser)
        }
    }

    override fun onYouTubeIFrameAPIReady() {
        if (isPageReady) runWithWebView { it.loadUrl("javascript:hideInfo()") }
    }

    @CallSuper
    override fun onReady(totalDuration: Long) {
        this.isPageReady = true
        this.totalDuration = totalDuration * 1000L
        pendingIfNotReady?.let {
            setVolume(curVolume, true)
            mainThreadHandler.post(it)
        }
    }

    final override fun onStateChange(state: PlayerConstants.PlayerState) {
        if (state != PlayerConstants.PlayerState.LOADING && state != PlayerConstants.PlayerState.BUFFERING) inLoading = false
        if (state == PlayerConstants.PlayerState.PAUSED && playerState == PlayerConstants.PlayerState.STOP) return
        if (playerState == PlayerConstants.PlayerState.PAUSED && (state == PlayerConstants.PlayerState.LOADING)) return
        if (playerState.inLoading() && state.inLoading()) return
        if ((playerState == PlayerConstants.PlayerState.ERROR || playerState == PlayerConstants.PlayerState.STOP) && state.from == STATE_UNSTARTED) return
        if (playerState == PlayerConstants.PlayerState.LOADING && state == PlayerConstants.PlayerState.PAUSED) return
        try {
            getWebView()?.let {
                it.visibility = if (state.inMutual() && !(playerState.inLoading() && state == PlayerConstants.PlayerState.PAUSED)) View.VISIBLE else View.INVISIBLE
                it.loadUrl("javascript:hideInfo()")
                onPlayStateChange(state, playerState, state.from == "sync")
            }
        } finally {
            Utils.log("on player state changed from $playerState to $state ,from = ${state.from}")
            state.setFrom("")
            this.playerState = state
        }
    }

    final override fun onPlaybackQualityChange(playbackQuality: PlayerConstants.PlaybackQuality, playbackQualities: List<PlayerConstants.PlaybackQuality>?) {
        Utils.log("the cur play back quality is ${playbackQuality.name} , supported : ${playbackQualities?.joinToString { it.value }}")
        onPlayQualityChanged(playbackQuality, playbackQualities?.toMutableList())
    }

    override fun onPlaybackRateChange(playbackRate: PlayerConstants.PlaybackRate) {
        Utils.log("the cur play back rate is ${playbackRate.name}")
        curPlayingRate = playbackRate.rate
    }

    @CallSuper
    override fun onError(error: PlayerConstants.PlayerError) {
        Utils.log("play error case: ${error.name}")
        this.curPath = ""
        this.inLoading = false
        this.pendingIfNotReady = null
        this.curPlayingDuration = 0
        onStateChange(PlayerConstants.PlayerState.ERROR.setFrom(error.name))
    }

    @CallSuper
    override fun onCurrentSecond(second: Long) {
        curPlayingDuration = second * 1000L
        mainThreadHandler.post { onSeekChanged(false) }
    }

    @CallSuper
    override fun onCurrentPlayerInfo(curVolume: Int, isMute: Boolean, curSpeed: Float) {
        Utils.log("on video info parsed : volume = $curVolume isMuted = $isMute  speed =  $curSpeed")
        this.curPlayingRate = curSpeed
        this.curVolume = if (isMute) 0 else curVolume
    }

    @CallSuper
    override fun onVideoDuration(duration: Long) {
        Utils.log("on video duration parsed : $duration")
        totalDuration = duration * 1000L
        mainThreadHandler.post { onSeekChanged(false) }
    }

    @CallSuper
    override fun onVideoLoadedFraction(loadedFraction: Float) {
        curBuffering = loadedFraction
        mainThreadHandler.post { onSeekChanged(false) }
    }

    @CallSuper
    override fun onVideoUrl(videoUrl: String) {
        Utils.log("the youtube video path received : $videoUrl")
    }

    @CallSuper
    override fun onApiChange() {
    }

    fun isLoadData(): Boolean {
        val state = playerState.level > PlayerConstants.PlayerState.ENDED.level || playerState == PlayerConstants.PlayerState.PAUSED
        Utils.log("query cur state isLoadData = $state")
        return state
    }

    fun isLoading(accurate: Boolean): Boolean {
        val state = if (!isPageReady) true else if (accurate) (playerState.level == PlayerConstants.PlayerState.LOADING.level || playerState.level == PlayerConstants.PlayerState.BUFFERING.level) else playerState.level >= PlayerConstants.PlayerState.BUFFERING.level
        Utils.log("query cur state isLoading = $state")
        return state
    }

    fun isReady(accurate: Boolean): Boolean {
        val state = if (accurate) playerState.level == PlayerConstants.PlayerState.PREPARED.level else playerState.level >= PlayerConstants.PlayerState.PREPARED.level || playerState == PlayerConstants.PlayerState.PAUSED
        Utils.log("query cur state isReady = $state")
        return state
    }

    fun isPlaying(accurate: Boolean): Boolean {
        val state = if (accurate) playerState.level == PlayerConstants.PlayerState.PLAYING.level else playerState.level >= PlayerConstants.PlayerState.PLAYING.level
        Utils.log("query cur state isPlaying = $state")
        return state
    }

    fun isPause(accurate: Boolean): Boolean {
        val state = if (accurate) playerState == PlayerConstants.PlayerState.PAUSED else playerState.level <= PlayerConstants.PlayerState.PAUSED.level
        Utils.log("query cur state isPause = $state")
        return state
    }

    fun isStop(accurate: Boolean): Boolean {
        val state = if (accurate) playerState.level == PlayerConstants.PlayerState.STOP.level else playerState.level <= PlayerConstants.PlayerState.STOP.level
        Utils.log("query cur state isStop = $state")
        return state
    }

    fun isDestroyed(accurate: Boolean): Boolean {
        val state = if (accurate) playerState.level == PlayerConstants.PlayerState.UNKNOWN.level else playerState.level <= PlayerConstants.PlayerState.UNKNOWN.level
        Utils.log("query cur state isDestroyed = $state")
        return state
    }

    fun reset() {
        playerState = PlayerConstants.PlayerState.UNKNOWN
        curPath = ""
    }

    fun syncControllerState() {
        playerState.let {
            if (it == PlayerConstants.PlayerState.LOADING) onStateChange(PlayerConstants.PlayerState.LOADING.setFrom("sync"))
            if (it == PlayerConstants.PlayerState.BUFFERING) onStateChange(PlayerConstants.PlayerState.BUFFERING.setFrom("sync"))
            if (it == PlayerConstants.PlayerState.PLAYING || it == PlayerConstants.PlayerState.PAUSED.setFrom("sync")) onReady(totalDuration)
            if (it == PlayerConstants.PlayerState.PLAYING) onStateChange(PlayerConstants.PlayerState.PLAYING.setFrom("sync"))
            onCurrentPlayerInfo(curVolume, curVolume <= 0, curPlayingRate)
        }
    }
}