package com.zj.videotest.feed.data

interface FeedDataIn {

    fun getAvatarPath(): String

    fun getNickname(): String

    fun getDesc(): String

    fun getImagePath(): String

    fun getVideoPath(): String

    fun getSourceId(): String

    fun getClapsCount(): Int

    fun getViewWidth(): Int

    fun getViewHeight(): Int

    fun getType(): DataType
}