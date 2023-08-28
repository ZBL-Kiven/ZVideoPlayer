package com.zj.videotest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    fun clickFull(v: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(v.context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 20)
            return
        }
        val isFull = videoView.isFullScreen
        if (isFull) {
            controller?.stopNow(true, isRegulate = false)
        } else {
            val path = "/storage/emulated/0/DCIM/Camera/VID_20211207_184234.mp4"
            controller?.playOrResume(path)
        }
        videoView.fullScreen(!isFull, fromUser = true, payloads = null)
    }
}