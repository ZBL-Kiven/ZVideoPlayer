package com.zj.img.cache

import android.content.Context
import android.graphics.Bitmap

abstract class ImageHandler<T> {

    abstract fun load(context: Context, path: String, w: Int, h: Int, maxW: Int, maxH: Int, quality: Float, fillType: Int, onResult: (Bitmap?) -> Unit)

    abstract fun getCacheDir(context: Context, cache: CacheAble, payloads: String?): String

    abstract fun onCancel(context: Context)

    abstract fun onLowMemory(context: Context)

}