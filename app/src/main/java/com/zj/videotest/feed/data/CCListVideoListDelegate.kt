package com.zj.videotest.feed.data

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.zj.player.adapters.ListListVideoAdapterDelegate
import com.zj.player.img.ImgLoader
import com.zj.player.z.ZController
import com.zj.videotest.R
import com.zj.videotest.controllers.CCImageLoader
import com.zj.videotest.controllers.CCVideoController
import com.zj.videotest.delegate.VideoControllerPlayers
import com.zj.videotest.feed.FeedContentAdapter
import com.zj.videotest.feed.bean.VideoSource
import com.zj.views.list.adapters.BaseRecyclerAdapter
import com.zj.views.list.holders.BaseViewHolder
import java.lang.IllegalArgumentException
import java.lang.ref.SoftReference

abstract class CCListVideoListDelegate(delegateName: String, adapter: FeedContentAdapter) : ListListVideoAdapterDelegate<VideoSource, CCVideoController, BaseViewHolder, BaseRecyclerAdapter<BaseViewHolder, VideoSource>>(delegateName, adapter) {

    companion object {
        const val TAG_POSITION = R.id.special_feed_adapter_tag_id_position
        const val TAG_OVERLAY_VIEW = R.id.special_feed_adapter_tag_id_add_overlay
    }

    abstract fun onClap(d: VideoSource?, p: Int)
    abstract fun avatarClicked(d: VideoSource?, p: Int)
    abstract fun loadAvatar(path: String, imageView: ImageView)

    override fun createZController(delegateName: String, data: VideoSource?, vc: CCVideoController): ZController<*, *> {
        return VideoControllerPlayers.getOrCreatePlayerWithVc(delegateName, vc) { data?.getType() ?: DataType.VIDEO }
    }

    override fun getViewController(holder: BaseViewHolder?): CCVideoController? {
        return holder?.getView(R.id.r_main_fg_feed_item_vc)
    }

    override fun getItem(p: Int, adapter: BaseRecyclerAdapter<BaseViewHolder, VideoSource>): VideoSource? {
        return adapter.getItem(p)
    }

    override fun isInflateMediaType(d: VideoSource?): Boolean {
        return true
    }

    override fun getPathAndLogsCallId(d: VideoSource?): Pair<String, Any?>? {
        return Pair(d?.getVideoPath() ?: "", d?.getSourceId())
    }

    override fun getVideoDetailLayoutId(): Int {
        return R.layout.r_main_video_details_content
    }

    override val isSourcePlayAble: (d: VideoSource?) -> Boolean = { d -> d?.getType() == DataType.VIDEO || d?.getType() == DataType.YTB }

    /**bind normal media item data*/
    override fun onBindData(holder: BaseViewHolder?, p: Int, d: VideoSource?, playAble: Boolean, vc: CCVideoController?, pl: MutableList<Any?>?) {
        holder?.let { h ->
            if (pl.isNullOrEmpty()) {
                val avatarPath = d?.getAvatarPath() ?: ""
                h.getView<ImageView>(R.id.r_main_fg_feed_item_iv_avatar)?.let {
                    loadAvatar(avatarPath, it)
                    it.setOnClickListener { avatarClicked(d, p) }
                }
                h.getView<TextView>(R.id.r_main_fg_feed_item_tv_nickname)?.text = d?.getNickname()
                h.getView<TextView>(R.id.r_main_fg_feed_item_tv_desc)?.text = d?.getDesc()
                h.getView<TextView>(R.id.r_main_fg_feed_item_tv_claps)?.text = "${d?.getClapsCount() ?: 0}"
                h.getView<View>(R.id.r_main_fg_feed_item_ll_claps)?.setOnClickListener {
                    onClap(d, p)
                }
                vc?.setTag(TAG_POSITION, p) // important properties.
                vc?.setTag(TAG_OVERLAY_VIEW, d?.getSourceId() ?: "TAG_OVERLAY_VIEW$p")
                val imgPath = d?.getImagePath() ?: ""
                val videoWidth = d?.getViewWidth() ?: 1
                val videoHeight = d?.getViewHeight() ?: 1
                val tag = d?.getSourceId() ?: return
                val imgType = when (val type = d.getType()) {
                    DataType.VIDEO, DataType.YTB, DataType.IMG -> ImgLoader.ImgType.IMG
                    DataType.GIF -> ImgLoader.ImgType.GIF
                    else -> throw IllegalArgumentException("the data type [$type] is not supported !")
                }
                vc?.loadBackground(tag, imgPath, videoWidth, videoHeight, imgType, CCImageLoader())
            }
        }
    }

    /**bind special item data , call when the #[isInflateMediaType] returns false */
    override fun onBindTypeData(holder: SoftReference<BaseViewHolder>?, d: VideoSource?, p: Int, pl: MutableList<Any?>?) {

    }

    /** on bind full screen data if #[getVideoDetailLayoutId] returns a valid layout id*/
    override fun onBindFullScreenLayout(contentLayout: View, vc: CCVideoController?, d: VideoSource?, p: Int, pl: List<Any?>?) {
        vc?.findViewById<ImageView>(R.id.r_main_fg_list_iv_avatar)?.let {
            loadAvatar(d?.getAvatarPath() ?: "", it)
        }
    }

    private fun loadToolsData(vc: CCVideoController?, d: VideoSource?, p: Int, pl: MutableList<Any>?) {
        if (pl?.get(0).toString() == "setData") {

        }
    }
}