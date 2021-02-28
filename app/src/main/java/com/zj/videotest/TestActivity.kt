package com.zj.videotest

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zj.videotest.delegate.VideoControllerPlayers
import com.zj.videotest.ytb.YtbContentChecker
import com.zj.webkit.CCWebView
import kotlinx.android.synthetic.main.test_act_content.*

class TestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CCWebView.onAppAttached(this, "")
        setContentView(R.layout.test_act_content)

        val str = text.text.toString()

        mPlay.setOnClickListener {
            mResult.text = "checking..."
            YtbContentChecker.checkYtbLinkAvailable(this, str, 30000) { isOK, path ->
                mResult.text = "$isOK    :  $path"
            }
        }
    }

    override fun onPause() {
        super.onPause()
        VideoControllerPlayers.stopVideo()
    }
}