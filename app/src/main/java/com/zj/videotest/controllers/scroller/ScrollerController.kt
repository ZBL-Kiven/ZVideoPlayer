package com.zj.videotest.controllers.scroller

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.zj.player.controller.BaseListVideoController
import com.zj.player.full.TrackOrientation
import com.zj.views.ut.DPUtils

abstract class ScrollerController @JvmOverloads constructor(c: Context, attr: AttributeSet? = null, def: Int = 0) : BaseListVideoController(c, attr, def) {
    private var lastHeight = 0
    private var scrolled = 0f
    private var parseCancel = false
    private var viewScroller: ViewScroller? = null

    override fun onFullScreenChanged(isFull: Boolean) {
        super.onFullScreenChanged(isFull)
        scrolled = 0f
        lastHeight = 0
    }

    override fun onTouchActionEvent(videoRoot: View?, event: MotionEvent, lastX: Float, lastY: Float, orientation: TrackOrientation?): Boolean {
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
                false
            } else if (parseCancel) {
                if (scrolled == 0f && orientation != TrackOrientation.TOP_BOTTOM) parseCancel = it.layoutParams.height < lastHeight
                return false
            } else {
                var lh = 0
                try {
                    val lp = it.layoutParams
                    lh = (lp.height - dy).coerceAtLeast(DPUtils.dp2px(246f)).coerceAtMost(lastHeight)
                    lp.height = lh
                    it.layoutParams = lp
                    (it.parent as ViewGroup).let { vp ->
                        val lpp = vp.layoutParams
                        lpp.height = lh
                        vp.layoutParams = lpp
                    }
                    true
                } finally {
                    if (lh == lastHeight) scrolled = 0f else scrolled += dy
                }
            }
        }
        return false
    }

    private fun instanceScroller(videoRoot: View?) {
        viewScroller = object : ViewScroller(videoRoot ?: this@ScrollerController) {
            override fun constrainScrollBy(dx: Int, dy: Int) {
                val or = if (dy > 0) TrackOrientation.TOP_BOTTOM else TrackOrientation.BOTTOM_TOP
                onMoving(videoRoot, dy, or)
            }
        }
    }
}