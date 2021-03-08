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
        return curPath != null && render?.ytbDelegate?.isLoadData() ?: false
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
        render?.play(curPath?.first)
    }

    override fun pause() {
        render?.pause()
    }

    override fun stop() {
        render?.stop(true, isRegulate = false)
    }

    override fun stopNow(withNotify: Boolean, isRegulate: Boolean) {
        render?.stop(withNotify, isRegulate)
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
        render?.syncControllerState()
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