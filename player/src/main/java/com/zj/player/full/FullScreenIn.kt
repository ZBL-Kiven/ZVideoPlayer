package com.zj.player.full

import android.view.KeyEvent
import android.view.View

internal interface FullContentListener : FullScreenListener {

    fun onContentLayoutInflated(content: View)

    fun onFullMaxChanged(dialog: ZPlayerFullScreenView, isMax: Boolean)
}

internal interface FullScreenListener {

    fun onDisplayChanged(isShow: Boolean, payloads: Map<String, Any?>?)

    fun onFocusChange(dialog: ZPlayerFullScreenView, isMax: Boolean)

    fun onTrack(isStart: Boolean, isEnd: Boolean, formTrigDuration: Float)

    fun onKeyEvent(code: Int, event: KeyEvent): Boolean
}

enum class RotateOrientation(val degree: Float) {
    P0(0f), P1(180f), L0(270f), L1(90f)
}