package com.zj.player.img.scale;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.view.ViewConfiguration;

@SuppressWarnings("unused")
public class ImageViewTouch extends ImageViewTouchBase {

    public static final int left = 1;
    public static final int top = 1 << 1;
    public static final int right = 1 << 2;
    public static final int bottom = 1 << 3;

    static final float SCROLL_DELTA_THRESHOLD = 1.0f;
    protected ScaleGestureDetector mScaleDetector;
    protected GestureDetector mGestureDetector;
    protected int mTouchSlop;
    protected float mScaleFactor;
    protected int mDoubleTapDirection;
    protected OnGestureListener mGestureListener;
    protected OnScaleGestureListener mScaleListener;

    protected ImageViewTouchEnableIn mDoubleTapEnabled = () -> true;
    protected ImageViewTouchEnableIn mScaleEnabled = () -> true;
    protected ImageViewTouchEnableIn mScrollEnabled = () -> true;
    protected ImageViewTouchEnableIn mTouchEnabled = () -> true;

    private int durationMs;
    private OnImageViewTouchDoubleTapListener mDoubleTapListener;
    private OnImageViewTouchSingleTapListener mSingleTapListener;

    public ImageViewTouch(Context context) {
        super(context);
    }

    public ImageViewTouch(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImageViewTouch(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void init(Context context, AttributeSet attrs, int defStyle) {
        super.init(context, attrs, defStyle);
        durationMs = 300;
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        mGestureListener = getGestureListener();
        mScaleListener = getScaleListener();
        mScaleDetector = new ScaleGestureDetector(getContext(), mScaleListener);
        mGestureDetector = new GestureDetector(getContext(), mGestureListener, null, true);
        mDoubleTapDirection = 1;
    }

    public void setDoubleTapListener(OnImageViewTouchDoubleTapListener listener) {
        mDoubleTapListener = listener;
    }

    public void setSingleTapListener(OnImageViewTouchSingleTapListener listener) {
        mSingleTapListener = listener;
    }

    public void setDoubleTapEnabled(ImageViewTouchEnableIn func) {
        mDoubleTapEnabled = func;
    }

    public void setScaleEnabled(ImageViewTouchEnableIn func) {
        mScaleEnabled = func;
    }

    public void setTouchEnabled(ImageViewTouchEnableIn func) {
        mTouchEnabled = func;
    }

    public void setScrollEnabled(ImageViewTouchEnableIn func) {
        mScrollEnabled = func;
    }

    public boolean getDoubleTapEnable() {
        return mTouchEnabled.enable() && mDoubleTapEnabled.enable();
    }

    public boolean getScaleEnable() {
        return mTouchEnabled.enable() && mScaleEnabled.enable();
    }

    public boolean isScrollDisAble() {
        return !mTouchEnabled.enable() || !mScrollEnabled.enable();
    }

    protected OnGestureListener getGestureListener() {
        return new GestureListener();
    }

    protected OnScaleGestureListener getScaleListener() {
        return new ScaleListener();
    }

    @Override
    protected void _setImageDrawable(final Drawable drawable, final Matrix initial_matrix, float min_zoom, float max_zoom) {
        super._setImageDrawable(drawable, initial_matrix, min_zoom, max_zoom);
        mScaleFactor = getMaxScale() / 3;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mTouchEnabled.enable()) return false;
        mScaleDetector.onTouchEvent(event);
        boolean detect = true;
        if (!mScaleDetector.isInProgress()) {
            mGestureDetector.onTouchEvent(event);
        }

        int action = event.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return onUp(event);
        }
        return true;
    }

    @Override
    protected void onZoomAnimationCompleted(float scale) {
        if (scale < getMinScale()) {
            zoomTo(getMinScale(), 50);
        }
    }

    protected float onDoubleTapPost(float scale, float maxZoom) {
        if (mDoubleTapDirection == 1) {
            if ((scale + (mScaleFactor * 2)) <= maxZoom) {
                return scale + mScaleFactor;
            } else {
                mDoubleTapDirection = -1;
                return maxZoom;
            }
        } else {
            mDoubleTapDirection = 1;
            return getMinScale();
        }
    }

    public boolean onSingleTapConfirmed(MotionEvent e) {
        return true;
    }

    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (getScale() == 1f) return false;
        mUserScaled = true;
        scrollBy(-distanceX, -distanceY);
        invalidate();
        return true;
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        float diffX = e2.getX() - e1.getX();
        float diffY = e2.getY() - e1.getY();
        if (Math.abs(velocityX) > 800 || Math.abs(velocityY) > 800) {
            mUserScaled = true;
            scrollBy(diffX / 2, diffY / 2, durationMs);
            invalidate();
            return true;
        }
        return false;
    }

    public boolean onDown(MotionEvent e) {
        return true;
    }

    public boolean onUp(MotionEvent e) {
        if (getScale() < getMinScale()) {
            zoomTo(getMinScale(), 50);
        }
        return true;
    }

    public boolean onSingleTapUp(MotionEvent e) {
        return true;
    }

    /**
     * Determines whether this ImageViewTouch can be scrolled.
     *
     * @param direction - positive direction value means scroll from right to left,
     *                  negative value means scroll from left to right
     * @return true if there is some more place to scroll, false - otherwise.
     */
    public boolean canScroll(int direction, boolean elastic) {
        RectF bitmapRect = getBitmapRect();
        updateRect(bitmapRect, mScrollRect);
        Rect imageViewRect = new Rect();
        getGlobalVisibleRect(imageViewRect);

        boolean ltr = (direction & left) != 0;
        boolean rtl = (direction & right) != 0;
        boolean ttb = (direction & top) != 0;
        boolean btt = (direction & bottom) != 0;

        if (null == bitmapRect) {
            return false;
        }
        boolean spaceInRight = bitmapRect.right - (elastic ? 30 : 0) > imageViewRect.right;
        boolean spaceInLeft = bitmapRect.left + (elastic ? 30 : 0) < imageViewRect.left;
        boolean spaceInTop = bitmapRect.top + (elastic ? 30 : 0) < imageViewRect.top;
        boolean spaceInBottom = bitmapRect.bottom - (elastic ? 30 : 0) > imageViewRect.bottom;
        return (ltr && spaceInLeft) || (rtl && spaceInRight) || (ttb && spaceInTop) || (btt && spaceInBottom);
    }

    public class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (null != mSingleTapListener) {
                mSingleTapListener.onSingleTapConfirmed();
            }
            return ImageViewTouch.this.onSingleTapConfirmed(e);
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (getDoubleTapEnable()) {
                mUserScaled = true;
                float targetScale = getScale();
                targetScale = onDoubleTapPost(targetScale, getMaxScale());
                targetScale = Math.min(getMaxScale(), Math.max(targetScale, getMinScale()));
                zoomTo(targetScale, e.getX(), e.getY(), DEFAULT_ANIMATION_DURATION);
                invalidate();
            }

            if (null != mDoubleTapListener) {
                mDoubleTapListener.onDoubleTap();
            }
            return super.onDoubleTap(e);
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (isLongClickable()) {
                if (!mScaleDetector.isInProgress()) {
                    setPressed(true);
                    performLongClick();
                }
            }
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (isScrollDisAble() || !scrollAble()) return false;
            if (e1 == null || e2 == null) return false;
            if (e1.getPointerCount() > 1 || e2.getPointerCount() > 1) return false;
            if (mScaleDetector.isInProgress()) return false;
            return ImageViewTouch.this.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (isScrollDisAble()) return false;
            if (e1.getPointerCount() > 1 || e2.getPointerCount() > 1) return false;
            if (mScaleDetector.isInProgress()) return false;
            if (getScale() == 1f) return false;

            return ImageViewTouch.this.onFling(e1, e2, velocityX, velocityY);
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return ImageViewTouch.this.onSingleTapUp(e);
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return ImageViewTouch.this.onDown(e);
        }
    }


    public class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        private boolean mScaled = false;

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float span = detector.getCurrentSpan() - detector.getPreviousSpan();
            float targetScale = getScale() * detector.getScaleFactor();

            if (getScaleEnable()) {
                if (mScaled && span != 0) {
                    mUserScaled = true;
                    targetScale = Math.min(getMaxScale(), Math.max(targetScale, getMinScale() - 0.1f));
                    zoomTo(targetScale, detector.getFocusX(), detector.getFocusY());
                    mDoubleTapDirection = 1;
                    invalidate();
                    return true;
                }
                if (!mScaled) mScaled = true;
            }
            return true;
        }
    }

    public boolean scrollAble() {
        return canScroll(top | right | left | bottom, false);
    }

    public interface OnImageViewTouchDoubleTapListener {

        void onDoubleTap();
    }

    public interface OnImageViewTouchSingleTapListener {

        void onSingleTapConfirmed();
    }
}
