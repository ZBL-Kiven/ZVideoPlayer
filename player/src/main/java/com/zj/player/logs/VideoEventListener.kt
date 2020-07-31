package com.zj.player.logs

/**
 * @author ZJJ on 2020.6.16
 *
 * return true to shutdown this event
 *
 * return false to continue event in default
 *
 * */
@Suppress("unused")
abstract class VideoEventListener {

    abstract fun onError(e: Exception)

    abstract fun onLog(s: String, curPath: String, accessKey: String, modeName: String, params: Map<String, Any>?)
}