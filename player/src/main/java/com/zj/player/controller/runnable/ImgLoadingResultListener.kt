package com.zj.player.controller.runnable

import com.zj.player.img.ImgLoader

interface ImgLoadingResultListener<T : Any> {
    fun invoke(path: String?, type: ImgLoader.ImgType, tag: T, e: Exception?)
}