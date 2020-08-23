package com.zj.player.img.scale

import android.content.Context
import android.util.AttributeSet
import com.zj.player.img.scale.easing.ScaleEffect

internal class TouchScaleImageView : ImageViewTouch {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    override fun getEasingEffect(): ScaleEffect {
        return ScaleEffect.CUBIC
    }
}
