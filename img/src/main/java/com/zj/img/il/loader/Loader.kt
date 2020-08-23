package com.zj.img.il.loader

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import java.lang.ref.WeakReference


@Suppress("unused")
class Loader internal constructor(private val path: Any?) {

    fun withAct(act: WeakReference<Activity>): LoaderConfig? {
        return LoaderConfig.load(Glide.with(act.get() ?: return null), path)
    }

    fun withFa(f: WeakReference<FragmentActivity>): LoaderConfig? {
        return LoaderConfig.load(Glide.with(f.get() ?: return null), path)
    }

    fun withIv(iv: ImageView): LoaderConfig? {
        return LoaderConfig.load(Glide.with(iv), path)
    }

    fun withCtx(ctx: WeakReference<Context>): LoaderConfig? {
        return LoaderConfig.load(Glide.with(ctx.get() ?: return null), path)
    }
}
