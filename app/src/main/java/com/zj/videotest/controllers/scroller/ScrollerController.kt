package com.zj.videotest.controllers.scroller

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import com.zj.player.controller.BaseListVideoController
import com.zj.player.full.TrackOrientation
import com.zj.player.full.Transaction
import com.zj.views.ut.DPUtils

abstract class ScrollerController<T, VC> @JvmOverloads constructor(c: Context, attr: AttributeSet? = null, def: Int = 0) : BaseListVideoController<T, VC>(c, attr, def) {
    private var lastHeight = 0
    private var scrolled = 0f
    private var parseCancel = false
    private var viewScroller: ViewScroller? = null
    private var inAnimation = false
    private val leastHeight = DPUtils.dp2px(246f)

    override fun onFullScreenChanged(isFull: Boolean, payloads: Map<String, Any?>?) {
        super.onFullScreenChanged(isFull, payloads)
        if (isFull && payloads?.get("isExpand") != null) {
            videoRoot?.let {
                //                inAnimation = true
                //                it.postDelayed({
                //                    val h = it.height.toFloat()
                //                    scrolled = h
                //                    lastHeight = it.height
                //                    val anim = ValueAnimator.ofFloat(0.0f, 1.0f)
                //                    anim.addUpdateListener { a ->
                //                        onMoving(it, (h * a.animatedFraction).toInt(), TrackOrientation.BOTTOM_TOP)
                //                    }
                //                    anim.addListener(onEnd = {
                //                        inAnimation = false
                //                    })
                //                    anim.duration = 500
                //                    anim.start()
                //                }, 300)
            }
        } else {
            viewScroller?.clear()
            scrolled = 0f
            lastHeight = 0
        }
    }

    @CallSuper
    override fun onFullKeyEvent(code: Int, event: KeyEvent): Boolean {
        return if (inAnimation) true else super.onFullKeyEvent(code, event)
    }


    override fun onFullScreenClick(transaction: Transaction): Transaction {
        var pl: MutableMap<String, Any?>? = transaction.payloads?.toMutableMap()
        if (pl == null) pl = mutableMapOf()
        pl["isExpand"] = 0
        return super.onFullScreenClick(transaction)
    }

    override fun onTouchActionEvent(videoRoot: View?, event: MotionEvent, lastX: Float, lastY: Float, orientation: TrackOrientation?): Boolean {
        if (inAnimation) return true
        if (lastY <= 0) return false
        if (viewScroller == null) instanceScroller(videoRoot)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (lastHeight == 0) lastHeight = videoRoot?.height ?: 0
                viewScroller?.onEventDown(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parseCancel = false
                if (parseCancel) lastHeight = 0
                viewScroller?.onEventUp(event)
                return scrolled != 0f
            }
            MotionEvent.ACTION_MOVE -> {
                if (lastHeight == 0) return false
                viewScroller?.onEventMove(event, lastY)
                return onMoving(videoRoot, (lastY - event.rawY).toInt(), orientation)
            }
        }
        return false
    }

    private fun onMoving(videoRoot: View?, dy: Int, orientation: TrackOrientation?): Boolean {
        videoRoot?.let {
            return if (scrolled == 0.0f && orientation == TrackOrientation.TOP_BOTTOM) {
                parseCancel = true
                setLayoutParams(it, ViewGroup.LayoutParams.MATCH_PARENT)
                viewScroller?.clear()
                false
            } else if (parseCancel) {
                if (scrolled == 0f && orientation != TrackOrientation.TOP_BOTTOM) parseCancel = it.layoutParams.height < lastHeight
                viewScroller?.clear()
                return false
            } else {
                var lh = 0
                try {
                    var lph = it.layoutParams.height
                    if (lph == ViewGroup.LayoutParams.MATCH_PARENT) lph = lastHeight
                    lh = (lph - dy).coerceAtLeast(leastHeight).coerceAtMost(lastHeight)
                    setLayoutParams(it, lh)
                    true
                } finally {
                    if (lh == leastHeight || lh == lastHeight) {
                        if (lh == lastHeight) {
                            scrolled = 0f
                        }
                        viewScroller?.clear()
                    } else scrolled += dy
                }
            }
        }
        return false
    }

    private fun setLayoutParams(videoRoot: View, h: Int) {
        val height = if (h == ViewGroup.LayoutParams.MATCH_PARENT) h else if (h == lastHeight) ViewGroup.LayoutParams.MATCH_PARENT else h
        val lp = videoRoot.layoutParams
        lp.height = height
        (videoRoot.parent as? ViewGroup)?.let { vp ->
            val lpp = vp.layoutParams
            lpp.height = height
            vp.layoutParams = lpp
        }
    }

    private fun instanceScroller(videoRoot: View?) {
        viewScroller = object : ViewScroller(videoRoot ?: this@ScrollerController.videoRoot ?: this@ScrollerController) {
            override fun constrainScrollBy(dx: Int, dy: Int) {
                val or = if (dy > 0) TrackOrientation.TOP_BOTTOM else TrackOrientation.BOTTOM_TOP
                onMoving(videoRoot, dy, or)
            }
        }
    }
}