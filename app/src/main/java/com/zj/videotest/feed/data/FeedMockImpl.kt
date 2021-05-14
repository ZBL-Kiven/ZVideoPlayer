package com.zj.videotest.feed.data

import android.content.res.Resources
import com.zj.videotest.feed.bean.VideoSource
import com.zj.views.ut.DPUtils
import java.util.*

class FeedMockImpl(private val imgPath: String, private val dataType: DataType, private val videoPath: String = "") : FeedDataIn {

    private val id = UUID.randomUUID().toString()

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
        return id
    }

    override fun getClapsCount(): Int {
        return 9989
    }

    override fun getViewWidth(): Int {
        return Resources.getSystem().displayMetrics.widthPixels
    }

    override fun getViewHeight(): Int {
        return DPUtils.dp2px(252f)
    }

    override fun getType(): DataType {
        return dataType
    }

    companion object {
        fun createMock(): MutableList<VideoSource> {
            val lst = mutableListOf<VideoSource>()
            lst.add(VideoSource("https://gcdn.channelthree.tv/20201111/8/7/1/9/4/871946e853194067b61ce0f8601fb261.jpg", DataType.VIDEO, "https://gcdn.channelthree.tv/20201111/f/e/2/f/3/fe2f3916532d48498ef6d7a4e0a1cdd5.mp4"))
            lst.add(VideoSource("https://i.ytimg.com/vi/bPZc7avrCT4/hqdefault.jpg?sqp=-oaymwEcCPYBEIoBSFXyq4qpAw4IARUAAIhCGAFwAcABBg==&rs=AOn4CLAPrfs2Bhirrkj87653AbMChENCVA", DataType.YTB, "https://youtu.be/npDKX_p0ZSk"))
            lst.add(VideoSource("https://i.ytimg.com/vi/cK1igzo7XKs/hqdefault.jpg?sqp=-oaymwEcCPYBEIoBSFXyq4qpAw4IARUAAIhCGAFwAcABBg==&rs=AOn4CLBROMigCs-xYUqQwrNj9_HKSHvaNw", DataType.YTB, "https://youtu.be/cK1igzo7XKs"))
            lst.add(VideoSource("https://img-blog.csdnimg.cn/20190301125102646.png", DataType.VIDEO, "http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4"))
            lst.add(VideoSource("https://ss1.bdstatic.com/70cFvXSh_Q1YnxGkpoWK1HF6hhy/it/u=1091405991,859863778&fm=26&gp=0.jpg", DataType.IMG, ""))
            lst.add(VideoSource("https://gcdn.channelthree.tv/20200623/2/7/a/3/f/27a3f845395a48dbaf535a35e1841f65.jpg", DataType.VIDEO, "https://gcdn.channelthree.tv/20200623/f/f/a/0/1/ffa018ffa0dc4540977e401c79ba964a.mp4"))
            lst.add(VideoSource("https://img-blog.csdnimg.cn/20190301125255914.png", DataType.VIDEO, "http://vjs.zencdn.net/v/oceans.mp4"))
            lst.add(VideoSource("https://img-blog.csdnimg.cn/20190301125528758.png", DataType.VIDEO, "http://vfx.mtime.cn/Video/2019/03/17/mp4/190317150237409904.mp4"))
            lst.add(VideoSource("https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1597838728787&di=75ce91926c9c9a3d7bf8a7b95611510a&imgtype=0&src=http%3A%2F%2Fphotocdn.sohu.com%2F20150915%2Fmp31822141_1442278668147_10.gif", DataType.GIF, ""))
            lst.add(VideoSource("https://gimg2.baidu.com/image_search/src=http%3A%2F%2Fhiphotos.baidu.com%2Ffeed%2Fpic%2Fitem%2F962bd40735fae6cded1edd4e05b30f2442a70f72.jpg&refer=http%3A%2F%2Fhiphotos.baidu.com&app=2002&size=f9999,10000&q=a80&n=0&g=0n&fmt=jpeg?sec=1621007753&t=9267c0db34ecdf67ea3e114f17379a8f", DataType.GIF, ""))
            lst.add(VideoSource("https://gimg2.baidu.com/image_search/src=http%3A%2F%2Fup.54fcnr.com%2Fpic_source%2F9c%2F0f%2Fde%2F9c0fdecd8a4ccb305fadda5081f6ea7b.gif&refer=http%3A%2F%2Fup.54fcnr.com&app=2002&size=f9999,10000&q=a80&n=0&g=0n&fmt=jpeg?sec=1621007753&t=9faffea9390ade4aa11df93f7dfa4e5b", DataType.GIF, ""))
            return lst
        }
    }
}