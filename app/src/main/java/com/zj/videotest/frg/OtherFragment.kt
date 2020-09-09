package com.zj.videotest.frg

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.zj.cf.fragments.BaseLinkageFragment
import com.zj.videotest.R
import com.zj.videotest.feed.apis.config.Constance

class OtherFragment : BaseLinkageFragment() {

    override fun getView(inflater: LayoutInflater, container: ViewGroup?): View {
        return inflater.inflate(R.layout.r_mian_fg_content, container, false)
    }

    override fun onCreate() {
        super.onCreate()
        Constance.curUserId = "${System.currentTimeMillis()}"
        initData(false)
        initListener()
    }

    private fun initData(isLoadMore: Boolean) {

    }

    private fun initListener() {

    }
}