package com.zj.videotest.feed

import com.zj.views.list.adapters.AnimationAdapter

abstract class ListenerAnimAdapter<T>(id: Int) : AnimationAdapter<T>(id) {

    abstract fun onDataChange(data: MutableList<T>?)

    override fun add(data: MutableList<T>?) {
        onDataChange(getData())
        super.add(data)
    }

    override fun add(data: MutableList<T>?, position: Int) {
        onDataChange(getData())
        super.add(data, position)
    }

    override fun add(info: T, position: Int) {
        onDataChange(data)
        super.add(info, position)
    }

    override fun add(info: T) {
        onDataChange(data)
        super.add(info)
    }

    override fun remove(info: T) {
        onDataChange(data)
        super.remove(info)
    }

    override fun remove(position: Int) {
        onDataChange(data)
        super.remove(position)
    }

    override fun clear() {
        onDataChange(null)
        super.clear()
    }

    override fun change(data: MutableList<T>?) {
        onDataChange(data)
        super.change(data)
    }
}