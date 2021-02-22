package com.zj.videotest.cusRender

import com.zj.player.base.BasePlayer
import com.zj.player.ut.PlayerEventController

class CusWebPlayer : BasePlayer<CusWebRender> {

    private var controller: PlayerEventController<CusWebRender>? = null
    private var curAccessKey: String = ""
    private var curPath: Pair<String, Any?>? = null
    private val render: CusWebRender?; get() = controller?.playerView

    override fun isLoadData(): Boolean {
        return render?.isLoadData() ?: false
    }

    override fun isLoading(accurate: Boolean): Boolean {
        return render?.isLoading(accurate) ?: false
    }

    override fun isReady(accurate: Boolean): Boolean {
        return render?.isReady(accurate) ?: false
    }

    override fun isPlaying(accurate: Boolean): Boolean {
        return render?.isPlaying(accurate) ?: false
    }

    override fun isPause(accurate: Boolean): Boolean {
        return render?.isPause(accurate) ?: false
    }

    override fun isStop(accurate: Boolean): Boolean {
        return render?.isStop(accurate) ?: false
    }

    override fun isDestroyed(accurate: Boolean): Boolean {
        return render?.isDestroyed(accurate) ?: false
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
        render?.load(curPath?.first)
        if (autoPlay) play()
    }

    override fun play() {
        controller?.onPlay(currentPlayPath(), false)
    }

    override fun pause() {

    }

    override fun stop() {
        render?.stop()
    }

    override fun stopNow(withNotify: Boolean, isRegulate: Boolean) {

    }

    override fun seekTo(progress: Int, fromUser: Boolean) {

    }

    override fun release() {

    }

    override fun setSpeed(s: Float) {

    }

    override fun getSpeed(): Float {
        return 0f
    }

    override fun setVolume(volume: Float) {

    }

    override fun getVolume(): Float {
        return 0f
    }

    override fun autoPlay(autoPlay: Boolean) {

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
}