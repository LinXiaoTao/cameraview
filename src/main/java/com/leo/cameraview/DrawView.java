package com.leo.cameraview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

/**
 * Created on 2017/9/13 下午7:39.
 * leo linxiaotao1993@vip.qq.com
 */

class DrawView extends View {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Rect mDrawRect;

    DrawView(Context context) {
        super(context);
        mPaint.setColor(Color.CYAN);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(3);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mDrawRect != null) {
            canvas.drawRect(mDrawRect, mPaint);
        }
    }

    void drawRect(Rect drawRect) {
        if (drawRect != mDrawRect) {
            mDrawRect = drawRect;
            postInvalidate();
        }
    }

}
