package com.zj.player.logs

internal object BehaviorLogsTable {

    //------- ZPlayer ------
    const val TYPE_PLAYER = "playerEvent"

    class PlayerBaseData(detail: String, name: String, params: Map<String, Any>? = null) : BehaviorData(TYPE_PLAYER, detail, name, params)

    fun requestParams(callId: Any?, requestParams: Map<String, Any>): PlayerBaseData {
        return PlayerBaseData(detail = "data request params", name = "requestParams", params = mutableMapOf<String, Any>().apply { putAll(requestParams);put("callId", callId ?: "") })
    }

    fun seekTo(callId: Any?, progress: Long): PlayerBaseData {
        return PlayerBaseData(detail = "seek video", name = "seekTo", params = mapOf(Pair("progress", progress), Pair("callId", callId ?: "")))
    }

    fun videoStopped(callId: Any?, curLookedPercent: Float): PlayerBaseData {
        return PlayerBaseData(detail = "video stopped", name = "videoStop", params = mapOf(Pair("percent", curLookedPercent), Pair("callId", callId ?: "")))
    }

    fun playError(callId: Any?, msg: String): PlayerBaseData {
        return PlayerBaseData(detail = "video play error", name = "playError", params = mapOf(Pair("msg", msg), Pair("callId", callId ?: "")))
    }

    fun released(): PlayerBaseData {
        return PlayerBaseData(detail = "video player released", name = "shutdown")
    }

    fun newSpeed(callId: Any?, s: Float): PlayerBaseData {
        return PlayerBaseData(detail = "video set speed", name = "setVolume", params = mapOf(Pair("speed", s), Pair("callId", callId ?: "")))
    }

    fun newVolume(callId: Any?, volume: Float): PlayerBaseData {
        return PlayerBaseData(detail = "video set volume", name = "setVolume", params = mapOf(Pair("volume", volume), Pair("callId", callId ?: "")))
    }

    //------- ZController ------
    const val TYPE_CONTROLLER = "controllerEvent"

    class ControllerBaseData(detail: String, name: String, params: Map<String, Any>? = null) : BehaviorData(TYPE_CONTROLLER, detail, name, params)

    fun setNewData(url: String, callId: Any?, autoPlay: Boolean): ControllerBaseData {
        return ControllerBaseData(detail = "video play error", name = "setData", params = mapOf(Pair("url", url), Pair("callId", callId ?: ""), Pair("autoPlay", autoPlay)))
    }

    fun controllerState(state: String, callId: Any?, url: String): ControllerBaseData {
        return ControllerBaseData(detail = "video controller state change", name = "onStatus", params = mapOf(Pair("status", state), Pair("url", url), Pair("callId", callId ?: "")))
    }

    // ------   UserOperation - ViewController ------
    const val TYPE_VIEW_CONTROLLER = "viewEvent"

    class ViewBaseData(detail: String, name: String, params: Map<String, Any>? = null) : BehaviorData(TYPE_VIEW_CONTROLLER, detail, name, params)

    fun onPlayClick(): ViewBaseData {
        return ViewBaseData(detail = "click play button", name = "playView")
    }

    fun onSpeedClick(): ViewBaseData {
        return ViewBaseData(detail = "click speed button", name = "speedView")
    }

    fun onMuteClick(): ViewBaseData {
        return ViewBaseData(detail = "click mute Button", name = "muteView")
    }

    fun onLockScreenClick(): ViewBaseData {
        return ViewBaseData(detail = "click lock screen Button", name = "lockScreenView")
    }

    fun onToolsBarShow(isShow: Boolean): ViewBaseData {
        return ViewBaseData(detail = "on the tools bar visibility changed", name = "toolsBarVisible", params = mapOf(Pair("isShow", isShow)))
    }

    fun thumbImgVisible(isShowThumb: Boolean, isShowBackground: Boolean): ViewBaseData {
        return ViewBaseData(detail = "on the overlay views visibility changed", name = "thumbImgVisible", params = mapOf(Pair("isShowThumb", isShowThumb), Pair("isShowBackground", isShowBackground)))
    }

    fun onFullscreen(isFull: Boolean): ViewBaseData {
        return ViewBaseData(detail = "user set video playing mode as fullscreen", name = "fullScreen", params = mapOf(Pair("isFull", isFull)))
    }

    fun onFullMaxScreen(isFull: Boolean): ViewBaseData {
        return ViewBaseData(detail = "user set video playing mode as fullscreen max", name = "fullMaxScreen", params = mapOf(Pair("isFull", isFull)))
    }

    //------- ZRender ------
    const val TYPE_ZRender = "renderEvent"

    class RenderBaseData(detail: String, name: String, params: Map<String, Any>? = null) : BehaviorData(TYPE_ZRender, detail, name, params)

    fun onRenderSet(renderCoreName: Int, resizeMode: Int): RenderBaseData {
        return RenderBaseData(detail = "on renderer core frame set", name = "rendererCore", params = mapOf(Pair("curRenderer", renderCoreName), Pair("resizeMode", resizeMode)))
    }

}