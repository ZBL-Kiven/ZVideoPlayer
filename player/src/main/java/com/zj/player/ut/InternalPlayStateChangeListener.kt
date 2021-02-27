package com.zj.player.ut

import com.zj.player.z.ZController

internal interface InternalPlayStateChangeListener {
    fun onState(runningName: String, isPlaying: Boolean, desc: String?, controller: ZController<*, *>?)
}