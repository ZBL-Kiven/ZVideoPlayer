package com.zj.img.il.loader

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import java.lang.ref.WeakReference


@Suppress("unused")
class Loader internal constructor(private val path: Any?) {

    fun withAct(act: WeakReference<Activity>): LoaderConfig<Drawable>? {
        return LoaderConfig.load(Glide.with(act.get() ?: return null), path)
    }

    fun withFa(f: WeakReference<FragmentActivity>): LoaderConfig<Drawable>? {
        return LoaderConfig.load(Glide.with(f.get() ?: return null), path)
    }

    fun withIv(iv: WeakReference<ImageView>): LoaderConfig<Drawable>? {
        return LoaderConfig.load(Glide.with(iv.get() ?: return null), path)
    }

    fun withCtx(ctx: WeakReference<Context>): LoaderConfig<Drawable>? {
        return LoaderConfig.load(Glide.with(ctx.get() ?: return null), path)
    }
}
