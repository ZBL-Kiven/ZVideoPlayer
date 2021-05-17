package com.zj.player.interfaces

import com.zj.player.controller.BaseListVideoController


/**
 * @author ZJJ on 2020.6.16
 * this interface linked the list video view controller [com.zj.player.controller.BaseListVideoController] to adapter.
 * the [com.zj.player.adapters.ListVideoAdapterDelegate] will deal the play request after the [waitingForPlay] called .
 **/
internal interface ListVideoControllerIn<T, VC, C : BaseListVideoController<T, VC>> : VideoDetailBindIn<T, C> {

    fun waitingForPlay(curPlayingIndex: Int, delay: Long, fromUser: Boolean = false)

    fun onFullScreenChanged(vc: C, isFull: Boolean)
}