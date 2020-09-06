package com.zj.videotest.controllers

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.zj.loading.BaseLoadingView
import com.zj.loading.DisplayMode
import com.zj.player.base.LoadingMode
import com.zj.player.controller.BaseListVideoController
import com.zj.player.img.ImgLoader
import com.zj.videotest.R

class CCVideoController @JvmOverloads constructor(c: Context, attr: AttributeSet? = null, def: Int = 0) : BaseListVideoController(c, attr, def) {

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

    override fun completing(path: String, isRegulate: Boolean) {
        isInterruptPlayBtnAnim = true
        showOrHidePlayBtn(false, withState = false)
        full(false)
    }

    override fun loadingEventDispatch(view: View, loadingMode: LoadingMode, isSetInNow: Boolean) {
        super.loadingEventDispatch(view, loadingMode, isSetInNow)
        view.post {
            var hintText = ""
            val refresh = context.getString(R.string.z_player_str_loading_video_error_tint)
            (view as? BaseLoadingView)?.let {
                val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
                it.layoutParams = lp
                val mod = when (loadingMode) {
                    LoadingMode.None -> DisplayMode.NORMAL
                    LoadingMode.Fail -> {
                        hintText = context.getString(R.string.z_player_str_loading_video_error);DisplayMode.NO_DATA
                    }
                    LoadingMode.Loading -> {
                        hintText = context.getString(R.string.z_player_str_loading_video_progress);DisplayMode.LOADING.delay(500)
                    }
                    else -> DisplayMode.NORMAL
                }

                it.setMode(mod, hintText, refresh, isSetInNow)
            }
        }
    }

}