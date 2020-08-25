package com.zj.player.controller

import android.content.Context
import android.util.AttributeSet
import com.zj.player.BaseVideoController
import com.zj.player.img.ImgLoader
import com.zj.player.img.cache.ImageHandler
import java.lang.ref.WeakReference

abstract class BackgroundVideoController @JvmOverloads constructor(c: Context, attr: AttributeSet? = null, def: Int = 0) : BaseVideoController(c, attr, def) {

    init {
        ImgLoader.autoTrimMemory(c)
    }

    private var path: String = ""
    private var tag: String? = null
    private var isLoading = false

    abstract fun onImgGot(path: String, type: ImgLoader.ImgType, tag: String, e: Exception?)

    open fun getErrorPlaceholder(): Int {
        return 0
    }

    fun <L : ImageHandler<String>> loadBackground(tag: String, p: String, imageWidth: Int, imageHeight: Int, type: ImgLoader.ImgType, loader: L) {
        if (tag == this.tag) {
            return
        } else {
            this.tag?.let { if (isLoading) cancel() }
            this.tag = tag
        }
        isLoading = true
        post {
            val w = this.width
            val h = this.height
            if (w <= 0 || h <= 0) return@post
            val ctx = WeakReference(context)
            ImgLoader.with(tag).load(p).asType(type).override(imageWidth, imageHeight).limitSize(w, h, 0.5f).quality(0.5f).start(ctx, loader) { filePath, type, tag, e ->
                if (tag == this.tag) {
                    try {
                        if (filePath.isNullOrEmpty()) {
                            val thumb = getThumbView()
                            val bg = getBackgroundView()
                            val ph = getErrorPlaceholder()
                            if (ph == 0) {
                                thumb?.setImageBitmap(null)
                                bg?.setImageBitmap(null)
                            } else {
                                bg?.setImageResource(ph)
                            }
                        }
                        val fp = filePath ?: ""
                        onImgGot(fp, type, tag, e)
                    } finally {
                        isLoading = false
                    }
                }
            }
        }
    }

    private fun cancel() {
        ImgLoader.cancel(tag ?: return)
        getThumbView()?.setImageBitmap(null)
        getThumbView()?.setImageDrawable(null)
        getBackgroundView()?.setImageBitmap(null)
    }
}