package com.zj.img

import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.MainThread
import com.zj.img.adapters.LoadAdapter
import com.zj.img.cache.ImageCacheUtil
import com.zj.img.loader.CompletedListener
import java.lang.IllegalArgumentException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.min

class ImgLoader<T : Any> private constructor(private val tag: T?, vararg ta: LoadAdapter<T>) {

    enum class ImgType {
        IMG, GIF
    }

    companion object : ComponentCallbacks {
        private val loadCachePool = ConcurrentHashMap<Any, ImageCacheUtil<*>>()
        private val completedListeners = ConcurrentHashMap<Any, CompletedListener<*>>()
        private val threadPool = Executors.newFixedThreadPool(5)

        private var cacheDir: String = ""

        fun <T : Any> with(tag: T): ImgLoader<T> {
            return ImgLoader(tag)
        }

        fun <T : Any> with(tag: T, vararg adapter: LoadAdapter<T>): ImgLoader<T> {
            return ImgLoader(tag, *adapter)
        }

        fun autoTrimMemory(context: Context) {
            (context.applicationContext as? Application)?.registerComponentCallbacks(this) ?: throw IllegalArgumentException("the context type with contextWrapper is unsupported!")
        }

        fun cancel(tag: Any) {
            loadCachePool[tag]?.cancel()
        }

        fun proportionalWH(originWidth: Int, originHeight: Int, maxWidth: Int, maxHeight: Int, minScale: Float): Array<Int> {
            val minWidth = (maxWidth * minScale).toInt()
            val minHeight = (maxHeight * minScale).toInt()
            var width: Int = originWidth
            var height: Int = originHeight

            if (width > maxWidth) {
                val maxWOffset = width * 1.0f / maxWidth
                width = maxWidth
                height = (height / maxWOffset).toInt()
            }
            if (height > maxHeight) {
                val maxHOffset = height * 1.0f / maxHeight
                height = maxHeight
                width = (width / maxHOffset).toInt()
            }
            if (width < minWidth) {
                val minWOffset = width * 1.0f / minWidth
                width = minWidth
                height = (height / minWOffset).toInt()
            }
            if (height < minHeight) {
                val minHOffset = height * 1.0f / minHeight
                height = minHeight
                width = (width / minHOffset).toInt()
            }
            return arrayOf(min(maxWidth, width), min(maxHeight, height))
        }

        override fun onLowMemory() {
            loadCachePool.forEach { (_, v) ->
                v.trimMemory()
            }
        }

        override fun onConfigurationChanged(newConfig: Configuration) {
            loadCachePool.forEach { (_, v) ->
                v.cancel()
            }
        }
    }

    private var listener: CompletedListener<T>? = null

    fun override(w: Int, h: Int) {

    }

    fun limitSize(w: Int, h: Int, minScale: Float) {

    }

    fun quality(quality: Float) {

    }

    fun asType(type: ImgType) {

    }

    fun load(path: String) {

    }

    @MainThread
    private fun calculateSize(w: Int, h: Int, maxWidth: Int, maxHeight: Int, minScale: Float): Pair<Boolean, Array<Int>> {
        if (w <= 0 || h <= 0) {
            Log.e("----- in ImageLoader , ", "Error print: the image size should not be 0 or negative")
        }
        val size = proportionalWH(w, h, maxWidth, maxHeight, minScale)
        val isCenterCrop = size[0] >= maxWidth || size[1] >= maxHeight
        return Pair(isCenterCrop, size)
    }


    // val p = calculateSize(w, h, (maxWidth * quality).toInt(), (maxHeight * quality).toInt(), minScale)
    //        val fillType = if (p.first) {
    //            ImageCacheUtil.CENTER_CROP
    //        } else {
    //            ImageCacheUtil.FIT_CENTER
    //        }
    //
    //        fun <M : BaseRequestOptions<M>> getFillType(t: M): BaseRequestOptions<M> {
    //            if (imgType == ImageLoader.Companion.ImgType.IMG) {
    //                t.format(DecodeFormat.PREFER_RGB_565)
    //            }
    //            return when (fillType) {
    //                ImageCacheUtil.FIT_CENTER -> t.fitCenter()
    //                ImageCacheUtil.CENTER_CROP -> t.centerCrop()
    //                ImageCacheUtil.CIRCLE -> t.circleCrop()
    //                ImageCacheUtil.CENTER_INSIDE -> t.centerInside()
    //                else -> t
    //            }
    //        }
    //
    //        val utl = ImageCacheUtil<T>(it, tag, BaseGlideLoader(it), p.second[0], p.second[1], maxWidth, maxHeight, quality, data, fillType, payloads)
    //        if (tag != null) ImageLoader.loadCachePool[tag.hashCode()] = utl
    //        utl.load { s, o, t ->
    //            onLoaded(s, o, t)
    //        }
    //        when (imgType) {
    //            //            ImageLoader.Companion.ImgType.IMG -> getFillType(Glide.with(thumb).asBitmap().load(path)).override(p.second[0], p.second[1]).thumbnail(quality).into(object : CustomTarget<Bitmap>() {
    //            //                override fun onLoadCleared(placeholder: Drawable?) {
    //            //
    //            //                }
    //            //
    //            //                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
    //            //
    //            //                }
    //            //            })
    //            //            ImageLoader.Companion.ImgType.GIF -> getFillType(Glide.with(thumb).asGif().load(path)).override(p.second[0], p.second[1]).thumbnail(quality).into(object : CustomTarget<GifDrawable>() {
    //            //                override fun onLoadCleared(placeholder: Drawable?) {
    //            //
    //            //                }
    //            //
    //            //                override fun onResourceReady(resource: GifDrawable, transition: Transition<in GifDrawable>?) {
    //            //
    //            //                }
    //            //            })
    //        }

}