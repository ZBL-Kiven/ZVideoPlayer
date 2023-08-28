package com.zj.player.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zj.player.ut.ResizeMode;

import static com.zj.player.ut.Constance.RESIZE_MODE_FILL;
import static com.zj.player.ut.Constance.RESIZE_MODE_FIT;
import static com.zj.player.ut.Constance.RESIZE_MODE_FIXED_HEIGHT;
import static com.zj.player.ut.Constance.RESIZE_MODE_FIXED_WIDTH;
import static com.zj.player.ut.Constance.RESIZE_MODE_ZOOM;


/**
 * @author zjj on 2020/6/22.
 * <p>
 * A FrameLayout that re-sizes itself to match a specified aspect ratio.
 */
@SuppressWarnings("unused")
public class AspectRatioFrameLayout extends FrameLayout {

    public AspectRatioFrameLayout(Context context) {
        this(context, null, 0);
    }

    public AspectRatioFrameLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AspectRatioFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        resizeMode = RESIZE_MODE_FIT;
        aspectRatioUpdateDispatcher = new AspectRatioUpdateDispatcher();
    }

    /**
     * Listener to be notified about changes of the aspect ratios of this view.
     */
    public interface AspectRatioListener {

        /**
         * Called when either the target aspect ratio or the view aspect ratio is updated.
         *
         * @param targetAspectRatio   The aspect ratio that has been set in {@link #setAspectRatio(float)}
         * @param naturalAspectRatio  The natural aspect ratio of this view (before its width and height
         *                            are modified to satisfy the target aspect ratio).
         * @param aspectRatioMismatch Whether the target and natural aspect ratios differ enough for
         *                            changing the resize mode to have an effect.
         */
        void onAspectRatioUpdated(float targetAspectRatio, float naturalAspectRatio, boolean aspectRatioMismatch);
    }

    /**
     * The {@link FrameLayout} will not resize itself if the fractional difference between its natural
     * aspect ratio and the requested aspect ratio falls below this threshold.
     *
     * <p>This tolerance allows the view to occupy the whole of the screen when the requested aspect
     * ratio is very close, but not exactly equal to, the aspect ratio of the screen. This may reduce
     * the number of view layers that need to be composited by the underlying system, which can help
     * to reduce power consumption.
     */
    private static final float MAX_ASPECT_RATIO_DEFORMATION_FRACTION = 0.01f;

    private final AspectRatioUpdateDispatcher aspectRatioUpdateDispatcher;

    private AspectRatioListener aspectRatioListener;

    private float videoAspectRatio;
    private @ResizeMode
    int resizeMode;


    /**
     * Sets the aspect ratio that this view should satisfy.
     *
     * @param widthHeightRatio The width to height ratio.
     */
    public void setAspectRatio(float widthHeightRatio) {
        if (this.videoAspectRatio != widthHeightRatio) {
            this.videoAspectRatio = widthHeightRatio;
            requestLayout();
        }
    }

    /**
     * Sets the {@link AspectRatioListener}.
     *
     * @param listener The listener to be notified about aspect ratios changes.
     */
    public void setAspectRatioListener(AspectRatioListener listener) {
        this.aspectRatioListener = listener;
    }

    /**
     * Returns the {@link ResizeMode}.
     */
    public @ResizeMode
    int getResizeMode() {
        return resizeMode;
    }

    /**
     * Sets the {@link ResizeMode}
     *
     * @param resizeMode The {@link ResizeMode}.
     */
    public void setResizeMode(@ResizeMode int resizeMode) {
        if (this.resizeMode != resizeMode) {
            this.resizeMode = resizeMode;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (videoAspectRatio <= 0) {
            return;
        }
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        float viewAspectRatio = (float) width / height;
        float aspectDeformation = videoAspectRatio / viewAspectRatio - 1;
        if (Math.abs(aspectDeformation) <= MAX_ASPECT_RATIO_DEFORMATION_FRACTION) {
            aspectRatioUpdateDispatcher.scheduleUpdate(videoAspectRatio, viewAspectRatio, false);
            return;
        }

        switch (resizeMode) {
            case RESIZE_MODE_FIXED_WIDTH:
                height = (int) (width / videoAspectRatio);
                break;
            case RESIZE_MODE_FIXED_HEIGHT:
                width = (int) (height * videoAspectRatio);
                break;
            case RESIZE_MODE_ZOOM:
                if (aspectDeformation > 0) {
                    width = (int) (height * videoAspectRatio);
                } else {
                    height = (int) (width / videoAspectRatio);
                }
                break;
            case RESIZE_MODE_FIT:
                if (aspectDeformation > 0) {
                    height = (int) (width / videoAspectRatio);
                } else {
                    width = (int) (height * videoAspectRatio);
                }
                break;
            case RESIZE_MODE_FILL:
            default:
                // Ignore target aspect ratio
                break;
        }
        aspectRatioUpdateDispatcher.scheduleUpdate(videoAspectRatio, viewAspectRatio, true);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    /**
     * Dispatches updates to {@link AspectRatioListener}.
     */
    private final class AspectRatioUpdateDispatcher implements Runnable {

        private float targetAspectRatio;
        private float naturalAspectRatio;
        private boolean aspectRatioMismatch;
        private boolean isScheduled;

        void scheduleUpdate(float targetAspectRatio, float naturalAspectRatio, boolean aspectRatioMismatch) {
            this.targetAspectRatio = targetAspectRatio;
            this.naturalAspectRatio = naturalAspectRatio;
            this.aspectRatioMismatch = aspectRatioMismatch;

            if (!isScheduled) {
                isScheduled = true;
                post(this);
            }
        }

        @Override
        public void run() {
            isScheduled = false;
            if (aspectRatioListener == null) {
                return;
            }
            aspectRatioListener.onAspectRatioUpdated(targetAspectRatio, naturalAspectRatio, aspectRatioMismatch);
        }
    }
}
