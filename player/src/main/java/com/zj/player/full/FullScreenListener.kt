package com.zj.player.full

import android.view.View

interface FullScreenListener {

    fun onDisplayChanged(isShow: Boolean)

    fun onContentLayoutInflated(content: View)

    fun onFullMaxChanged(isMax: Boolean)
}