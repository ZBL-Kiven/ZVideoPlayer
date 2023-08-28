package com.zj.videotest.frg

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zj.cf.fragments.BaseLinkageFragment
import com.zj.videotest.R
import com.zj.videotest.delegate.VideoControllerPlayers
import com.zj.videotest.feed.FeedAdapterInterface
import com.zj.videotest.feed.FeedContentAdapter
import com.zj.videotest.feed.apis.config.Constance
import com.zj.videotest.feed.apis.init.AppInitApi
import com.zj.videotest.feed.bean.VideoSource
import com.zj.views.list.refresh.layout.RefreshLayout
import com.zj.views.list.refresh.layout.api.RefreshLayoutIn
import com.zj.views.list.refresh.layout.listener.OnRefreshLoadMoreListener

class RFeedFragment : BaseLinkageFragment() {

    private var rvContent: RecyclerView? = null
    private var adapter: FeedContentAdapter? = null
    private var refreshLayout: RefreshLayout? = null

    override fun getView(inflater: LayoutInflater, container: ViewGroup?): View {
        return inflater.inflate(R.layout.r_mian_fg_content, container, false)
    }

    override fun onCreate() {
        super.onCreate()
        Constance.curUserId = "${System.currentTimeMillis()}"
        rvContent = find(R.id.r_main_frg_feed_rv)
        refreshLayout = find(R.id.r_main_frg_feed_refresh)
        adapter = FeedContentAdapter()
        rvContent?.layoutManager = LinearLayoutManager(this.activity)
        rvContent?.adapter = adapter
        initData(false)
        initListener()
    }

    private fun initData(isLoadMore: Boolean) {
        if (!isLoadMore) VideoControllerPlayers.stopVideo()
        AppInitApi.getFeedMock { b, d, es ->
            if (isLoadMore) {
                if (d.isNullOrEmpty()) refreshLayout?.setNoMoreData(true)
                else refreshLayout?.finishLoadMore()
            } else {
                refreshLayout?.finishRefresh()
            }
            if (b) setAdapterData(d?.toMutableList(), isLoadMore)
            else if (es != null) Toast.makeText(activity, es.message(), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setAdapterData(d: MutableList<VideoSource>?, loadMore: Boolean) {
        if (d.isNullOrEmpty()) return
        if (loadMore) {
            adapter?.add(d)
        } else {
            adapter?.change(d)
        }
    }

    private fun initListener() {
        refreshLayout?.setOnRefreshLoadMoreListener(object : OnRefreshLoadMoreListener {
            override fun onLoadMore(p0: RefreshLayoutIn) {
                initData(true)
            }

            override fun onRefresh(p0: RefreshLayoutIn) {
                initData(false)
            }
        })

        adapter?.setAdapterInterface(object : FeedAdapterInterface<VideoSource> {
            override fun clap(d: VideoSource?, p: Int) {
                d?.let { it.clapped = !it.clapped }
                adapter?.notifyItemChanged(p, "setData")
            }

            override fun avatarClicked(d: VideoSource?, p: Int) {
                d?.let { it.picClicked = !it.picClicked }
                adapter?.notifyItemChanged(p, "setData")
            }

            override fun onShare(v: View, d: VideoSource?, p: Int) {
                d?.let { it.shareCount++ }
                adapter?.notifyItemChanged(p, "setData")
            }

            override fun onLoadMore(tag: Int) {
                Log.e("------- ", "onLoadMore   $tag")
                initData(true)
            }
        })
    }

    override fun onPaused() {
        super.onPaused()
        adapter?.pause()
    }

    override fun onResumed() {
        super.onResumed()
        adapter?.resume()
    }

    override fun onDestroyed() {
        super.onDestroyed()
        adapter?.destroy()
    }
}