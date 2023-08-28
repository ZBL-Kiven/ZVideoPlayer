package com.zj.player.img.scale

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import com.zj.player.img.scale.easing.ScaleEffect

class TouchScaleImageView : ImageViewTouch {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    override fun getEasingEffect(): ScaleEffect {
        return ScaleEffect.CUBIC
    }

    internal fun getRealBounds(): IntArray {
        val dw: Int = this.drawable.bounds.width()
        val dh: Int = this.drawable.bounds.height()
        val m: Matrix = this.imageMatrix
        val values = FloatArray(10)
        m.getValues(values)
        val sx = values[0]
        val sy = values[4]
        val cw = (dw * sx).toInt()
        val ch = (dh * sy).toInt()
        return intArrayOf(cw, ch)
    }
}
