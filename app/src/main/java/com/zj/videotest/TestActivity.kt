package com.zj.videotest

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.zj.videotest.delegate.VideoControllerPlayers
import com.zj.videotest.ytb.YtbContentChecker
import com.zj.webkit.CCWebView

class TestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CCWebView.onAppAttached(this, "")
        setContentView(R.layout.test_act_content)

        val mEt = findViewById<TextView>(R.id.text)
        val mPlay = findViewById<View>(R.id.mPlay)
        val mResult = findViewById<TextView>(R.id.mResult)

        mPlay.setOnClickListener {
            mResult.text = "checking..."
            YtbContentChecker.checkYtbLinkAvailable(this, mEt.text.toString(), 30000) { isOK, path ->
                mResult.text = "$isOK    :  $path"
            }
        }
    }

    override fun onPause() {
        super.onPause()
        VideoControllerPlayers.stopVideo()
    }
}