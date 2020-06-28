package com.zj.player

/**
 * @author ZJJ on 2020.6.16
 *
 * return true to shutdown this event
 *
 * return false to continue event in default
 *
 * */
@Suppress("unused")
interface VideoEventListener {

    fun onError(e: Exception?)

    fun onLog(s: String, curPath: String, accessKey: String, modeName: String)

}