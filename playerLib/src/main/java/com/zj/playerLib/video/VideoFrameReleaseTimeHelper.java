//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.video;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Handler.Callback;
import android.view.Choreographer;
import android.view.Display;
import android.view.WindowManager;
import android.view.Choreographer.FrameCallback;
import androidx.annotation.Nullable;
import com.zj.playerLib.util.Util;

@TargetApi(16)
public final class VideoFrameReleaseTimeHelper {
    private static final long CHOREOGRAPHER_SAMPLE_DELAY_MILLIS = 500L;
    private static final long MAX_ALLOWED_DRIFT_NS = 20000000L;
    private static final long VSYNC_OFFSET_PERCENTAGE = 80L;
    private static final int MIN_FRAMES_FOR_ADJUSTMENT = 6;
    private final WindowManager windowManager;
    private final VSyncSampler vsyncSampler;
    private final DefaultDisplayListener displayListener;
    private long vsyncDurationNs;
    private long vsyncOffsetNs;
    private long lastFramePresentationTimeUs;
    private long adjustedLastFrameTimeNs;
    private long pendingAdjustedFrameTimeNs;
    private boolean haveSync;
    private long syncUnadjustedReleaseTimeNs;
    private long syncFramePresentationTimeNs;
    private long frameCount;

    public VideoFrameReleaseTimeHelper() {
        this((Context)null);
    }

    public VideoFrameReleaseTimeHelper(@Nullable Context context) {
        if (context != null) {
            context = context.getApplicationContext();
            this.windowManager = (WindowManager)context.getSystemService("window");
        } else {
            this.windowManager = null;
        }

        if (this.windowManager != null) {
            this.displayListener = Util.SDK_INT >= 17 ? this.maybeBuildDefaultDisplayListenerV17(context) : null;
            this.vsyncSampler = VSyncSampler.getInstance();
        } else {
            this.displayListener = null;
            this.vsyncSampler = null;
        }

        this.vsyncDurationNs = -9223372036854775807L;
        this.vsyncOffsetNs = -9223372036854775807L;
    }

    public void enable() {
        this.haveSync = false;
        if (this.windowManager != null) {
            this.vsyncSampler.addObserver();
            if (this.displayListener != null) {
                this.displayListener.register();
            }

            this.updateDefaultDisplayRefreshRateParams();
        }

    }

    public void disable() {
        if (this.windowManager != null) {
            if (this.displayListener != null) {
                this.displayListener.unregister();
            }

            this.vsyncSampler.removeObserver();
        }

    }

    public long adjustReleaseTime(long framePresentationTimeUs, long unadjustedReleaseTimeNs) {
        long framePresentationTimeNs = framePresentationTimeUs * 1000L;
        long adjustedFrameTimeNs = framePresentationTimeNs;
        long adjustedReleaseTimeNs = unadjustedReleaseTimeNs;
        long sampledVsyncTimeNs;
        long snappedTimeNs;
        if (this.haveSync) {
            if (framePresentationTimeUs != this.lastFramePresentationTimeUs) {
                ++this.frameCount;
                this.adjustedLastFrameTimeNs = this.pendingAdjustedFrameTimeNs;
            }

            if (this.frameCount >= 6L) {
                sampledVsyncTimeNs = (framePresentationTimeNs - this.syncFramePresentationTimeNs) / this.frameCount;
                snappedTimeNs = this.adjustedLastFrameTimeNs + sampledVsyncTimeNs;
                if (this.isDriftTooLarge(snappedTimeNs, unadjustedReleaseTimeNs)) {
                    this.haveSync = false;
                } else {
                    adjustedFrameTimeNs = snappedTimeNs;
                    adjustedReleaseTimeNs = this.syncUnadjustedReleaseTimeNs + snappedTimeNs - this.syncFramePresentationTimeNs;
                }
            } else if (this.isDriftTooLarge(framePresentationTimeNs, unadjustedReleaseTimeNs)) {
                this.haveSync = false;
            }
        }

        if (!this.haveSync) {
            this.syncFramePresentationTimeNs = framePresentationTimeNs;
            this.syncUnadjustedReleaseTimeNs = unadjustedReleaseTimeNs;
            this.frameCount = 0L;
            this.haveSync = true;
        }

        this.lastFramePresentationTimeUs = framePresentationTimeUs;
        this.pendingAdjustedFrameTimeNs = adjustedFrameTimeNs;
        if (this.vsyncSampler != null && this.vsyncDurationNs != -9223372036854775807L) {
            sampledVsyncTimeNs = this.vsyncSampler.sampledVsyncTimeNs;
            if (sampledVsyncTimeNs == -9223372036854775807L) {
                return adjustedReleaseTimeNs;
            } else {
                snappedTimeNs = closestVsync(adjustedReleaseTimeNs, sampledVsyncTimeNs, this.vsyncDurationNs);
                return snappedTimeNs - this.vsyncOffsetNs;
            }
        } else {
            return adjustedReleaseTimeNs;
        }
    }

    @TargetApi(17)
    private VideoFrameReleaseTimeHelper.DefaultDisplayListener maybeBuildDefaultDisplayListenerV17(Context context) {
        DisplayManager manager = (DisplayManager)context.getSystemService("display");
        return manager == null ? null : new DefaultDisplayListener(manager);
    }

    private void updateDefaultDisplayRefreshRateParams() {
        Display defaultDisplay = this.windowManager.getDefaultDisplay();
        if (defaultDisplay != null) {
            double defaultDisplayRefreshRate = (double)defaultDisplay.getRefreshRate();
            this.vsyncDurationNs = (long)(1.0E9D / defaultDisplayRefreshRate);
            this.vsyncOffsetNs = this.vsyncDurationNs * 80L / 100L;
        }

    }

    private boolean isDriftTooLarge(long frameTimeNs, long releaseTimeNs) {
        long elapsedFrameTimeNs = frameTimeNs - this.syncFramePresentationTimeNs;
        long elapsedReleaseTimeNs = releaseTimeNs - this.syncUnadjustedReleaseTimeNs;
        return Math.abs(elapsedReleaseTimeNs - elapsedFrameTimeNs) > 20000000L;
    }

    private static long closestVsync(long releaseTime, long sampledVsyncTime, long vsyncDuration) {
        long vsyncCount = (releaseTime - sampledVsyncTime) / vsyncDuration;
        long snappedTimeNs = sampledVsyncTime + vsyncDuration * vsyncCount;
        long snappedBeforeNs;
        long snappedAfterNs;
        if (releaseTime <= snappedTimeNs) {
            snappedBeforeNs = snappedTimeNs - vsyncDuration;
            snappedAfterNs = snappedTimeNs;
        } else {
            snappedBeforeNs = snappedTimeNs;
            snappedAfterNs = snappedTimeNs + vsyncDuration;
        }

        long snappedAfterDiff = snappedAfterNs - releaseTime;
        long snappedBeforeDiff = releaseTime - snappedBeforeNs;
        return snappedAfterDiff < snappedBeforeDiff ? snappedAfterNs : snappedBeforeNs;
    }

    private static final class VSyncSampler implements FrameCallback, Callback {
        public volatile long sampledVsyncTimeNs = -9223372036854775807L;
        private static final int CREATE_CHOREOGRAPHER = 0;
        private static final int MSG_ADD_OBSERVER = 1;
        private static final int MSG_REMOVE_OBSERVER = 2;
        private static final VSyncSampler INSTANCE = new VSyncSampler();
        private final Handler handler;
        private final HandlerThread choreographerOwnerThread = new HandlerThread("ChoreographerOwner:Handler");
        private Choreographer choreographer;
        private int observerCount;

        public static VSyncSampler getInstance() {
            return INSTANCE;
        }

        private VSyncSampler() {
            this.choreographerOwnerThread.start();
            this.handler = Util.createHandler(this.choreographerOwnerThread.getLooper(), this);
            this.handler.sendEmptyMessage(0);
        }

        public void addObserver() {
            this.handler.sendEmptyMessage(1);
        }

        public void removeObserver() {
            this.handler.sendEmptyMessage(2);
        }

        public void doFrame(long vsyncTimeNs) {
            this.sampledVsyncTimeNs = vsyncTimeNs;
            this.choreographer.postFrameCallbackDelayed(this, 500L);
        }

        public boolean handleMessage(Message message) {
            switch(message.what) {
            case 0:
                this.createChoreographerInstanceInternal();
                return true;
            case 1:
                this.addObserverInternal();
                return true;
            case 2:
                this.removeObserverInternal();
                return true;
            default:
                return false;
            }
        }

        private void createChoreographerInstanceInternal() {
            this.choreographer = Choreographer.getInstance();
        }

        private void addObserverInternal() {
            ++this.observerCount;
            if (this.observerCount == 1) {
                this.choreographer.postFrameCallback(this);
            }

        }

        private void removeObserverInternal() {
            --this.observerCount;
            if (this.observerCount == 0) {
                this.choreographer.removeFrameCallback(this);
                this.sampledVsyncTimeNs = -9223372036854775807L;
            }

        }
    }

    @TargetApi(17)
    private final class DefaultDisplayListener implements DisplayListener {
        private final DisplayManager displayManager;

        public DefaultDisplayListener(DisplayManager displayManager) {
            this.displayManager = displayManager;
        }

        public void register() {
            this.displayManager.registerDisplayListener(this, (Handler)null);
        }

        public void unregister() {
            this.displayManager.unregisterDisplayListener(this);
        }

        public void onDisplayAdded(int displayId) {
        }

        public void onDisplayRemoved(int displayId) {
        }

        public void onDisplayChanged(int displayId) {
            if (displayId == 0) {
                VideoFrameReleaseTimeHelper.this.updateDefaultDisplayRefreshRateParams();
            }

        }
    }
}
