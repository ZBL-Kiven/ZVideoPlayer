package com.zj.player.config

import com.zj.player.ZPlayer


/**
 * @author ZJJ on 2020.6.16
 *
 * The configurator of the video player. This configurator is only suitable for the loading of remote resources. It is not necessary to load local resources.
 * */
@Suppress("unused")
class VideoConfig private constructor() {

    companion object {
        fun create(): VideoConfig {
            return VideoConfig()
        }
    }

    internal var maxCacheSize: Long = ZPlayer.DEFAULT_VIDEO_MAX_CACHED_SIZE
    internal var cacheFileDir: String = ZPlayer.DEFAULT_VIDEO_CACHED_PATH
    internal var cacheEnable: Boolean = true
    internal var requestProperty: MutableMap<String, String>? = null

    /**
     * The default local disk persistent cache size is [ZPlayer.DEFAULT_VIDEO_MAX_CACHED_SIZE]. If you need to reset this value, please use this method.
     * */
    fun updateMaxCacheSize(maxCacheSize: Long): VideoConfig {
        this.maxCacheSize = maxCacheSize
        return this
    }

    /**
     * Set the file address of the local disk persistent cache. The default is [ZPlayer.DEFAULT_VIDEO_CACHED_PATH]
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
     * Set the parameters configured when the far end requests video
     * */
    fun setRequestProperty(requestProperty: MutableMap<String, String>?): VideoConfig {
        this.requestProperty = requestProperty
        return this
    }
}