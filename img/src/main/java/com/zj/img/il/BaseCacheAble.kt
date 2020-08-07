package com.zj.img.il

import com.zj.img.cache.CacheAble

internal data class BaseCacheAble(val path: String, val oriPath: String) : CacheAble {

    override fun getCacheName(payloads: String?): String {
        return path
    }

    override fun getOriginalPath(payloads: String?): String? {
        return oriPath
    }

}