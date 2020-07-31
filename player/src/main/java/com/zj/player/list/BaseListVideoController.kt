package com.zj.player.list

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.zj.player.BaseVideoController
import com.zj.player.ZController

/**
 * @author ZJJ on 2020.6.16
 * the view controller and adapt use in list view,
 * implements the data binder interfaces.
 **/
open class BaseListVideoController @JvmOverloads constructor(c: Context, attr: AttributeSet? = null, def: Int = 0) : BaseVideoController(c, attr, def) {

    private var videoControllerIn: VideoControllerIn? = null

    private var curPlayingIndex: Int = -1

    var isCompleted: Boolean = false

    val isBindingController: Boolean
        get() {
            return controller != null
        }

    open fun onBindHolder(index: Int) {
        curPlayingIndex = index
    }

    override fun onPlayClick(v: View) {
        load(v, false)
    }

    override fun reload(v: View) {
        load(v, true)
    }

    private fun load(v: View, reload: Boolean) {
        if (controller?.isPlaying() == true) if (reload) super.reload(v) else super.onPlayClick(v) else {
            controller?.let {
                if (reload) super.reload(v) else super.onPlayClick(v)
            } ?: videoControllerIn?.waitingForPlay(curPlayingIndex)
        }
    }

    override fun onCompleted(path: String, isRegulate: Boolean) {
        super.onCompleted(path, isRegulate)
        isCompleted = true
    }

    internal fun setControllerIn(ci: VideoControllerIn) {
        this.videoControllerIn = ci
    }

    internal fun resetWhenDisFocus() {
        controller?.updateViewController(null)
        controller = null
        this.reset()
    }

    open fun reset(isShowThumb: Boolean = true, isShowBackground: Boolean = true, isSinkBottomShader: Boolean = false) {
        isCompleted = false
        reset(true, isRegulate = true, isShowThumb = isShowThumb, isShowBackground = isShowBackground, isSinkBottomShader = isSinkBottomShader)
    }

    override fun onFullScreenListener(isFull: Boolean) {
        getBackgroundView()?.visibility = if (isFull) View.GONE else View.VISIBLE
    }

    fun onBehaviorDetached(p: String, callId: Any?) {
        recordLogs("the data $p detached form window", this::class.java.simpleName, Pair("path", p), Pair("callId", callId ?: ""), Pair("name", "videoDetached"))
    }

    fun onBehaviorAttached(p: String, callId: Any?) {
        recordLogs("the data $p attached form window", this::class.java.simpleName, Pair("path", p), Pair("callId", callId ?: ""), Pair("name", "videoAttached"))
    }
}