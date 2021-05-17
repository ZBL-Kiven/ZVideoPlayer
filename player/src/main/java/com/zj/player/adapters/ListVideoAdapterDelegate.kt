package com.zj.player.adapters

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
import android.view.animation.AccelerateInterpolator
import androidx.annotation.MainThread
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zj.player.R
import com.zj.player.z.ZController
import com.zj.player.controller.BaseListVideoController
import com.zj.player.interfaces.ListVideoControllerIn
import com.zj.player.logs.ZPlayerLogs
import com.zj.player.ut.InternalPlayStateChangeListener
import java.lang.NullPointerException
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.min

/**
 * @author ZJJ on 2020.6.16
 *
 * of course ZPlayer running in the list adapter as so well.
 * create an instance of [BaseListVideoController] in your data Adapter ,and see [AdapterDelegateIn]
 **/
@Suppress("MemberVisibilityCanBePrivate", "unused")
abstract class ListVideoAdapterDelegate<T, V : BaseListVideoController<T, V>, VH : RecyclerView.ViewHolder, ADAPTER : RecyclerView.Adapter<VH>>(private val delegateName: String, private val adapter: ADAPTER) : AdapterDelegateIn<T, VH>, ListVideoControllerIn<T, V>, InternalPlayStateChangeListener, RecyclerView.AdapterDataObserver() {

    var curFullScreenController: V? = null
    private var controller: ZController<*, *>? = null
    private var curPlayingIndex: Int = -1
    private var isStopWhenItemDetached = true
    private var isAutoPlayWhenItemAttached = true
    private var isPausedToAutoPlay = false
    private var isAutoScrollToVisible = true
    private var recyclerView: RecyclerView? = null
    private val waitingForPlayClicked = R.id.delegate_waiting_for_play_clicked
    private val waitingForPlayScrolled = R.id.delegate_waiting_for_play_scrolled
    private val waitingForPlayIdle = R.id.delegate_waiting_for_play_idle
    protected abstract fun createZController(delegateName: String, data: T?, vc: V): ZController<*, *>
    protected abstract fun getViewController(holder: VH?): V?
    protected abstract fun getItem(p: Int, adapter: ADAPTER): T?
    protected abstract fun isInflateMediaType(d: T?): Boolean
    protected abstract fun getPathAndLogsCallId(d: T?): Pair<String, Any?>?
    protected abstract fun onBindData(holder: VH?, p: Int, d: T?, playAble: Boolean, vc: V?, pl: MutableList<Any?>?)
    protected open fun onBindTypeData(holder: SoftReference<VH>?, d: T?, p: Int, pl: MutableList<Any?>?) {}
    protected open fun onBindDelegate(holder: VH?, p: Int, d: T?, pl: MutableList<Any?>?) {}
    protected open fun onPlayStateChanged(runningName: String, isPlaying: Boolean, desc: String?, controller: ZController<*, *>?) {}
    protected open fun checkControllerMatching(data: T?, controller: ZController<*, *>?): Boolean {
        return controller != null && !controller.isDestroyed() && controller.runningName != delegateName
    }

    protected abstract val isSourcePlayAble: (d: T?) -> Boolean

    /**
     * overridden your data adapter and call;
     * record and set a scroller when recycler is attached
     * */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        if (this.recyclerView == null || this.recyclerView != recyclerView) {
            recyclerView.setHasFixedSize(true)
            recyclerView.clearOnScrollListeners()
            (recyclerView.parent as? ViewGroup)?.descendantFocusability = FOCUS_BLOCK_DESCENDANTS
            recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
            recyclerView.addOnScrollListener(recyclerScrollerListener)
            this.recyclerView = recyclerView
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        if (recyclerView == this.recyclerView) recyclerView.clearOnScrollListeners()
        else this.recyclerView?.clearOnScrollListeners()
        this.recyclerView = null
    }

    override fun onViewDetachedFromWindow(holder: WeakReference<VH>?) {
        holder?.get()?.let holder@{ h ->
            val position = h.adapterPosition
            getViewController(h)?.let {
                if (it.isFullScreen) return@holder
                getItem(position, adapter)?.let { p ->
                    val pac = getPathAndLogsCallId(p)
                    pac?.let { pv -> it.onBehaviorDetached(pv.first, pv.second) }
                }
                if (isStopWhenItemDetached && position == curPlayingIndex) if (it.isBindingController) controller?.stopNow(false)
                it.resetWhenDisFocus()
            }
        }
        holder?.clear()
    }

    override fun onViewAttachedToWindow(holder: VH) {}

    override fun onViewRecycled(holder: VH) {
        getViewController(holder)?.let {
            it.clearVideoListDataIn()
            if (curFullScreenController != null && curFullScreenController == it) {
                curFullScreenController = null
                it.fullScreen(isFull = false, fromUser = false, payloads = null)
            }
        }
    }

    override fun bindData(holder: SoftReference<VH>?, p: Int, d: T?, pl: MutableList<Any?>?) {
        holder?.get()?.let { h ->
            if (isInflateMediaType(d)) {
                val playAble = isSourcePlayAble(d)
                if (!pl.isNullOrEmpty() && pl[0]?.toString()?.startsWith(DEFAULT_LOADER) == false) {
                    val vc = getViewController(h)
                    onBindData(h, p, d, playAble, vc, pl)
                } else {
                    onBindDelegate(h, p, d, pl)
                    bindDelegateData(h, p, d, playAble, pl)
                }
            } else {
                onBindTypeData(SoftReference(h), d, p, pl)
            }
        }
    }

    @MainThread
    fun isVisible(position: Int): Boolean {
        (recyclerView?.layoutManager as? LinearLayoutManager)?.let { lm ->
            val first = lm.findFirstVisibleItemPosition()
            val last = lm.findLastVisibleItemPosition()
            return position in first..last
        }
        return false
    }

    @MainThread
    fun <V : View> findViewByPosition(position: Int, id: Int, attachForce: Boolean = false): V? {
        recyclerView?.findViewHolderForAdapterPosition(position)?.let {
            if (!attachForce || it.itemView.isAttachedToWindow) return it.itemView.findViewById(id)
        }
        return null
    }

    open fun waitingForPlay(index: Int, delay: Long = 16L) {
        waitingForPlay(index, delay, true)
    }

    final override fun waitingForPlay(curPlayingIndex: Int, delay: Long, fromUser: Boolean) {
        if (curPlayingIndex !in 0 until adapter.itemCount) return
        if (fromUser) {
            recyclerView?.scrollToPosition(curPlayingIndex)
        }
        handler?.removeMessages(waitingForPlayClicked)
        handler?.sendMessageDelayed(Message.obtain().apply {
            this.what = waitingForPlayClicked
            this.arg1 = curPlayingIndex
            this.obj = fromUser
        }, delay)
    }

    override fun onFullScreenChanged(vc: V, isFull: Boolean) {
        this.curFullScreenController = if (isFull) {
            this.adapter.registerAdapterDataObserver(this);vc
        } else {
            this.adapter.unregisterAdapterDataObserver(this); null
        }
    }

    final override fun onState(runningName: String, isPlaying: Boolean, desc: String?, controller: ZController<*, *>?) {
        if (runningName == this.delegateName && isPlaying && isPausedToAutoPlay) {
            ZPlayerLogs.debug("paused by state changed  runningName = $runningName  ,cur delegate name = $delegateName  isPlaying = $isPlaying   isPausedToAutoPlay = $isPausedToAutoPlay")
            pause()
        }
        this.onPlayStateChanged(runningName, isPlaying, desc, controller)
    }

    private fun bindDelegateData(h: VH, p: Int, d: T?, playAble: Boolean, pl: MutableList<Any?>?) {
        getViewController(h)?.let { vc ->
            vc.setVideoListDetailIn(p, d, this)
            if (playAble != vc.isPlayable) vc.isPlayable = playAble
            if (pl?.isNullOrEmpty() == false) {
                val pls = pl.first()?.toString() ?: ""
                if (pls.isNotEmpty() && pls.startsWith(DEFAULT_LOADER)) {
                    var index: Int
                    var fromUser: Boolean
                    pls.split("#").let {
                        index = it[1].toInt()
                        fromUser = it[2] == "true"
                    }
                    vc.post {
                        if (p == index) {
                            if (playAble && vc.isPlayable) {
                                if (!vc.isBindingController) onBindVideoView(d, vc)
                                playOrResume(vc, p, d, fromUser)
                            } else {
                                if (controller?.isPlaying() == true) {
                                    controller?.stopNow(false, isRegulate = true)
                                }
                            }
                            curPlayingIndex = p
                        } else {
                            vc.resetWhenDisFocus()
                        }
                    }
                    return@bindDelegateData
                }
            }
            onBindData(h, p, getItem(p, adapter), playAble, vc, pl)
            getPathAndLogsCallId(d)?.let {
                vc.post {
                    if (curPlayingIndex == p && controller?.getPath() == it.first && controller?.isPlaying() == true) {
                        if (!vc.isBindingController) onBindVideoView(d, vc)
                        if (!checkControllerMatching(d, controller)) controller = getZController(d, vc)
                        controller?.playOrResume(it.first, it.second)
                    } else vc.resetWhenDisFocus()
                }
            }
        }
    }

    private fun onBindVideoView(d: T?, vc: V) {
        if (!checkControllerMatching(d, controller)) {
            controller = getZController(d, vc)
        } else {
            controller?.updateViewController(delegateName, vc)
        }
    }

    private fun getZController(data: T?, vc: V): ZController<*, *>? {
        val c = createZController(delegateName, data, vc)
        if (c != controller) {
            controller = c
        }
        controller?.bindInternalPlayStateListener(delegateName, this)
        return controller
    }

    @Suppress("UNCHECKED_CAST")
    private fun onScrollIdle() {
        (recyclerView?.layoutManager as? LinearLayoutManager)?.let { lm ->
            val fvc = lm.findFirstCompletelyVisibleItemPosition()
            val lv = lm.findLastVisibleItemPosition()
            val lvi = lm.findLastCompletelyVisibleItemPosition()
            var fvi = lm.findFirstVisibleItemPosition()
            if (fvc < 0) {
                if (lv - fvi > 1) fvi++
            }
            val fv = if (fvc < 0) fvi else fvc
            var offset = 0
            val tr = run indexFound@{
                if (lv - fv > 0) {
                    (fv..lvi).forEach {
                        getViewController((recyclerView?.findViewHolderForAdapterPosition(it) as? VH))?.let { vc ->
                            if (vc.isFullScreen || vc.isBindingController) return@indexFound it
                        }
                    }
                }
                val cp = Rect()
                var pt = 0
                var pb = 0
                recyclerView?.getLocalVisibleRect(cp)
                recyclerView?.let {
                    pt = it.paddingTop
                    pb = it.paddingBottom
                    cp.top += pt + it.translationY.toInt()
                    cp.bottom -= pb - it.translationY.toInt()
                }
                when (fvi) {
                    lv - 1 -> {
                        val ccf = Rect()
                        var hft = 0
                        var hlt = 0
                        var hlb = 0
                        (recyclerView?.findViewHolderForAdapterPosition(fv) as? VH)?.itemView?.let {
                            it.getLocalVisibleRect(ccf)
                            hft = it.top
                        }
                        (recyclerView?.findViewHolderForAdapterPosition(lv) as? VH)?.itemView?.let {
                            hlt = it.top
                            hlb = it.bottom
                        }
                        val offF = (ccf.bottom - ccf.top) / 2f - (cp.centerY() - 1.5f)
                        val offL = (ccf.bottom - ccf.top) / 2f + hlt - cp.centerY()
                        val cy = min(abs(offF), abs(offL))
                        val next = if (cy == abs(offF)) fv else lv
                        offset = if (next == fv) if (hft >= 0) 0 else hft - pt else if (hlb <= cp.bottom) 0 else hlb - cp.bottom + pb
                        next
                    }
                    else -> fv
                }
            }
            getViewController((recyclerView?.findViewHolderForAdapterPosition(tr) as? VH))?.let { vc ->
                if (!vc.isBindingController || (vc.isBindingController && controller?.isPause() == true)) {
                    vc.clickPlayBtn(true)
                    ZPlayerLogs.debug("can click ")
                } else {
                    ZPlayerLogs.debug("isBind =  ${vc.isBindingController}   isPaused = ${controller?.isPause() == true}")
                }
            }
            if (isAutoScrollToVisible && offset != 0) {
                ZPlayerLogs.debug("offset =  $offset ")
                recyclerView?.smoothScrollBy(0, offset, AccelerateInterpolator(), 500)
            } else {
                ZPlayerLogs.debug(" enable =  $isAutoScrollToVisible   offset = $offset ")
            }
        }
    }

    private fun playOrResume(vc: V?, p: Int, data: T?, fromUser: Boolean = false) {
        if (vc == null) {
            ZPlayerLogs.onError(NullPointerException("use a null view controller ,means show what?"))
            return
        }
        controller = if (checkControllerMatching(data, controller)) controller else getZController(data, vc)
        controller?.let { ctr ->
            getPathAndLogsCallId(data ?: getItem(p, adapter))?.let { d ->
                fun play() {
                    ctr.playOrResume(d.first, d.second)
                    vc.onBehaviorAttached(d.first, d.second)
                }
                //when ctr is playing another path or in other positions
                if ((ctr.isLoadData() && p != curPlayingIndex) || (ctr.isLoadData() && ctr.getPath() != d.first)) {
                    ctr.stopNow(false)
                }
                if (fromUser || p != curPlayingIndex || (!vc.isCompleted && !ctr.isLoadData()) || (ctr.isLoadData() && !ctr.isPlaying() && !ctr.isPause(true) && !vc.isCompleted)) {
                    play()
                }
            }
        } ?: ZPlayerLogs.onError(NullPointerException("where is the thread call crashed, make the ZController to be null?"))
    }

    fun setIsStopWhenItemDetached(`is`: Boolean) {
        this.isStopWhenItemDetached = `is`
    }

    fun setIsAutoPlayWhenItemAttached(`is`: Boolean) {
        this.isAutoPlayWhenItemAttached = `is`
    }

    fun setIsAutoScrollToCenter(`is`: Boolean) {
        this.isAutoScrollToVisible = `is`
    }

    fun resume(autoPlay: Boolean = false) {
        resume(-1, autoPlay)
    }

    fun resume(position: Int = -1, autoPlay: Boolean = false) {
        isPausedToAutoPlay = false
        handler?.removeMessages(waitingForPlayIdle)
        if (autoPlay) handler?.sendMessageDelayed(Message.obtain().apply {
            what = waitingForPlayIdle
            arg1 = position
        }, 500)
    }

    fun pause() {
        isPausedToAutoPlay = true
        controller?.pause()
    }

    fun release(destroyPlayer: Boolean) {
        handler?.removeCallbacksAndMessages(null)
        curFullScreenController = null
        controller?.removeInternalPlayStateListener(delegateName)
        controller?.release(destroyPlayer)
        controller = null
        recyclerView?.clearOnScrollListeners()
        recyclerView = null
        handler = null
    }

    private fun idle(position: Int = -1) {
        if (isPausedToAutoPlay) return
        if (position == -1) {
            handler?.removeMessages(waitingForPlayScrolled)
            handler?.sendEmptyMessageDelayed(waitingForPlayScrolled, 150)
        } else {
            if (position in 0 until adapter.itemCount) recyclerView?.smoothScrollToPosition(position)
        }
    }

    private var handler: Handler? = Handler(Looper.getMainLooper()) {
        when (it.what) {
            waitingForPlayClicked -> {
                controller?.stopNow()
                adapter.notifyItemRangeChanged(0, adapter.itemCount, String.format(LOAD_STR_DEFAULT_LOADER, it.arg1, it.obj.toString()))
            }
            waitingForPlayScrolled -> onScrollIdle()
            waitingForPlayIdle -> idle(it.arg1)
        }
        return@Handler false
    }

    private val recyclerScrollerListener = object : RecyclerView.OnScrollListener() {

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (isPausedToAutoPlay || !isAutoPlayWhenItemAttached) return
            handler?.removeMessages(waitingForPlayScrolled)
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                handler?.removeMessages(waitingForPlayClicked)
                handler?.sendEmptyMessageDelayed(waitingForPlayScrolled, 150)
            }
        }
    }

    companion object {
        private const val DEFAULT_LOADER = "loadOrReset"
        private const val LOAD_STR_DEFAULT_LOADER = "$DEFAULT_LOADER#%d#%s"
    }

    override fun onChanged() {
        super.onChanged()
        curFullScreenController?.onDetailViewNotifyChanged(null)
    }

    override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
        super.onItemRangeChanged(positionStart, itemCount)
        curFullScreenController?.onDetailViewNotifyChanged(null)
    }

    override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
        super.onItemRangeChanged(positionStart, itemCount, payload)
        curFullScreenController?.onDetailViewNotifyChanged(payload)
    }

    override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
        super.onItemRangeMoved(fromPosition, toPosition, itemCount)
        if (curFullScreenController?.curPlayingIndex in fromPosition..toPosition) curFullScreenController?.fullScreen(false, fromUser = false)
    }

    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
        super.onItemRangeRemoved(positionStart, itemCount)
        if (curFullScreenController?.curPlayingIndex in positionStart..(positionStart + itemCount)) curFullScreenController?.fullScreen(false, fromUser = false)
    }
}