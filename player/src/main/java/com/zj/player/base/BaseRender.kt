package com.zj.player.base

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.CallSuper
import com.zj.player.ut.PlayerEventController
import com.zj.player.view.AspectRatioFrameLayout

abstract class BaseRender @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AspectRatioFrameLayout(context, attrs, defStyleAttr) {

    private var controller: PlayerEventController<*>? = null

    companion object {
        fun <T : BaseRender> create(ctx: Context, cls: Class<T>): T {
            val m = cls.getConstructor(Context::class.java)
            return m.newInstance(ctx)
        }
    }

    internal fun init(controller: PlayerEventController<*>) {
        this.controller = controller
    }

    protected fun notifyTo(func: PlayerEventController<*>.() -> Unit) {
        this.controller?.let { func.invoke(it) }
    }

    @CallSuper
    open fun release() {
        controller = null
    }
}