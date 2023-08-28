package com.zj.videotest.feed.apis.config

import com.zj.api.interceptor.HeaderProvider
import com.zj.api.interceptor.UrlProvider


object Constance {

    var curUserId = ""

    /**
     *
     * */
    fun getApiBaseUrl(): UrlProvider {
        return object : UrlProvider() {
            override fun url(): String {
                return "http://newfeeds.ccdev.lerjin.com/"
            }
        }
    }

    /**
     *
     * */
    fun getApiHeader(): HeaderProvider {
        return object : HeaderProvider {
            override fun headers(): Map<String, String> {
                return hashMapOf<String, String>().apply {
                    this["Content-Type"] = "application/json"
                    this["charset"] = "UTF-8"
                    this["token"] = "NmJlNDZjOGEtMmI2Yy00NTI2LTkyN2UtN2FjNDM1NGFjYjg3"
                    this["userId"] = curUserId
                }
            }
        }
    }
}