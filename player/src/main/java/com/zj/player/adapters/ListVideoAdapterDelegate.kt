package com.zj.player.adapters

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.annotation.MainThread
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zj.player.R
import com.zj.player.ZController
import com.zj.player.controller.BaseListVideoController
import com.zj.player.list.VideoControllerIn
import com.zj.player.logs.ZPlayerLogs
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
abstract class ListVideoAdapterDelegate<T, V : BaseListVideoController, VH : RecyclerView.ViewHolder>(private val adapter: RecyclerView.Adapter<VH>) : AdapterDelegateIn<T, VH>, VideoControllerIn {

    private var controller: ZController? = null
    private var curPlayingIndex: Int = -1
    private var isStopWhenItemDetached = true
    private var isAutoPlayWhenItemAttached = true
    private var isPausedToAutoPlay = false
    private var isAutoScrollToVisible = true
    private var recyclerView: RecyclerView? = null
    private val waitingForPlayClicked = R.id.delegate_waiting_for_play_clicked
    private val waitingForPlayScrolled = R.id.delegate_waiting_for_play_scrolled
    protected abstract fun createZController(vc: V): ZController
    protected abstract fun getViewController(holder: VH?): V?
    protected abstract fun getItem(p: Int): T?
    protected abstract fun isInflateMediaType(d: T?): Boolean
    protected abstract fun getPathAndLogsCallId(d: T?): Pair<String, Any?>?
    protected abstract fun onBindData(holder: VH?, p: Int, d: T?, playAble: Boolean, vc: V?, pl: MutableList<Any>?)
    protected open fun onBindTypeData(holder: SoftReference<VH>?, d: T?, p: Int, pl: MutableList<Any>?) {}
    protected open fun onBindDelegate(holder: VH?, p: Int, d: T?, pl: MutableList<Any>?) {}
    protected abstract val isSourcePlayAble: (d: T?) -> Boolean

    /**
     * overridden your data adapter and call;
     * record and set a scroller when recycler is attached
     * */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        if (this.recyclerView == null || this.recyclerView != recyclerView) {
            recyclerView.setHasFixedSize(true)
            recyclerView.clearOnScrollListeners()
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
        holder?.get()?.let { h ->
            val position = h.adapterPosition
            getViewController(h)?.let {
                getItem(position)?.let { p ->
                    val pac = getPathAndLogsCallId(p)
                    pac?.let { pv -> it.onBehaviorDetached(pv.first, pv.second) }
                }
                if (isStopWhenItemDetached && position == curPlayingIndex) if (it.isBindingController) controller?.stopNow(false)
                it.resetWhenDisFocus()
            }
        }
        holder?.clear()
    }

    override fun bindData(holder: SoftReference<VH>?, p: Int, d: T?, pl: MutableList<Any>?) {
        holder?.get()?.let { h ->
            if (isInflateMediaType(d)) {
                val playAble = isSourcePlayAble(d)
                if (!pl.isNullOrEmpty() && !pl[0].toString().startsWith(DEFAULT_LOADER)) {
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

    fun setIsStopWhenItemDetached(`is`: Boolean) {
        this.isStopWhenItemDetached = `is`
    }

    fun setIsAutoPlayWhenItemAttached(`is`: Boolean) {
        this.isAutoPlayWhenItemAttached = `is`
    }

    fun setIsAutoScrollToCenter(`is`: Boolean) {
        this.isAutoScrollToVisible = `is`
    }

    fun resume() {
        isPausedToAutoPlay = false
        controller?.playOrResume()
    }

    fun pause() {
        isPausedToAutoPlay = true
        controller?.pause()
    }

    fun cancelAll() {
        handler?.removeCallbacksAndMessages(null)
        controller?.stopNow(true, isRegulate = true)
    }

    fun release() {
        handler?.removeCallbacksAndMessages(null)
        controller?.stopNow()
        controller?.release()
        controller = null
        recyclerView?.clearOnScrollListeners()
        recyclerView = null
        handler = null
    }

    private fun onBindVideoView(vc: V) {
        if (controller == null) {
            controller = createZController(vc)
        } else {
            controller?.updateViewController(vc)
        }
    }

    open fun waitingForPlay(index: Int, delay: Long = 16L) {
        waitingForPlay(index, delay, true)
    }

    final override fun waitingForPlay(curPlayingIndex: Int, delay: Long, fromUser: Boolean) {
        if (curPlayingIndex !in 0 until adapter.itemCount) return
        if (fromUser) recyclerView?.smoothScrollToPosition(curPlayingIndex)
        handler?.removeMessages(waitingForPlayClicked)
        handler?.sendMessageDelayed(Message.obtain().apply {
            this.what = waitingForPlayClicked
            this.arg1 = curPlayingIndex
            this.obj = fromUser
        }, delay)
    }

    private fun bindDelegateData(h: VH, p: Int, d: T?, playAble: Boolean, pl: MutableList<Any>?) {
        getViewController(h)?.let { vc ->
            vc.onBindHolder(p)
            vc.setControllerIn(this)
            if (playAble != vc.isPlayable) vc.isPlayable = playAble
            if (pl?.isNullOrEmpty() == false) {
                val pls = pl.last().toString()
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
                                if (!vc.isBindingController) onBindVideoView(vc)
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
            onBindData(h, p, getItem(p), playAble, vc, pl)
            getPathAndLogsCallId(d)?.let {
                vc.post {
                    if (curPlayingIndex == p && controller?.getPath() == it.first && controller?.isPlaying() == true) {
                        if (!vc.isBindingController) onBindVideoView(vc)
                        controller?.playOrResume(it.first, it.second)
                    } else vc.resetWhenDisFocus()
                }
            }
        }
    }

    private fun onScrollIdle() {
        (recyclerView?.layoutManager as? LinearLayoutManager)?.let { lm ->
            var fv = lm.findFirstCompletelyVisibleItemPosition()
            var lv = lm.findLastCompletelyVisibleItemPosition()
            var offsetPositions: Int? = null
            var scrollAuto = false
            if (fv < 0 && lv < 0) {
                fv = lm.findFirstVisibleItemPosition()
                lv = lm.findLastVisibleItemPosition()
                scrollAuto = true
            }
            val cp = Rect()
            recyclerView?.getLocalVisibleRect(cp)
            val tr = if (fv == lv) fv else if (fv >= 0 && lv >= 0) {
                @Suppress("UNCHECKED_CAST") val vft = (recyclerView?.findViewHolderForAdapterPosition(fv) as? VH)?.itemView?.top ?: 0
                @Suppress("UNCHECKED_CAST") val vlt = (recyclerView?.findViewHolderForAdapterPosition(lv) as? VH)?.itemView
                val fvo = vft - cp.top
                val lvo = vlt?.let { min(vlt.bottom - cp.bottom, vlt.top - cp.top) } ?: 0
                val trv = min(abs(fvo), abs(lvo))
                val isFo = abs(fvo) == trv
                offsetPositions = if (isFo) fvo - 20 else lvo + 20
                if (isFo) fv else lv
            } else if (fv <= 0) lv else fv
            @Suppress("UNCHECKED_CAST") (getViewController(recyclerView?.findViewHolderForAdapterPosition(tr) as? VH)?.let {
                if (!it.isBindingController) it.clickPlayBtn()
            })
            if (!scrollAuto) return@let
            val offset = offsetPositions ?: {
                val itemOffset = @Suppress("UNCHECKED_CAST") (recyclerView?.findViewHolderForAdapterPosition(tr) as? VH)?.itemView?.top ?: 0
                itemOffset - cp.top
            }.invoke()
            if (scrollAuto && isAutoScrollToVisible) {
                recyclerView?.smoothScrollBy(0, offset, AccelerateInterpolator(), 600)
            }
        }
    }

    private fun playOrResume(vc: V?, p: Int, data: T?, formUser: Boolean = false) {
        if (vc == null) {
            ZPlayerLogs.onError(NullPointerException("use a null view controller ,means show what?"))
            return
        }
        controller = controller ?: createZController(vc)
        controller?.let { ctr ->
            getPathAndLogsCallId(data ?: getItem(p))?.let { d ->
                fun play() {
                    ctr.playOrResume(d.first, d.second)
                    vc.onBehaviorAttached(d.first, d.second)
                }
                //when ctr is playing another path or in other positions
                if ((ctr.isLoadData() && p != curPlayingIndex) || (ctr.isLoadData() && ctr.getPath() != d.first)) {
                    ctr.stopNow(false)
                }
                if (formUser || p != curPlayingIndex || (!vc.isCompleted && !ctr.isLoadData()) || (ctr.isLoadData() && !ctr.isPlaying() && !ctr.isPause(true) && !vc.isCompleted)) {
                    play()
                }
            }
        } ?: ZPlayerLogs.onError(NullPointerException("where is the thread call crashed, make the ZController to be null?"))
    }

    private var handler: Handler? = Handler(Looper.getMainLooper()) {
        when (it.what) {
            waitingForPlayClicked -> {
                controller?.stopNow()
                adapter.notifyItemRangeChanged(0, adapter.itemCount, String.format(LOAD_STR_DEFAULT_LOADER, it.arg1, it.obj.toString()))
            }
            waitingForPlayScrolled -> onScrollIdle()
        }
        return@Handler false
    }

    fun idle(position: Int = -1) {
        if (position == -1) {
            handler?.removeMessages(waitingForPlayScrolled)
            handler?.sendEmptyMessageDelayed(waitingForPlayScrolled, 150)
        } else {
            if (position in 0 until adapter.itemCount) recyclerView?.smoothScrollToPosition(position)
        }
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
}