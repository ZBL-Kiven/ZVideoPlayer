package com.zj.videotest.ytb

import com.zj.player.base.BasePlayer
import com.zj.player.ut.PlayQualityLevel
import com.zj.player.ut.PlayerEventController

class CusWebPlayer : BasePlayer<CusWebRender> {

    private var controller: PlayerEventController<CusWebRender>? = null
    private var curAccessKey: String = ""
    private var curPath: Pair<String, Any?>? = null
    private val render: CusWebRender?; get() = controller?.playerView

    override fun isLoadData(): Boolean {
        return curPath != null && (render?.ytbDelegate?.isPageReady == true && (isLoading() || isPause(true))) && render != null
    }

    override fun isLoading(accurate: Boolean): Boolean {
        return render?.ytbDelegate?.isLoading(accurate) ?: false
    }

    override fun isReady(accurate: Boolean): Boolean {
        return render?.ytbDelegate?.isReady(accurate) ?: false
    }

    override fun isPlaying(accurate: Boolean): Boolean {
        return render?.ytbDelegate?.isPlaying(accurate) ?: false
    }

    override fun isPause(accurate: Boolean): Boolean {
        return render?.ytbDelegate?.isPause(accurate) ?: !accurate
    }

    override fun isStop(accurate: Boolean): Boolean {
        return render?.ytbDelegate?.isStop(accurate) ?: true
    }

    override fun isDestroyed(accurate: Boolean): Boolean {
        return render?.ytbDelegate?.isDestroyed(accurate) ?: true
    }

    override fun currentPlayPath(): String {
        return curPath?.first ?: ""
    }

    override fun currentCallId(): Any? {
        return curPath?.second
    }

    override fun setData(path: String, autoPlay: Boolean, callId: Any?) {
        curPath = Pair(path, callId)
        render?.setVideoFrame()
        render?.load(curPath?.first ?: "")
        if (autoPlay) play()
    }

    override fun play() {
        render?.play()
    }

    override fun pause() {
        render?.pause()
    }

    override fun stop() {
        render?.stop(true, isRegulate = false)
        curPath = null
    }

    override fun stopNow(withNotify: Boolean, isRegulate: Boolean) {
        render?.stop(withNotify, isRegulate)
        curPath = null
    }

    override fun seekTo(progress: Int, fromUser: Boolean) {
        render?.seekTo(progress, fromUser)
    }

    override fun release() {
        curAccessKey = ""
        curPath = null
        this.controller = null
    }

    override fun setSpeed(s: Float) {
        render?.setSpeed(s)
    }

    override fun getSpeed(): Float {
        return render?.ytbDelegate?.curPlayingRate ?: 0f
    }

    override fun setVolume(volume: Int, maxVolume: Int) {
        render?.setVolume(volume, maxVolume)
    }

    override fun getVolume(): Int {
        return render?.ytbDelegate?.curVolume ?: 0
    }

    override fun autoPlay(autoPlay: Boolean) {
        render?.autoPlay(autoPlay)
    }

    override fun updateControllerState() {
        //        curState.let {
        //            if (it == VideoState.LOADING) controller?.onLoading(currentPlayPath(), true)
        //            if (it == VideoState.SEEK_LOADING) controller?.onSeekingLoading(currentPlayPath(), true)
        //            if (it == VideoState.PLAY || it == VideoState.PAUSE || it == VideoState.COMPLETING || it == VideoState.READY) {
        //                controller?.onPrepare(currentPlayPath(), duration, true)
        //            }
        //            when (it) {
        //                VideoState.PLAY -> controller?.onPlay(currentPlayPath(), true)
        //                VideoState.COMPLETING -> controller?.completing(currentPlayPath(), true)
        //                VideoState.COMPLETED -> controller?.onCompleted(currentPlayPath(), true)
        //                VideoState.PAUSE -> controller?.onPause(currentPlayPath(), true)
        //                VideoState.STOP, VideoState.DESTROY -> {
        //                    val e = (it.obj() as? ExoPlaybackException)
        //                    if (e != null) controller?.onError(e)
        //                    else controller?.onStop(true, currentPlayPath(), true)
        //                }
        //                else -> {
        //                }
        //            }
        //        }
        //        controller?.onPlayerInfo(getVolume(), getSpeed())
    }

    override fun setController(controller: PlayerEventController<CusWebRender>): String {
        curAccessKey = System.currentTimeMillis().toString()
        this.controller?.onStop(true, currentPlayPath(), true)
        this.controller = controller
        return curAccessKey
    }

    override fun requirePlayQuality(level: PlayQualityLevel) {
        render?.requirePlayQuality(level)
    }
}