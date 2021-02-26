package com.zj.player.controller

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.CallSuper
import com.zj.player.list.VideoControllerIn

/**
 * @author ZJJ on 2020.6.16
 * the view controller and adapt use in list view,
 * implements the data binder interfaces.
 **/
@Suppress("unused")
abstract class BaseListVideoController @JvmOverloads constructor(c: Context, attr: AttributeSet? = null, def: Int = 0) : BackgroundVideoController(c, attr, def) {

    private var videoControllerIn: VideoControllerIn? = null

    private var curPlayingIndex: Int = -1

    var isCompleted: Boolean = false

    private var completedListener: ((BaseListVideoController) -> Unit)? = null
    private var playingStateListener: ((BaseListVideoController) -> Unit)? = null
    private var fullScreenChangeListener: ((BaseListVideoController, Boolean, payloads: Map<String, Any?>?) -> Unit)? = null
    private var resetListener: ((BaseListVideoController) -> Unit)? = null
    private var onTrackListener: ((playAble: Boolean, start: Boolean, end: Boolean, formTrigDuration: Float) -> Unit)? = null

    val isBindingController: Boolean
        get() {
            return controller != null
        }

    @CallSuper
    open fun onBindHolder(index: Int) {
        curPlayingIndex = index
    }

    override fun onPlayClick(v: View, fromUser: Boolean) {
        load(v, false, fromUser)
    }

    override fun reload(v: View) {
        load(v, reload = true, fromUser = false)
    }

    override fun onLoading(path: String, isRegulate: Boolean) {
        super.onLoading(path, isRegulate)
        playingStateListener?.invoke(this)
    }

    override fun onPlay(path: String, isRegulate: Boolean) {
        super.onPlay(path, isRegulate)
        playingStateListener?.invoke(this)
    }

    private fun load(v: View, reload: Boolean, fromUser: Boolean) {
        if (controller?.isPlaying() == true) if (reload) super.reload(v) else super.onPlayClick(v, false) else {
            controller?.let {
                if (reload) super.reload(v) else super.onPlayClick(v, false)
            } ?: videoControllerIn?.waitingForPlay(curPlayingIndex, 20L, fromUser)
        }
    }

    override fun onCompleted(path: String, isRegulate: Boolean) {
        super.onCompleted(path, isRegulate)
        isCompleted = true
        completedListener?.invoke(this)
    }

    internal fun setControllerIn(ci: VideoControllerIn) {
        this.videoControllerIn = ci
    }

    @CallSuper
    open fun resetWhenDisFocus() {
        controller?.updateViewController(null)
        controller = null
        resetListener?.invoke(this)
        this.reset()
    }

    override fun onFullScreenChanged(isFull: Boolean, payloads: Map<String, Any?>?) {
        fullScreenChangeListener?.invoke(this, isFull, payloads)
    }

    override fun onTrack(playAble: Boolean, start: Boolean, end: Boolean, formTrigDuration: Float) {
        onTrackListener?.invoke(playAble, start, end, formTrigDuration)
    }

    /**
     * It should be noted that this method will be called in [resetWhenDisFocus],
     * [com.zj.player.adapters.ListVideoAdapterDelegate] is mainly the way to reset the Controller in the list
     * Therefore, this method cannot be exposed or overridden.
     * */
    private fun reset(isShowThumb: Boolean = true, isShowBackground: Boolean = true, isSinkBottomShader: Boolean = false) {
        reset(true, isRegulate = true, isShowPlayBtn = isPlayable, isShowThumb = isShowThumb, isShowBackground = isShowBackground, isSinkBottomShader = isSinkBottomShader)
        isCompleted = false
    }

    fun setOnCompletedListener(l: ((BaseListVideoController) -> Unit)? = null) {
        this.completedListener = l
    }

    fun setPlayingStateListener(l: ((BaseListVideoController) -> Unit)? = null) {
        this.playingStateListener = l
    }

    fun setOnFullScreenChangedListener(l: ((BaseListVideoController, Boolean, payloads: Map<String, Any?>?) -> Unit)? = null) {
        this.fullScreenChangeListener = l
    }

    fun setOnTrackListener(l: ((playAble: Boolean, start: Boolean, end: Boolean, formTrigDuration: Float) -> Unit)? = null) {
        this.onTrackListener = l
    }

    fun setOnResetListener(l: ((BaseListVideoController) -> Unit)? = null) {
        this.resetListener = l
    }

    fun onBehaviorDetached(p: String, callId: Any?) {
        if (p.isNotEmpty()) recordLogs("the data $p detached form window", this::class.java.simpleName, Pair("path", p), Pair("callId", callId ?: ""), Pair("name", "videoDetached"))
    }

    fun onBehaviorAttached(p: String, callId: Any?) {
        if (p.isNotEmpty()) recordLogs("the data $p attached form window", this::class.java.simpleName, Pair("path", p), Pair("callId", callId ?: ""), Pair("name", "videoAttached"))
    }
}