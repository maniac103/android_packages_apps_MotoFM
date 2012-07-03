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
    public static final int MARQUEE_PIXELS_PER_REFRESH = 1;
    public static final int MARQUEE_PIXELS_PER_SECOND = 30;
    public static final int MARQUEE_UPDATES_PER_SECOND = 20;
    public static final int MARQUEE_REFRESH_DELAY = 50;
    public static final int MARQUEE_START_DELAY = 2000;

    public static final int MARQUEE_START = 1;
    public static final int MARQUEE_TICK = 2;

    private int mAscent;
    private int mLastX;
    private int mScrollLength;
    private int mScrollPos;
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
        if (mAscent == 2) {
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
        mMarqueeHandler.removeCallbacks(mTickEvent);
        mScrollPos = 0;
        mScrollLength = (int) (mTextPaint.measureText(mText) - getWidth());
        invalidate();
        mMarqueeHandler.postDelayed(mTickEvent, MARQUEE_START_DELAY);
    }

    public void marqueeTick() {
        int delay = MARQUEE_REFRESH_DELAY;
        mScrollPos++;
        invalidate();
        if (mScrollPos > mScrollLength) {
            mScrollPos = 0;
            delay = MARQUEE_START_DELAY;
        }
        mMarqueeHandler.postDelayed(mTickEvent, delay);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.translate(mScrollPos, 0);
        canvas.drawText(mText, getPaddingLeft(), getPaddingTop() - mAscent, mTextPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(measureWidth(widthMeasureSpec), measureHeight(heightMeasureSpec));
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean superResult = super.onTouchEvent(ev);
        switch (ev.getAction()) {
            case 0:
                mTouchDown = true;
                mLastX = (int) ev.getX();
                marqueePause();
                return true;
            case 2:
                if (mTouchDown) {
                    int x = (int) ev.getX();
                    mScrollPos -= x - mLastX;
                    mLastX = x;
                    invalidate();
                    return true;
                }
                break;
            case 1:
            case 3:
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
