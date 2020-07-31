package com.zj.player.list

/**
 * @author ZJJ on 2020.6.16
 * this interface linked the list video view controller [BaseListVideoController] to adapter.
 * the [ListVideoAdapterDelegate] will deal the play request after the [waitingForPlay] called .
 **/
internal interface VideoControllerIn {

    fun waitingForPlay(curPlayingIndex: Int)
}