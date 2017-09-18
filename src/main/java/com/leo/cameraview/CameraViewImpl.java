package com.leo.cameraview;

import android.view.View;
import android.view.ViewGroup;

import java.util.Set;

/**
 * Created on 2017/9/9 上午11:39.
 * leo linxiaotao1993@vip.qq.com
 */

abstract class CameraViewImpl {

    protected final Callback mCallback;

    protected final PreviewImpl mPreview;

    private final static int DEFAULT_TOUCH_FOCUS_SIZE = 200;

    protected int mTouchFocusWidth = DEFAULT_TOUCH_FOCUS_SIZE;
    protected int mTouchFocusHeight = DEFAULT_TOUCH_FOCUS_SIZE;

    CameraViewImpl(Callback callback, PreviewImpl preview) {
        mCallback = callback;
        mPreview = preview;
    }

    View getView() {
        return mPreview.getView();
    }

    void setTouchFocusSize(int width, int height) {
        mTouchFocusWidth = width;
        mTouchFocusHeight = height;
    }

    abstract boolean start();

    abstract void stop();

    abstract void takePicture();

    abstract boolean isCameraOpened();

    abstract boolean isShowingPreview();

    abstract void setFacing(int facing);

    abstract int getFacing();

    abstract boolean getAutoFocus();

    abstract void setAutoFocus(boolean autoFocus);

    abstract boolean getTouchFocus();

    abstract void setTouchFocus(boolean touchFocus);

    abstract void setDisplayOrientation(int displayOrientation);

    abstract void setPreviewSize(Size previewSize);

    abstract Size getPreviewSize();

    abstract void setPictureSize(Size pictureSize);

    abstract boolean setAspectRatio(AspectRatio ratio);

    abstract AspectRatio getAspectRatio();

    abstract Set<AspectRatio> getSupportedAspectRatios();

    abstract void setPreviewFormat(int pixel_format);

    abstract void setDrawView(ViewGroup parent);

    protected void dispatchStartPreview() {
        mCallback.onStartPreview();
    }

    protected void dispatchStopreview() {
        mCallback.onStopPreview();
    }


    interface Callback {

        void onCameraOpened();

        void onCameraClosed();

        void onStartPreview();

        void onStopPreview();

        void onPreviewFrame(byte[] data);

        void onPictureTaken(byte[] data);
    }

}
