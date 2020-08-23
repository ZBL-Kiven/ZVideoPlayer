package com.zj.img.loader

import com.zj.img.adapters.LoadAdapter

internal abstract class Loader<T : Any> internal constructor(tag: T,vararg adapter: LoadAdapter<T>) {


    abstract fun load()



}