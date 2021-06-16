package com.zj.player.logs

import android.util.Log
import com.zj.player.ut.Constance
import java.lang.IllegalStateException

object ZPlayerLogs {

    private var videoEventListener: VideoEventListener? = null
    internal var debugAble: Boolean = false

    /**
     * Set a listener for events generated during video playback. And open the player core logs.
     * This may generate a large amount of data during use.
     * It is recommended to start collecting information in Debug mode or UAT environment.
     * */
    fun setVideoEventListener(videoEventListener: VideoEventListener?) {
        Constance.CORE_LOG_ABLE = true
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
        if (Constance.CORE_LOG_ABLE) videoEventListener?.onLog(s, curPath, accessKey, modeName, convertBean(data))
    }

    internal fun onLog(s: String, curPath: String, accessKey: String, modeName: String, vararg params: Pair<String, Any>) {
        if (Constance.CORE_LOG_ABLE) videoEventListener?.onLog(s, curPath, accessKey, modeName, params.toMap())
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