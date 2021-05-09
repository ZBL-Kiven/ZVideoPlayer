package com.zj.videotest.controllers

import android.content.Context
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.request.BaseRequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.zj.player.img.ImgLoader
import com.zj.player.img.cache.ImageHandler
import com.zj.player.img.loader.FillType
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Future

class CCImageLoader<T : Any> : ImageHandler<T>() {

    private var future: Future<File?>? = null

    override fun load(context: WeakReference<Context>, type: ImgLoader.ImgType, path: String, w: Int, h: Int, fillType: FillType, tag: T, onResult: (path: String?, tag: T, type: ImgLoader.ImgType, Exception?) -> Unit) {
        fun <T : BaseRequestOptions<T>> getFillType(t: T): BaseRequestOptions<T> {
            return when (fillType) {
                FillType.FIT_CENTER -> t.fitCenter()
                FillType.CENTER_CROP -> t.centerCrop()
                else -> t
            }
        }

        var ex: java.lang.Exception? = null
        try {
            getFillType(Glide.with(context.get() ?: return).asFile().load(path)).override(w, h).skipMemoryCache(true).into(object : CustomTarget<File>() {
                override fun onLoadCleared(placeholder: Drawable?) {
                    onResult(null, tag, type, ex)
                }

                override fun onResourceReady(resource: File, transition: Transition<in File>?) {
                    onResult(resource.path, tag, type, ex)
                }
            })
        } catch (e: java.lang.Exception) {
            ex = e
        } finally {
            context.clear()
        }
    }

    override fun onCancel(context: Context) {
        if (future?.isDone == false) future?.cancel(true)
    }

    override fun onLowMemory(context: Context) {
        if (future?.isDone == false) onCancel(context)
        Glide.get(context).onLowMemory()
    }
}