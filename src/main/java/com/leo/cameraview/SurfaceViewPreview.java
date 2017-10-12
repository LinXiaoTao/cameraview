package com.leo.cameraview;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.view.ViewCompat;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;


/**
 * Created on 2017/9/9 下午2:35.
 * leo linxiaotao1993@vip.qq.com
 */

final class SurfaceViewPreview extends PreviewImpl {

    private final SurfaceView mSurfaceView;

    SurfaceViewPreview(Context context, ViewGroup parent) {
        final View view = View.inflate(context, R.layout.surface_view, parent);
        mSurfaceView = (SurfaceView) view.findViewById(R.id.surface_view);
        final SurfaceHolder surfaceHolder = mSurfaceView.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                setSize(width, height);
                if (!ViewCompat.isInLayout(mSurfaceView)) {
                    dispatchSurfaceChanged();
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                setSize(0, 0);
            }
        });
    }

    @Override
    Surface getSurface() {
        return mSurfaceView.getHolder().getSurface();
    }

    @Override
    View getView() {
        return mSurfaceView;
    }

    @Override
    Class getOutputClass() {
        return SurfaceHolder.class;
    }

    @Override
    void setDisplayOrientation(int displayOrientation) {

    }

    @Override
    boolean isReady() {
        return (getWidth() != 0 && getHeight() != 0);
    }

    @Override
    Object getDisplaySurface() {
        return mSurfaceView.getHolder();
    }

    @Override
    Bitmap getBitmap() {
        mSurfaceView.setDrawingCacheEnabled(true);
        mSurfaceView.buildDrawingCache(true);
        Bitmap resultBitmap = Bitmap.createBitmap(mSurfaceView.getDrawingCache(true));
        mSurfaceView.setDrawingCacheEnabled(false);
        return resultBitmap;
    }


}
