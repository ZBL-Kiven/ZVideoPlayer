package com.zj.videotest.test

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zj.player.ZPlayer
import com.zj.player.z.ZController
import com.zj.videotest.R
import kotlinx.android.synthetic.main.test_act_content.*


class TestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.test_act_content)
        val c = ZPlayer.build(mVideoView)

        mPlay.setOnClickListener {
            val path = mEditText.text.toString()
            if (path.isNotEmpty()) {
                c.playOrResume(path)
            }
        }
    }
}