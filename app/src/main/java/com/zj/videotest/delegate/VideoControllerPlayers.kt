package com.zj.videotest.delegate

import com.zj.player.ZPlayer
import com.zj.player.config.VideoConfig
import com.zj.player.ut.Controller
import com.zj.player.z.ZController
import com.zj.videotest.feed.data.DataType
import com.zj.videotest.ytb.CusWebPlayer
import com.zj.videotest.ytb.CusWebRender

object VideoControllerPlayers {

    private var ytbController: ZController<*, *>? = null
    private var controller: ZController<*, *>? = null
    private var config = VideoConfig.create().setCacheEnable(true).setDebugAble(true).setCacheFileDir("cachedVideos").updateMaxCacheSize(200L * 1024 * 1024)

    fun <V : Controller> getOrCreatePlayerWithVc(vc: V, data: () -> DataType): ZController<*, *> {
        val c = when (data.invoke()) {
            DataType.YTB -> {
                if (ytbController == null || ytbController?.isDestroyed() == true) {
                    ytbController = ZPlayer.build(vc, CusWebPlayer(), CusWebRender::class.java)
                } else ytbController?.updateViewController(vc)
                ytbController
            }
            else -> {
                if (controller == null || controller?.isDestroyed() == true) {
                    controller = ZPlayer.build(vc, config)
                } else controller?.updateViewController(vc)
                controller
            }
        }
        return c!!
    }

    fun checkControllerMatching(type: DataType?, controller: ZController<*, *>?): Boolean {
        return controller != null && ((type == DataType.VIDEO && controller.isDefaultPlayerType()) || ((type == DataType.YTB && controller.checkPlayerType(CusWebPlayer::class.java))))
    }

    fun stopIfNotCurrent(path: String?) {
        try {
            if (ytbController?.getPath() != path || path.isNullOrEmpty()) {
                ytbController?.stopNow(true, isRegulate = true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            if (controller?.getPath() != path || path.isNullOrEmpty()) {
                controller?.stopNow(true, isRegulate = true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            controller?.release()
        }
    }

    fun stopVideo() {
        try {
            ytbController?.stopNow(true, isRegulate = true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            controller?.stopNow(true, isRegulate = true)
        } catch (e: Exception) {
            e.printStackTrace()
            controller?.release()
        }
    }
}