package com.zj.player.img.cache

import android.content.Context
import androidx.annotation.WorkerThread
import com.zj.player.img.ImgLoader
import com.zj.player.img.loader.FillType

abstract class ImageHandler<T : Any> : Runnable {

    private lateinit var context: Context
    private lateinit var type: ImgLoader.ImgType
    private lateinit var path: String
    private var w: Int = 0
    private var h: Int = 0
    private lateinit var fillType: FillType
    private lateinit var tag: T
    private lateinit var onResult: (path: String?, tag: T, type: ImgLoader.ImgType, Exception?) -> Unit


    internal fun initData(context: Context, type: ImgLoader.ImgType, path: String, w: Int, h: Int, fillType: FillType, tag: T, onResult: (path: String?, tag: T, type: ImgLoader.ImgType, Exception?) -> Unit) {
        this.context = context
        this.type = type
        this.path = path
        this.w = w
        this.h = h
        this.fillType = fillType
        this.tag = tag
        this.onResult = onResult
    }

    final override fun run() {
        load(context, type, path, w, h, fillType, tag, onResult)
    }

    @WorkerThread
    //running in work thread!
    abstract fun load(context: Context, type: ImgLoader.ImgType, path: String, w: Int, h: Int, fillType: FillType, tag: T, onResult: (path: String?, tag: T, type: ImgLoader.ImgType, Exception?) -> Unit)

    abstract fun onCancel(context: Context)

    abstract fun onLowMemory(context: Context)

}