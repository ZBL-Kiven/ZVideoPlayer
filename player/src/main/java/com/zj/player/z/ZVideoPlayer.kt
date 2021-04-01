package com.zj.player.z

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.WindowManager
import androidx.annotation.UiThread
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ExoPlaybackException.*
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.Util
import com.zj.player.base.BasePlayer
import com.zj.player.base.VideoLoadControl
import com.zj.player.base.VideoState
import com.zj.player.config.VideoConfig
import com.zj.player.logs.BehaviorData
import com.zj.player.logs.BehaviorLogsTable
import com.zj.player.logs.ZPlayerLogs
import com.zj.player.ut.Constance
import com.zj.player.ut.Constance.CORE_LOG_ABLE
import com.zj.player.ut.PlayQualityLevel
import com.zj.player.ut.PlayerEventController
import com.zj.player.ut.RenderEvent
import java.io.File
import java.lang.IllegalArgumentException
import kotlin.math.max
import kotlin.math.min

/**
 * @author ZJJ on 2020.6.16
 *
 * The core component used in video playback is based on Google [ExoPlayer].
 * In most cases, you don’t have to rewrite the interaction mode and monitoring logic it provides, but it is supported in special cases.
 * ZPlayer supports user behavior Collection and full logging,see [log]. and output through [ZController].
 * In addition, if you use the configuration method to build, you can configure some required parameters for video loading, see [VideoConfig] .
 * Calling [release] will completely clear all the content related to this ZPlayer instance to reclaim the memory,
 * and the cached video loaded by the cache method will not be affected.
 * ZPlayer instances after [release] can still re [setData] and create new related resources without causing additional memory or performance consumption.
 * */

@Suppress("unused", "MemberVisibilityCanBePrivate")
open class ZVideoPlayer(var config: VideoConfig = VideoConfig.create()) : BasePlayer<ZRender>, Player.EventListener {

    companion object {
        const val DEFAULT_VIDEO_CACHED_PATH = "videoCache"
        const val ONLY_RESET = "onlyReset"
        const val DEFAULT_VIDEO_MAX_CACHED_SIZE = 512 * 1024 * 1024L
        private var cache: Cache? = null
        private const val HANDLE_SEEK = 10101
        private const val HANDLE_PROGRESS = 10102
        private const val HANDLE_STATE = 10103
        @Suppress("DEPRECATION") val stableFlag = WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        @Suppress("DEPRECATION") val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
    }

    private var playPath: Pair<String, Any?>? = null
    protected var player: SimpleExoPlayer? = null
    private var isReady = false
    private var duration = 0L
    private var controller: PlayerEventController<ZRender>? = null
    private var autoPlay = false
    private var _curLookedProgress = 0
    private var curVolume = 0
    private var maxVolume = 0

    private var curAccessKey: String = ""
        get() {
            return if (field.isEmpty()) "not set" else field
        }

    private val renderListener = RenderEvent { controller?.onFirstFrameRender() }

    private var handler: Handler? = Handler(Looper.getMainLooper()) {
        when (it.what) {
            HANDLE_SEEK -> seekNow(it.obj as Long, duration, it.arg2 == 0)
            HANDLE_PROGRESS -> updateProgress()
            HANDLE_STATE -> synchronized(curState) {
                curState = it.obj as VideoState
            }
        }
        return@Handler false
    }

    private var curState: VideoState = VideoState.DESTROY
        set(value) {
            if (value.obj().toString() == ONLY_RESET) {
                value.setObj(null)
                field = value
                return
            }
            field.setObj(null)
            if (field == value) return
            log("update player status form ${field.name} to ${value.name}")
            when (value) {
                VideoState.SEEK_LOADING -> {
                    controller?.onSeekingLoading(currentPlayPath(), false)
                }
                VideoState.LOADING -> {
                    isReady = false
                    controller?.onPlayQualityChanged(PlayQualityLevel.AUTO, null)
                    controller?.onLoading(currentPlayPath(), false)
                    loading()
                }
                VideoState.READY -> {
                    duration = player?.duration ?: 0
                    controller?.onPrepare(currentPlayPath(), duration, false)
                    isReady = true
                    if (autoPlay) {
                        setPlayerState(VideoState.PLAY)
                        autoPlay(false)
                    } else if (field == VideoState.SEEK_LOADING) {
                        setPlayerState(VideoState.PLAY)
                    }
                }
                VideoState.PLAY -> {
                    runWithPlayer {
                        if (it.currentPosition >= it.duration) {
                            seekNow(0, duration, false)
                        }
                        field = value
                        it.playWhenReady = true
                    }
                    startProgressListen()
                    controller?.onPlay(currentPlayPath(), false)
                }

                VideoState.COMPLETING -> {
                    controller?.completing(currentPlayPath(), false)
                }

                VideoState.COMPLETED -> {
                    setPlayerState(VideoState.STOP)
                    controller?.onCompleted(currentPlayPath(), false)
                }

                VideoState.PAUSE -> {
                    if (isPause()) return
                    runWithPlayer { it.playWhenReady = false }
                    stopProgressListen()
                    val hideNotify = (value.obj() as? Boolean) ?: false
                    if (!hideNotify) controller?.onPause(currentPlayPath(), false)
                }

                VideoState.STOP -> {
                    val error = (value.obj() as? ExoPlaybackException)
                    if (isStop()) return
                    if (isPlaying()) setPlayerState(VideoState.PAUSE)
                    resetAndStop(true, isRegulate = false, e = error)
                }

                VideoState.DESTROY -> {
                    if (isDestroyed()) return
                    if (isPlaying()) setPlayerState(VideoState.PAUSE)
                    if (!isStop()) setPlayerState(VideoState.STOP)
                    controller = null
                }
            }
            field = value
        }

    override fun setController(controller: PlayerEventController<ZRender>): String {
        curAccessKey = System.currentTimeMillis().toString()
        this.controller?.onStop(true, currentPlayPath(), true)
        this.controller = controller
        return curAccessKey
    }

    private fun loading(videoUrl: String = currentPlayPath()) {
        val context = controller?.context ?: return
        handler?.removeCallbacksAndMessages(null)
        if (player == null) {
            log("new player create")
            createPlayer(context)
        }
        log("video $videoUrl in loading...")
        (controller?.playerView)?.let {
            it.setPlayer(player, Constance.SURFACE_TYPE_TEXTURE_VIEW)
            it.setRenderListener(renderListener)
        } ?: throw IllegalArgumentException("The renderer based on ZPlayer must be of ZRender type !!")
        if (videoUrl.isEmpty()) {
            controller?.onError(NullPointerException("the video path should not be null or empty !!"))
            return
        }
        val f = File(videoUrl)
        val dataSource = if (f.exists() && f.isFile) {
            createDefaultDataSource(context, Uri.parse(f.path))
        } else {
            createCachedDataSource(context, videoUrl)
        }
        runWithPlayer {
            it.prepare(dataSource)
            it.addListener(this)
        }
    }

    private fun createPlayer(context: Context) {
        val control: LoadControl = config.let {
            VideoLoadControl.Builder().createDefaultLoadControl(it.minBufferMs, it.maxBufferMs, it.bufferForPlaybackMs, it.bufferForPlaybackAfterBufferMs)
        }
        val renderFactory = DefaultRenderersFactory(context)
        player = ExoPlayerFactory.newSimpleInstance(context, renderFactory, DefaultTrackSelector(), control)
        player?.videoScalingMode = config.videoScaleMod
    }

    private fun createDefaultDataSource(context: Context, path: Uri?): MediaSource {
        log("create [default media data source]")
        val dataSourceFactory: DataSource.Factory = DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName), DefaultBandwidthMeter())
        return ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(path)
    }

    private fun createCachedDataSource(context: Context, videoUrl: String): MediaSource? {
        val httpFactory = DefaultHttpDataSourceFactory(Util.getUserAgent(context, context.packageName), DefaultBandwidthMeter())
        config.requestProperty?.let {
            log("set request property $it", BehaviorLogsTable.requestParams(currentCallId(), it.toMap()))
            httpFactory.defaultRequestProperties.set(it.toMap())
        }
        if (cache == null) {
            val cachePath = context.applicationContext.externalCacheDir
            val cacheDir = File(cachePath, config.cacheFileDir)
            cache = SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(config.maxCacheSize))
        }
        val cachedDataSourceFactory = CacheDataSourceFactory(cache, httpFactory)
        return ExtractorMediaSource.Factory(if (config.cacheEnable) {
            log("create [http cache media data source] media data source");cachedDataSourceFactory
        } else {
            log("create [http media data source] media data source"); httpFactory
        }).createMediaSource(Uri.parse(videoUrl))
    }

    private fun setPlayerState(state: VideoState) {
        if (isReady() && state == VideoState.READY) return
        handler?.let {
            it.sendMessage(Message.obtain().apply { what = HANDLE_STATE; obj = state })
        }
    }

    private fun seekNow(progress: Long, duration: Long, fromUser: Boolean) {
        runWithPlayer { p ->
            val seekProgress = (max(0f, min(100, progress) / 100f * max(duration, 1) - 1)).toInt()
            p.seekTo(progress)
            if (fromUser) {
                controller?.onSeekChanged(seekProgress, player?.bufferedPosition ?: 0L, true, progress, duration)
            }
            log("video seek to $progress", BehaviorLogsTable.seekTo(currentCallId(), progress))
        }
    }

    private fun runWithPlayer(block: (SimpleExoPlayer) -> Unit) {
        player?.let {
            try {
                block(it)
            } catch (e: Exception) {
                ZPlayerLogs.onError(e)
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun resetAndStop(notifyStop: Boolean = false, isRegulate: Boolean = false, e: ExoPlaybackException?) {
        isReady = false
        autoPlay(false)
        runWithPlayer {
            it.removeListener(this)
            it.release()
        }
        handler?.removeCallbacksAndMessages(null)
        player = null
        if (e != null) controller?.onError(e)
        else controller?.onStop(notifyStop, currentPlayPath(), isRegulate)
        log("video finished , current progress at $_curLookedProgress%", BehaviorLogsTable.videoStopped(currentCallId(), _curLookedProgress / 100f))
    }

    private fun updateProgress() {
        if (isReady()) {
            val curDuration = player?.currentPosition ?: 0
            val bufferedPosition = player?.bufferedPosition ?: 0L
            if (curDuration > 0) {
                val interval = curDuration * 1.0f / max(1, duration)
                val curSeekProgress = (interval * 100 + 0.5f).toInt()
                _curLookedProgress = curSeekProgress
                controller?.onSeekChanged(curSeekProgress, bufferedPosition, false, curDuration, duration)
                if (interval >= 0.99f) {
                    if (curState != VideoState.COMPLETING) setPlayerState(VideoState.COMPLETING)
                }
            }
            startProgressListen()
        } else {
            stopProgressListen()
        }
    }

    private fun startProgressListen() {
        stopProgressListen()
        val interval = controller?.progressInterval ?: -1
        if (interval > 0) handler?.sendEmptyMessageDelayed(HANDLE_PROGRESS, interval)
    }

    private fun stopProgressListen() {
        handler?.removeMessages(HANDLE_PROGRESS)
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        when (playbackState) {
            Player.STATE_ENDED -> {
                setPlayerState(VideoState.COMPLETED)
            }
            Player.STATE_READY -> {
                setPlayerState(VideoState.READY)
            }
            Player.STATE_BUFFERING -> {
                setPlayerState(VideoState.SEEK_LOADING)
            }
        }
    }

    override fun onPlayerError(error: ExoPlaybackException?) {
        val sb = StringBuilder()
        try {
            error?.let {
                if (it.type == TYPE_RENDERER) sb.append("rendererException : ").append(it.rendererException?.message).append(" \n")
                if (it.type == TYPE_SOURCE) sb.append("sourceException : ").append(it.sourceException?.message).append(" \n")
                if (it.type == TYPE_UNEXPECTED) sb.append("unexpectedException : ").append(it.unexpectedException?.message)
            }
            log("video on play error case : $sb", BehaviorLogsTable.playError(currentCallId(), "$sb"))
            if (error?.type == TYPE_RENDERER && error.rendererException is MediaCodecRenderer.DecoderInitializationException) {
                player?.release()
                player = null
                if (currentPlayPath().isNotEmpty()) {
                    setPlayerState(VideoState.LOADING)
                } else {
                    setPlayerState(VideoState.STOP)
                }
            } else {
                setPlayerState(VideoState.STOP.setObj(error))
            }
        } catch (e: Exception) {
            log("video on play error and uncaught exception: type =  ${error?.type} some message: $sb", BehaviorLogsTable.playError(currentCallId(), "$sb"))
            setPlayerState(VideoState.STOP)
        }
    }

    override fun isLoadData(): Boolean {
        return isLoading() || isPause(true)
    }

    override fun isLoading(accurate: Boolean): Boolean {
        return if (accurate) (curState.pri == VideoState.LOADING.pri || curState.pri == VideoState.SEEK_LOADING.pri) else curState.pri >= VideoState.SEEK_LOADING.pri
    }

    override fun isReady(accurate: Boolean): Boolean {
        return if (accurate) curState.pri == VideoState.READY.pri else curState.pri >= VideoState.READY.pri
    }

    override fun isPlaying(accurate: Boolean): Boolean {
        return if (accurate) curState.pri == VideoState.PLAY.pri else curState.pri >= VideoState.PLAY.pri
    }

    override fun isPause(accurate: Boolean): Boolean {
        return if (accurate) curState.pri == VideoState.PAUSE.pri else curState.pri <= VideoState.PAUSE.pri
    }

    override fun isStop(accurate: Boolean): Boolean {
        return if (accurate) curState.pri == VideoState.STOP.pri else curState.pri <= VideoState.STOP.pri
    }

    override fun isDestroyed(accurate: Boolean): Boolean {
        return if (accurate) curState.pri == VideoState.DESTROY.pri else curState.pri <= VideoState.DESTROY.pri
    }

    override fun currentPlayPath(): String {
        return playPath?.first ?: ""
    }

    override fun currentCallId(): Any? {
        return playPath?.second
    }

    override fun requirePlayQuality(level: PlayQualityLevel) {}

    override fun setData(path: String, autoPlay: Boolean, callId: Any?) {
        log("set video data to $path")
        val isNewData = playPath?.first != path
        playPath = Pair(path, callId)
        if (autoPlay || isNewData) setPlayerState(VideoState.LOADING)
    }

    override fun play() {
        if (isReady) {
            log("video call play and ready")
            setPlayerState(VideoState.PLAY)
        } else {
            setPlayerState(VideoState.LOADING)
        }
        autoPlay(true)
    }

    override fun pause() {
        log("video call pause")
        setPlayerState(VideoState.PAUSE)
    }

    override fun stop() {
        log("video call stop")
        setPlayerState(VideoState.STOP)
    }

    override fun stopNow(withNotify: Boolean, isRegulate: Boolean) {
        handler?.removeCallbacksAndMessages(null)
        synchronized(curState) {
            if (curState != VideoState.STOP) {
                curState = VideoState.STOP.setObj(ONLY_RESET)
                resetAndStop(withNotify, isRegulate, null)
            }
        }
    }

    override fun seekTo(progress: Long, fromUser: Boolean) {
        if (curState != VideoState.PAUSE && fromUser) {
            setPlayerState(VideoState.PAUSE.setObj(true))
        }
        handler?.removeMessages(HANDLE_SEEK)
        handler?.sendMessageDelayed(Message.obtain().apply {
            what = HANDLE_SEEK
            obj = progress
            arg2 = if (fromUser) 0 else 1
        }, 200)
    }

    override fun release() {
        setPlayerState(VideoState.DESTROY)
        log("video call release", BehaviorLogsTable.released())
    }

    override fun setSpeed(s: Float) {
        runWithPlayer {
            val p = it.playbackParameters
            val np = PlaybackParameters(s, p.pitch, p.skipSilence)
            log("video update speed from ${p.speed} to $s", BehaviorLogsTable.newSpeed(currentCallId(), s))
            it.playbackParameters = np
            controller?.onPlayerInfo(getVolume(), s)
        }
    }

    override fun getSpeed(): Float {
        return player?.playbackParameters?.speed ?: 1f
    }

    override fun setVolume(volume: Int, maxVolume: Int) {
        this.curVolume = volume
        this.maxVolume = maxVolume
        runWithPlayer {
            log("video set volume to $volume", BehaviorLogsTable.newVolume(currentCallId(), volume))
            it.volume = volume * 1.0f / maxVolume
            controller?.onPlayerInfo(volume, getSpeed())
        }
    }

    override fun getVolume(): Int {
        return max(((player?.volume ?: 0f) * maxVolume).toInt(), curVolume)
    }

    override fun autoPlay(autoPlay: Boolean) {
        log("set video auto play to $autoPlay")
        this.autoPlay = autoPlay
        if (curState == VideoState.READY) {
            setPlayerState(VideoState.PLAY)
        }
    }

    @UiThread
    override fun updateControllerState() {
        curState.let {
            if (it == VideoState.LOADING) controller?.onLoading(currentPlayPath(), true)
            if (it == VideoState.SEEK_LOADING) controller?.onSeekingLoading(currentPlayPath(), true)
            if (it == VideoState.PLAY || it == VideoState.PAUSE || it == VideoState.COMPLETING || it == VideoState.READY) {
                controller?.onPrepare(currentPlayPath(), duration, true)
            }
            when (it) {
                VideoState.PLAY -> controller?.onPlay(currentPlayPath(), true)
                VideoState.COMPLETING -> controller?.completing(currentPlayPath(), true)
                VideoState.COMPLETED -> controller?.onCompleted(currentPlayPath(), true)
                VideoState.PAUSE -> controller?.onPause(currentPlayPath(), true)
                VideoState.STOP, VideoState.DESTROY -> {
                    val e = (it.obj() as? ExoPlaybackException)
                    if (e != null) controller?.onError(e)
                    else controller?.onStop(true, currentPlayPath(), true)
                }
                else -> {
                }
            }
        }
        controller?.onPlayerInfo(getVolume(), getSpeed())
    }

    private fun log(s: String, bd: BehaviorData? = null) {
        if (CORE_LOG_ABLE) ZPlayerLogs.onLog(s, currentPlayPath(), curAccessKey, "ZPlayer", bd)
    }
}