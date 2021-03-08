package com.zj.videotest.ytb


import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import com.zj.player.base.BaseRender
import com.zj.player.ut.Constance
import com.zj.player.ut.PlayQualityLevel
import com.zj.player.ut.ResizeMode
import com.zj.youtube.YoutubeDelegate
import com.zj.youtube.constance.PlayerConstants
import com.zj.youtube.options.IFramePlayerOptions
import com.zj.youtube.utils.PendingLoadTask
import java.lang.Exception
import kotlin.math.max

class CusWebRender(ctx: Context) : BaseRender(ctx) {

    private var autoPlay: Boolean = false
    private var isReady = false
    internal var ytbDelegate = object : YoutubeDelegate(true) {

        override fun onSeekChanged(fromUser: Boolean) {
            val buffering = (curBuffering * 100.0f).toInt()
            val seek = (curPlayingDuration * 1.0f / max(1, totalDuration) * 100.0f + 0.5f).toInt()
            notifyTo { this.onSeekChanged(seek, buffering, fromUser, curPlayingDuration, totalDuration) }
        }

        override fun getWebView(): WebView? {
            return this@CusWebRender.getWebView()
        }

        override fun onSeekParsed(progress: Int, fromUser: Boolean) {
            this@CusWebRender.onSeekParsed(progress, fromUser)
        }

        override fun onCurrentPlayerInfo(curVolume: Int, curSpeed: Float) {
            super.onCurrentPlayerInfo(curVolume, curSpeed)
            notifyTo { this.onPlayerInfo(curVolume, curPlayingRate) }
        }

        override fun onReady(totalDuration: Long) {
            super.onReady(totalDuration)
            notifyTo { this.onPrepare(curPath, totalDuration, false) }
            isReady = true
        }

        override fun onPlaybackRateChange(playbackRate: PlayerConstants.PlaybackRate) {
            super.onPlaybackRateChange(playbackRate)
            notifyTo { this.onPlayerInfo(curVolume, curPlayingRate) }
        }

        override fun onError(error: PlayerConstants.PlayerError) {
            super.onError(error)
            notifyTo { this.onError(Exception(error.name)) }
        }

        override fun onPlayQualityChanged(quality: PlayerConstants.PlaybackQuality, supports: List<PlayerConstants.PlaybackQuality>?) {
            notifyTo { onPlayQualityChanged(changeListFunc.invoke(listOf(quality))?.get(0) ?: PlayQualityLevel.AUTO, changeListFunc.invoke(supports)) }
        }

        override fun onPlayStateChange(curState: PlayerConstants.PlayerState, oldState: PlayerConstants.PlayerState, fromSync: Boolean) {
            when (curState) {
                PlayerConstants.PlayerState.ERROR -> notifyTo { onError(Exception(curState.from)) }
                PlayerConstants.PlayerState.ENDED -> notifyTo { onCompleted(curPath, fromSync) }
                PlayerConstants.PlayerState.PLAYING -> notifyTo { onPlay(curPath, fromSync) }
                PlayerConstants.PlayerState.PAUSED -> notifyTo { onPause(curPath, fromSync) }
                PlayerConstants.PlayerState.BUFFERING -> notifyTo { onSeekingLoading(curPath, fromSync) }
                PlayerConstants.PlayerState.STOP -> notifyTo { onStop(true, curPath, fromSync) }
                PlayerConstants.PlayerState.LOADING -> notifyTo { onLoading(curPath, fromSync) }
                PlayerConstants.PlayerState.PREPARED -> {
                    if (autoPlay) this@CusWebRender.play()
                }
                else -> {
                }
            }
        }
    }
    private var youTubePlayerBridge = YtbWebBridge(ytbDelegate)
    private var webView: CCYtbWebView? = CCYtbWebView(ctx, youTubePlayerBridge)
    private var ytbOptions: IFramePlayerOptions = IFramePlayerOptions.default

    init {
        ytbDelegate.initYoutubeScript(true, ytbOptions)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true
    }

    private fun getWebView(): CCYtbWebView? {
        return webView
    }

    fun setVideoFrame(@ResizeMode resizeMode: Int = Constance.RESIZE_MODE_FIT) {
        webView?.let {
            it.removeAllViews()
            this.resizeMode = resizeMode
            (it.parent as? ViewGroup)?.let { vp ->
                if (vp == this) return
                else vp.removeView(it)
            }
            val params = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.gravity = Gravity.CENTER
            addView(it, 0, params)
        }
    }

    override fun reset() {
        ytbDelegate.reset()
    }

    override fun release() {
        ytbDelegate.destroy()
        webView = null
        super.release()
    }

    fun autoPlay(isAuto: Boolean) {
        this.autoPlay = isAuto
        play()
    }

    fun load(path: String, fromPending: Boolean = false) {
        if (!ytbDelegate.isPageReady) notifyTo { onLoading(path, true) }
        val videoId = if (path.contains(PlayerConstants.youtubeIdHost)) path.replace(PlayerConstants.youtubeIdHost, "")
        else if (path.contains(PlayerConstants.youtubeHost)) path.replace(PlayerConstants.youtubeHost, "")
        else {
            if (path.endsWith("/")) path.removeSuffix("/")
            val li = path.lastIndexOf("/") + 1
            if (li in 0..path.length) {
                path.substring(li, path.length)
            } else path
        }
        ytbDelegate.loadVideoById(videoId, 0f, PlayerConstants.PlaybackQuality.DEFAULT.value, object : PendingLoadTask(path) {
            override fun run() {
                load(this.path, true)
            }
        }, fromPending)
    }

    fun play(path: String? = null) {
        val p = if (path.isNullOrEmpty()) if (ytbDelegate.curPath.isEmpty()) "" else ytbDelegate.curPath else path
        if (ytbDelegate.isReady(false)) {
            if (!ytbDelegate.play()) {
                load(p)
            }
        } else load(p)
    }

    fun pause() {
        ytbDelegate.pause()
    }

    fun stop(withNotify: Boolean, isRegulate: Boolean) {
        ytbDelegate.stop()
        if (withNotify) notifyTo { onStop(withNotify, ytbDelegate.curPath, isRegulate) }
    }

    fun seekTo(progress: Int, fromUser: Boolean) {
        if (fromUser) {
            ytbDelegate.pause()
        }
        ytbDelegate.seekTo(progress, fromUser)
    }

    private fun onSeekParsed(progress: Int, fromUser: Boolean) {
        ytbDelegate.seekNow(progress, fromUser)
    }

    fun setSpeed(rate: Float) {
        ytbDelegate.setPlaybackRate(rate)
        notifyTo { onPlayerInfo(ytbDelegate.curVolume, ytbDelegate.curPlayingRate) }
    }

    fun setVolume(volume: Int, maxVolume: Int) {
        ytbDelegate.setVolume((volume * 1.0f / maxVolume * 100f).toInt(), true)
        notifyTo { onPlayerInfo(ytbDelegate.curVolume, ytbDelegate.curPlayingRate) }
    }

    fun requirePlayQuality(level: PlayQualityLevel) {
        val quality = when (level) {
            PlayQualityLevel.AUTO -> PlayerConstants.PlaybackQuality.DEFAULT.value
            PlayQualityLevel.SMALL -> PlayerConstants.PlaybackQuality.SMALL.value
            PlayQualityLevel.MEDIUM -> PlayerConstants.PlaybackQuality.MEDIUM.value
            PlayQualityLevel.M720 -> PlayerConstants.PlaybackQuality.HD720.value
            PlayQualityLevel.H1080 -> PlayerConstants.PlaybackQuality.HD1080.value
            PlayQualityLevel.LARGE -> PlayerConstants.PlaybackQuality.LARGE.value
            PlayQualityLevel.BR -> PlayerConstants.PlaybackQuality.HIGH_RES.value
        }
        ytbDelegate.setPlaybackQuality(quality)
    }

    fun syncControllerState() {
        ytbDelegate.syncControllerState()
    }

    private val changeListFunc = { lst: List<PlayerConstants.PlaybackQuality>? ->
        lst?.map {
            when (it) {
                PlayerConstants.PlaybackQuality.DEFAULT, PlayerConstants.PlaybackQuality.UNKNOWN -> PlayQualityLevel.AUTO
                PlayerConstants.PlaybackQuality.SMALL -> PlayQualityLevel.SMALL
                PlayerConstants.PlaybackQuality.MEDIUM -> PlayQualityLevel.MEDIUM
                PlayerConstants.PlaybackQuality.HD720 -> PlayQualityLevel.M720
                PlayerConstants.PlaybackQuality.HD1080 -> PlayQualityLevel.H1080
                PlayerConstants.PlaybackQuality.LARGE -> PlayQualityLevel.LARGE
                PlayerConstants.PlaybackQuality.HIGH_RES -> PlayQualityLevel.BR
            }
        }
    }
}