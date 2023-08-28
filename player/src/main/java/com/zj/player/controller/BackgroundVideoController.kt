package com.zj.player.controller

import android.content.Context
import android.util.AttributeSet
import com.zj.player.R
import com.zj.player.z.ZVideoView
import com.zj.player.img.ImgLoader
import com.zj.player.img.cache.ImageHandler
import java.lang.ref.WeakReference

abstract class BackgroundVideoController @JvmOverloads constructor(c: Context, attr: AttributeSet? = null, def: Int = 0) : ZVideoView(c, attr, def) {

    init {
        ImgLoader.autoTrimMemory(c)
    }

    private var imgTag = R.id.tag_image_view_loader
    private var path: String = ""
    private var tag: String? = null
    private var lastTag: String? = null
    private var isLoading = false

    abstract fun onImgGot(path: String, type: ImgLoader.ImgType, tag: String, e: Exception?)

    open fun getErrorPlaceholder(): Int {
        return 0
    }

    open fun <L : ImageHandler<String>> loadBackground(tag: String, p: String, imageWidth: Int, imageHeight: Int, type: ImgLoader.ImgType, loader: L) {
        if (tag == this.getTag(imgTag)) {
            return
        } else {
            if (isLoading) cancel()
            this.lastTag = tag
            this.setTag(imgTag, lastTag)
        }
        isLoading = true
        post {
            if (this.getTag(imgTag) != lastTag) return@post
            val w = this.width
            val h = this.height
            if (w <= 0 || h <= 0) return@post
            val ctx = WeakReference(context)
            ImgLoader.with(this.getTag(imgTag).toString()).load(p).asType(type).override(imageWidth, imageHeight).limitSize(w, h, 0.5f).quality(0.5f).start(ctx, loader) { filePath, type, tag, e ->
                try {
                    if (this.getTag(imgTag) == tag) {
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
                    }
                } finally {
                    isLoading = false
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