package com.zj.player.UT;

import android.media.MediaCodec;
import android.opengl.GLSurfaceView;

import androidx.annotation.IntDef;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static android.media.MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT;
import static android.media.MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING;

/**
 * Video scaling modes for {@link MediaCodec}-based {@link GLSurfaceView.Renderer}s. One of
 * {@link MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT} or {@link MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING}.
 */
@SuppressWarnings("JavadocReference")
@Documented
@Retention(RetentionPolicy.SOURCE)
@IntDef(value = {VIDEO_SCALING_MODE_SCALE_TO_FIT, VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING})
public @interface ScalingMode {
}