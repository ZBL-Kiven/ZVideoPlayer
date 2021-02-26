package com.zj.videotest.frg

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.zj.cf.fragments.BaseLinkageFragment
import com.zj.videotest.R
import com.zj.videotest.TestActivity
import com.zj.videotest.feed.apis.config.Constance
import kotlinx.android.synthetic.main.r_other_fg_content.*

class OtherFragment : BaseLinkageFragment() {

    override fun getView(inflater: LayoutInflater, container: ViewGroup?): View {
        return inflater.inflate(R.layout.r_other_fg_content, container, false)
    }

    override fun onCreate() {
        super.onCreate()
        a_other.setOnClickListener {
            startActivity(Intent(it.context, TestActivity::class.java))
        }
    }
}