package com.zj.player

import com.zj.player.base.BasePlayer
import com.zj.player.base.BaseRender
import com.zj.player.config.VideoConfig
import com.zj.player.ut.Controller
import com.zj.player.z.ZController
import com.zj.player.z.ZRender
import com.zj.player.z.ZVideoPlayer
import com.zj.player.z.ZVideoView

/**
 * build a video controller.
 * require a viewController and a player ex [ZVideoPlayer]
 * the uniqueId is required and it also binding with a viewController, changed if recreate or viewController [com.zj.player.z.ZController.updateViewController] updated.
 * */
@Suppress("unused")
object ZPlayer {

    fun build(viewController: Controller): ZController<ZVideoPlayer, ZRender> {
        return build(viewController, ZVideoPlayer(VideoConfig.create()), ZRender::class.java)
    }

    fun build(viewController: Controller, config: VideoConfig): ZController<ZVideoPlayer, ZRender> {
        return build(viewController, ZVideoPlayer(config), ZRender::class.java)
    }

    fun <P : ZVideoPlayer> build(viewController: Controller, player: P): ZController<ZVideoPlayer, ZRender> {
        return build(viewController, player, ZRender::class.java)
    }

    fun <P : BasePlayer<R>, R : BaseRender> build(viewController: Controller, player: P, render: Class<R>): ZController<P, R> {
        return ZController(player, render, viewController)
    }

    /**
     * After setting this property, all ViewController instances configured with app:useMuteGlobal in xml take effect。
     * @see ZVideoView.muteIsUseGlobal  bind to [ZVideoView.muteGlobalDefault]
     * */
    fun setGlobalMuteDefault(isMute: Boolean) {
        ZVideoView.setGlobalMuteDefault(isMute)
    }

}