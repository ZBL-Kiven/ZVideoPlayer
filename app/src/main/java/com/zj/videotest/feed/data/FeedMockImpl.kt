package com.zj.videotest.feed.data

import java.util.*

class FeedMockImpl(private val videoPath: String, private val imgPath: String) : FeedDataIn {

    override fun getAvatarPath(): String {
        return "https://encrypted-tbn0.gstatic.com/images?q=tbn%3AANd9GcRc96kcLicYy25CFi7P_ocMargwSC_vjRxIMg&usqp=CAU"
    }

    override fun getNickname(): String {
        return "Test01"
    }

    override fun getDesc(): String {
        return "it`s a simple desc for mock impl"
    }

    override fun getImagePath(): String {
        return imgPath
    }

    override fun getVideoPath(): String {
        return videoPath
    }

    override fun getSourceId(): String {
        return UUID.randomUUID().toString()
    }

    override fun getClapsCount(): Int {
        return 9989
    }

    override fun getViewWidth(): Int {
        return 1080
    }

    override fun getViewHeight(): Int {
        return 768
    }
}