package com.zj.videotest.refresh;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zj.videotest.R;
import com.zj.views.list.refresh.layout.api.RefreshLayoutIn;
import com.zj.views.list.refresh.layout.constant.SpinnerStyle;
import com.zj.views.list.refresh.layout.simple.SimpleComponent;
import com.zj.views.ut.DPUtils;

public class RefreshFooter extends SimpleComponent implements com.zj.views.list.refresh.layout.api.RefreshFooter {

    protected Paint mPaint;
    protected int mNormalColor = 0xffeeeeee;
    protected int mAnimatingColor = 0xffeea30f;
    protected int mNoMoreDataTextColor = 0xFFC0B7C3;
    protected float mCircleSpacing, curAlpha;
    protected long mStartTime = 0;
    protected boolean mIsStarted = false;
    protected boolean isNoMoreData = false;
    protected String noMoreDataText;

    protected TimeInterpolator mInterpolator = new AccelerateDecelerateInterpolator();

    public RefreshFooter(Context context) {
        this(context, null);
    }

    public RefreshFooter(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs, 0);
        mPaint = new Paint();
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAntiAlias(true);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTextSize(DPUtils.sp2px(12));
        mSpinnerStyle = SpinnerStyle.Translate;
        mCircleSpacing = DPUtils.dp2px(12);
        noMoreDataText = context.getResources().getString(R.string.r_main_feed_list_load_more_no_data);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final int width = getWidth();
        final int height = getHeight();
        if (isNoMoreData) {
            canvas.save();
            Paint.FontMetrics m = mPaint.getFontMetrics();
            float centerY = height / 2f;
            float textY = centerY - (m.bottom - m.top) / 2f - m.top;
            float textX = width / 2f;
            mPaint.setColor(mNoMoreDataTextColor);
            canvas.drawText(noMoreDataText, textX, textY, mPaint);
            float textLen = mPaint.measureText(noMoreDataText);
            float textR = textLen / 2f + mCircleSpacing;
            canvas.drawLine(mCircleSpacing, centerY - 1, textX - textR, centerY, mPaint);
            canvas.drawLine(textX + textR, centerY - 1, width - mCircleSpacing, centerY, mPaint);
            canvas.restore();
        } else {
            float radius = (Math.min(width, height) - mCircleSpacing) / 6f;
            float x = width / 2f - (radius * 2 + mCircleSpacing);
            float y = height / 2f;
            mPaint.setAlpha((int) (curAlpha * 255f));
            final long now = System.currentTimeMillis();
            for (int i = 0; i < 3; i++) {
                long time = now - mStartTime - 120 * (i + 1);
                float percent = time > 0 ? ((time % 750) / 750f) : 0;
                percent = mInterpolator.getInterpolation(percent);

                canvas.save();
                float translateX = x + (radius * 2) * i + mCircleSpacing * i;
                canvas.translate(translateX, y);
                if (percent < 0.5) {
                    float scale = 1 - percent * 2 * 0.7f;
                    canvas.scale(scale, scale);
                } else {
                    float scale = percent * 2 * 0.7f - 0.4f;
                    canvas.scale(scale, scale);
                }
                canvas.drawCircle(0, 0, radius, mPaint);
                canvas.restore();
            }
        }
        super.dispatchDraw(canvas);
        if (mIsStarted) {
            invalidate();
        }
    }

    @Override
    public void onMoving(boolean isDragging, float percent, int offset, int height, int maxDragHeight) {
        super.onMoving(isDragging, percent, offset, height, maxDragHeight);
        if (percent < 0.6f) curAlpha = 0;
        else curAlpha = Math.min(1, (percent - 0.6f) / 0.6f);
    }

    @Override
    public void onStartAnimator(@NonNull RefreshLayoutIn refreshLayout, int height, int maxDragHeight) {
        super.onStartAnimator(refreshLayout, height, maxDragHeight);
        if (mIsStarted) return;
        invalidate();
        mIsStarted = true;
        mStartTime = System.currentTimeMillis();
        mPaint.setColor(mAnimatingColor);
    }

    @Override
    public int onFinish(@NonNull RefreshLayoutIn layout, boolean success) {
        mIsStarted = false;
        mStartTime = 0;
        mPaint.setColor(mNormalColor);
        return 0;
    }

    @Override
    public boolean setNoMoreData(boolean noMoreData) {
        isNoMoreData = noMoreData;
        if (!mIsStarted) invalidate();
        return false;
    }

}
