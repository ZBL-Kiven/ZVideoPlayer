package com.zj.videotest.feed

import android.animation.Animator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.zj.player.z.ZController
import com.zj.player.base.BasePlayer
import com.zj.videotest.R
import com.zj.videotest.TestActivity2
import com.zj.videotest.controllers.CCVideoController
import com.zj.videotest.feed.bean.VideoSource
import com.zj.videotest.feed.data.CCListVideoListDelegate
import com.zj.videotest.feed.data.CCListVideoListDelegate.Companion.TAG_OVERLAY_VIEW
import com.zj.videotest.feed.data.CCListVideoListDelegate.Companion.TAG_POSITION
import com.zj.views.list.holders.BaseViewHolder
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference

class FeedContentAdapter : ListenerAnimAdapter<VideoSource>(R.layout.r_main_fg_feed_item) {

    init {
        setFirstOnly(false)
    }

    private var adapterInterface: FeedAdapterInterface<VideoSource>? = null
    private var loadDistance: Int = 5
    private var curLoadingTentaclePosition: Int = 5


    private var finishOverrideView: View? = null
        @SuppressLint("InflateParams") get() {
            if (field == null) {
                context?.let { ctx ->
                    val root = LayoutInflater.from(ctx).inflate(R.layout.r_main_fg_feed_item_finish_view, null, false)
                    root.findViewById<View>(R.id.r_main_fg_feed_item_share_replay).setOnClickListener {
                        (root.parent as? ViewGroup)?.removeView(root)
                        (root?.getTag(TAG_POSITION) as? Int)?.let { p ->
                            adapterDelegate?.waitingForPlay(p)
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

    private var adapterDelegate: CCListVideoListDelegate? = object : CCListVideoListDelegate("COMMON", this@FeedContentAdapter) {
        override fun onClap(d: VideoSource?, p: Int) {
            adapterInterface?.clap(d, p)
        }

        override fun avatarClicked(d: VideoSource?, p: Int) {
            adapterInterface?.avatarClicked(d, p)
        }

        override fun loadAvatar(path: String, imageView: ImageView) {
            this@FeedContentAdapter.loadAvatar(path, imageView)
        }

        override fun onPlayStateChanged(runningName: String, isPlaying: Boolean, desc: String?, controller: ZController<*, *>?) {
            this@FeedContentAdapter.onState(isPlaying, controller)
        }

        override fun onBindData(holder: BaseViewHolder<VideoSource?>?, p: Int, d: VideoSource?, playAble: Boolean, vc: CCVideoController?, pl: MutableList<Any?>?) {
            super.onBindData(holder, p, d, playAble, vc, pl)
            if (pl.isNullOrEmpty()) {
                vc?.setOnCompletedListener(if (playAble) onVcCompletedListener else null)
                vc?.setOnResetListener(if (playAble) onResetListener else null)
                vc?.setOnTrackListener(if (playAble) onTrackListener else null)
            }
        }

        override fun onBindFullScreenLayout(contentLayout: View, vc: CCVideoController?, d: VideoSource?, p: Int, pl: List<Any?>?) {
            super.onBindFullScreenLayout(contentLayout, vc, d, p, pl)
            contentLayout.findViewById<View>(R.id.r_main_fg_list_iv_avatar).setOnClickListener {
                it.context.startActivity(Intent(it.context, TestActivity2::class.java))
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        adapterDelegate?.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        adapterDelegate?.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onViewDetachedFromWindow(holder: BaseViewHolder<VideoSource?>) {
        super.onViewDetachedFromWindow(holder)
        stopOrResumeGif(true, holder)
        adapterDelegate?.onViewDetachedFromWindow(WeakReference(holder))
    }

    override fun onViewAttachedToWindow(holder: BaseViewHolder<VideoSource?>) {
        super.onViewAttachedToWindow(holder)
        stopOrResumeGif(false, holder)
    }

    override fun onViewRecycled(holder: BaseViewHolder<VideoSource?>) {
        holder.getView<CCVideoController>(R.id.r_main_fg_feed_item_vc)?.let {
            if (!it.isFullScreen) {
                it.setOnCompletedListener(null)
                it.setOnResetListener(null)
                it.setOnTrackListener(null)
                adapterDelegate?.onViewRecycled(holder)
                super.onViewRecycled(holder)
            }
        } ?: super.onViewRecycled(holder)
    }

    private fun stopOrResumeGif(stop: Boolean, holder: BaseViewHolder<VideoSource?>) {
        holder.getView<CCVideoController>(R.id.r_main_fg_feed_item_vc)?.stopOrResumeGif(stop)
    }

    override fun bindData(holder: BaseViewHolder<VideoSource?>?, p: Int, d: VideoSource?, pl: MutableList<Any?>?) {
        if (pl == null) {
            if (curLoadingTentaclePosition != maxPosition && p >= maxPosition - loadDistance) {
                curLoadingTentaclePosition = maxPosition
                Handler(Looper.getMainLooper()).post { adapterInterface?.onLoadMore(maxPosition) }
            }
        }
        holder?.let { h ->
            adapterDelegate?.bindData(SoftReference(h), p, d, pl)
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

    private val onVcCompletedListener: (CCVideoController) -> Unit = {
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

    private val onResetListener: (CCVideoController) -> Unit = {
        it.getTag(TAG_OVERLAY_VIEW)?.let { tag -> it.removeView(tag, WeakReference(finishOverrideView)) }
    }

    private fun loadAvatar(url: String, iv: ImageView) {
        val width = if (iv.width <= 0) 1 else iv.width
        val height = if (iv.height <= 0) 1 else iv.height
        Glide.with(iv).load(url).override(width, height).circleCrop().into(iv)
    }

    private fun onShare(v: View, p: Int) {
        adapterInterface?.onShare(v, getItem(p), p)
    }

    override fun getAnimators(p0: View?): Array<Animator> {
        if (p0 == null) return arrayOf()
        val anim = ObjectAnimator.ofFloat(p0, "alpha", 0.0f, 1.0f).setDuration(300)
        return arrayOf(anim)
    }

    fun setAdapterInterface(adapterInterface: FeedAdapterInterface<VideoSource>) {
        this.adapterInterface = adapterInterface
    }

    fun resume(): Boolean {
        return resumeIfVisible()
    }

    fun pause() {
        adapterDelegate?.pause()
    }

    fun destroy() {
        adapterDelegate?.release(true)
        adapterDelegate = null
    }

    private fun onState(isPlaying: Boolean, controller: ZController<out BasePlayer<*>, *>?) {
        if (isPlaying) {
            val vc = (controller?.getController() as? CCVideoController) ?: return
            val tag = finishOverrideView?.getTag(TAG_OVERLAY_VIEW)
            if (vc.containsOverlayView(tag, WeakReference(finishOverrideView))) {
                vc.removeView(tag, WeakReference(finishOverrideView))
            }
        }
    }

    override fun onDataChange(data: MutableList<VideoSource>?) {
        context?.let { Glide.get(it).clearMemory() }
    }

    override fun onDataFullChange() {
        resumeIfVisible()
    }

    private fun resumeIfVisible(): Boolean {
        return if (!data.isNullOrEmpty()) {
            adapterDelegate?.resume(true);true
        } else false
    }
}