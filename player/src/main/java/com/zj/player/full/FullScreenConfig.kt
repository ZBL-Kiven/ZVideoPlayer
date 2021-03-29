package com.zj.player.full

import android.content.Context
import android.view.View

internal class FullScreenConfig internal constructor(private var controllerView: View?) {

    var contentLayout: Int = -1; private set
    var fullMaxScreenEnable: Boolean = false; private set
    var isDefaultMaxScreen: Boolean = false; private set
    var translateNavigation: Boolean = false; private set
    var defaultScreenOrientation: Int = -1; private set
    var allowReversePortrait: Boolean = false; private set
    var onFullScreenListener: FullScreenListener? = null
    var onFullContentListener: FullContentListener? = null
    var transactionAnimDuration: Int = 250; private set
    var isAnimDurationOnlyStart: Boolean = true; private set
    var payloads: Map<String, Any?>? = null; private set
    private var defaultTransactionAnimDuration: Int = 250
    private var preToDismissAgree: ((lambda: () -> Unit) -> Unit)? = null
    private var preToFullMaxChangeAgree: ((lambda: () -> Unit) -> Unit)? = null

    fun preToDismiss(agree: () -> Unit) {
        preToDismissAgree?.invoke(agree) ?: agree.invoke()
    }

    fun preToFullMaxChange(agree: () -> Unit) {
        preToFullMaxChangeAgree?.invoke(agree) ?: agree.invoke()
    }

    fun transactionAnimDuration(duration: Int, isStart: Boolean, default: Int): FullScreenConfig {
        this.transactionAnimDuration = duration.coerceAtLeast(0)
        this.isAnimDurationOnlyStart = isStart
        this.defaultTransactionAnimDuration = default.coerceAtLeast(0)
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

    fun allowReversePortrait(allow: Boolean): FullScreenConfig {
        this.allowReversePortrait = allow
        return this
    }

    fun transactionNavigation(translateNavigation: Boolean): FullScreenConfig {
        this.translateNavigation = translateNavigation
        return this
    }

    fun getControllerView(): View? {
        return controllerView
    }

    fun setPreDismissInterceptor(l: ((lambda: () -> Unit) -> Unit)?): FullScreenConfig {
        this.preToDismissAgree = l
        return this
    }

    fun setPreFullMaxChangeInterceptor(l: ((lambda: () -> Unit) -> Unit)?): FullScreenConfig {
        this.preToFullMaxChangeAgree = l
        return this
    }

    fun payLoads(payloads: Map<String, Any?>?): FullScreenConfig {
        this.payloads = payloads
        return this
    }

    fun start(context: Context): ZPlayerFullScreenView {
        return ZPlayerFullScreenView(context, this)
    }

    fun clear() {
        this.controllerView = null
    }

    fun resetDurationWithDefault() {
        this.transactionAnimDuration = defaultTransactionAnimDuration
    }
}