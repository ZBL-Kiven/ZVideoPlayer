package com.zj.player.base

import com.zj.playerLib.C
import com.zj.playerLib.DefaultLoadControl.*
import com.zj.playerLib.LoadControl
import com.zj.playerLib.Renderer
import com.zj.playerLib.source.TrackGroupArray
import com.zj.playerLib.trackselection.TrackSelectionArray
import com.zj.playerLib.upstream.Allocator
import com.zj.playerLib.upstream.DefaultAllocator
import com.zj.playerLib.util.Assertions
import com.zj.playerLib.util.PriorityTaskManager
import com.zj.playerLib.util.Util

/**
 * @author ZJJ on 2020/6/22.
 * The default [LoadControl] implementation. more configrations can used than Default.
 * */
@Suppress("unused", "MemberVisibilityCanBePrivate", "SpellCheckingInspection")
internal class VideoLoadControl internal constructor(allocator: DefaultAllocator, minBufferMs: Int, maxBufferMs: Int, bufferForPlaybackMs: Int, bufferForPlaybackAfterRebufferMs: Int, targetBufferBytes: Int, prioritizeTimeOverSizeThresholds: Boolean, priorityTaskManager: PriorityTaskManager?, backBufferDurationMs: Int, retainBackBufferFromKeyframe: Boolean) : LoadControl {
    /** Builder for [VideoLoadControl].  */
    class Builder {
        private var allocator: DefaultAllocator? = null
        private var minBufferMs: Int
        private var maxBufferMs: Int
        private var bufferForPlaybackMs: Int
        private var bufferForPlaybackAfterRebufferMs: Int
        private var targetBufferBytes: Int
        private var prioritizeTimeOverSizeThresholds: Boolean
        private var priorityTaskManager: PriorityTaskManager?
        private var backBufferDurationMs: Int
        private var retainBackBufferFromKeyframe: Boolean
        private var createDefaultLoadControlCalled = false

        /**
         * Sets the [DefaultAllocator] used by the loader.
         *
         * @param allocator The [DefaultAllocator].
         * @return This builder, for convenience.
         * @throws IllegalStateException If [.createDefaultLoadControl] has already been called.
         */
        fun setAllocator(allocator: DefaultAllocator?): Builder {
            Assertions.checkState(!createDefaultLoadControlCalled)
            this.allocator = allocator
            return this
        }

        /**
         * Sets the buffer duration parameters.
         *
         * @param minBufferMs The minimum duration of media that the player will attempt to ensure is
         * buffered at all times, in milliseconds.
         * @param maxBufferMs The maximum duration of media that the player will attempt to buffer, in
         * milliseconds.
         * @param bufferForPlaybackMs The duration of media that must be buffered for playback to start
         * or resume following a user action such as a seek, in milliseconds.
         * @param bufferForPlaybackAfterRebufferMs The default duration of media that must be buffered
         * for playback to resume after a rebuffer, in milliseconds. A rebuffer is defined to be
         * caused by buffer depletion rather than a user action.
         * @return This builder, for convenience.
         * @throws IllegalStateException If [.createDefaultLoadControl] has already been called.
         */
        fun setBufferDurationsMs(minBufferMs: Int, maxBufferMs: Int, bufferForPlaybackMs: Int, bufferForPlaybackAfterRebufferMs: Int): Builder {
            Assertions.checkState(!createDefaultLoadControlCalled)
            this.minBufferMs = minBufferMs
            this.maxBufferMs = maxBufferMs
            this.bufferForPlaybackMs = bufferForPlaybackMs
            this.bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs
            return this
        }

        /**
         * Sets the target buffer size in bytes. If set to [C.LENGTH_UNSET], the target buffer
         * size will be calculated based on the selected tracks.
         *
         * @param targetBufferBytes The target buffer size in bytes.
         * @return This builder, for convenience.
         * @throws IllegalStateException If [.createDefaultLoadControl] has already been called.
         */
        fun setTargetBufferBytes(targetBufferBytes: Int): Builder {
            Assertions.checkState(!createDefaultLoadControlCalled)
            this.targetBufferBytes = targetBufferBytes
            return this
        }

        /**
         * Sets whether the load control prioritizes buffer time constraints over buffer size
         * constraints.
         *
         * @param prioritizeTimeOverSizeThresholds Whether the load control prioritizes buffer time
         * constraints over buffer size constraints.
         * @return This builder, for convenience.
         * @throws IllegalStateException If [.createDefaultLoadControl] has already been called.
         */
        fun setPrioritizeTimeOverSizeThresholds(prioritizeTimeOverSizeThresholds: Boolean): Builder {
            Assertions.checkState(!createDefaultLoadControlCalled)
            this.prioritizeTimeOverSizeThresholds = prioritizeTimeOverSizeThresholds
            return this
        }

        /**
         * Sets the [PriorityTaskManager] to use.
         *
         * @param priorityTaskManager The [PriorityTaskManager] to use.
         * @return This builder, for convenience.
         * @throws IllegalStateException If [.createDefaultLoadControl] has already been called.
         */
        fun setPriorityTaskManager(priorityTaskManager: PriorityTaskManager?): Builder {
            Assertions.checkState(!createDefaultLoadControlCalled)
            this.priorityTaskManager = priorityTaskManager
            return this
        }

        /**
         * Sets the back buffer duration, and whether the back buffer is retained from the previous
         * keyframe.
         *
         * @param backBufferDurationMs The back buffer duration in milliseconds.
         * @param retainBackBufferFromKeyframe Whether the back buffer is retained from the previous
         * keyframe.
         * @return This builder, for convenience.
         * @throws IllegalStateException If [.createDefaultLoadControl] has already been called.
         */
        fun setBackBuffer(backBufferDurationMs: Int, retainBackBufferFromKeyframe: Boolean): Builder {
            Assertions.checkState(!createDefaultLoadControlCalled)
            this.backBufferDurationMs = backBufferDurationMs
            this.retainBackBufferFromKeyframe = retainBackBufferFromKeyframe
            return this
        }

        /** Creates a [VideoLoadControl].  */
        fun createDefaultLoadControl(minBufferMs: Int, maxBufferMs: Int, bufferForPlaybackMs: Int, bufferForPlaybackAfterBufferMs: Int): VideoLoadControl {
            createDefaultLoadControlCalled = true
            val allocator = this.allocator ?: run {
                val a = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE); allocator = a;a
            }
            return VideoLoadControl(allocator, minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterBufferMs, targetBufferBytes, prioritizeTimeOverSizeThresholds, priorityTaskManager, backBufferDurationMs, retainBackBufferFromKeyframe)
        }

        /** Constructs a new instance.  */
        init {
            minBufferMs = DEFAULT_MIN_BUFFER_MS
            maxBufferMs = DEFAULT_MAX_BUFFER_MS
            bufferForPlaybackMs = DEFAULT_BUFFER_FOR_PLAYBACK_MS
            bufferForPlaybackAfterRebufferMs = DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            targetBufferBytes = DEFAULT_TARGET_BUFFER_BYTES
            prioritizeTimeOverSizeThresholds = DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS
            priorityTaskManager = null
            backBufferDurationMs = DEFAULT_BACK_BUFFER_DURATION_MS
            retainBackBufferFromKeyframe = DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME
        }
    }

    private val allocator: DefaultAllocator
    private val minBufferUs: Long
    private val maxBufferUs: Long
    private val bufferForPlaybackUs: Long
    private val bufferForPlaybackAfterRebufferUs: Long
    private val targetBufferBytesOverwrite: Int
    private val prioritizeTimeOverSizeThresholds: Boolean
    private val priorityTaskManager: PriorityTaskManager?
    private val backBufferDurationUs: Long
    private val retainBackBufferFromKeyframe: Boolean
    private var targetBufferSize = 0
    private var isBuffering = false

    override fun onPrepared() {
        reset(false)
    }

    override fun onTracksSelected(renderers: Array<Renderer>, trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
        targetBufferSize = if (targetBufferBytesOverwrite == C.LENGTH_UNSET) calculateTargetBufferSize(renderers, trackSelections) else targetBufferBytesOverwrite
        allocator.setTargetBufferSize(targetBufferSize)
    }

    override fun onStopped() {
        reset(true)
    }

    override fun onReleased() {
        reset(true)
    }

    override fun getAllocator(): Allocator {
        return allocator
    }

    override fun getBackBufferDurationUs(): Long {
        return backBufferDurationUs
    }

    override fun retainBackBufferFromKeyframe(): Boolean {
        return retainBackBufferFromKeyframe
    }

    override fun shouldContinueLoading(bufferedDurationUs: Long, playbackSpeed: Float): Boolean {
        val targetBufferSizeReached = allocator.totalBytesAllocated >= targetBufferSize
        val wasBuffering = isBuffering
        var minBufferUs = minBufferUs
        if (playbackSpeed > 1) {
            val mediaDurationMinBufferUs = Util.getMediaDurationForPlayoutDuration(minBufferUs, playbackSpeed)
            minBufferUs = mediaDurationMinBufferUs.coerceAtMost(maxBufferUs)
        }
        if (bufferedDurationUs < minBufferUs) {
            isBuffering = prioritizeTimeOverSizeThresholds || !targetBufferSizeReached
        } else if (bufferedDurationUs >= maxBufferUs || targetBufferSizeReached) {
            isBuffering = false
        } // Else don't change the buffering state
        if (priorityTaskManager != null && isBuffering != wasBuffering) {
            if (isBuffering) {
                priorityTaskManager.add(C.PRIORITY_PLAYBACK)
            } else {
                priorityTaskManager.remove(C.PRIORITY_PLAYBACK)
            }
        }
        return isBuffering
    }

    override fun shouldStartPlayback(bufferedDurationUs: Long, playbackSpeed: Float, rebuffering: Boolean): Boolean {
        var bdu = bufferedDurationUs
        bdu = Util.getPlayoutDurationForMediaDuration(bdu, playbackSpeed)
        val minBufferDurationUs = if (rebuffering) bufferForPlaybackAfterRebufferUs else bufferForPlaybackUs
        return minBufferDurationUs <= 0 || bdu >= minBufferDurationUs || !prioritizeTimeOverSizeThresholds && allocator.totalBytesAllocated >= targetBufferSize
    }

    /**
     * Calculate target buffer size in bytes based on the selected tracks. The player will try not to
     * exceed this target buffer. Only used when `targetBufferBytes` is [C.LENGTH_UNSET].
     *
     * @param renderers The renderers for which the track were selected.
     * @param trackSelectionArray The selected tracks.
     * @return The target buffer size in bytes.
     */
    private fun calculateTargetBufferSize(renderers: Array<Renderer>, trackSelectionArray: TrackSelectionArray): Int {
        var targetBufferSize = 0
        for (i in renderers.indices) {
            if (trackSelectionArray[i] != null) {
                targetBufferSize += Util.getDefaultBufferSize(renderers[i].trackType)
            }
        }
        return targetBufferSize
    }

    private fun reset(resetAllocator: Boolean) {
        targetBufferSize = 0
        if (priorityTaskManager != null && isBuffering) {
            priorityTaskManager.remove(C.PRIORITY_PLAYBACK)
        }
        isBuffering = false
        if (resetAllocator) {
            allocator.reset()
        }
    }

    companion object {
        /**
         * The default target buffer size in bytes. The value ([C.LENGTH_UNSET]) means that the load
         * control will calculate the target buffer size based on the selected tracks.
         */
        const val DEFAULT_TARGET_BUFFER_BYTES = C.LENGTH_UNSET

        /** The default prioritization of buffer time constraints over size constraints.  */
        const val DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS = true

        /** The default back buffer duration in milliseconds.  */
        const val DEFAULT_BACK_BUFFER_DURATION_MS = 0

        /** The default for whether the back buffer is retained from the previous keyframe.  */
        const val DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME = false
        private fun assertGreaterOrEqual(value1: Int, value2: Int, name1: String, name2: String) {
            Assertions.checkArgument(value1 >= value2, "$name1 cannot be less than $name2")
        }
    }

    init {
        assertGreaterOrEqual(bufferForPlaybackMs, 0, "bufferForPlaybackMs", "0")
        assertGreaterOrEqual(bufferForPlaybackAfterRebufferMs, 0, "bufferForPlaybackAfterRebufferMs", "0")
        assertGreaterOrEqual(minBufferMs, bufferForPlaybackMs, "minBufferMs", "bufferForPlaybackMs")
        assertGreaterOrEqual(minBufferMs, bufferForPlaybackAfterRebufferMs, "minBufferMs", "bufferForPlaybackAfterRebufferMs")
        assertGreaterOrEqual(maxBufferMs, minBufferMs, "maxBufferMs", "minBufferMs")
        assertGreaterOrEqual(backBufferDurationMs, 0, "backBufferDurationMs", "0")
        this.allocator = allocator
        minBufferUs = C.msToUs(minBufferMs.toLong())
        maxBufferUs = C.msToUs(maxBufferMs.toLong())
        bufferForPlaybackUs = C.msToUs(bufferForPlaybackMs.toLong())
        bufferForPlaybackAfterRebufferUs = C.msToUs(bufferForPlaybackAfterRebufferMs.toLong())
        targetBufferBytesOverwrite = targetBufferBytes
        this.prioritizeTimeOverSizeThresholds = prioritizeTimeOverSizeThresholds
        this.priorityTaskManager = priorityTaskManager
        backBufferDurationUs = C.msToUs(backBufferDurationMs.toLong())
        this.retainBackBufferFromKeyframe = retainBackBufferFromKeyframe
    }
}