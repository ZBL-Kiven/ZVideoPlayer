package com.zj.player.img

import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.annotation.FloatRange
import androidx.annotation.MainThread
import com.zj.player.img.cache.ImageCacheUtil
import com.zj.player.img.cache.ImageHandler
import com.zj.player.img.loader.FillType
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.NullPointerException
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

@Suppress("unused", "MemberVisibilityCanBePrivate")
class ImgLoader<T : Any> private constructor(private val path: String, private val tag: T) {

    enum class ImgType {
        IMG, GIF
    }

    data class LimitSize(val w: Int, val h: Int, val minScale: Float)

    class RequestOption<T : Any>(private val tag: T) {

        fun load(path: String): ImgLoader<T> {
            return ImgLoader(path, tag)
        }
    }

    internal companion object : ComponentCallbacks {

        private val loadCachePool = ConcurrentHashMap<Any, ImageCacheUtil<*>>()

        private var isTrimAuto = false

        fun <T : Any> with(tag: T): RequestOption<T> {
            return RequestOption(tag)
        }

        internal fun autoTrimMemory(context: Context) {
            if (isTrimAuto) return
            isTrimAuto = true
            (context.applicationContext as? Application)?.registerComponentCallbacks(this) ?: throw IllegalArgumentException("the context type with contextWrapper is unsupported!")
        }

        fun cancel(tag: Any) {
            loadCachePool.remove(tag)?.cancel()
        }

        fun clear() {
            loadCachePool.forEach { (_, v) ->
                v.cancel()
            }
        }

        override fun onLowMemory() {
            loadCachePool.forEach { (_, v) ->
                v.onLowMemory()
            }
        }

        override fun onConfigurationChanged(newConfig: Configuration) {
            clear()
        }
    }

    private var limit: LimitSize? = null
    private var overrideSize: Pair<Int, Int>? = null
    private var type: ImgType = ImgType.IMG
    private var quality: Float = 1.0f
    private var fillType = FillType.DEFAULT

    fun override(w: Int, h: Int): ImgLoader<T> {
        this.overrideSize = Pair(w, h);return this
    }

    fun scaleType(fillType: FillType): ImgLoader<T> {
        this.fillType = fillType;return this
    }

    fun limitSize(w: Int, h: Int, @FloatRange(from = 0.0, to = 1.0) minScale: Float): ImgLoader<T> {
        overrideSize?.let {
            if (minScale <= 0) throw IllegalArgumentException("MinScale As the ratio of the minimum upper limit, it must be a non-negative number!")
        } ?: throw NullPointerException("you`d must set override before you set limit size")
        this.limit = LimitSize(w, h, minScale);return this
    }

    fun quality(@FloatRange(from = 0.0, to = 1.0) quality: Float): ImgLoader<T> {
        if (quality <= 0) throw IllegalArgumentException("Quality must be a non-negative and in 0.0f to 1.0f number!")
        this.quality = quality;return this
    }

    fun asType(type: ImgType): ImgLoader<T> {
        this.type = type;return this
    }

    fun <R : ImageHandler<T>> start(wkc: WeakReference<Context>, loader: R, payloads: String? = null, onResult: (filePath: String?, type: ImgType, tag: T, e: Exception?) -> Unit) {
        val w = overrideSize?.first ?: 0
        val h = overrideSize?.second ?: 0
        val mw = limit?.w ?: 0
        val mh = limit?.h ?: 0
        val scale = limit?.minScale ?: 0f

        var width = w
        var height = h
        var fillType = fillType

        if (w > 0 && h > 0 && limit != null) {
            val p = calculateSize(w, h, mw, mh, scale)
            fillType = if (p.first) {
                FillType.CENTER_CROP
            } else {
                FillType.FIT_CENTER
            }
            width = p.second[0]
            height = p.second[1]
        }
        val utl = ImageCacheUtil(wkc, tag, loader, width, height, path, fillType, payloads)
        loadCachePool[tag] = utl
        utl.load(type) { p, t, tag, e ->
            onResult(p, t, tag, e)
            loadCachePool.remove(tag)
        }
    }

    @MainThread
    private fun calculateSize(w: Int, h: Int, maxWidth: Int, maxHeight: Int, minScale: Float): Pair<Boolean, Array<Int>> {
        if (w <= 0 || h <= 0) {
            Log.e("----- , ", "in ImgLoader.calculateSize : Error print: the image size should not be 0 or negative")
        }
        val size = proportionalWH(w, h, maxWidth, maxHeight, minScale)
        val isCenterCrop = size[0] >= maxWidth || size[1] >= maxHeight
        return Pair(isCenterCrop, size)
    }

    private fun proportionalWH(originWidth: Int, originHeight: Int, maxWidth: Int, maxHeight: Int, minScale: Float): Array<Int> {
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
}