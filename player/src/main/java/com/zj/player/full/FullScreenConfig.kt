package com.zj.player.full

import android.content.Context
import android.view.View
import java.lang.ref.WeakReference

internal class FullScreenConfig internal constructor(private var controllerView: WeakReference<View?>?) {

    var contentLayout: Int = -1; private set
    var fullMaxScreenEnable: Boolean = false; private set
    var isDefaultMaxScreen: Boolean = false; private set
    var translateNavigation: Boolean = false; private set
    var defaultScreenOrientation: Int = -1; private set
    var onFullScreenListener: FullScreenListener? = null
    var onFullContentListener: FullContentListener? = null
    var transactionAnimDuration: Int = 250; private set
    var isAnimDurationOnlyStart: Boolean = true; private set
    var payloads: Map<String, Any?>? = null; private set
    private var defaultTransactionAnimDuration: Int = 250

    fun transactionAnimDuration(duration: Int, isStart: Boolean, default: Int): FullScreenConfig {
        this.transactionAnimDuration = duration
        this.isAnimDurationOnlyStart = isStart
        this.defaultTransactionAnimDuration = default
        return this
    }

    fun withFullContentScreen(contentLayout: Int, fullMaxScreenEnable: Boolean, fullContentListener: FullContentListener): FullScreenConfig {
        this.contentLayout = contentLayout
        this.fullMaxScreenEnable = fullMaxScreenEnable
        this.isDefaultMaxScreen = false
        this.onFullContentListener = fullContentListener
        return this
    }

    fun withFullMaxScreen(fullScreenListener: FullScreenListener): FullScreenConfig {
        this.contentLayout = -1
        this.fullMaxScreenEnable = false
        this.isDefaultMaxScreen = true
        this.onFullScreenListener = fullScreenListener
        return this
    }

    fun defaultOrientation(or: Int): FullScreenConfig {
        this.defaultScreenOrientation = or
        return this
    }

    fun transactionNavigation(translateNavigation: Boolean): FullScreenConfig {
        this.translateNavigation = translateNavigation
        return this
    }

    fun getControllerView(): View? {
        return controllerView?.get()
    }

    fun payLoads(payloads: Map<String, Any?>?): FullScreenConfig {
        this.payloads = payloads
        return this
    }

    fun start(context: Context): BaseGestureFullScreenDialog {
        return BaseGestureFullScreenDialog(context, this)
    }

    fun clear() {
        controllerView?.clear()
        controllerView = null
    }

    fun reSetDurationWithDefault() {
        this.transactionAnimDuration = defaultTransactionAnimDuration
    }
}