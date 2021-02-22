package com.zj.videotest.cusRender

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.zj.player.base.BaseRender
import com.zj.player.ut.Constance
import com.zj.player.ut.ResizeMode
import com.zj.webkit.CCWebView
import com.zj.webkit.proctol.WebJavaScriptIn

class CusWebRender(private var ctx: Context) : BaseRender(ctx), WebJavaScriptIn {

    override val name = ""

    private var webView: CCWebView<CusWebRender>? = null

    init {
        webView = object : CCWebView<CusWebRender>(ctx) {
            override val javaScriptClient: CusWebRender; get() = this@CusWebRender
            override val webDebugEnable: Boolean; get() = true
        }
        initWebView()
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true
    }

    private fun initWebView() {

    }

    fun setVideoFrame(@ResizeMode resizeMode: Int = Constance.RESIZE_MODE_FIT) {
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.removeAllViews()
            this.resizeMode = resizeMode
            val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            it.layoutParams = params
            addView(it, 0)
        }
    }

    fun setPlayer() {

    }

    override fun release() {
        webView?.clearHistory()
        webView?.clearFormData()
        webView?.removeAllViews()
        webView?.destroy()
    }

    fun isLoadData(): Boolean {
        return true
    }

    fun isLoading(accurate: Boolean): Boolean? {
        return false
    }

    fun isReady(accurate: Boolean): Boolean? {
        return false

    }

    fun isPlaying(accurate: Boolean): Boolean? {
        return true

    }

    fun isPause(accurate: Boolean): Boolean? {
        return false
    }

    fun isStop(accurate: Boolean): Boolean? {
        return false
    }

    fun isDestroyed(accurate: Boolean): Boolean? {
        return false
    }

    fun load(path: String?) {
        if (path.isNullOrEmpty()) {

        }
        webView?.loadUrl(path ?: "")
    }

    fun stop() {
        webView?.removeAllViews()
    }
}