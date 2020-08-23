package com.zj.videotest.feed.apis.init

import com.zj.videotest.feed.bean.VideoSource
import io.reactivex.Observable
import retrofit2.http.GET

interface AppInitService {

    @GET("feeds/home-pull")
    fun getFeed(): Observable<List<VideoSource>>

}