package com.zj.videotest.feed.apis.init

import com.zj.videotest.feed.apis.config.ApiErrorHandler
import com.zj.videotest.feed.apis.config.Constance
import com.zj.api.BaseApi
import com.zj.api.interceptor.HeaderProvider
import com.zj.api.interceptor.UrlProvider
import com.zj.videotest.feed.bean.VideoSource
import com.zj.videotest.feed.data.FeedMockImpl
import retrofit2.HttpException

/**
 * Created by ZJJ on 2020/7/17
 *
 * the App init configuration fetcher service
 * */

object AppInitApi {

    /**
     * 配置示例：
     *
     * 每个模块都可以单独使用配置，
     *
     * 配置包括 ： 「baseUrl ，header ，timeOut , certificate , errorHandler 」自定义
     *
     * 高级配置包括：interceptor , HttpClient ，DataConverter ， CallbackScheduler
     *
     * 高级扩展: Retrofit 实例自定义
     *
     * */

    private inline fun <reified T : Any> getDefaultApi(
        baseUrl: UrlProvider = Constance.getApiBaseUrl(),
        header: HeaderProvider = Constance.getApiHeader(),
        timeOut: Long = 5000
    ): BaseApi<T> {
        return BaseApi.create<T>(ApiErrorHandler).baseUrl(baseUrl).header(header).timeOut(timeOut)
            .build()
    }

    fun getFeed(r: (b: Boolean, d: List<VideoSource>?, es: HttpException?) -> Unit) {
        getDefaultApi<AppInitService>().request({ it.getFeed() }, r)
    }

    fun getFeedMock(r: (b: Boolean, d: List<VideoSource>?, es: HttpException?) -> Unit) {
        r(true, FeedMockImpl.createMock(), null)
    }
}