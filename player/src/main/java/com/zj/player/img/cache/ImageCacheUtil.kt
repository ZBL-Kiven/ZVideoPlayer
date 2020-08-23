package com.zj.player.img.cache

import android.content.Context
import com.zj.player.img.ImgLoader
import com.zj.player.img.loader.FillType
import java.io.File
import java.lang.Exception
import java.lang.NullPointerException
import java.util.concurrent.Executors
import kotlin.IllegalArgumentException


/**
 * Created by ZJJ on 19/12/13
 *
 * Picture of any size multi-level buffer,
 *
 * By calling load () normally, the size after this resize is automatically reserved for the image,
 *
 * When loading original images of the same size, thumbnails will be automatically obtained,
 *
 * Avoid taking out the original image and recalculating the compression every time you load it
 *
 * Especially suitable for display the IM thumb image list and UserAvatar
 *
 * Support local pictures, resource files, network pictures
 * */
@Suppress("unused")
internal class ImageCacheUtil<T : Any>(private val context: Context, private val tag: T, private val imgHandler: ImageHandler<T>, private val w: Int, private val h: Int, private val path: String, private val fillType: FillType, private val payloads: String? = null) {

    companion object {
        private val imageSaverService = Executors.newFixedThreadPool(5)
    }

    private fun getPayloads(): String? {
        return payloads
    }

    fun cancel() {
        imgHandler.onCancel(context)
    }

    fun onLowMemory() {
        imgHandler.onLowMemory(context)
    }

    fun load(type: ImgLoader.ImgType, onGot: ((filePath: String?, type: ImgLoader.ImgType, tag: T, Exception?) -> Unit)) {
        val path = this.path
        if (w <= 0) throw IllegalArgumentException("the load width must not be a zero or negative number")
        if (h <= 0) throw IllegalArgumentException("the load height must not be a zero or negative number")
        if (path.isEmpty()) {
            onGot(path, type, tag, NullPointerException("the original path was null or empty!!"))
            return
        }
        imgHandler.initData(context, type, path, w, h, fillType, tag) { cachePath, tag, imgType, e ->
            onGot(cachePath, imgType, tag, e)
        }
        imageSaverService.execute(imgHandler)
    }
}