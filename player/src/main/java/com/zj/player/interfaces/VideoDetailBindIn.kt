package com.zj.player.interfaces

import android.view.View

interface VideoDetailBindIn<T, VC> {

    fun getVideoDetailLayoutId(): Int

    fun onBindFullScreenLayout(contentLayout: View, vc: VC?, d: T?, p: Int, pl: List<Any?>?)
}