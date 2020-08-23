package com.zj.img.ut

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.zj.img.BuildConfig
import java.security.MessageDigest


class RenderScriptBlur(private val context: Context, private val radius: Int) : com.bumptech.glide.load.resource.bitmap.BitmapTransformation() {

    companion object {
        const val ID: String = BuildConfig.LIBRARY_PACKAGE_NAME.plus("RenderScriptBlur")
        val ID_BYTES = ID.toByteArray(Key.CHARSET)
    }

    override fun hashCode(): Int {
        return ID.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is RenderScriptBlur
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
    }

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap? {
        val renderScript = RenderScript.create(context)
        try {
            val inputBmp = Bitmap.createScaledBitmap(toTransform, outWidth / 8, outHeight / 8, false)
            val input = Allocation.createFromBitmap(renderScript, inputBmp)
            val output = Allocation.createTyped(renderScript, input.type)
            val scriptIntrinsicBlur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
            scriptIntrinsicBlur.setInput(input)
            scriptIntrinsicBlur.setRadius(radius.toFloat())
            scriptIntrinsicBlur.forEach(output)
            output.copyTo(inputBmp)
            return inputBmp
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return null
        } finally {
            renderScript.destroy()
        }
    }
}