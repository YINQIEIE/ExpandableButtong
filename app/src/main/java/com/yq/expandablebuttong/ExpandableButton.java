package com.yq.expandablebuttong;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;


/**
 * Created by Administrator on 2017/12/27.
 * see http://blog.csdn.net/wbwjx/article/details/50583812
 */

public class ExpandableButton extends View {

    final int[] custom_attrs = new int[]{android.R.attr.textColor, android.R.attr.textSize};
    private Paint mTextPaint, menuTextPaint, bgPaint, circlePaint, ringPaint;
    private int mtextSize = 50;//文字大小
    private int textColor = Color.WHITE;
    private int bgColor = 0xFFFFE640;//整体背景色
    private int circlebgColor = 0xFFEA9F58;//圆形按钮颜色
    private int ringColor = 0xEA9F58;//圆环颜色
    private int ringWidth = 10;//呼吸环宽度
    private Point circleCenterPoint;//圆形按钮中心点坐标

    private int mWidth, mHeight, circleRadius;//宽、高、圆半径
    private RectF bgRectF;
    private ValueAnimator bgAnimation;
    private ValueAnimator breathingAnimation;

    private String[] menus = new String[]{"文字", "图片", "视频"};
    private float textBaseLineY;//文字绘制基线
    private int menuStartX;//菜单开始位置，
    private int textRectWidth;//每个菜单的文字区域，平分
    private boolean isOpen = false;//是否展开
    private boolean isAnimating;//动画是否正在执行
    private ValueAnimator textAlphaAnimation;
    private AnimatorSet set;//set.Reverse(); 在 api 26 才有
    private int bgAnimationTime = 300;
    private int textAnimationTime = 100;

    private int circlePosition = 1;//默认右边

    public ExpandableButton(Context context) {
        this(context, null);
    }

    public ExpandableButton(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initAttrs(context, attrs);
        init();
    }

    private void initAttrs(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ExpandableButton);
        bgColor = a.getColor(R.styleable.ExpandableButton_whole_bg_color, bgColor);
        circlebgColor = a.getColor(R.styleable.ExpandableButton_circle_btn_color, circlebgColor);
        mtextSize = a.getDimensionPixelSize(R.styleable.ExpandableButton_android_textSize, mtextSize);
        textColor = a.getColor(R.styleable.ExpandableButton_android_textColor, textColor);
        circlePosition = a.getInt(R.styleable.ExpandableButton_circle_position, circlePosition);
        Log.i("animation", circlePosition + "");
        a.recycle();
    }

    private void init() {
        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(mtextSize);
        mTextPaint.setColor(textColor);

        menuTextPaint = new Paint();
        menuTextPaint.setAntiAlias(true);
        menuTextPaint.setTextAlign(Paint.Align.CENTER);
        menuTextPaint.setTextSize(mtextSize);
        menuTextPaint.setColor(textColor);
        menuTextPaint.setAlpha(0);//防止第一次展开的时候闪一下

        bgPaint = new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setColor(bgColor);

        circlePaint = new Paint();
        circlePaint.setAntiAlias(true);
        circlePaint.setColor(circlebgColor);

        ringPaint = new Paint();
        ringPaint.setAntiAlias(true);
        ringPaint.setStrokeWidth(ringWidth);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(ringColor);

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mWidth = getMeasuredWidth();
        mHeight = getMeasuredHeight();

        circleRadius = mHeight / 2 - ringWidth;
        bgRectF = new RectF();

        circleCenterPoint = new Point();

        Paint.FontMetrics fontMet = mTextPaint.getFontMetrics();
        textBaseLineY = mHeight / 2 - (fontMet.ascent + fontMet.descent) / 2;

        if (isLeft()) {//左边
            circleCenterPoint.set(circleRadius + ringWidth, mHeight / 2);
            menuStartX = circleCenterPoint.y * 2;
        } else {//右边
            circleCenterPoint.set(mWidth - circleRadius - ringWidth, mHeight / 2);
            menuStartX = circleCenterPoint.y * 2;
        }
        textRectWidth = (mWidth - 3 * circleCenterPoint.y) / menus.length;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //画背景
        if (isOpen || (!isOpen && isAnimating)) {
            canvas.drawRoundRect(bgRectF, mHeight / 2, mHeight / 2, bgPaint);
            for (int i = 0; i < menus.length; i++) {
                canvas.drawText(menus[i], menuStartX + textRectWidth * i + textRectWidth / 2, textBaseLineY, menuTextPaint);
            }
        }
        //画圆形按钮及文字
        canvas.drawCircle(circleCenterPoint.x, circleCenterPoint.y, circleRadius, circlePaint);
        //画呼吸圆环
        //在画圆环时，圆环部分上的中心点为设置的半径，所以半径要为 circleRadius + ringWidth / 2
        canvas.drawCircle(circleCenterPoint.x, circleCenterPoint.y, circleRadius + ringWidth / 2, ringPaint);
        String btnText = "打开";
        if (isOpen)
            btnText = "关闭";
        if (isLeft())
            canvas.drawText(btnText, circleCenterPoint.x, textBaseLineY, mTextPaint);
        else
            canvas.drawText(btnText, mWidth - circleRadius - ringWidth, textBaseLineY, mTextPaint);
        startBreathing();

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isAnimating) return false;
        float touchX = event.getX();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!isOpen &&
                        ((isLeft() && touchX > 2 * circleCenterPoint.y)
                                || (!isLeft() && touchX < mWidth - mHeight)))//未展开点击菜单区域不处理
                    return false;
                break;

            case MotionEvent.ACTION_UP:
                if ((isLeft() && touchX < circleCenterPoint.y * 2) || (!isLeft() && touchX > mWidth - mHeight)) {//在圆形按钮的位置
                    if (isOpen)
                        close();
                    else
                        open();
                } else if (touchX < menuStartX) {
                    // to do nothing
                } else {
                    int clickPosition = (int) ((touchX - menuStartX) / textRectWidth);
                    Toast.makeText(getContext(), "item clicked is " + menus[clickPosition], Toast.LENGTH_LONG).show();
                }
                break;
        }

        return true;
    }

    /**
     * 判断圆形按钮在左边还是右边
     *
     * @return
     */
    private boolean isLeft() {
        return circlePosition == 0;
    }

    private void open() {
        isOpen = true;
        isAnimating = true;
        ringPaint.setColor(bgColor);
        int aniStartX = 0, aniEndX = 0;
        if (isLeft()) {
            aniStartX = circleCenterPoint.x * 2;//从圆形按钮右侧切线处开始，即 mHeight
            aniEndX = mWidth;
        } else {
            aniStartX = mWidth - mHeight;
            aniEndX = 0;
        }
        bgAnimation = ValueAnimator.ofInt(aniStartX, aniEndX);
        bgAnimation.setDuration(bgAnimationTime);
        bgAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int x = (int) animation.getAnimatedValue();
                if (isLeft())
                    bgRectF.set(ringWidth, ringWidth, x, mHeight - ringWidth);
                else
                    bgRectF.set(x, ringWidth, mWidth - ringWidth, mHeight - ringWidth);
                invalidate();
            }
        });
        textAlphaAnimation = ValueAnimator.ofInt(0, 255);
        textAlphaAnimation.setDuration(textAnimationTime);
        textAlphaAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                menuTextPaint.setAlpha((int) animation.getAnimatedValue());
                invalidate();
            }
        });

        set = new AnimatorSet();
        set.playSequentially(bgAnimation, textAlphaAnimation);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimating = false;
            }
        });
        set.start();
    }


    private void close() {
        isOpen = false;
        isAnimating = true;
        ringPaint.setColor(ringColor);
        int aniStartX = 0, aniEndX = 0;
        if (isLeft()) {
            aniStartX = mWidth;
            aniEndX = circleCenterPoint.x * 2;//到圆形按钮的右侧切线位置停止动画
        } else {
            aniStartX = 0;
            aniEndX = mWidth - mHeight;
        }
        bgAnimation = ValueAnimator.ofInt(aniStartX, aniEndX);
        bgAnimation.setDuration(bgAnimationTime);
        bgAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int x = (int) animation.getAnimatedValue();
                if (isLeft())
                    bgRectF.set(ringWidth, ringWidth, x, mHeight - ringWidth);
                else
                    bgRectF.set(x, ringWidth, mWidth - ringWidth, mHeight - ringWidth);
                invalidate();
            }
        });

        textAlphaAnimation = ValueAnimator.ofInt(255, 0);
        textAlphaAnimation.setDuration(textAnimationTime);
        textAlphaAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                menuTextPaint.setAlpha((int) animation.getAnimatedValue());
                invalidate();
            }
        });
        AnimatorSet set = new AnimatorSet();
        set.playSequentially(textAlphaAnimation, bgAnimation);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimating = false;
            }
        });
        set.start();
    }

    private void startBreathing() {
        if (breathingAnimation != null && breathingAnimation.isRunning())
            return;
        breathingAnimation = ValueAnimator.ofInt(0x33, 0xCC);
        breathingAnimation.setDuration(2000);
        breathingAnimation.setRepeatCount(ValueAnimator.INFINITE);
        breathingAnimation.setRepeatMode(ValueAnimator.REVERSE);
        breathingAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                ringPaint.setAlpha((int) animation.getAnimatedValue());
                invalidate();
            }
        });
        breathingAnimation.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (set.isRunning()) {
            set.cancel();
            set = null;
        }
        if (breathingAnimation != null) {
            if (breathingAnimation.isRunning())
                breathingAnimation.cancel();
        }
    }


}
