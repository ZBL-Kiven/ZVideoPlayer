package com.zj.videotest

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.gyf.immersionbar.ImmersionBar
import com.zj.cf.managers.BaseFragmentManager
import com.zj.player.logs.VideoEventListener
import com.zj.player.logs.ZPlayerLogs
import com.zj.videotest.feed.RFeedFragment
import com.zj.views.DrawableTextView

class MainActivity : AppCompatActivity() {

    private var fragmentManager: BaseFragmentManager? = null
    private var mRFeedFragment: RFeedFragment? = null
    private var mRewardFragmentR: RFeedFragment? = null
    private var mMeFragmentR: RFeedFragment? = null

    private var mFeedNavView: DrawableTextView? = null
    private var mRewardNavView: DrawableTextView? = null
    private var mMeNavView: DrawableTextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.r_main_act_content)
        ImmersionBar.with(this).transparentStatusBar().statusBarDarkFont(true).init()

        initView()
        initFrg()
        ZPlayerLogs.setVideoEventListener(object : VideoEventListener() {
            override fun onError(e: Exception) {
//                Log.e("------ ", " ================ error !!     ${e.message}")
            }

            override fun onLog(s: String, curPath: String, accessKey: String, modeName: String, params: Map<String, Any>?) {
//                if (params != null) Log.e("------ ", s)
            }
        })
    }

    private fun initView() {
        mFeedNavView = findViewById(R.id.r_main_act_fragment_nav_btn_feed)
        mRewardNavView = findViewById(R.id.r_main_act_fragment_nav_btn_reward)
        mMeNavView = findViewById(R.id.r_main_act_fragment_nav_btn_me)
    }

    private fun initFrg() {
        mRFeedFragment = RFeedFragment()
        mRewardFragmentR = RFeedFragment()
        mMeFragmentR = RFeedFragment()
        fragmentManager = object : BaseFragmentManager(this, R.id.r_main_act_fragment_content, 0, listOfNotNull(mFeedNavView, mRewardNavView, mMeNavView), mRFeedFragment, mRewardFragmentR, mMeFragmentR) {
            override fun syncSelectState(selectId: String) {
                super.syncSelectState(selectId)
                if (selectId == mRFeedFragment?.fId) {

                } else {
                    //todo hide time bar
                }
            }

            override fun whenShowSameFragment(shownId: String) {
                if (shownId == mRFeedFragment?.fId) {

                }
            }
        }
    }
}
