package com.zj.player.full

import android.graphics.RectF

internal class RectFCalculateUtil(bv: RectF, sv: RectF) {

    private var lastOffset: Float = 0.0f
    private val ol: Float = (sv.left - bv.left) * 1.0f
    private val or: Float = (bv.right - sv.right) * 1.0f
    private val tr: Float = (sv.top - bv.top) * 1.0f
    private val br: Float = (bv.bottom - sv.bottom) * 1.0f
    private var lastRectF: RectF? = null

    fun calculate(offset: Float): RectF {
        if (offset < 0 || lastOffset == offset) lastRectF?.let { return it } ?: RectF()
        try {
            val nextL = offset * ol
            val nextT = offset * tr
            val nextR = offset * or
            val nextB = offset * br
            lastRectF = RectF(nextL, nextT, nextR, nextB)
            return lastRectF ?: RectF()
        } finally {
            lastOffset = offset
        }
    }
}