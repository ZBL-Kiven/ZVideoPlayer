package com.zj.img

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.ImageView
import androidx.annotation.MainThread
import androidx.annotation.NonNull
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.BaseRequestOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.zj.img.cache.CacheAble
import com.zj.img.cache.ImageCacheUtil
import com.zj.img.cache.ImageSaveTask
import com.zj.img.il.loader.BaseGlideLoader
import com.zj.img.il.loader.Loader
import com.zj.img.ut.AutomationImageCalculateUtils
import jp.wasabeef.glide.transformations.internal.FastBlur
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.Executors

@Suppress("MemberVisibilityCanBePrivate", "unused")
class ImageLoader<T : Any?> private constructor(private val tag: T? = null) {

    companion object {

        enum class ImgType {
            IMG, GIF
        }

        private val loadCachePool = ConcurrentHashMap<Int, ImageCacheUtil<*>>()
        private val threadPool = Executors.newFixedThreadPool(5)

        private var cacheDir: String = ""

        fun <T : Any?> with(tag: T): ImageLoader<T> {
            return ImageLoader(tag)
        }

        @MainThread
        fun withLoad(path: Any?): Loader {
            return Loader(path)
        }

        fun clear(context: Context) {
            try {
                Glide.get(context).clearMemory()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun cancel(tag: Any?) {
            loadCachePool[tag.hashCode()]?.cancel()
        }

        fun cancelAll() {
            loadCachePool.values.forEach {
                it.cancel()
            }
        }
    }

    @MainThread
    fun download(wfCtx: WeakReference<Context>, w: Int, h: Int, maxWidth: Int, maxHeight: Int, minScale: Float, quality: Float, data: CacheAble, payloads: String? = null, onLoaded: (path: String, origin: String, tag: T?) -> Unit) {
        val p = calculateSize(w, h, maxWidth, maxHeight, minScale)
        val fillType = if (p.first) {
            ImageCacheUtil.CENTER_CROP
        } else {
            ImageCacheUtil.FIT_CENTER
        }
//        wfCtx.get()?.let {
//            val utl = ImageCacheUtil<T>(it, tag, BaseGlideLoader(it), p.second[0], p.second[1], maxWidth, maxHeight, quality, data, fillType, payloads)
//            if (tag != null) loadCachePool[tag.hashCode()] = utl
//            utl.load { s, o, t ->
//                onLoaded(s, o, t)
//            }
//        }
    }

    @MainThread
    private fun calculateSize(w: Int, h: Int, maxWidth: Int, maxHeight: Int, minScale: Float): Pair<Boolean, Array<Int>> {
        if (w <= 0 || h <= 0) {
            Log.e("----- in ImageLoader , ", "Error print: the image size should not be 0 or negative")
        }
        val size = AutomationImageCalculateUtils.proportionalWH(w, h, maxWidth, maxHeight, minScale)
        val isCenterCrop = size[0] >= maxWidth || size[1] >= maxHeight
        return Pair(isCenterCrop, size)
    }

    @MainThread
    fun loadWithListener(path: String, w: Int, h: Int, maxWidth: Int, maxHeight: Int, minScale: Float, quality: Float, @NonNull thumb: ImageView, imgType: ImgType = ImgType.IMG, onLoad: (isSuccess: Boolean, tag: T?, res: Bitmap?) -> Unit) {
        val p = calculateSize(w, h, maxWidth, maxHeight, minScale)
        val fillType = if (p.first) {
            ImageCacheUtil.CENTER_CROP
        } else {
            ImageCacheUtil.FIT_CENTER
        }

        fun <M : BaseRequestOptions<M>> getFillType(t: M): BaseRequestOptions<M> {
            if (imgType == ImgType.IMG) {
                t.format(DecodeFormat.PREFER_RGB_565)
            }
            return when (fillType) {
                ImageCacheUtil.FIT_CENTER -> t.fitCenter()
                ImageCacheUtil.CENTER_CROP -> t.centerCrop()
                ImageCacheUtil.CIRCLE -> t.circleCrop()
                ImageCacheUtil.CENTER_INSIDE -> t.centerInside()
                else -> t
            }
        }

        val lw = (p.second[0] * quality + 0.5f).toInt()
        val lh = (p.second[1] * quality + 0.5f).toInt()
        when (imgType) {
            ImgType.IMG -> getFillType(Glide.with(thumb).asBitmap().load(path)).override(lw, lh).thumbnail(quality).into(object : CustomTarget<Bitmap>() {
                override fun onLoadCleared(placeholder: Drawable?) {
                    thumb.setImageDrawable(null)
                    onLoad(false, tag, null)
                }

                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    thumb.setImageBitmap(resource)
                    onLoad(true, tag, resource)
                }
            })
            ImgType.GIF -> getFillType(Glide.with(thumb).asGif().load(path)).override(lw, lh).thumbnail(quality).into(object : CustomTarget<GifDrawable>() {
                override fun onLoadCleared(placeholder: Drawable?) {
                    thumb.setImageBitmap(null)
                    onLoad(false, tag, null)
                }

                override fun onResourceReady(resource: GifDrawable, transition: Transition<in GifDrawable>?) {
                    thumb.setImageDrawable(resource)
                    if (!resource.isRunning) resource.start()
                    onLoad(true, tag, resource.firstFrame)
                }
            })
        }
    }

    @MainThread
    fun simpleLoad(v: ImageView, path: String, w: Int, h: Int) {
        Glide.with(v).load(path).override(w, h).into(v)
    }

    @MainThread
    fun simpleLoad(context: Context, v: ImageView, path: String, w: Int, h: Int) {
        Glide.with(context).load(path).override(w, h).into(v)
    }

    @MainThread
    fun simpleLoadCircle(v: ImageView, path: String, w: Int, h: Int) {
        val options = RequestOptions().centerCrop().transform(CircleCrop())
        Glide.with(v).load(path).override(w, h).apply(options).into(v)
    }

    @MainThread
    fun simpleLoadCircle(context: Context, v: ImageView, path: String, w: Int, h: Int) {
        val options = RequestOptions().centerCrop().transform(CircleCrop())
        Glide.with(context).load(path).override(w, h).apply(options).into(v)
    }

    private val saveDirectory = ConcurrentSkipListSet<T>()
    private var onSavedListener: ((tag: T?, path: String) -> Unit)? = null

    @MainThread
    fun saveBlurMirrorIfNotExits(dir: String, fileName: String, bitmap: Bitmap, recyclerNeeded: Boolean, onSaved: (tag: T?, path: String) -> Unit, vararg otherParam: String) {
        this.onSavedListener = onSaved
        val cachedFolder = File(dir)
        val fb = StringBuilder().append(fileName)
        otherParam.forEach {
            fb.append("_").append(it)
        }
        fb.append(".jpg")
        val fn = fb.toString()
        val f = File(cachedFolder, fn)
        if (f.exists() && !f.isDirectory && f.length() > 0) {
            onSavedListener?.invoke(tag, f.path)
        } else {
            if (saveDirectory.contains(tag)) return
            saveDirectory.add(tag)
            if (f.exists()) f.delete()
            threadPool.execute(ImageSaveTask(tag, bitmap, dir, fn, recyclerNeeded, { t, s ->
                saveDirectory.remove(t)
                onSavedListener?.invoke(t, s)
            }, {
                FastBlur.blur(it, 90, false)
            }))
        }
    }
}