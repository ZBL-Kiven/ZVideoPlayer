package com.zj.videotest.feed

import android.animation.Animator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.zj.player.BaseVideoController
import com.zj.videotest.feed.data.FeedDataIn
import com.zj.player.ZController
import com.zj.player.config.VideoConfig
import com.zj.player.controller.BaseListVideoController
import com.zj.player.img.ImgLoader
import com.zj.player.list.ListVideoAdapterDelegate
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
    private var controller: ZController? = null

    private companion object {
        const val TAG_POSITION = R.id.special_feed_adapter_tag_id_position
        const val TAG_OVERLAY_VIEW = R.id.special_feed_adapter_tag_id_add_overlay
    }

    private var finishOverrideView: View? = null
        @SuppressLint("InflateParams") get() {
            if (field == null) {
                context?.let { ctx ->
                    val root = LayoutInflater.from(ctx).inflate(R.layout.r_main_fg_feed_item_finish_view, null, false)
                    root.findViewById<View>(R.id.r_main_fg_feed_item_share_replay).setOnClickListener {
                        (root.parent as? ViewGroup)?.removeView(root)
                        (root?.getTag(TAG_POSITION) as? Int)?.let { p ->
                            getDelegate()?.waitingForPlay(p)
                        }
                    }
                    root.findViewById<View>(R.id.r_main_fg_feed_item_share_facebook).setOnClickListener {
                        (root?.getTag(TAG_POSITION) as? Int)?.let { p ->
                            onShare(it, p)
                        }
                    }
                    root.findViewById<View>(R.id.r_main_fg_feed_item_share_message).setOnClickListener {
                        (root?.getTag(TAG_POSITION) as? Int)?.let { p ->
                            onShare(it, p)
                        }
                    }
                    root.findViewById<View>(R.id.r_main_fg_feed_item_share_whats_app).setOnClickListener {
                        (root?.getTag(TAG_POSITION) as? Int)?.let { p ->
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
            it.setOnCompletedListener(null)
            it.setOnResetListener(null)
            it.setOnTrackListener(null)
            it.setOnFullScreenChangedListener(null)
//            it.actionListener = null
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
            Handler(Looper.getMainLooper()).post { adapterInterface?.onLoadMore(maxPosition) }
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
            if (controller == null) controller = ZController.build(vc, VideoConfig.create().setCacheEnable(true).setDebugAble(true).setCacheFileDir("feed/videos").updateMaxCacheSize(200L * 1024 * 1024))
            else controller?.updateViewController(vc)
            return controller!!
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
            vc.setTag(TAG_POSITION, p) // important properties.
            vc.setTag(TAG_OVERLAY_VIEW, d?.getSourceId() ?: "TAG_OVERLAY_VIEW$p")
            vc.setOnCompletedListener(if (playAble) onVcCompletedListener else null)
            vc.setPlayingStateListener(if (playAble) onPlayingStateChangedListener else null)
            vc.setOnResetListener(if (playAble) onResetListener else null)
            vc.setOnTrackListener(if (playAble) onTrackListener else null)
//            vc.actionListener = if (playAble) actionListener else null
            vc.setOnFullScreenChangedListener(onFullScreenListener)
            onBindAdapterData(d, vc, pl)
        }
    }

    private val onTrackListener: (playAble: Boolean, start: Boolean, end: Boolean, formTrigDuration: Float) -> Unit = { playAble, start, end, _ ->
        if (playAble) {
            finishOverrideView?.let {
                if (start) {
                    it.animate().cancel()
                    it.animate().alpha(0.0f).setDuration(200).start()
                }
                if (end) {
                    it.animate().cancel()
                    it.animate().alpha(1.0f).setDuration(200).start()
                }
            }
        }
    }

    private val onPlayingStateChangedListener: (BaseListVideoController) -> Unit = {
        if (!isAutoPlayAble) adapterDelegate?.pause()
        val tag = finishOverrideView?.getTag(TAG_OVERLAY_VIEW)
        if (it.containsOverlayView(tag, WeakReference(finishOverrideView))) {
            it.removeView(tag, WeakReference(finishOverrideView))
        }
    }

    private val onVcCompletedListener: (BaseListVideoController) -> Unit = {
        finishOverrideView?.setTag(TAG_POSITION, it.getTag(TAG_POSITION))
        it.addOverlayView(it.getTag(TAG_OVERLAY_VIEW), WeakReference(finishOverrideView)) { rl ->
            rl.apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
                it.getThumbView()?.id?.let { idRes ->
                    addRule(RelativeLayout.ALIGN_END, idRes)
                    addRule(RelativeLayout.ALIGN_START, idRes)
                    addRule(RelativeLayout.ALIGN_TOP, idRes)
                    addRule(RelativeLayout.ALIGN_BOTTOM, idRes)
                }
            }
        }
    }

    private val onFullScreenListener: (BaseVideoController, Boolean) -> Unit = { vc, _ ->
        controller?.let {
            (vc.getTag(TAG_POSITION) as? Int)?.let { p ->
                getItem(p)?.getVideoPath()?.let { path -> cancelIfNotCurrent(path) }
                resumeIfVisible(p)
            }
        }
    }


    private val onResetListener: (BaseListVideoController) -> Unit = {
        it.getTag(TAG_OVERLAY_VIEW)?.let { tag -> it.removeView(tag, WeakReference(finishOverrideView)) }
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

    private var isAutoPlayAble = true

    fun resume() {
        isAutoPlayAble = true
        resumeIfVisible()
    }

    fun pause() {
        isAutoPlayAble = false
        adapterDelegate?.pause()
    }

    fun cancelAllPLay() {
        adapterDelegate?.cancelAll()
    }

    private fun cancelIfNotCurrent(path: String) {
        if (controller?.getPath() != path) {
            cancelAllPLay()
        }
    }

    fun destroy() {
        isAutoPlayAble = false
        adapterDelegate?.release()
        adapterDelegate = null
    }

    override fun onDataChange(data: MutableList<T>?) {
        release()
    }

    override fun onDataFullChange() {
        resumeIfVisible()
    }

    private fun release() {
        context?.let { Glide.get(it).clearMemory() }
        Runtime.getRuntime().gc()
    }

    private fun resumeIfVisible(position: Int = -1) {
        if (!data.isNullOrEmpty()) {
            adapterDelegate?.resume()
            if (isAutoPlayAble) adapterDelegate?.idle(position)
        }
    }
}