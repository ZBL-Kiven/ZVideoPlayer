package com.zj.playerLib.video;

public interface VideoListener {

    default void onVideoSizeChanged(int width, int height, int unAppliedRotationDegrees, float pixelWidthHeightRatio) {
    }

    default void onSurfaceSizeChanged(int width, int height) {
    }

    default void onRenderedFirstFrame() {
    }
}
