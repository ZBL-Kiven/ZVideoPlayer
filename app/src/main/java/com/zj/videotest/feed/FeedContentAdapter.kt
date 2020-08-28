package com.zj.videotest.feed

import android.animation.Animator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.zj.videotest.feed.data.FeedDataIn
import com.zj.player.ZController
import com.zj.player.config.VideoConfig
import com.zj.player.controller.BaseListVideoController
import com.zj.player.img.ImgLoader
import com.zj.player.list.ListVideoAdapterDelegate
import com.zj.videotest.BuildConfig
import com.zj.videotest.R
import com.zj.videotest.controllers.CCImageLoader
import com.zj.videotest.feed.data.DataType
import com.zj.videotest.controllers.CCVideoController
import com.zj.views.list.holders.BaseViewHolder
import java.lang.IllegalArgumentException
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference

class FeedContentAdapter<T : FeedDataIn> : ListenerAnimAdapter<T>(R.layout.r_main_fg_feed_item) {

    init {
        setFirstOnly(false)
    }

    private var adapterInterface: FeedAdapterInterface<T>? = null
    private var loadDistance: Int = 5
    private var curLoadingTentaclePosition: Int = 5

    private var finishOverrideView: View? = null
        @SuppressLint("InflateParams") get() {
            if (field == null) {
                context?.let { ctx ->
                    val root = LayoutInflater.from(ctx).inflate(R.layout.r_main_fg_feed_item_finish_view, null, false)
                    root.findViewById<View>(R.id.r_main_fg_feed_item_share_replay).setOnClickListener {
                        (root.parent as? ViewGroup)?.removeView(root)
                        (root?.tag as? Int)?.let { p ->
                            getDelegate()?.waitingForPlay(p)
                        }
                    }
                    root.findViewById<View>(R.id.r_main_fg_feed_item_share_facebook).setOnClickListener {
                        (root?.tag as? Int)?.let { p ->
                            onShare(it, p)
                        }
                    }
                    root.findViewById<View>(R.id.r_main_fg_feed_item_share_message).setOnClickListener {
                        (root?.tag as? Int)?.let { p ->
                            onShare(it, p)
                        }
                    }
                    root.findViewById<View>(R.id.r_main_fg_feed_item_share_whats_app).setOnClickListener {
                        (root?.tag as? Int)?.let { p ->
                            onShare(it, p)
                        }
                    }
                    root.z = 100f
                    field = root
                }
            }
            return field
        }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        adapterDelegate?.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        adapterDelegate?.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onViewDetachedFromWindow(holder: BaseViewHolder) {
        super.onViewDetachedFromWindow(holder)
        stopOrResumeGif(true, holder)
        adapterDelegate?.onViewDetachedFromWindow(WeakReference(holder))
    }

    override fun onViewAttachedToWindow(holder: BaseViewHolder) {
        super.onViewAttachedToWindow(holder)
        stopOrResumeGif(false, holder)
    }

    override fun onViewRecycled(holder: BaseViewHolder) {
        holder.getView<CCVideoController>(R.id.r_main_fg_feed_item_vc)?.let {
            try {
                context?.let { ctx ->
                    val thumb = it.getThumbView() ?: return
                    Glide.with(ctx).clear(thumb)
                    val bg = it.getBackgroundView() ?: return
                    Glide.with(ctx).clear(bg)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        super.onViewRecycled(holder)
    }

    private fun stopOrResumeGif(stop: Boolean, holder: BaseViewHolder) {
        holder.getView<CCVideoController>(R.id.r_main_fg_feed_item_vc)?.stopOrResumeGif(stop)
    }

    override fun bindData(holder: BaseViewHolder?, p: Int, d: T?, pl: MutableList<Any>?) {
        if (curLoadingTentaclePosition != maxPosition && p >= maxPosition - loadDistance) {
            curLoadingTentaclePosition = maxPosition
            adapterInterface?.onLoadMore(maxPosition)
        }
        holder?.let { h ->
            val avatarPath = d?.getAvatarPath() ?: ""
            h.getView<ImageView>(R.id.r_main_fg_feed_item_iv_avatar)?.let {
                it.post { loadAvatar(avatarPath, it) }
                it.setOnClickListener { adapterInterface?.avatarClicked(d, p) }
            }
            h.getView<TextView>(R.id.r_main_fg_feed_item_tv_nickname)?.text = d?.getNickname()
            h.getView<TextView>(R.id.r_main_fg_feed_item_tv_desc)?.text = d?.getDesc()
            h.getView<TextView>(R.id.r_main_fg_feed_item_tv_claps)?.text = "${d?.getClapsCount() ?: 0}"
            h.getView<View>(R.id.r_main_fg_feed_item_ll_claps)?.setOnClickListener {
                adapterInterface?.clap(d, p)
            }
            adapterDelegate?.bindData(SoftReference(holder), p, d, d?.getType() == DataType.VIDEO, pl)
        }
    }

    private var adapterDelegate: ListVideoAdapterDelegate<T, CCVideoController, BaseViewHolder>? = object : ListVideoAdapterDelegate<T, CCVideoController, BaseViewHolder>(this@FeedContentAdapter) {

        override fun createZController(vc: CCVideoController): ZController {
            return ZController.build(vc, VideoConfig.create().setCacheEnable(true).setDebugAble(BuildConfig.DEBUG).setCacheFileDir("feed/videos").updateMaxCacheSize(200L * 1024 * 1024))
        }

        override fun getViewController(holder: BaseViewHolder?): CCVideoController? {
            return holder?.getView(R.id.r_main_fg_feed_item_vc)
        }

        override fun getItem(p: Int): T? {
            return this@FeedContentAdapter.getItem(p)
        }

        override fun getPathAndLogsCallId(d: T?): Pair<String, Any?>? {
            return Pair(d?.getVideoPath() ?: "", d?.getSourceId())
        }

        override fun onBindData(holder: SoftReference<BaseViewHolder>?, p: Int, d: T?, playAble: Boolean, vc: CCVideoController, pl: MutableList<Any>?) {
            vc.tag = p // important properties.
            vc.setOnCompletedListener(if (playAble) onVcCompletedListener else null)
            vc.setOnResetListener(if (playAble) onResetListener else null)
            vc.setOnFullScreenChangedListener(if (playAble) onFullScreenListener else null)
            onBindAdapterData(d, vc, pl)
        }
    }

    private val onFullScreenListener: (BaseListVideoController) -> Unit = {
        if (finishOverrideView?.tag == it.tag) {
            it.getVideoRootView()?.let { root ->
                if ((finishOverrideView?.parent as? ViewGroup) == root) finishOverrideView?.layoutParams = it.getOverlayValidParams()
            }
        }
    }

    private val onVcCompletedListener: (BaseListVideoController) -> Unit = {
        finishOverrideView?.tag = it.tag
        val lp = it.getOverlayValidParams()
        it.getVideoRootView()?.let { root ->
            removeIfNeed(it) {
                root.addView(finishOverrideView, lp)
            }
        }
    }
    private val onResetListener: (BaseListVideoController) -> Unit = {
        removeIfNeed(it)
    }

    private fun removeIfNeed(vc: BaseListVideoController, onNext: ((View) -> Unit)? = null) {
        finishOverrideView?.let { v ->
            if (v.tag == vc.tag) {
                (v.parent as? ViewGroup)?.removeView(v)
                onNext?.invoke(v)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onBindAdapterData(d: T?, vc: BaseListVideoController, pl: MutableList<Any>?) {
        val imgPath = d?.getImagePath() ?: ""
        val videoWidth = d?.getViewWidth() ?: 1
        val videoHeight = d?.getViewHeight() ?: 1
        loadThumbImage(videoWidth, videoHeight, imgPath, vc, d)
        vc.setScreenContentLayout(R.layout.r_main_video_details_content) { v ->
            v.findViewById<ImageView>(R.id.r_main_fg_list_iv_avatar)?.let {
                loadAvatar(d?.getAvatarPath() ?: "", it)
            }
        }
    }

    private fun loadAvatar(url: String, iv: ImageView) {
        val width = if (iv.width <= 0) 1 else iv.width
        val height = if (iv.height <= 0) 1 else iv.height
        Glide.with(iv).load(url).override(width, height).circleCrop().into(iv)
    }

    private fun loadThumbImage(videoWidth: Int, videoHeight: Int, imgPath: String, vc: BaseListVideoController, d: T?) {
        val tag = d?.getSourceId() ?: return
        val imgType = when (val type = d.getType()) {
            DataType.VIDEO, DataType.IMG -> ImgLoader.ImgType.IMG
            DataType.GIF -> ImgLoader.ImgType.GIF
            else -> throw IllegalArgumentException("the data type [$type] is not supported !")
        }
        vc.loadBackground(tag, imgPath, videoWidth, videoHeight, imgType, CCImageLoader())
    }

    private fun onShare(v: View, p: Int) {
        adapterInterface?.onShare(v, getItem(p), p)
    }

    override fun getAnimators(p0: View?): Array<Animator> {
        if (p0 == null) return arrayOf()
        val anim = ObjectAnimator.ofFloat(p0, "alpha", 0.0f, 1.0f).setDuration(300)
        return arrayOf(anim)
    }

    fun setAdapterInterface(adapterInterface: FeedAdapterInterface<T>) {
        this.adapterInterface = adapterInterface
    }

    private fun getDelegate(): ListVideoAdapterDelegate<T, CCVideoController, BaseViewHolder>? {
        return adapterDelegate
    }

    fun cancelAllPLay() {
        adapterDelegate?.cancelAll()
    }

    fun resume() {
        adapterDelegate?.resume()
    }

    fun release() {
        context?.let { Glide.get(it).clearMemory() }
        System.gc()
    }

    fun destroy() {
        adapterDelegate?.release()
        adapterDelegate = null
    }

    override fun onDataChange(data: MutableList<T>?) {
        release()
    }
}