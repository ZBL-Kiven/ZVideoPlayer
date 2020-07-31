package com.zj.videotest.feed

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zj.cf.fragments.BaseLinkageFragment
import com.zj.videotest.R
import com.zj.videotest.feed.data.FeedMockImpl

class RFeedFragment : BaseLinkageFragment() {

    private var rvContent: RecyclerView? = null
    private var adapter: FeedContentAdapter<FeedMockImpl>? = null


    override fun getView(inflater: LayoutInflater, container: ViewGroup?): View {
        return inflater.inflate(R.layout.r_mian_fg_content, container, false)
    }

    override fun onCreate() {
        super.onCreate()
        rvContent = find(R.id.r_main_frg_feed_rv)
        adapter = FeedContentAdapter()
        rvContent?.adapter = adapter
        rvContent?.layoutManager = LinearLayoutManager(this.activity)
        adapter?.add(arrayListOf(
                FeedMockImpl("https://gcdn.channelthree.tv/20200714/4/6/f/4/e/46f4ed05895a4797bb3c00565aac3980.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200716/9/e/b/e/0/9ebe0040976547379e61a2be93b386ca.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200616/4/f/2/f/9/4f2f94b03d674d8880b5c4091857dacb.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200714/4/6/f/4/e/46f4ed05895a4797bb3c00565aac3980.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200716/9/e/b/e/0/9ebe0040976547379e61a2be93b386ca.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200616/4/f/2/f/9/4f2f94b03d674d8880b5c4091857dacb.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200714/4/6/f/4/e/46f4ed05895a4797bb3c00565aac3980.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200716/9/e/b/e/0/9ebe0040976547379e61a2be93b386ca.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200616/4/f/2/f/9/4f2f94b03d674d8880b5c4091857dacb.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200714/4/6/f/4/e/46f4ed05895a4797bb3c00565aac3980.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200716/9/e/b/e/0/9ebe0040976547379e61a2be93b386ca.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200616/4/f/2/f/9/4f2f94b03d674d8880b5c4091857dacb.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200714/4/6/f/4/e/46f4ed05895a4797bb3c00565aac3980.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200716/9/e/b/e/0/9ebe0040976547379e61a2be93b386ca.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200616/4/f/2/f/9/4f2f94b03d674d8880b5c4091857dacb.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200714/4/6/f/4/e/46f4ed05895a4797bb3c00565aac3980.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200716/9/e/b/e/0/9ebe0040976547379e61a2be93b386ca.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200616/4/f/2/f/9/4f2f94b03d674d8880b5c4091857dacb.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200714/4/6/f/4/e/46f4ed05895a4797bb3c00565aac3980.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200716/9/e/b/e/0/9ebe0040976547379e61a2be93b386ca.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200616/4/f/2/f/9/4f2f94b03d674d8880b5c4091857dacb.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200714/4/6/f/4/e/46f4ed05895a4797bb3c00565aac3980.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200716/9/e/b/e/0/9ebe0040976547379e61a2be93b386ca.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200616/4/f/2/f/9/4f2f94b03d674d8880b5c4091857dacb.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200714/4/6/f/4/e/46f4ed05895a4797bb3c00565aac3980.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200716/9/e/b/e/0/9ebe0040976547379e61a2be93b386ca.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200616/4/f/2/f/9/4f2f94b03d674d8880b5c4091857dacb.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200714/4/6/f/4/e/46f4ed05895a4797bb3c00565aac3980.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200716/9/e/b/e/0/9ebe0040976547379e61a2be93b386ca.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200616/4/f/2/f/9/4f2f94b03d674d8880b5c4091857dacb.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200714/4/6/f/4/e/46f4ed05895a4797bb3c00565aac3980.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200716/9/e/b/e/0/9ebe0040976547379e61a2be93b386ca.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200616/4/f/2/f/9/4f2f94b03d674d8880b5c4091857dacb.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200714/4/6/f/4/e/46f4ed05895a4797bb3c00565aac3980.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200716/9/e/b/e/0/9ebe0040976547379e61a2be93b386ca.mp4"),
                FeedMockImpl("https://gcdn.channelthree.tv/20200616/4/f/2/f/9/4f2f94b03d674d8880b5c4091857dacb.mp4")
                )
        )
    }


    private fun initVideoPlayer(w: Int, h: Int, preview: String, overlay: String) {

    }


}