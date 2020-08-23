package com.zj.img.cache

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Message
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal class ImageSaveTask<T>(private val tag: T?, private val bmp: Bitmap, private val cacheFolderPath: String, private val fileName: String, private val recyclerNeeded: Boolean, private var onGot: ((T?, String) -> Unit)? = null, private var transaction: ((Bitmap) -> Bitmap)? = null) : Runnable {

    companion object {
        private const val SAVED = 0xa3b7e
    }

    private var handler: Handler? = Handler(Looper.getMainLooper()) {
        if (it.what == SAVED) {
            onGot?.invoke(tag, it.obj.toString())
            clear()
        }
        return@Handler false
    }

    override fun run() {
        val path = putImg(fileName, bmp)
        handler?.sendMessage(Message.obtain().apply {
            what = SAVED
            obj = path
        })
    }

    private fun putImg(fileName: String, bmp: Bitmap): String {
        val f = File(cacheFolderPath)
        if (!f.exists()) {
            f.mkdirs()
        }
        if (!f.isDirectory) throw NoSuchFileException(f, null, "the cached folder was not found ,is your cache directory path was point to a file?")

        val file = File(f, fileName)
        if (!file.exists()) {
            try {
                file.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
                if (file.isDirectory) f.delete()
            }
        }
        val bitMap = transaction?.invoke(bmp) ?: bmp
        val fos = FileOutputStream(file)
        return try {
            bitMap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.close()
            fos.flush()
            file.path
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        } finally {
            if (recyclerNeeded && !bmp.isRecycled) {
                bmp.recycle()
            }
            if ((recyclerNeeded || bitMap != bmp) && !bitMap.isRecycled) {
                bitMap.recycle()
            }
        }
    }

    private fun clear() {
        try {
            if (recyclerNeeded && !bmp.isRecycled) bmp.recycle()
            handler?.removeCallbacksAndMessages(null)
            handler = null
            onGot = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}