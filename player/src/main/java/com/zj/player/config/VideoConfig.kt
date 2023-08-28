package com.zj.player.config

import com.zj.playerLib.C
import com.zj.player.ut.ScalingMode
import com.zj.player.z.ZVideoPlayer
import com.zj.player.logs.ZPlayerLogs


/**
 * @author ZJJ on 2020.6.16
 *
 * The configurator of the video player. This configurator is only suitable for the loading of remote resources. It is not necessary to load local resources.
 * */
@Suppress("unused")
class VideoConfig private constructor() {

    companion object {

        private const val MAX_CACHE_SIZE = ZVideoPlayer.DEFAULT_VIDEO_MAX_CACHED_SIZE
        private const val CACHE_FILE_DIR = ZVideoPlayer.DEFAULT_VIDEO_CACHED_PATH
        private const val CACHE_ENABLE = true
        private const val VIDEO_SCALE_MOD = C.VIDEO_SCALING_MODE_SCALE_TO_FIT

        /**
         * The default minimum duration of media that the player will attempt to ensure is buffered at all
         * times, in milliseconds.
         */
        private const val DEFAULT_MIN_BUFFER_MS = 15000

        /**
         * The default maximum duration of media that the player will attempt to buffer, in milliseconds.
         */
        private const val DEFAULT_MAX_BUFFER_MS = 50000

        /**
         * The default duration of media that must be buffered for playback to start or resume following a
         * user action such as a seek, in milliseconds.
         */
        private const val DEFAULT_BUFFER_FOR_PLAYBACK_MS = 2500

        /**
         * The default duration of media that must be buffered for playback to resume after a re-buffer, in
         * milliseconds. A re-buffer is defined to be caused by buffer depletion rather than a user action.
         */
        private const val DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_RE_BUFFER_MS = 5000

        fun create(): VideoConfig {
            return VideoConfig()
        }
    }

    internal var maxCacheSize: Long = MAX_CACHE_SIZE
    internal var cacheFileDir: String = CACHE_FILE_DIR
    internal var cacheEnable: Boolean = CACHE_ENABLE
    internal var videoScaleMod: Int = VIDEO_SCALE_MOD
    internal var minBufferMs = DEFAULT_MIN_BUFFER_MS
    internal var maxBufferMs = DEFAULT_MAX_BUFFER_MS
    internal var bufferForPlaybackMs = DEFAULT_BUFFER_FOR_PLAYBACK_MS
    internal var bufferForPlaybackAfterBufferMs = DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_RE_BUFFER_MS
    internal var requestProperty: List<Pair<String, String>>? = null


    /**
     * The default local disk persistent cache size is [ZVideoPlayer.DEFAULT_VIDEO_MAX_CACHED_SIZE]. If you need to reset this value, please use this method.
     * */
    fun updateMaxCacheSize(maxCacheSize: Long): VideoConfig {
        this.maxCacheSize = maxCacheSize
        return this
    }

    /**
     * Set the file address of the local disk persistent cache. The default is [ZVideoPlayer.DEFAULT_VIDEO_CACHED_PATH]
     * If the address changes when the new version is upgraded, the user cannot read the previous cache, and the previous cache will not be cleared.
     * */
    fun setCacheFileDir(cacheFileDir: String): VideoConfig {
        this.cacheFileDir = cacheFileDir
        return this
    }

    /**
     * Set whether to support cache
     * */
    fun setCacheEnable(cacheEnable: Boolean): VideoConfig {
        this.cacheEnable = cacheEnable
        return this
    }

    /**
     * the renderer codec type , see [ScalingMode]
     * */
    fun videoScaleMod(@ScalingMode mod: Int): VideoConfig {
        this.videoScaleMod = mod
        return this
    }

    /**
     * Set the parameters configured when the far end requests video
     * */
    fun setRequestProperty(vararg requestProperty: Pair<String, String>?): VideoConfig {
        this.requestProperty = requestProperty.filterNotNull()
        return this
    }

    /**
     * if debug able , the all of crashes will throw in runtime .
     * */
    fun setDebugAble(debugAble: Boolean): VideoConfig {
        ZPlayerLogs.debugAble = debugAble
        return this
    }

    /**
     * @param minBufferMs see in #[DEFAULT_MIN_BUFFER_MS]
     * @param maxBufferMs see in #[DEFAULT_MAX_BUFFER_MS]
     * @param bufferForPlaybackMs see in #[DEFAULT_BUFFER_FOR_PLAYBACK_MS]
     * @param bufferForPlaybackAfterBufferMs see in #[DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_RE_BUFFER_MS]
     * */
    fun updateVideoLoadRule(minBufferMs: Int, maxBufferMs: Int, bufferForPlaybackMs: Int, bufferForPlaybackAfterBufferMs: Int): VideoConfig {
        this.minBufferMs = minBufferMs
        this.maxBufferMs = maxBufferMs
        this.bufferForPlaybackMs = bufferForPlaybackMs
        this.bufferForPlaybackAfterBufferMs = bufferForPlaybackAfterBufferMs
        return this
    }
}