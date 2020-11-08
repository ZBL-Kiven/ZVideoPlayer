package com.zj.videotest.controllers

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.RelativeLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.zj.player.controller.BaseListVideoController
import com.zj.player.full.TrackOrientation
import com.zj.player.img.ImgLoader
import com.zj.views.ut.DPUtils
import java.lang.ref.WeakReference

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

    override fun onSeekChanged(seek: Int, buffered: Int, fromUser: Boolean, videoSize: Long) {
        super.onSeekChanged(seek, buffered, fromUser, videoSize)
        if (fromUser) Log.e("=-=-=", "$seek")
    }

    override fun completing(path: String, isRegulate: Boolean) {
        isInterruptPlayBtnAnim = true
        showOrHidePlayBtn(false, withState = false)
        full(false)
    }


    private var lastHeight = 0
    private var scrolled = 0f
    private var parseCancel = false

    override fun onFullScreenChanged(isFull: Boolean) {
        super.onFullScreenChanged(isFull)
        scrolled = 0f
        lastHeight = 0
    }

    override fun onTouchActionEvent(videoRoot: View?, event: MotionEvent, lastX: Float, lastY: Float, orientation: TrackOrientation?): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (lastHeight == 0) lastHeight = videoRoot?.height ?: 0
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parseCancel = false
                return scrolled == 0f
            }
            MotionEvent.ACTION_MOVE -> {
                if (lastHeight == 0) return false
                return onMoving(videoRoot, event, lastY, orientation)
            }
        }
        return false
    }

    private fun onMoving(videoRoot: View?, event: MotionEvent, lastY: Float, orientation: TrackOrientation?): Boolean {
        Log.e("===== ", "$lastY     $orientation     $scrolled     $lastHeight")
        videoRoot?.let {
            return if (scrolled == 0.0f && orientation == TrackOrientation.TOP_BOTTOM) {
                parseCancel = true
                false
            } else if (parseCancel) {
                if (scrolled == 0f && orientation != TrackOrientation.TOP_BOTTOM) parseCancel = it.layoutParams.height < lastHeight
                return false
            } else {
                val step = lastY - event.rawY
                var lh = 0
                try {
                    val lp = it.layoutParams
                    lh = (lp.height - step.toInt()).coerceAtLeast(DPUtils.dp2px(246f)).coerceAtMost(lastHeight)
                    lp.height = lh
                    it.layoutParams = lp
                    true
                } finally {
                    if (lh == lastHeight) scrolled = 0f else scrolled += step
                }
            }
        }
        return false
    }
}