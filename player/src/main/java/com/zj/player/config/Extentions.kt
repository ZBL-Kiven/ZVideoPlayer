package com.zj.player.config

import android.view.View
import android.view.ViewGroup

internal fun ViewGroup.forEach(r: (View) -> Unit) {
    for (i in 0 until childCount) {
        val cv = getChildAt(i)
        r(cv)
    }
}

