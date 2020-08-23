package com.zj.img.il.loader

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.TransitionOptions
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import jp.wasabeef.glide.transformations.BlurTransformation


@Suppress("unused", "CheckResult")
class LoaderConfig internal constructor(private val glide: RequestBuilder<Drawable>) {

    companion object {
        internal fun <T> load(rm: RequestManager?, p: T?): LoaderConfig? {
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

    fun thumbnail(sizeMultiplier: Float): LoaderConfig {
        glide.thumbnail(sizeMultiplier);return this
    }

    fun error(id: Int): LoaderConfig {
        glide.error(id);return this
    }

    fun error(drawable: Drawable): LoaderConfig {
        glide.error(drawable);return this
    }

    fun override(w: Int, h: Int): LoaderConfig {
        glide.override(w, h);return this
    }

    fun override(size: Int): LoaderConfig {
        glide.override(size);return this
    }

    fun blur(radius: Int): LoaderConfig {
        glide.transform(BlurTransformation(radius));return this
    }

    fun centerCrop(): LoaderConfig {
        glide.centerCrop();return this
    }

    fun transform(trs: Transformation<Bitmap>): LoaderConfig {
        glide.transform(trs);return this
    }

    fun placeHolder(@DrawableRes id: Int) {
        glide.apply(RequestOptions().placeholder(id))
    }

    fun crossFade(): LoaderConfig {
        val drawableCrossFadeFactory = DrawableCrossFadeFactory.Builder(300).setCrossFadeEnabled(true).build()
        val transOp: TransitionOptions<DrawableTransitionOptions, in Drawable> = DrawableTransitionOptions.with(drawableCrossFadeFactory)
        glide.transition(transOp)
        return this
    }

    fun addListener(l: RequestListener<Drawable>) {
        glide.addListener(l)
    }

    fun memoryEnable(enable: Boolean): LoaderConfig {
        glide.skipMemoryCache(enable)
        return this
    }

    fun fitCenter(): LoaderConfig {
        glide.fitCenter();return this
    }

    fun centerInside(): LoaderConfig {
        glide.centerInside();return this
    }

    fun circle(): LoaderConfig {
        glide.transform(CircleCrop());return this
    }

    fun into(iv: ImageView?) {
        glide.diskCacheStrategy(DiskCacheStrategy.NONE).into(iv ?: return)
    }
}