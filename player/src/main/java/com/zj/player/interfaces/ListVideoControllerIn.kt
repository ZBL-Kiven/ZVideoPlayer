package com.zj.player.interfaces


/**
 * @author ZJJ on 2020.6.16
 * this interface linked the list video view controller [com.zj.player.controller.BaseListVideoController] to adapter.
 * the [com.zj.player.adapters.ListListVideoAdapterDelegate] will deal the play request after the [waitingForPlay] called .
 **/
internal interface ListVideoControllerIn<T, VC> : VideoDetailBindIn<T, VC> {

    fun waitingForPlay(curPlayingIndex: Int, delay: Long, fromUser: Boolean = false)

    fun onFullScreenChanged(vc: VC, isFull: Boolean)
}