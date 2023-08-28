package com.zj.videotest.controllers.scroller

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.Interpolator
import android.widget.OverScroller
import androidx.core.view.ViewCompat
import kotlin.math.abs

abstract class ViewScroller(private val targetView: View) {

    abstract fun constrainScrollBy(dx: Int, dy: Int)

    companion object {
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SETTLING = 2
    }

    private var mMinFlingVelocity = 0
    private var mMaxFlingVelocity = 0
    private var mTouchSlop = 0
    private var mScrollState: Int = SCROLL_STATE_IDLE
    private val mVelocityTracker = VelocityTracker.obtain()
    private val mViewFling: ViewFling = ViewFling(targetView.context)

    init {
        val vc = ViewConfiguration.get(targetView.context)
        mTouchSlop = vc.scaledTouchSlop
        mMinFlingVelocity = vc.scaledMinimumFlingVelocity
        mMaxFlingVelocity = vc.scaledMaximumFlingVelocity
    }

    private fun addMovement(event: MotionEvent) {
        val vte = MotionEvent.obtain(event)
        mVelocityTracker.addMovement(vte)
        vte.recycle()
    }

    fun onEventDown(event: MotionEvent) {
        addMovement(event)
    }

    fun onEventMove(event: MotionEvent, lastY: Float) {
        if (mScrollState != SCROLL_STATE_DRAGGING) {
            var startScroll = false
            var dy = lastY - event.rawY
            if (abs(dy) > mTouchSlop) {
                if (dy > 0) {
                    dy -= mTouchSlop
                } else {
                    dy += mTouchSlop
                }
                startScroll = true
            }
            if (startScroll) {
                setScrollState(SCROLL_STATE_DRAGGING)
            }
        }
        addMovement(event)
    }

    fun onEventUp(event: MotionEvent) {
        addMovement(event)
        mVelocityTracker.computeCurrentVelocity(1000, mMaxFlingVelocity.toFloat())
        var yVelocity = -mVelocityTracker.yVelocity
        yVelocity = if (abs(yVelocity) < mMinFlingVelocity) 0f else (-mMaxFlingVelocity.toFloat()).coerceAtLeast(yVelocity.coerceAtMost(mMaxFlingVelocity.toFloat()))
        if (yVelocity != 0f) {
            mViewFling.fling(yVelocity.toInt())
        } else {
            setScrollState(SCROLL_STATE_IDLE)
        }
        resetTouch()
    }

    fun clear(){
        resetTouch()
        mViewFling.stop()
        mScrollState = SCROLL_STATE_IDLE
    }

    private fun setScrollState(state: Int) {
        if (state == mScrollState) {
            return
        }
        mScrollState = state
        if (state != SCROLL_STATE_SETTLING) {
            mViewFling.stop()
        }
    }

    private fun resetTouch() {
        mVelocityTracker?.clear()
    }

    inner class ViewFling(context: Context) : Runnable {
        private val sQuinticInterpolator = Interpolator { f: Float ->
            val t = f - 1.0f
            t * t * t * t * t + 1.0f
        }
        private var mLastFlingY = 0
        private var mEatRunOnAnimationRequest = false
        private var mReSchedulePostAnimationCallback = false
        private var mScroller = OverScroller(context, sQuinticInterpolator)


        override fun run() {
            disableRunOnAnimationRequests()
            val scroller = mScroller
            if (scroller.computeScrollOffset()) {
                val y = scroller.currY
                val dy = y - mLastFlingY
                mLastFlingY = y
                constrainScrollBy(0, dy)
                postOnAnimation()
            }
            enableRunOnAnimationRequests()
        }

        fun fling(velocityY: Int) {
            mLastFlingY = 0
            setScrollState(SCROLL_STATE_SETTLING)
            mScroller.fling(0, 0, 0, velocityY, Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE)
            postOnAnimation()
        }

        fun stop() {
            targetView.removeCallbacks(this)
            mScroller.abortAnimation()
        }

        private fun disableRunOnAnimationRequests() {
            mReSchedulePostAnimationCallback = false
            mEatRunOnAnimationRequest = true
        }

        private fun enableRunOnAnimationRequests() {
            mEatRunOnAnimationRequest = false
            if (mReSchedulePostAnimationCallback) {
                postOnAnimation()
            }
        }

        private fun postOnAnimation() {
            if (mEatRunOnAnimationRequest) {
                mReSchedulePostAnimationCallback = true
            } else {
                targetView.removeCallbacks(this)
                ViewCompat.postOnAnimation(targetView, this)
            }
        }
    }
}