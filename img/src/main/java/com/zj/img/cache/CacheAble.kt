package com.zj.img.cache

@Suppress("unused")
interface CacheAble {

    /**
     * the cache folder name
     * */
    fun getCacheName(payloads: String?): String

    /**
     * the data original path or url
     * */
    fun getOriginalPath(payloads: String?): String?

}