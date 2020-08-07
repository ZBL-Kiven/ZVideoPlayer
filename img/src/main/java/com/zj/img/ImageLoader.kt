package com.zj.img

import android.content.Context
import android.util.Log
import com.zj.img.cache.CacheAble
import com.zj.img.cache.ImageCacheUtil
import com.zj.img.il.BaseCacheAble
import com.zj.img.ut.AutomationImageCalculateUtils
import com.zj.img.il.loader.BaseGlideLoader
import com.zj.img.il.loader.Loader

@Suppress("MemberVisibilityCanBePrivate", "unused")
object ImageLoader {

    private class ImageLoadUtil(context: Context, w: Int, h: Int, quality: Float, cache: CacheAble, fillType: Int, payloads: String? = null) : BaseGlideLoader(context, w, h, quality, cache, fillType, payloads) {
        private val cacheDir = "${context.getExternalFilesDir(getCache().getCacheName(getPayloads()))?.path}"

        override fun getCacheDir(context: Context): String {
            return cacheDir
        }
    }

    fun load(context: Context, w: Int, h: Int, maxWidth: Int, maxHeight: Int, minScale: Float, quality: Float, cacheDirectory: String, url: String, payloads: String, onLoaded: (loader: Loader) -> Unit) {
        load(context, w, h, maxWidth, maxHeight, minScale, quality, BaseCacheAble(cacheDirectory, url), payloads, onLoaded)
    }

    fun load(context: Context, w: Int, h: Int, maxWidth: Int, maxHeight: Int, minScale: Float, quality: Float, data: CacheAble, payloads: String, onLoaded: (loader: Loader) -> Unit) {
        Log.e("----- ","load start 00000   ${System.currentTimeMillis()}")
        if (w <= 0 || h <= 0) {
            Log.e("----- in ImageLoader , ", "Error print: the image size should not be 0 or negative")
        }
        val size = AutomationImageCalculateUtils.proportionalWH(w, h, maxWidth, maxHeight, minScale)
        val isCenterCrop = size[0] >= maxWidth || size[1] >= maxHeight
        val p = Pair(isCenterCrop, size)
        val fillType = if (p.first) {
            ImageCacheUtil.CENTER_CROP
        } else {
            ImageCacheUtil.FIT_CENTER
        }
        val width = p.second[0]
        val height = p.second[1]
        ImageLoadUtil(context, width, height, quality, data, fillType, payloads).load { path ->
            onLoaded(Loader(path))
        }
    }
}