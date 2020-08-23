package com.zj.videotest.feed.data

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
        return 1080
    }

    override fun getViewHeight(): Int {
        return 768
    }

    override fun getType(): DataType {
        return if (videoPath.isEmpty()) dataType else DataType.VIDEO
    }

    companion object {
        fun createMock(): MutableList<FeedDataIn> {
            val lst = mutableListOf<FeedDataIn>()
            lst.add(FeedMockImpl("https://ss1.bdstatic.com/70cFvXSh_Q1YnxGkpoWK1HF6hhy/it/u=1091405991,859863778&fm=26&gp=0.jpg", DataType.IMG))
            lst.add(FeedMockImpl("https://img-blog.csdnimg.cn/20190301125102646.png", DataType.VIDEO, "http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4"))
            lst.add(FeedMockImpl("https://img-blog.csdnimg.cn/20190301125255914.png", DataType.VIDEO, "http://vjs.zencdn.net/v/oceans.mp4"))
            lst.add(FeedMockImpl("https://img-blog.csdnimg.cn/20190301125528758.png", DataType.VIDEO, "http://vfx.mtime.cn/Video/2019/02/04/mp4/190204084208765161.mp4"))
            lst.add(FeedMockImpl("https://img-blog.csdnimg.cn/20190301125528758.png", DataType.VIDEO, "http://vfx.mtime.cn/Video/2019/03/21/mp4/190321153853126488.mp4"))
            lst.add(FeedMockImpl("https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1597838728787&di=75ce91926c9c9a3d7bf8a7b95611510a&imgtype=0&src=http%3A%2F%2Fphotocdn.sohu.com%2F20150915%2Fmp31822141_1442278668147_10.gif", DataType.GIF))
            lst.add(FeedMockImpl("https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1597838788438&di=305456b2cfe61e21a9f401f119660ff8&imgtype=0&src=http%3A%2F%2Fimg.wxcha.com%2Ffile%2F201907%2F10%2F531aebf030.gif", DataType.GIF))
            lst.add(FeedMockImpl("https://img-blog.csdnimg.cn/20190301125528758.png", DataType.VIDEO, "http://vfx.mtime.cn/Video/2019/03/19/mp4/190319222227698228.mp4"))
            lst.add(FeedMockImpl("https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1597836633672&di=9a40478f2649231f7fb91941b1fceb88&imgtype=0&src=http%3A%2F%2Fimg.wxcha.com%2Ffile%2F201608%2F05%2Fa91f4f5f1f.gif", DataType.GIF))
            lst.add(FeedMockImpl("https://img-blog.csdnimg.cn/20190301125528758.png", DataType.VIDEO, "http://vfx.mtime.cn/Video/2019/03/19/mp4/190319212559089721.mp4"))
            lst.add(FeedMockImpl("https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1597836523222&di=a3e60fc22f64ce9d766b545d7bfb3260&imgtype=0&src=http%3A%2F%2Fb-ssl.duitang.com%2Fuploads%2Fitem%2F201811%2F07%2F20181107163751_Vad8e.thumb.700_0.gif", DataType.GIF))
            lst.add(FeedMockImpl("https://img-blog.csdnimg.cn/20190301125528758.png", DataType.VIDEO, "http://vfx.mtime.cn/Video/2019/03/18/mp4/190318231014076505.mp4"))
            lst.add(FeedMockImpl("https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1597839015433&di=9e550dd916f2ed1c1282db831789b2ab&imgtype=0&src=http%3A%2F%2Fphotocdn.sohu.com%2F20150604%2Fmp17682435_1433407682099_2.gif", DataType.GIF))
            lst.add(FeedMockImpl("https://img-blog.csdnimg.cn/20190301125528758.png", DataType.VIDEO, "http://vfx.mtime.cn/Video/2019/03/17/mp4/190317150237409904.mp4"))
            lst.add(FeedMockImpl("https://ss0.bdstatic.com/70cFvHSh_Q1YnxGkpoWK1HF6hhy/it/u=2042442512,2409881156&fm=26&gp=0.jpg", DataType.IMG))
            lst.add(FeedMockImpl("https://img-blog.csdnimg.cn/20190301125528758.png", DataType.VIDEO, "http://vfx.mtime.cn/Video/2019/03/13/mp4/190313094901111138.mp4"))
            lst.add(FeedMockImpl("https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1597836601921&di=cb3d7317fe10b8bf3a69f17625ee79f0&imgtype=0&src=http%3A%2F%2Fimg3.cache.netease.com%2Fcnews%2F2015%2F6%2F17%2F201506171210361fe75.gif", DataType.GIF))
            return lst
        }
    }
}