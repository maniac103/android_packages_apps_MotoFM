package com.motorola.fmradio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class MarqueeText extends View {
    public static final int MARQUEE_PIXELS_PER_REFRESH = 3;
    public static final int MARQUEE_REFRESH_DELAY = 50;
    public static final int MARQUEE_START_DELAY = 2000;

    public static final int MARQUEE_START = 1;
    public static final int MARQUEE_TICK = 2;

    private int mAscent;
    private int mLastX;
    private int mScrollLength;
    private int mScrollPos;
    private boolean mScrollForward;
    private String mText;
    private Paint mTextPaint;
    private boolean mTouchDown = false;
    private Handler mMarqueeHandler = new Handler();

    private Runnable mTickEvent = new Runnable() {
        @Override
        public void run() {
            marqueeTick();
        }
    };

    public MarqueeText(Context context) {
        super(context);
        initMarqueeText();
    }

    public MarqueeText(Context context, AttributeSet attrs) {
        super(context, attrs);
        initMarqueeText();
    }

    private final void initMarqueeText() {
        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(16.0F);
        mTextPaint.setColor(Color.rgb(156, 0, 0));
        setPadding(3, 3, 3, 3);
    }

    private int measureHeight(int measureSpec) {
        int result = 0;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        mAscent = (int) mTextPaint.ascent();
        if (specMode == MeasureSpec.EXACTLY) {
            return specSize;
        }
        result = (int) (getPaddingTop() + getPaddingBottom() + mTextPaint.descent() + mAscent);
        if (specMode == MeasureSpec.AT_MOST) {
            return result;
        }
        return Math.min(result, specSize);
    }

    private int measureWidth(int measureSpec) {
        int result = 0;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        if (specMode == MeasureSpec.EXACTLY) {
            return specSize;
        }
        result = (int) mTextPaint.measureText(mText) + getPaddingLeft() + getPaddingRight();
        if (specMode == MeasureSpec.AT_MOST) {
            return result;
        }
        return Math.min(result, specSize);
    }

    public void marqueePause() {
        mMarqueeHandler.removeCallbacks(mTickEvent);
    }

    public void marqueeResume() {
        mMarqueeHandler.postDelayed(mTickEvent, MARQUEE_REFRESH_DELAY);
    }

    public void marqueeStart() {
        marqueePause();
        mScrollPos = 0;
        mScrollForward = true;
        mScrollLength = (int) (mTextPaint.measureText(mText) - getWidth());
        invalidate();
        if (mScrollLength > 0) {
            marqueeResume();
        }
    }

    public void marqueeTick() {
        if (mScrollForward) {
            mScrollPos += MARQUEE_PIXELS_PER_REFRESH;
            if (mScrollPos > mScrollLength) {
                mScrollPos = mScrollLength;
                mScrollForward = false;
            }
        } else {
            mScrollPos -= MARQUEE_PIXELS_PER_REFRESH;
            if (mScrollPos < 0) {
                mScrollPos = 0;
                mScrollForward = true;
            }
        }

        invalidate();
        mMarqueeHandler.postDelayed(mTickEvent, MARQUEE_REFRESH_DELAY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mText != null) {
            canvas.translate(-mScrollPos, 0);
            canvas.drawText(mText, getPaddingLeft(), getPaddingTop() - mAscent, mTextPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(measureWidth(widthMeasureSpec), measureHeight(heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            marqueeStart();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean superResult = super.onTouchEvent(ev);
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTouchDown = true;
                mLastX = (int) ev.getX();
                marqueePause();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mTouchDown) {
                    int x = (int) ev.getX();
                    mScrollPos -= x - mLastX;
                    mLastX = x;
                    invalidate();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTouchDown = false;
                marqueeResume();
                return true;
        }

        return superResult;
    }

    public void setMarqueeSpeed(int pixelsPerSec) {
    }

    public void setText(String text) {
        mText = text;
        requestLayout();
        invalidate();
        marqueeStart();
    }

    public void setTextColor(int color) {
        mTextPaint.setColor(color);
        invalidate();
    }

    public void setTextSize(int size) {
        mTextPaint.setTextSize(size);
        requestLayout();
        invalidate();
    }
}
