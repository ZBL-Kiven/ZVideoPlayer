package com.zj.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.annotation.UiThread
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ExoPlaybackException.*
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.upstream.cache.*
import com.google.android.exoplayer2.util.Util
import com.zj.player.UT.PlayerEventController
import com.zj.player.UT.RenderEvent
import com.zj.player.base.VideoState
import com.zj.player.config.VideoConfig
import java.io.File
import java.lang.StringBuilder
import kotlin.math.max
import kotlin.math.min

/**
 * @author ZJJ on 2020.6.16
 *
 * The core component used in video playback is based on Google [ExoPlayer].
 * In most cases, you don’t have to rewrite the interaction mode and monitoring logic it provides, but it is supported in special cases.
 * ZPlayer supports user behavior Collection and full logging, eg: [log], [logP], and output through [ZController].
 * In addition, if you use the configuration method to build, you can configure some required parameters for video loading, see [VideoConfig] .
 * Calling [release] will completely clear all the content related to this ZPlayer instance to reclaim the memory,
 * and the cached video loaded by the cache method will not be affected.
 * ZPlayer instances after [release] can still re [setData] and create new related resources without causing additional memory or performance consumption.
 * */

@Suppress("unused", "MemberVisibilityCanBePrivate")
open class ZPlayer(var config: VideoConfig? = null) : Player.EventListener {

    companion object {
        var logAble = true
        const val DEFAULT_VIDEO_CACHED_PATH = "videoCache"
        const val DEFAULT_VIDEO_MAX_CACHED_SIZE = 512 * 1024 * 1024L
        private var cache: Cache? = null
        private const val HANDLE_SEEK = 10101
        private const val HANDLE_PROGRESS = 10102
        private const val HANDLE_STATE = 10103
    }

    private var playPath: String = ""
    protected var player: SimpleExoPlayer? = null
    private var isReady = false
    private var duration = 0L
    private var controller: PlayerEventController? = null
    private var autoPlay = false
    private var _curLookedProgress = 0

    private var curAccessKey: String = ""
        get() {
            return if (field.isEmpty()) "not set" else field
        }

    private val renderListener = RenderEvent { controller?.onFirstFrameRender() }

    private var handler: Handler? = Handler(Looper.getMainLooper()) {
        when (it.what) {
            HANDLE_SEEK -> seekNow(it.arg1, it.obj as Boolean, duration)
            HANDLE_PROGRESS -> updateProgress()
            HANDLE_STATE -> curState = it.obj as VideoState
        }
        return@Handler false
    }

    private var curState: VideoState = VideoState.DESTROY
        set(value) {
            field.setObj(null)
            if (field == value) return
            log("update player status form ${field.name} to ${value.name}")
            when (value) {
                VideoState.SEEK_LOADING -> {
                    controller?.onSeekingLoading(currentPlayPath(), false)
                }
                VideoState.LOADING -> {
                    isReady = false
                    controller?.onLoading(currentPlayPath(), false)
                    loading(currentPlayPath())
                }
                VideoState.READY -> {
                    duration = player?.duration ?: 0
                    controller?.onPrepare(currentPlayPath(), duration, false)
                    isReady = true
                    if (autoPlay) {
                        player?.playWhenReady
                        setPlayerState(VideoState.PLAY)
                        autoPlay(false)
                        return
                    }
                }
                VideoState.PLAY -> {
                    runWithPlayer {
                        if (it.currentPosition >= it.duration) {
                            seekNow(0, false, duration)
                        }
                        field = value
                        it.playWhenReady = true
                    }
                    startProgressListen()
                    controller?.onPlay(currentPlayPath(), false)
                    return
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
                    isReady = false
                    autoPlay(false)
                    resetAndStop(true, error)
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

    internal fun setViewController(c: PlayerEventController): String {
        curAccessKey = System.currentTimeMillis().toString()
        this.controller?.onCompleted(currentPlayPath(), false)
        this.controller = c
        return curAccessKey
    }

    private fun loading(videoUrl: String) {
        handler?.removeCallbacksAndMessages(null)
        val context = controller?.context ?: return
        if (player == null) {
            log("new player create")
            player = ExoPlayerFactory.newSimpleInstance(context, DefaultTrackSelector())
        }
        log("video $videoUrl in loading...")
        controller?.playerView?.let {
            it.setPlayer(player)
            it.setRenderListener(renderListener)
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

    private fun createDefaultDataSource(context: Context, path: Uri?): MediaSource {
        log("create default media data source")
        val dataSourceFactory: DataSource.Factory = DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName), DefaultBandwidthMeter())
        return ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(path)
    }

    private fun createCachedDataSource(context: Context, videoUrl: String): MediaSource? {
        val httpFactory = DefaultHttpDataSourceFactory(Util.getUserAgent(context, context.packageName), DefaultBandwidthMeter())
        config?.requestProperty?.let {
            log("set request property $it")
            httpFactory.defaultRequestProperties.set(it)
        }
        if (cache == null) {
            val cachePath = context.applicationContext.externalCacheDir
            val cacheDir = File(cachePath, config?.cacheFileDir ?: DEFAULT_VIDEO_CACHED_PATH)
            cache = SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor((config?.maxCacheSize) ?: DEFAULT_VIDEO_MAX_CACHED_SIZE))
        }
        val cachedDataSourceFactory = CacheDataSourceFactory(cache, httpFactory)
        return ExtractorMediaSource.Factory(if (config?.cacheEnable == true) {
            log("create http cache media data source media data source");cachedDataSourceFactory
        } else {
            log("create http media data source media data source"); httpFactory
        }).createMediaSource(Uri.parse(videoUrl))
    }

    private fun setPlayerState(state: VideoState) {
        if (isReady() && state == VideoState.READY) {
            log("update player status fail , the cur state is already READY")
            return
        }
        handler?.let {
            it.sendMessage(Message.obtain().apply { what = HANDLE_STATE; obj = state })
        }
    }

    private fun seekNow(progress: Int, fromUser: Boolean, duration: Long) {
        val seekProgress = (max(0f, min(100, progress) / 100f * max(duration, 1) - 1)).toLong()
        if (fromUser) setPlayerState(VideoState.SEEK_LOADING)
        runWithPlayer { p ->
            p.seekTo(seekProgress)
            log("video seek to $seekProgress")
        }
    }

    private fun runWithPlayer(block: (SimpleExoPlayer) -> Unit) {
        player?.let {
            try {
                block(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun resetAndStop(notifyStop: Boolean = false, e: ExoPlaybackException?) {
        runWithPlayer {
            it.removeListener(this)
            it.release()
        }
        if (notifyStop) {
            if (e != null) controller?.onError(e)
            else controller?.onStop(currentPlayPath(), false)
        }
        handler?.removeCallbacksAndMessages(null)
        player = null
        log("video finished , current progress at $_curLookedProgress%")
    }

    private fun updateProgress() {
        if (isReady()) {
            val curDuration = player?.currentPosition ?: 0
            val bufferedPosition = player?.bufferedPosition ?: 0L
            if (curDuration > 0) {
                val interval = curDuration * 1.0f / max(1, duration)
                val buffered = bufferedPosition * 1.0f / max(1, duration)
                val curSeekProgress = (interval * 100 + 0.5f).toInt()
                val curBufferProgress = (buffered * 100 + 0.5f).toInt()
                _curLookedProgress = curSeekProgress
                controller?.onSeekChanged(min(100, curSeekProgress), curBufferProgress, false, duration)
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
        }
    }

    override fun onPlayerError(error: ExoPlaybackException?) {
        val sb = StringBuilder()
        error?.let {
            if (it.type == TYPE_RENDERER) sb.append("rendererException : ").append(it.rendererException?.message).append(" \n")
            if (it.type == TYPE_SOURCE) sb.append("sourceException : ").append(it.sourceException?.message).append(" \n")
            if (it.type == TYPE_UNEXPECTED) sb.append("unexpectedException : ").append(it.unexpectedException?.message)
        }
        log("video on play error case : $sb")
        setPlayerState(VideoState.STOP.setObj(error))
    }

    private fun log(s: String) {
        if (logAble) controller?.onLog(s, currentPlayPath(), curAccessKey, "ZPlayer")
    }

    private fun logP(s: String) {
        if (logAble) controller?.onLog(s, currentPlayPath(), curAccessKey, "preference")
    }

    open fun isReady(): Boolean {
        return curState.pri >= VideoState.READY.pri
    }

    open fun isPlaying(): Boolean {
        return curState.pri >= VideoState.PLAY.pri
    }

    open fun isPause(): Boolean {
        return curState.pri <= VideoState.PAUSE.pri
    }

    open fun isStop(): Boolean {
        return curState.pri <= VideoState.STOP.pri
    }

    open fun isDestroyed(): Boolean {
        return curState.pri <= VideoState.DESTROY.pri
    }

    open fun currentPlayPath(): String {
        return playPath
    }

    open fun setData(path: String, autoPlay: Boolean) {
        log("set video data to $path")
        playPath = path
        if (autoPlay) setPlayerState(VideoState.LOADING)
    }

    open fun play() {
        if (isReady) {
            log("video call play and ready")
            setPlayerState(VideoState.PLAY)
        } else {
            setPlayerState(VideoState.LOADING)
        }
        autoPlay(true)
    }

    open fun pause() {
        log("video call pause")
        setPlayerState(VideoState.PAUSE)
    }

    open fun stop() {
        log("video call stop")
        setPlayerState(VideoState.STOP)
    }

    open fun seekTo(progress: Int, fromUser: Boolean) {
        if (curState != VideoState.PAUSE && fromUser) {
            setPlayerState(VideoState.PAUSE.setObj(true))
        }
        handler?.removeMessages(HANDLE_SEEK)
        handler?.sendMessageDelayed(Message.obtain().apply {
            what = HANDLE_SEEK
            arg1 = progress
            obj = fromUser
        }, 200)
    }

    open fun release() {
        log("video call release")
        setPlayerState(VideoState.DESTROY)
    }

    open fun setSpeed(s: Float) {
        runWithPlayer {
            val p = it.playbackParameters
            val np = PlaybackParameters(s, p.pitch, p.skipSilence)
            log("video update speed from ${p.speed} to $s")
            it.playbackParameters = np
        }
    }

    internal fun autoPlay(autoPlay: Boolean) {
        log("set video auto play to $autoPlay")
        this.autoPlay = autoPlay
        if (curState == VideoState.READY) {
            setPlayerState(VideoState.PLAY)
        }
    }

    @UiThread
    internal fun updateControllerState() {
        curState.let {
            when (it) {
                VideoState.SEEK_LOADING -> controller?.onSeekingLoading(currentPlayPath(), true)
                VideoState.LOADING -> controller?.onLoading(currentPlayPath(), true)
                VideoState.READY -> controller?.onPrepare(currentPlayPath(), duration, true)
                VideoState.PLAY -> controller?.onPlay(currentPlayPath(), true)
                VideoState.COMPLETING -> controller?.completing(currentPlayPath(), true)
                VideoState.COMPLETED -> controller?.onCompleted(currentPlayPath(), true)
                VideoState.PAUSE -> controller?.onPause(currentPlayPath(), true)
                VideoState.STOP, VideoState.DESTROY -> {
                    val e = (it.obj() as? ExoPlaybackException)
                    if (e != null) controller?.onError(e)
                    else controller?.onStop(currentPlayPath(), true)
                }
            }
        }
    }
}