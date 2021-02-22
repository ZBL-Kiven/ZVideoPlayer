package com.zj.videotest.ytb


import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebSettings
import com.zj.player.base.BaseRender
import com.zj.player.ut.Constance
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

        override fun onSeekParsed(progress: Int, fromUser: Boolean) {
            this@CusWebRender.onSeekParsed(progress, fromUser)
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

        override fun onStateChange(state: PlayerConstants.PlayerState) {
            if (state != playerState) when (state) {
                PlayerConstants.PlayerState.UNKNOWN -> {
                    notifyTo { onError(Exception(state.from)) }
                }
                PlayerConstants.PlayerState.ENDED -> notifyTo { onCompleted(curPath, false) }
                PlayerConstants.PlayerState.PLAYING -> notifyTo { onPlay(curPath, false) }
                PlayerConstants.PlayerState.PAUSED -> notifyTo { onPause(curPath, false) }
                PlayerConstants.PlayerState.BUFFERING -> notifyTo { onSeekingLoading(curPath, false) }
                PlayerConstants.PlayerState.STOP -> notifyTo { onStop(true, curPath, false) }
                PlayerConstants.PlayerState.LOADING -> notifyTo { onLoading(curPath, false) }
                PlayerConstants.PlayerState.PREPARED -> {
                    if (autoPlay) this@CusWebRender.play()
                }
                else -> {
                }
            }
            super.onStateChange(state)
        }
    }
    private var youTubePlayerBridge = YtbWebBridge(ytbDelegate)
    private var webView = CCYtbWebView(ctx, youTubePlayerBridge)
    private var ytbOptions: IFramePlayerOptions = IFramePlayerOptions.default

    init {
        initWebView()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true
    }

    private fun initWebView() {
        webView.settings?.let {
            it.mediaPlaybackRequiresUserGesture = false
            it.cacheMode = WebSettings.LOAD_NO_CACHE
        }
        ytbDelegate.initYoutubeScript(webView, ytbOptions)
    }

    fun setVideoFrame(@ResizeMode resizeMode: Int = Constance.RESIZE_MODE_FIT) {
        webView.let {
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

    override fun release() {
        ytbDelegate.destroy(webView)
        super.release()
    }

    fun autoPlay(isAuto: Boolean) {
        this.autoPlay = isAuto
        play()
    }

    fun load(path: String) {
        val videoId = if (path.contains(PlayerConstants.youtubeIdHost)) path.replace(PlayerConstants.youtubeIdHost, "")
        else if (path.contains(PlayerConstants.youtubeHost)) path.replace(PlayerConstants.youtubeHost, "")
        else {
            if (path.endsWith("/")) path.removeSuffix("/")
            val li = path.lastIndexOf("/") + 1
            if (li in 0..path.length) {
                path.substring(li, path.length)
            } else path
        }
        ytbDelegate.loadVideoById(webView, videoId, 0f, PlayerConstants.PlaybackQuality.DEFAULT.value, object : PendingLoadTask(path) {
            override fun run() {
                load(this.path)
            }
        })
    }

    fun play() {
        if (ytbDelegate.isReady(false)) {
            ytbDelegate.play(webView)
        } else if (ytbDelegate.isPageReady) load(ytbDelegate.curPath)
    }

    fun pause() {
        ytbDelegate.pause(webView)
    }

    fun stop(withNotify: Boolean, isRegulate: Boolean) {
        ytbDelegate.stop(webView)
        if (withNotify) notifyTo { onStop(withNotify, ytbDelegate.curPath, isRegulate) }
    }

    fun seekTo(progress: Int, fromUser: Boolean) {
        if (fromUser) {
            ytbDelegate.pause(webView)
        }
        ytbDelegate.seekTo(progress, fromUser)
    }

    private fun onSeekParsed(progress: Int, fromUser: Boolean) {
        ytbDelegate.seekNow(webView, progress, fromUser)
    }

    fun setSpeed(rate: Float) {
        ytbDelegate.setPlaybackRate(webView, rate)
        notifyTo { onPlayerInfo(ytbDelegate.curVolume, ytbDelegate.curPlayingRate) }
    }

    fun setVolume(volume: Int, maxVolume: Int) {
        ytbDelegate.setVolume(webView, (volume * 1.0f / maxVolume * 100f).toInt())
        notifyTo { onPlayerInfo(ytbDelegate.curVolume, ytbDelegate.curPlayingRate) }
    }
}