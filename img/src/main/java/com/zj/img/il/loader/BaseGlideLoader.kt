package com.zj.img.il.loader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.BaseRequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.zj.img.cache.CacheAble
import com.zj.img.cache.ImageCacheUtil.Companion.CENTER_CROP
import com.zj.img.cache.ImageCacheUtil.Companion.CENTER_INSIDE
import com.zj.img.cache.ImageCacheUtil.Companion.CIRCLE
import com.zj.img.cache.ImageCacheUtil.Companion.FIT_CENTER
import com.zj.img.cache.ImageHandler

@Suppress("unused")
class BaseGlideLoader<T>(private val context: Context?) : ImageHandler<T>() {

    private var onResult: ((Bitmap?) -> Unit)? = null
    private var maxWidth: Int = 0
    private var maxHeight: Int = 0

    private val target = object : CustomTarget<Bitmap>() {
        override fun onLoadCleared(placeholder: Drawable?) {
            onResult?.invoke(null)
        }

        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
            onResult?.invoke(if (resource.isRecycled) null else resource)
        }

        override fun onLoadFailed(errorDrawable: Drawable?) {
            onResult?.invoke(null)
        }

    }

    override fun getCacheDir(context: Context, cache: CacheAble, payloads: String?): String {
        return "${context.getExternalFilesDir(cache.getCacheName(payloads))?.path}"
    }

    override fun onCancel(context: Context) {
        onResult = null
        try {
            Glide.with(context).clear(target)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun load(context: Context, path: String, w: Int, h: Int, maxW: Int, maxH: Int, quality: Float, fillType: Int, onResult: (Bitmap?) -> Unit) {
        this@BaseGlideLoader.onResult = onResult
        this.maxWidth = w
        this.maxHeight = h
        fun <T : BaseRequestOptions<T>> getFillType(t: T): BaseRequestOptions<T> {
            return when (fillType) {
                FIT_CENTER -> t.fitCenter()
                CENTER_CROP -> t.centerCrop()
                CIRCLE -> t.circleCrop()
                CENTER_INSIDE -> t.centerInside()
                else -> t
            }
        }
        getFillType(Glide.with(context).asBitmap().load(path).override(w, h)).skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE).into(target)
    }
}