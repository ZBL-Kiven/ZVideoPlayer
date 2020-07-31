package com.zj.player.list

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.animation.AccelerateInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zj.player.ZController
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
    private var isAutoScrollToCenter = false
    private var recyclerView: RecyclerView? = null
    protected abstract fun createZController(vc: BaseListVideoController): ZController
    protected abstract fun getViewController(holder: VH?): V?
    protected abstract fun getItem(p: Int): T?
    protected abstract fun getPathAndLogsCallId(d: T?): Pair<String, Any?>?
    protected abstract fun onBindData(holder: VH?, p: Int, d: T?, vc: BaseListVideoController, pl: MutableList<Any>?)

    /**
     * overridden your data adapter and call;
     * record and set a scroller when recycler is attached
     * */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        if (isAutoPlayWhenItemAttached && (this.recyclerView == null || this.recyclerView != recyclerView)) {
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

    override fun onViewDetachedFromWindow(holder: VH) {
        getViewController(holder)?.let {
            it.post {
                getItem(holder.adapterPosition)?.let { p ->
                    val pac = getPathAndLogsCallId(p)
                    pac?.let { pv -> it.onBehaviorDetached(pv.first, pv.second) }
                }
                if (isStopWhenItemDetached && holder.adapterPosition == curPlayingIndex) {
                    if (it.isBindingController) controller?.stopNow() else it.resetWhenDisFocus()
                }
            }
        }
    }

    override fun bindData(holder: VH?, p: Int, d: T?, pl: MutableList<Any>?) {
        getViewController(holder)?.let { vc ->
            vc.onBindHolder(p)
            vc.setControllerIn(this)
            if (pl?.isNullOrEmpty() == false) {
                val pls = pl.last().toString()
                if (pls.isNotEmpty()) {
                    if (pls.startsWith(DEFAULT_LOADER)) {
                        val index = pls.split("#")[1].toInt()
                        if (p == index) {
                            if (!vc.isBindingController) onBindVideoView(vc)
                            getPathAndLogsCallId(d)?.let {
                                controller?.playOrResume(it.first, it.second)
                                vc.onBehaviorAttached(it.first, it.second)
                            }
                            curPlayingIndex = p
                        } else {
                            vc.resetWhenDisFocus()
                        }
                    }
                }
                return
            }
            onBindData(holder, p, getItem(p), vc, pl)

            getPathAndLogsCallId(d)?.let {
                vc.post {
                    if (curPlayingIndex == p && controller?.getPath() == it.first && controller?.isPlaying() == true) {
                        if (!vc.isBindingController) onBindVideoView(vc)
                        controller?.playOrResume(it.first, it.second)
                    } else {
                        vc.resetWhenDisFocus()
                    }
                }
            }
        }
    }

    fun release() {
        handler?.removeCallbacksAndMessages(null)
        controller?.release()
        controller = null
        recyclerView?.clearOnScrollListeners()
        recyclerView = null
        handler = null
    }

    private fun onBindVideoView(vc: BaseListVideoController) {
        if (controller == null) {
            controller = createZController(vc)
        } else {
            controller?.updateViewController(vc)
        }
    }

    override fun waitingForPlay(curPlayingIndex: Int) {
        if (curPlayingIndex !in 0 until adapter.itemCount) return
        handler?.removeMessages(0)
        handler?.sendMessage(Message.obtain().apply {
            this.what = 0
            this.arg1 = curPlayingIndex
        })
    }

    private fun onScrollIdle() {
        (recyclerView?.layoutManager as? LinearLayoutManager)?.let { lm ->
            var fv = lm.findFirstCompletelyVisibleItemPosition()
            var lv = lm.findLastCompletelyVisibleItemPosition()
            var offsetPositions: Int? = null
            var scrollAuto = isAutoScrollToCenter
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
                offsetPositions = if (isFo) fvo else lvo
                if (isFo) fv else lv
            } else if (fv <= 0) lv else fv
            if (tr != curPlayingIndex || controller?.isPlaying() != true) {
                @Suppress("UNCHECKED_CAST") val vc = getViewController(recyclerView?.findViewHolderForAdapterPosition(tr) as? VH)
                vc?.clickPlayBtn()
            }
            if (!scrollAuto) return@let
            val offset = offsetPositions ?: {
                val itemOffset = @Suppress("UNCHECKED_CAST") (recyclerView?.findViewHolderForAdapterPosition(tr) as? VH)?.itemView?.top ?: 0
                itemOffset - cp.top
            }.invoke()
            recyclerView?.smoothScrollBy(0, offset, AccelerateInterpolator(), 600)
        }
    }

    private var handler: Handler? = Handler(Looper.getMainLooper()) {
        when (it.what) {
            0 -> {
                controller?.stopNow()
                adapter.notifyItemRangeChanged(0, adapter.itemCount, String.format(LOAD_STR_DEFAULT_LOADER, it.arg1))
            }
            1 -> onScrollIdle()
        }
        return@Handler false
    }

    private val recyclerScrollerListener = object : RecyclerView.OnScrollListener() {

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (!isAutoPlayWhenItemAttached) return
            handler?.removeMessages(1)
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                handler?.sendEmptyMessageDelayed(1, 300)
            }
        }
    }

    companion object {
        private const val DEFAULT_LOADER = "loadOrReset"
        private const val LOAD_STR_DEFAULT_LOADER = "$DEFAULT_LOADER#%d"
    }
}