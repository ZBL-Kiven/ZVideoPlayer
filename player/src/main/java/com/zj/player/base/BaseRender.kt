package com.zj.player.base

import android.content.Context
import android.util.AttributeSet
import com.zj.player.view.AspectRatioFrameLayout

abstract class BaseRender @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AspectRatioFrameLayout(context, attrs, defStyleAttr) {

    companion object {
        fun <T : BaseRender> create(ctx: Context, cls: Class<T>): T {
            val m = cls.getConstructor(Context::class.java)
            return m.newInstance(ctx)
        }
    }

    abstract fun release()
}