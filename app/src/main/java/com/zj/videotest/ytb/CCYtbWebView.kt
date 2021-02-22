package com.zj.videotest.ytb

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import com.zj.webkit.CCWebView
import com.zj.youtube.proctol.YouTubePlayerListener

@SuppressLint("ViewConstructor")
class CCYtbWebView(context: Context, private val ytbWebBridge: YtbWebBridge) : CCWebView<YtbWebBridge>(context) {

    private val isBackgroundPlaybackEnabled = false
    override val javaScriptClient: YtbWebBridge; get() = ytbWebBridge
    override val webDebugEnable: Boolean; get() = true

    override fun getDefaultVideoPoster(): Bitmap? {
        val result = super.getDefaultVideoPoster()
        return result ?: Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (isBackgroundPlaybackEnabled && (visibility == View.GONE || visibility == View.INVISIBLE)) return
        super.onWindowVisibilityChanged(visibility)
    }
}