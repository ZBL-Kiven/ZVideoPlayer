package com.zj.videotest

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.zj.player.VideoEventListener
import com.zj.player.ZController
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {


    private val path = "https://gcdn.channelthree.tv/20200616/4/f/2/f/9/4f2f94b03d674d8880b5c4091857dacb.mp4"
    private var controller: ZController? = null
    private var isOne = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()
        controller = ZController.build(videoView1)
        controller?.setData(path)
        controller?.setVideoEventListener(onVideoEventListener)
        initView()
        initListener()
    }

    private fun initView() {
        videoView1?.let {
            it.getThumbView()?.setImageResource(R.drawable.ic_launcher_foreground)
            it.getBackgroundView()?.setImageResource(R.drawable.ic_launcher_background)
        }
    }

    private fun initListener() {
        btn?.setOnClickListener {
            isOne = !isOne
            val vv = if (isOne) videoView1 else videoView2
            controller?.updateViewController(vv)
        }
    }

    private val onVideoEventListener = object : VideoEventListener {
        override fun onError(e: Exception?) {
            Log.e("zjj--- error", "${e?.message}")
        }

        override fun onLog(s: String, curPath: String, accessKey: String, modeName: String) {
            //            Log.e("zjj--- log", "$curPath  $modeName   $accessKey  ---- $s")
        }
    }
}
