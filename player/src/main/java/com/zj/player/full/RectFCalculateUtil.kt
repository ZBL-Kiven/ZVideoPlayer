package com.zj.player.full

import android.graphics.RectF

internal class RectFCalculateUtil(private val bv: RectF, sv: RectF) {

    var lastOffset: Float = 0.0f
    private val ol: Float
    private val or: Float
    private val tr: Float
    private val br: Float
    var lastRectF: RectF? = null

    init {
        if ((bv.right - bv.left) < (sv.right - sv.left) || (bv.bottom - bv.top) < (sv.bottom - sv.top)) throw IllegalArgumentException("invalid params, the named 's' must be small than named 'b' !")
        ol = (sv.left - bv.left) * 1.0f
        or = (bv.right - sv.right) * 1.0f
        tr = (sv.top - bv.top) * 1.0f
        br = (bv.bottom - sv.bottom) * 1.0f
    }

    fun calculate(offset: Float): RectF {
        if (offset < 0 || lastOffset == offset) lastRectF?.let { return it } ?: throw NullPointerException("can`t calculate with never been calculated and start with offset 0 ")
        try {
            val nextL = offset * ol
            val nextT = offset * tr
            val nextR = offset * or
            val nextB = offset * br
            lastRectF = RectF(nextL, nextT, nextR, nextB)
            return lastRectF ?: throw NullPointerException()
        } finally {
            lastOffset = offset
        }
    }
}