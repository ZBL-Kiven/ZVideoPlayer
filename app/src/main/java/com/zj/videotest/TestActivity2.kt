package com.zj.videotest

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.zj.player.z.ZController
import com.zj.player.z.ZVideoView
import com.zj.videotest.delegate.VideoControllerPlayers
import com.zj.videotest.feed.data.DataType
import com.zj.webkit.CCWebView

class TestActivity2 : AppCompatActivity() {

    private var controller: ZController<*, *>? = null
    private lateinit var videoView: ZVideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CCWebView.onAppAttached(this, "")
        setContentView(R.layout.test_act_2_content)
        videoView = findViewById(R.id.mVideoView)
        controller = VideoControllerPlayers.getOrCreatePlayerWithVc("test", videoView) { DataType.VIDEO }
    }

    override fun onResume() {
        super.onResume()
        controller?.playOrResume()
    }

    override fun onPause() {
        controller?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        VideoControllerPlayers.stopVideo()
    }

    fun clickFull(view: View) {
        val isFull = videoView.isFullScreen
        if (isFull) {
            controller?.stopNow(true, isRegulate = false)
        } else {
            controller?.playOrResume("http://vjs.zencdn.net/v/oceans.mp4")
        }
        videoView.fullScreen(!isFull, fromUser = true, payloads = null)
    }
}