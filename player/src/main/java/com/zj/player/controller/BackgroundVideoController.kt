package com.zj.player.controller

import android.content.Context
import android.util.AttributeSet
import com.zj.player.BaseVideoController
import com.zj.player.img.ImgLoader
import com.zj.player.img.cache.ImageHandler

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
            this.tag?.let { if (isLoading) ImgLoader.cancel(it) }
            this.tag = tag
        }
        post {
            ImgLoader.with(tag).load(p).asType(type).override(imageWidth, imageHeight).limitSize(width, height, 0.5f).quality(0.5f).start(context, loader) { filePath, type, tag, e ->
                if (tag == this.tag) {
                    post {
                        val thumb = getThumbView()
                        val bg = getBackgroundView()
                        if (filePath.isNullOrEmpty()) {
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
                    }
                }
            }
        }
    }
}