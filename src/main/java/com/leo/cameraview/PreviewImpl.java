package com.leo.cameraview;

import android.view.Surface;
import android.view.View;

/**
 * Created on 2017/9/9 上午11:45.
 * leo linxiaotao1993@vip.qq.com
 */

abstract class PreviewImpl {

    private Callback mCallback;
    private int mWidth;
    private int mHeight;


    abstract Surface getSurface();

    abstract View getView();

    abstract Class getOutputClass();

    abstract void setDisplayOrientation(int displayOrientation);

    abstract boolean isReady();

    abstract Object getDisplaySurface();

    PreviewImpl setCallback(Callback callback) {
        mCallback = callback;
        return this;
    }

    void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    int getWidth() {
        return mWidth;
    }

    int getHeight() {
        return mHeight;
    }


    interface Callback {
        void onSurfaceChanged();
    }

    protected void dispatchSurfaceChanged() {
        if (mCallback != null) {
            mCallback.onSurfaceChanged();
        }
    }
}
