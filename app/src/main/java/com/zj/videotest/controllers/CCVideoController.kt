package com.zj.videotest.controllers

import android.content.Context
import android.util.AttributeSet
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.zj.player.img.ImgLoader
import com.zj.videotest.controllers.scroller.ScrollerController
import jp.wasabeef.glide.transformations.BlurTransformation

class CCVideoController @JvmOverloads constructor(c: Context, attr: AttributeSet? = null, def: Int = 0) : ScrollerController(c, attr, def) {

    override fun onImgGot(path: String, type: ImgLoader.ImgType, tag: String, e: Exception?) {
        val transformation = if (path.startsWith("http")) RenderScriptBlur(context, 12) else BlurTransformation(12)
        val thumb = getThumbView() ?: return
        val background = getBackgroundView() ?: return
        val context = thumb.context
        when (type) {
            ImgLoader.ImgType.IMG -> {
                Glide.with(context).asBitmap().skipMemoryCache(true).fitCenter().load(path).into(thumb)
                Glide.with(context).asBitmap().load(path).centerCrop().thumbnail(0.15f).transform(transformation).into(background)
            }
            ImgLoader.ImgType.GIF -> {
                Glide.with(context).asGif().skipMemoryCache(true).fitCenter().load(path).into(thumb)
                Glide.with(context).asBitmap().load(path).centerCrop().thumbnail(0.15f).transform(transformation).into(background)
            }
        }
    }

    fun stopOrResumeGif(stop: Boolean) {
        val thumb = getThumbView() ?: return
        (thumb.drawable as? GifDrawable)?.let {
            if (stop) it.stop() else it.start()
        }
    }

    override fun completing(path: String, isRegulate: Boolean) {
        isInterruptPlayBtnAnim = true
        showOrHidePlayBtn(false, withState = false)
        full(false)
    }

    override fun onRootClick(x: Float, y: Float) {
        if (isFullScreen) super.onRootClick(x, y)
        else fullScreen(isFull = true, fromUser = true)
    }

    override fun onFullScreenChanged(isFull: Boolean, payloads: Map<String, Any?>?) {
        if (isFull) {
            setPlayIconEnable(2)
            setMuteIconEnable(if (super.isFull) 2 else 1)
        } else {
            setPlayIconEnable(1)
            setMuteIconEnable(3)
        }
        super.onFullScreenChanged(isFull, payloads)
        if (isFull) {
            if (isPlayable && !isBindingController) onPlayClick(true)
            else if (isPlayable && isBindingController) {
                if (controller?.isPlaying() == true || controller?.isStop(true) == true) return
                onPlayClick(true)
            }
        }
    }

    override fun resumeToolsWhenFullscreenChanged(): Boolean {
        return false
    }
}