package com.zj.img.loader

interface CompletedListener<T : Any> {

    fun onCompleted(tag: T)

    fun onCanceled(tag: T)

}