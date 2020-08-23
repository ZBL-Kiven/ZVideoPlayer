package com.zj.videotest.feed

import android.view.View

interface FeedAdapterInterface<T> {

    fun clap(d: T?, p: Int)

    fun avatarClicked(d: T?, p: Int)

    fun onShare(v: View, d: T?, p: Int)



}