package com.zj.player.list

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.zj.player.BaseVideoController

/**
 * @author ZJJ on 2020.6.16
 * the view controller and adapt use in list view,
 * implements the data binder interfaces.
 **/
open class BaseListVideoController @JvmOverloads constructor(c: Context, attr: AttributeSet? = null, def: Int = 0) : BaseVideoController(c, attr, def) {

    private var videoControllerIn: VideoControllerIn? = null

    private var curPlayingIndex: Int = -1

    var isBindingController = false

    open fun onBindHolder(index: Int) {
        curPlayingIndex = index
    }

    override fun onPlayClick(v: View) {
        if (controller?.isPlaying() == true) super.onPlayClick(v) else {
            controller?.let {
                super.onPlayClick(v)
            } ?: videoControllerIn?.waitingForPlay(curPlayingIndex)
        }
    }

    internal fun setControllerIn(ci: VideoControllerIn) {
        this.videoControllerIn = ci
    }

    fun resetWhenDisFocus() {
        isBindingController = false
        controller?.updateViewController(null)
        this.reset()
    }

    open fun reset(isShowThumb: Boolean = true, isShowBackground: Boolean = true, isSinkBottomShader: Boolean = false) {
        reset(true, isShowThumb, isShowBackground, isSinkBottomShader)
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