package com.zj.videotest.controllers

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.zj.loading.BaseLoadingView
import com.zj.loading.DisplayMode
import com.zj.player.base.LoadingMode
import com.zj.player.controller.BaseListVideoController
import com.zj.player.img.ImgLoader
import com.zj.videotest.R
import java.lang.ref.WeakReference

class CCVideoController @JvmOverloads constructor(c: Context, attr: AttributeSet? = null, def: Int = 0) : BaseListVideoController(c, attr, def) {

    val v = View(context)

    init {
        addViewWithZPoint("testTag", WeakReference(v), 100f, RelativeLayout.LayoutParams(200, 200))
    }

    override fun onImgGot(path: String, type: ImgLoader.ImgType, tag: String, e: Exception?) {
        val thumb = getThumbView() ?: return
        val background = getBackgroundView() ?: return
        when (type) {
            ImgLoader.ImgType.IMG -> {
                Glide.with(thumb).asBitmap().skipMemoryCache(true).fitCenter().load(path).into(thumb)
                Glide.with(background).asBitmap().load(path).centerCrop().thumbnail(0.35f).transform(RenderScriptBlur(background.context, 12)).into(background)
            }
            ImgLoader.ImgType.GIF -> {
                Glide.with(thumb).asGif().skipMemoryCache(true).fitCenter().load(path).into(thumb)
                Glide.with(background).asBitmap().load(path).centerCrop().thumbnail(0.35f).transform(RenderScriptBlur(background.context, 12)).into(background)
            }
        }
    }

    fun stopOrResumeGif(stop: Boolean) {
        val thumb = getThumbView() ?: return
        (thumb.drawable as? GifDrawable)?.let {
            if (stop) it.stop() else it.start()
        }
    }

    override fun onSeekChanged(seek: Int, buffered: Int, fromUser: Boolean, videoSize: Long) {
        super.onSeekChanged(seek, buffered, fromUser, videoSize)
        if (fromUser) Log.e("=-=-=", "$seek")
    }

    override fun completing(path: String, isRegulate: Boolean) {
        isInterruptPlayBtnAnim = true
        showOrHidePlayBtn(false, withState = false)
        full(false)
    }

    override fun reset(isNow: Boolean, isRegulate: Boolean, isShowPlayBtn: Boolean, isShowThumb: Boolean, isShowBackground: Boolean, isSinkBottomShader: Boolean) {
        super.reset(isNow, isRegulate, isShowPlayBtn, isShowThumb, isShowBackground, isSinkBottomShader)
        v.setBackgroundColor(Color.CYAN)
    }

    override fun onPlay(path: String, isRegulate: Boolean) {
        super.onPlay(path, isRegulate)
        v.setBackgroundColor(Color.YELLOW)
    }

    override fun onStop(path: String, isRegulate: Boolean) {
        super.onStop(path, isRegulate)
        Log.e("=====", "2222222   $path")
    }

    override fun onFullKeyEvent(code: Int, event: KeyEvent): Boolean {

        return true
    }
}