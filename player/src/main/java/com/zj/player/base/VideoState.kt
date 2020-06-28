package com.zj.player.base

internal enum class VideoState(val pri: Int) {
    COMPLETING(9), PLAY(8), READY(7), LOADING(6),SEEK_LOADING(5), COMPLETED(3), PAUSE(2), STOP(1), DESTROY(0);

    private var obj: Any? = null

    fun setObj(o: Any?): VideoState {
        this.obj = o
        return this
    }

    fun obj(): Any? {
        return obj
    }
}