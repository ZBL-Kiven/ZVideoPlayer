package com.zj.player.z

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Looper
import android.util.AttributeSet
import android.view.*
import androidx.annotation.Nullable
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.text.TextOutput
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.video.VideoListener
import com.zj.player.base.BaseRender
import com.zj.player.logs.BehaviorLogsTable
import com.zj.player.logs.ZPlayerLogs
import com.zj.player.ut.Constance.RESIZE_MODE_FIT
import com.zj.player.ut.Constance.SURFACE_TYPE_NONE
import com.zj.player.ut.Constance.SURFACE_TYPE_SURFACE_VIEW
import com.zj.player.ut.Constance.SURFACE_TYPE_TEXTURE_VIEW
import com.zj.player.ut.RenderEvent
import com.zj.player.ut.ResizeMode
import com.zj.player.ut.SurfaceType
import com.zj.player.view.AspectRatioFrameLayout

/**
 * @author ZJJ on 2020/6/22.
 *
 * based [AspectRatioFrameLayout] , automatically adapt the size of [Surface] rendered.
 * The methods that interact with [Player] include [VideoListener] and [Player.EventListener], and implement all the conversions required by the underlying playback by default.
 * It is not necessary to implement the [TextOutput] interface when barrage or other special effects are not required.
 * */

open class ZRender @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : BaseRender(context, attrs, defStyleAttr), VideoListener, View.OnLayoutChangeListener {

    private var videoSurfaceView: View? = null
    private var player: Player? = null
    private var textureViewRotation = 0
    private var renderEvent: RenderEvent? = null

    /**
     * it`s must to sure that called [setVideoFrame] first, else it can`t rendering any frames at surface
     * set [Player] to looper and binding the event listeners to media client
     * */
    fun setPlayer(@Nullable player: Player?, @SurfaceType surfaceType: Int = SURFACE_TYPE_SURFACE_VIEW, @ResizeMode resizeMode: Int = RESIZE_MODE_FIT) {
        if (this.player === player) return
        setVideoFrame(surfaceType, resizeMode)
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        Assertions.checkState(Looper.myLooper() == Looper.getMainLooper())
        Assertions.checkArgument(player == null || player.applicationLooper == Looper.getMainLooper())
        player?.let {
            val oldVideoComponent = it.videoComponent
            if (oldVideoComponent != null) {
                oldVideoComponent.removeVideoListener(this)
                videoSurfaceView?.let { v ->
                    when (v) {
                        is TextureView -> {
                            oldVideoComponent.clearVideoTextureView(v)
                        }
                        is SurfaceView -> {
                            oldVideoComponent.clearVideoSurfaceView(v)
                        }
                    }
                }
            }
        }
        this.player = player
        if (player != null) {
            val newVideoComponent = player.videoComponent
            if (newVideoComponent != null) videoSurfaceView?.let {
                when (it) {
                    is TextureView -> {
                        newVideoComponent.setVideoTextureView(it)
                    }
                    is SurfaceView -> {
                        newVideoComponent.setVideoSurfaceView(it)
                    }
                }
                newVideoComponent.addVideoListener(this)
            }
        }
    }

    fun setRenderListener(renderEvent: RenderEvent) {
        this.renderEvent = renderEvent
    }

    private fun setVideoFrame(@SurfaceType surfaceType: Int = SURFACE_TYPE_SURFACE_VIEW, @ResizeMode resizeMode: Int = RESIZE_MODE_FIT) {
        this.resizeMode = resizeMode
        if (surfaceType != SURFACE_TYPE_NONE) {
            val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            resetSurface()
            videoSurfaceView = when (surfaceType) {
                SURFACE_TYPE_TEXTURE_VIEW -> TextureView(context)
                else -> SurfaceView(context)
            }
            videoSurfaceView?.layoutParams = params
            addView(videoSurfaceView, 0)
        } else {
            videoSurfaceView = null
        }
        ZPlayerLogs.onLog("the renderer frame updated surfaceType = $surfaceType  ,resizeMode = $resizeMode", "", "", "Render", BehaviorLogsTable.onRenderSet(surfaceType, resizeMode))
    }

    final override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
        var videoAspectRatio: Float = if (height == 0 || width == 0) 1f else width * pixelWidthHeightRatio / height
        (videoSurfaceView as? TextureView)?.let {
            if (unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270) {
                videoAspectRatio = 1 / videoAspectRatio
            }
            if (textureViewRotation != 0) {
                it.removeOnLayoutChangeListener(this)
            }
            textureViewRotation = unappliedRotationDegrees
            if (textureViewRotation != 0) {
                it.addOnLayoutChangeListener(this)
            }
            applyTextureViewRotation(it, textureViewRotation)
        }
        onContentAspectRatioChanged(videoAspectRatio, videoSurfaceView)
    }

    final override fun onLayoutChange(view: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
        (view as? TextureView)?.let { applyTextureViewRotation(it, textureViewRotation) }
    }

    final override fun onRenderedFirstFrame() {
        renderEvent?.onRenderedFirstFrame()
    }

    /** Applies a texture rotation to a [TextureView].  */
    private fun applyTextureViewRotation(textureView: TextureView, textureViewRotation: Int) {
        val textureViewWidth = textureView.width.toFloat()
        val textureViewHeight = textureView.height.toFloat()
        if (textureViewWidth == 0f || textureViewHeight == 0f || textureViewRotation == 0) {
            textureView.setTransform(null)
        } else {
            val transformMatrix = Matrix()
            val pivotX = textureViewWidth / 2
            val pivotY = textureViewHeight / 2
            transformMatrix.postRotate(textureViewRotation.toFloat(), pivotX, pivotY)
            val originalTextureRect = RectF(0f, 0f, textureViewWidth, textureViewHeight)
            val rotatedTextureRect = RectF()
            transformMatrix.mapRect(rotatedTextureRect, originalTextureRect)
            transformMatrix.postScale(textureViewWidth / rotatedTextureRect.width(), textureViewHeight / rotatedTextureRect.height(), pivotX, pivotY)
            textureView.setTransform(transformMatrix)
        }
    }

    /**
     * @param contentAspectRatio The aspect ratio of the content.
     * @param contentView The view that holds the content being displayed, or `null`.
     */
    protected open fun onContentAspectRatioChanged(contentAspectRatio: Float, @Nullable contentView: View?) {
        setAspectRatio(contentAspectRatio)
    }

    private fun resetSurface() {
        videoSurfaceView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        videoSurfaceView = null
    }

    override fun reset() {}

    /**
     * don`t forgot call this after your video finished
     * */
    override fun release() {
        super.release()
        resetSurface()
        player = null
        renderEvent = null
    }
}
