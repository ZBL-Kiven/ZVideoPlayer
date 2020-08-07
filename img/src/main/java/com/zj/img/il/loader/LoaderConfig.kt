package com.zj.img.il.loader

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.TransitionOptions
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import jp.wasabeef.glide.transformations.BlurTransformation


@Suppress("unused", "CheckResult")
class LoaderConfig<T> internal constructor(private val glide: RequestBuilder<Drawable>) {

    companion object {
        internal fun <T> load(rm: RequestManager?, p: T?): LoaderConfig<Drawable>? {
            return rm?.let {
                LoaderConfig(when (p) {
                    is Bitmap -> rm.load(p)
                    is Drawable -> rm.load(p)
                    is Uri -> rm.load(p)
                    is String -> rm.load(p)
                    is Int -> rm.load(p)
                    is ByteArray -> rm.load(p)
                    else -> rm.load(p)
                })
            }
        }
    }

    fun thumbnail(sizeMultiplier: Float): LoaderConfig<T> {
        glide.thumbnail(sizeMultiplier);return this
    }

    fun error(id: Int): LoaderConfig<T> {
        glide.error(id);return this
    }

    fun error(drawable: Drawable): LoaderConfig<T> {
        glide.error(drawable);return this
    }

    fun override(w: Int, h: Int): LoaderConfig<T> {
        glide.override(w, h);return this
    }

    fun override(size: Int): LoaderConfig<T> {
        glide.override(size);return this
    }

    fun blur(radius: Int): LoaderConfig<T> {
        glide.transform(BlurTransformation(radius));return this
    }

    fun centerCrop(): LoaderConfig<T> {
        glide.centerCrop();return this
    }

    fun placeHolder(@DrawableRes id: Int) {
        glide.apply(RequestOptions().placeholder(id))
    }

    fun crossFade(): LoaderConfig<T> {
        val drawableCrossFadeFactory = DrawableCrossFadeFactory.Builder(300).setCrossFadeEnabled(true).build()
        val transOp: TransitionOptions<DrawableTransitionOptions, in Drawable> = DrawableTransitionOptions.with(drawableCrossFadeFactory)
        glide.transition(transOp)
        return this
    }

    fun memoryEnable(enable: Boolean): LoaderConfig<T> {
        glide.skipMemoryCache(enable)
        return this
    }

    fun fitCenter(): LoaderConfig<T> {
        glide.fitCenter();return this
    }

    fun centerInside(): LoaderConfig<T> {
        glide.centerInside();return this
    }

    fun circle(): LoaderConfig<T> {
        glide.transform(CircleCrop());return this
    }

    fun into(iv: ImageView?) {
        glide.diskCacheStrategy(DiskCacheStrategy.NONE).into(iv ?: return)
    }
}