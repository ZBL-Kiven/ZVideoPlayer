package com.zj.player.full

import android.graphics.RectF
import com.zj.player.BuildConfig
import kotlin.math.abs

internal class RectFCalculateUtil(bv: RectF, sv: RectF) {

    private var lastOffset: Float = 0.0f
    private val ol: Float
    private val or: Float
    private val tr: Float
    private val br: Float
    private var lastRectF: RectF? = null

    init {
        if (BuildConfig.DEBUG && ((bv.right - bv.left) < (sv.right - sv.left) || (bv.bottom - bv.top) < (sv.bottom - sv.top))) throw IllegalArgumentException("invalid params, the named 's' must be small than named 'b' !")
        ol = abs((sv.left - bv.left) * 1.0f)
        or = abs((bv.right - sv.right) * 1.0f)
        tr = abs((sv.top - bv.top) * 1.0f)
        br = abs((bv.bottom - sv.bottom) * 1.0f)
    }

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