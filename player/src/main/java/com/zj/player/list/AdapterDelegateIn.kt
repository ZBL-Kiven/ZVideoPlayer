package com.zj.player.list

import androidx.recyclerview.widget.RecyclerView

/**
 * @author ZJJ on 2020.6.16
 *
 * adapters delegate interface , override all of the necessary methods in your data adapter and call delegate by this .
 * used see [BaseListVideoController]
 * */
interface AdapterDelegateIn<T, VH : RecyclerView.ViewHolder> {
    /**
     * call in with the overridden form data adapter [RecyclerView.Adapter.onAttachedToRecyclerView]
     * */
    fun onAttachedToRecyclerView(recyclerView: RecyclerView)

    /**
     * call in with the overridden form data adapter [RecyclerView.Adapter.onDetachedFromRecyclerView]
     * */
    fun onDetachedFromRecyclerView(recyclerView: RecyclerView)

    /**
     * call in with the overridden form data adapter [RecyclerView.Adapter.onViewDetachedFromWindow]
     * */
    fun onViewDetachedFromWindow(holder: VH)

    /**
     * call in with the overridden form data adapter [RecyclerView.Adapter.bindViewHolder]
     * */
    fun bindData(holder: VH?, p: Int, d: T?, playAble: Boolean, pl: MutableList<Any>?)
}