package com.zj.videotest

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.zj.player.z.ZController
import com.zj.videotest.delegate.VideoControllerPlayers
import com.zj.videotest.feed.data.DataType
import com.zj.videotest.ytb.YtbContentChecker
import com.zj.webkit.CCWebView
import kotlinx.android.synthetic.main.test_act_2_content.*
import kotlinx.android.synthetic.main.test_act_content.*

class TestActivity2 : AppCompatActivity() {

    private var controller: ZController<*, *>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CCWebView.onAppAttached(this, "")
        setContentView(R.layout.test_act_2_content)
        controller = VideoControllerPlayers.getOrCreatePlayerWithVc("test", mVideoView) { DataType.VIDEO }
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
        val isFull = mVideoView.isFullScreen
        if (isFull) {
            controller?.stopNow(true, isRegulate = false)
        } else {
            controller?.playOrResume("http://vjs.zencdn.net/v/oceans.mp4")
        }
        mVideoView.fullScreen(!isFull, fromUser = true, payloads = null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            clickFull(tv1);return false
        }
        return super.onKeyDown(keyCode, event)
    }

}