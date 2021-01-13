package com.zj.videotest.test

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zj.player.ZController
import com.zj.videotest.R
import kotlinx.android.synthetic.main.test_act_content.*


class TestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.test_act_content)
        val path = "http://vfx.mtime.cn/Video/2019/03/21/mp4/190321153853126488.mp4"

        ZController.build(mVideoView).playOrResume(path)

    }

}