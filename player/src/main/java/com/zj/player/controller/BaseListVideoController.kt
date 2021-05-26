package com.zj.player.controller

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.CallSuper
import com.zj.player.interfaces.ListVideoControllerIn
import com.zj.player.interfaces.VideoDetailIn

/**
 * @author ZJJ on 2020.6.16
 * the view controller and adapt use in list view,
 * implements the data binder interfaces.
 **/
@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class BaseListVideoController<T, VC> @JvmOverloads constructor(c: Context, attr: AttributeSet? = null, def: Int = 0) : BackgroundVideoController(c, attr, def) {

    internal var curPlayingIndex: Int = -1
    abstract val getController: VC
    private var videoControllerIn: ListVideoControllerIn<T, VC>? = null
    private var completedListener: ((VC) -> Unit)? = null
    private var playingStateListener: ((VC) -> Unit)? = null
    private var fullScreenChangeListener: ((VC, Boolean, payloads: Map<String, Any?>?) -> Unit)? = null
    private var resetListener: ((VC) -> Unit)? = null
    private var onTrackListener: ((playAble: Boolean, start: Boolean, end: Boolean, formTrigDuration: Float) -> Unit)? = null

    var curBean: T? = null
    var isCompleted: Boolean = false
    val isBindingController: Boolean
        get() {
            return controller != null
        }
    var detailBindIn = object : VideoDetailIn {
        override fun onFullScreenLayoutInflated(v: View, pl: Any?) {
            val p = if (pl == null) null else listOf(pl)
            videoControllerIn?.onBindFullScreenLayout(v, getController, curBean, curPlayingIndex, p)
        }

        override fun getVideoDetailLayoutId(): Int {
            return videoControllerIn?.getVideoDetailLayoutId() ?: 0
        }
    }

    override fun onPlayClick(fromUser: Boolean) {
        isCompleted = false
        load(false, fromUser)
    }

    override fun reload() {
        load(reload = true, fromUser = false)
    }

    override fun onLoading(path: String, isRegulate: Boolean) {
        super.onLoading(path, isRegulate)
        playingStateListener?.invoke(getController)
    }

    override fun onPlay(path: String, isRegulate: Boolean) {
        isCompleted = false
        super.onPlay(path, isRegulate)
        playingStateListener?.invoke(getController)
    }

    private fun load(reload: Boolean, fromUser: Boolean) {
        isCompleted = false
        if (controller?.isLoadData() == true) {
            if (reload) super.reload() else super.onPlayClick(false)
        } else {
            videoControllerIn?.waitingForPlay(curPlayingIndex, 20L, fromUser) ?: controller?.let {
                if (reload) super.reload() else super.onPlayClick(false)
            }
        }
    }

    override fun onCompleted(path: String, isRegulate: Boolean) {
        super.onCompleted(path, isRegulate)
        if (clearControllerWhenCompleted()) controller?.clearRender()
        setOverlayViews(isShowThumb = true, isShowBackground = true, isSinkBottomShader = false)
        isCompleted = true
        completedListener?.invoke(getController)
    }

    internal fun setVideoListDetailIn(ci: ListVideoControllerIn<T, VC>) {
        this.videoControllerIn = ci
        super.setVideoDetailIn(detailBindIn)
    }

    internal fun clearVideoListDataIn() {
        this.videoControllerIn = null
        this.curPlayingIndex = -1
        this.curBean = null
        setVideoDetailIn(null)
    }

    @CallSuper
    open fun resetWhenDisFocus() {
        controller?.updateViewController("reset", null)
        controller = null
        resetListener?.invoke(getController)
        this.reset()
    }

    open fun onBindData(index: Int, curBean: T?, playAble: Boolean, pl: MutableList<Any?>?) {
        this.curPlayingIndex = index
        this.curBean = curBean
    }

    open fun clearControllerWhenCompleted(): Boolean {
        return true
    }

    @CallSuper
    override fun onFullScreenChanged(isFull: Boolean, payloads: Map<String, Any?>?) {
        super.onFullScreenChanged(isFull, payloads)
        videoControllerIn?.onFullScreenChanged(getController, isFull)
        fullScreenChangeListener?.invoke(getController, isFull, payloads)
    }

    override fun onTrack(playAble: Boolean, start: Boolean, end: Boolean, formTrigDuration: Float) {
        super.onTrack(playAble, start, end, formTrigDuration)
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

    fun setOnCompletedListener(l: ((VC) -> Unit)? = null) {
        this.completedListener = l
    }

    fun setPlayingStateListener(l: ((VC) -> Unit)? = null) {
        this.playingStateListener = l
    }

    fun setOnFullScreenChangedListener(l: ((VC, Boolean, payloads: Map<String, Any?>?) -> Unit)? = null) {
        this.fullScreenChangeListener = l
    }

    fun setOnTrackListener(l: ((playAble: Boolean, start: Boolean, end: Boolean, formTrigDuration: Float) -> Unit)? = null) {
        this.onTrackListener = l
    }

    fun setOnResetListener(l: ((VC) -> Unit)? = null) {
        this.resetListener = l
    }

    fun onBehaviorDetached(p: String, callId: Any?) {
        if (p.isNotEmpty()) recordLogs("the data $p detached form window", this::class.java.simpleName, Pair("path", p), Pair("callId", callId ?: ""), Pair("name", "videoDetached"))
    }

    fun onBehaviorAttached(p: String, callId: Any?) {
        if (p.isNotEmpty()) recordLogs("the data $p attached form window", this::class.java.simpleName, Pair("path", p), Pair("callId", callId ?: ""), Pair("name", "videoAttached"))
    }
}