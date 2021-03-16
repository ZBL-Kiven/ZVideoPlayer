package com.zj.player.anim

import android.animation.Animator
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * @author ZJJ on 2019.10.24
 */
internal class ZFullValueAnimator(private var listener: FullAnimatorListener?, private val isUseOffset: Boolean = true) : ValueAnimator(), Animator.AnimatorListener, ValueAnimator.AnimatorUpdateListener {

    var isFull: Boolean = false
    private var curDuration: Float = 0.toFloat()
    private var isCancel: Boolean = false

    fun start(isFull: Boolean) {
        if (isRunning) cancel()
        this.isFull = isFull
        removeAllListeners()
        addListener(this)
        addUpdateListener(this)
        super.start()
    }

    override fun cancel() {
        removeAllListeners()
        isCancel = true
        super.cancel()
    }

    init {
        setFloatValues(0.0f, 1.0f)
        interpolator = AccelerateDecelerateInterpolator()
        super.addListener(this)
        super.addUpdateListener(this)
    }

    abstract class FullAnimatorListener {

        open fun onStart() {}

        abstract fun onDurationChange(animation: ValueAnimator, duration: Float, isFull: Boolean)

        abstract fun onAnimEnd(animation: Animator, isFull: Boolean)
    }

    override fun onAnimationStart(animation: Animator) {
        if (curDuration != 0f) curDuration = 0f
        listener?.onStart()
    }

    override fun onAnimationEnd(animation: Animator) {
        curDuration = 0f
        listener?.onAnimEnd(animation, isFull)
    }

    override fun onAnimationCancel(animation: Animator) {
        curDuration = 0f
        listener?.onAnimEnd(animation, isFull)
    }

    override fun onAnimationRepeat(animation: Animator) {
        curDuration = 0f
    }

    override fun onAnimationUpdate(animation: ValueAnimator?) {
        if (isCancel) return
        if (listener != null) {
            animation?.let {
                val duration = (it.animatedValue as? Float) ?: 0f
                val offset = if (isUseOffset) duration - curDuration else duration
                listener?.onDurationChange(it, offset, isFull)
                curDuration = duration
            }
        }
    }
}