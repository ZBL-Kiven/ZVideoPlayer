package com.zj.player.base

import com.zj.player.ut.PlayerEventController


interface BasePlayer<R : BaseRender> {
    /**
     * check the player is set data
     * */
    fun isLoadData(): Boolean

    /**
     * Check if it is currently loading
     * */
    fun isLoading(accurate: Boolean = false): Boolean

    /**
     * Check whether the loading is currently complete
     * */
    fun isReady(accurate: Boolean = false): Boolean

    /**
     * Check if it is currently playing
     * */
    fun isPlaying(accurate: Boolean = false): Boolean

    /**
     * Check if it is currently paused
     * */
    fun isPause(accurate: Boolean = false): Boolean

    /**
     * Check if it is currently stopped
     * */
    fun isStop(accurate: Boolean = false): Boolean

    /**
     * Check if it is currently destroyed
     * */
    fun isDestroyed(accurate: Boolean = false): Boolean

    /**
     * Get the current playback path
     * */
    fun currentPlayPath(): String

    /**
     * Get the added value of the current playback
     * */
    fun currentCallId(): Any?

    /**
     * Set current playback path
     * @param callId Set any additional value for the current playback path
     * */
    fun setData(path: String, autoPlay: Boolean, callId: Any? = null)

    /**
     * Start to play
     * */
    fun play()

    /**
     * calling pause
     * */
    fun pause()

    /**
     * calling stop
     * */
    fun stop()

    /**
     * stop at now
     * @param withNotify Is the caller willing to synchronize this call to the UI
     * @param isRegulate Whether to adjust the tolerance of the playable button by this value
     * */
    fun stopNow(withNotify: Boolean, isRegulate: Boolean = false)

    /**
     * Retrieve the specified location
     * */
    fun seekTo(progress: Int, fromUser: Boolean)

    /**
     * Retrieve the specified location
     * */
    fun release()

    /**
     *Set current speed
     * */
    fun setSpeed(s: Float)

    /**
     * Get current playback speed
     * */
    fun getSpeed(): Float

    /**
     * Set current volume
     * */
    fun setVolume(volume: Int, maxVolume: Int)

    /**
     * Get current volume
     * */
    fun getVolume(): Int

    /**
     * Set whether to automatically play when needed
     * */
    fun autoPlay(autoPlay: Boolean)

    /**
     * When you need to synchronize the current state, it is common to switch multi-screen playback
     * */
    fun updateControllerState()

    /**
     * The current controller bound to this
     * */
    fun setController(controller: PlayerEventController<R>): String

}