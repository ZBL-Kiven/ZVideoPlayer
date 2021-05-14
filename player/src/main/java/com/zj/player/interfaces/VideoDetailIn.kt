package com.zj.player.interfaces

import android.view.View
import androidx.annotation.LayoutRes

interface VideoDetailIn {

    fun onFullScreenLayoutInflated(v: View, pl: Any?)

    @LayoutRes
    fun getVideoDetailLayoutId(): Int
}