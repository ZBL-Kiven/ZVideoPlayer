package com.zj.player.full

import android.view.View

interface FullContentListener {

    fun onDisplayChanged(dialog: BaseGestureFullScreenDialog, isShow: Boolean)

    fun onContentLayoutInflated(dialog: BaseGestureFullScreenDialog, content: View)

    fun onFullMaxChanged(dialog: BaseGestureFullScreenDialog, isMax: Boolean)

    fun onFocusChange(dialog: BaseGestureFullScreenDialog, isMax: Boolean)
}

interface FullScreenListener {

    fun onDisplayChanged(dialog: BaseGestureFullScreenDialog, isShow: Boolean)

    fun onFocusChange(dialog: BaseGestureFullScreenDialog, isMax: Boolean)
}