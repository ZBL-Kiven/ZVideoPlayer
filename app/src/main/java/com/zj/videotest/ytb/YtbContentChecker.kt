package com.zj.videotest.ytb

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.zj.player.base.InflateInfo
import com.zj.player.ut.Controller
import com.zj.player.ut.PlayQualityLevel
import com.zj.player.z.ZController
import com.zj.videotest.delegate.VideoControllerPlayers
import com.zj.videotest.feed.data.DataType
import java.lang.ref.WeakReference

class YtbContentChecker(context: ComponentActivity, private val path: String, timeOut: Long, private var onCheckResult: ((isOK: Boolean, path: String) -> Unit)?) : Controller, LifecycleObserver {

    companion object {
        fun checkYtbLinkAvailable(context: ComponentActivity, path: String, timeOut: Long, onCheckResult: (isOK: Boolean, path: String) -> Unit): YtbContentChecker {
            return YtbContentChecker(context, path, timeOut, onCheckResult)
        }
    }

    var controller: ZController<*, *>? = null
    private var act: WeakReference<ComponentActivity>? = WeakReference(context)
    private var handler: Handler? = Handler(Looper.getMainLooper()) {
        if (it.what == 711263) sendCheckResult(false)
        return@Handler false
    }

    init {
        context.lifecycle.addObserver(this)
        VideoControllerPlayers.getOrCreatePlayerWithVc("check", this) { DataType.YTB }
        handler?.sendEmptyMessageDelayed(711263, timeOut)
    }

    override fun keepScreenOnWhenPlaying(): Boolean {
        return true
    }

    private fun sendCheckResult(isOk: Boolean) {
        onCheckResult?.invoke(isOk, path)
        destroy()
    }

    override fun onLoading(p0: String?, p1: Boolean) {
    }

    override fun onPause(p0: String?, p1: Boolean) {
    }

    override fun updateCurPlayerInfo(p0: Int, p1: Float) {
    }

    override fun updateCurPlayingQuality(p0: PlayQualityLevel?, p1: MutableList<PlayQualityLevel>?) {
    }

    override fun onPrepare(p0: String?, p1: Long, p2: Boolean) {
        sendCheckResult(true)
    }

    override fun getContext(): Context? {
        return act?.get()
    }

    override fun onPlay(p0: String?, p1: Boolean) {
        sendCheckResult(true)
    }

    override fun onStop(p0: String?, p1: Boolean) {
    }

    override fun onCompleted(p0: String?, p1: Boolean) {
    }

    override fun completing(p0: String?, p1: Boolean) {
    }

    override fun getControllerInfo(): InflateInfo {
        return act?.get()?.let {
            InflateInfo(it.window?.decorView?.findViewById(android.R.id.content) as? ViewGroup, 0, ViewGroup.LayoutParams(1, 1))
        } ?: InflateInfo(null, 0, ViewGroup.LayoutParams(1, 1))
    }

    override fun onSeekChanged(seek: Int, buffered: Long, fromUser: Boolean, played: Long, videoSize: Long) {

    }

    override fun onSeekingLoading(p0: String?) {
    }

    override fun onError(p0: Exception?) {
        sendCheckResult(false)
    }

    override fun onControllerBind(p0: ZController<*, *>?) {
        controller = p0
        if (p0 == null) return
        controller?.setVolume(0,0)
        p0.playOrResume(path)
    }

    override fun onDestroy(p0: String?, p1: Boolean) {}

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun destroy() {
        act?.get()?.lifecycle?.removeObserver(this)
        controller?.updateViewController("check", null)
        handler?.removeCallbacksAndMessages(null)
        handler = null
        onCheckResult = null
        act?.clear()
        controller = null
        act = null
    }
}