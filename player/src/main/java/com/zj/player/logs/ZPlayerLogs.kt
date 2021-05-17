package com.zj.player.logs

import android.util.Log
import java.lang.IllegalStateException

object ZPlayerLogs {

    private var videoEventListener: VideoEventListener? = null
    internal var debugAble: Boolean = false

    /**
     * Set a listener for events generated during video playback.
     * */
    fun setVideoEventListener(videoEventListener: VideoEventListener?) {
        this.videoEventListener = videoEventListener
    }

    internal fun onError(es: String, soft: Boolean = false) {
        val e = IllegalStateException(es)
        onError(e, soft)
    }

    internal fun onError(e: Exception?, soft: Boolean = false) {
        e?.let {
            if (debugAble && !soft) throw e
            videoEventListener?.onError(e)
        }
    }

    internal fun debug(s: String) {
        if (debugAble) Log.d("ZPlayer.logs ---> ", "error detected, case:\n$s")
    }

    internal fun onLog(s: String, curPath: String, accessKey: String, modeName: String, data: BehaviorData?) {
        videoEventListener?.onLog(s, curPath, accessKey, modeName, convertBean(data))
    }

    internal fun onLog(s: String, curPath: String, accessKey: String, modeName: String, vararg params: Pair<String, Any>) {
        videoEventListener?.onLog(s, curPath, accessKey, modeName, params.toMap())
    }


    private fun convertBean(bean: BehaviorData?): Map<String, Any>? {
        if (bean == null) return null
        val returnMap: MutableMap<String, Any>
        return try {
            val cls: Class<*> = BehaviorData::class.java
            returnMap = mutableMapOf()
            val beanInfo = cls.declaredFields
            for (b in beanInfo) {
                b.isAccessible = true
                b.get(bean)?.let { returnMap[b.name] = it }
            }
            returnMap
        } catch (e: java.lang.Exception) {
            onError(IllegalStateException("the logs info covert to map failed! case: ${e.message}"))
            null
        }
    }

}