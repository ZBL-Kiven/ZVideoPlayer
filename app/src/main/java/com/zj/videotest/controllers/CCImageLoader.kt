package com.zj.videotest.controllers

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.request.BaseRequestOptions
import com.zj.player.img.ImgLoader
import com.zj.player.img.cache.ImageHandler
import com.zj.player.img.loader.FillType
import java.io.File
import java.util.concurrent.Future

class CCImageLoader<T : Any> : ImageHandler<T>() {

    private var future: Future<File?>? = null

    override fun load(context: Context, type: ImgLoader.ImgType, path: String, w: Int, h: Int, fillType: FillType, tag: T, onResult: (path: String?, tag: T, type: ImgLoader.ImgType, Exception?) -> Unit) {
        fun <T : BaseRequestOptions<T>> getFillType(t: T): BaseRequestOptions<T> {
            return when (fillType) {
                FillType.FIT_CENTER -> t.fitCenter()
                FillType.CENTER_CROP -> t.centerCrop()
                else -> t
            }
        }

        var ex: java.lang.Exception? = null
        var f: File? = null
        try {
            future = getFillType(Glide.with(context).asFile().load(path)).override(w, h).skipMemoryCache(true).submit(w, h)
            f = future?.get()
        } catch (e: java.lang.Exception) {
            ex = e
        }
        onResult(f?.path, tag, type, ex)
    }

    override fun onCancel(context: Context) {
        if (future?.isDone == false) future?.cancel(true)
    }

    override fun onLowMemory(context: Context) {
        if (future?.isDone == false) onCancel(context)
        Glide.get(context).onLowMemory()
    }
}