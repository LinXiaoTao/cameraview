package com.leo.cameraview;

import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Created on 2017/9/9 下午2:47.
 * leo linxiaotao1993@vip.qq.com
 */
@SuppressWarnings("deprecation")
final class Camera1 extends CameraViewImpl {

    private int mCameraId;
    private Camera mCamera;
    private Camera.Parameters mCameraParameters;
    private final Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();
    private boolean mShowingPreview;
    /** 摄像头面向 */
    private int mFacing;
    /** 自动聚焦 */
    private boolean mAutoFocus;
    /** 触摸聚焦 */
    private boolean mTouchFocus;
    private Camera.AutoFocusCallback mAutoFocusCallback;
    /** 摄像头预览帧数据尺寸 */
    private int mPreviewWidth, mPreviewHeight;
    /** 摄像头预览帧数据格式 */
    private int mPixelFormat;
    private int mDisplayOrientation;
    private boolean mTargetStartPreview;
    private DrawView mDrawView;

    private static final int INVALID_CAMERA_ID = -1;

    Camera1(Callback callback, PreviewImpl preview) {
        super(callback, preview);
        preview.setCallback(new PreviewImpl.Callback() {
            @Override
            public void onSurfaceChanged() {
                if (mCamera != null) {
                    setUpPreview();
                    adjustCameraParameters();
                    if (mTargetStartPreview && !mShowingPreview) {
                        mTargetStartPreview = false;
                        mShowingPreview = true;
                        mCamera.startPreview();
                        dispatchStartPreview();
                    }
                }
            }
        });
    }

    @Override
    boolean start() {
        chooseCamera();
        openCamera();
        if (isCameraOpened()) {
            if (mPreview.isReady()) {
                setUpPreview();
                mShowingPreview = true;
                mCamera.startPreview();
                dispatchStartPreview();
            } else {
                mTargetStartPreview = true;
            }
            return true;
        }

        return false;
    }

    @Override
    void stop() {
        if (mCamera != null) {
            mCamera.stopPreview();
            dispatchStopreview();
        }
        mShowingPreview = false;
        releaseCamera();
    }

    @Override
    boolean isCameraOpened() {
        return mCamera != null;
    }

    @Override
    boolean isShowingPreview() {
        return mShowingPreview;
    }

    @Override
    void setFacing(int facing) {
        if (mFacing == facing) {
            return;
        }
        mFacing = facing;
        if (isCameraOpened()) {
            stop();
            start();
        }
    }

    @Override
    int getFacing() {
        return mFacing;
    }

    @Override
    boolean getAutoFocus() {
        if (!isCameraOpened()) {
            return mAutoFocus;
        }
        String focusMode = mCameraParameters.getFocusMode();
        return !TextUtils.isEmpty(focusMode) && focusMode.contains("continuous");
    }

    @Override
    void setAutoFocus(boolean autoFocus) {
        if (mAutoFocus == autoFocus) {
            return;
        }
        if (setAutoFocusInternal(autoFocus)) {
            mCamera.setParameters(mCameraParameters);
        }
    }

    @Override
    boolean getTouchFocus() {
        return mTouchFocus;
    }

    @Override
    void setTouchFocus(boolean touchFocus) {
        if (mTouchFocus == touchFocus) {
            return;
        }

        if (setTouchFocusInternal(touchFocus)) {
            mCamera.setParameters(mCameraParameters);
        }

    }

    @Override
    void setDisplayOrientation(int displayOrientation) {
        if (mDisplayOrientation == displayOrientation) {
            return;
        }
        mDisplayOrientation = displayOrientation;
        if (isCameraOpened()) {
            final boolean needsToStopPreview = mShowingPreview && Build.VERSION.SDK_INT < 14;
            if (needsToStopPreview) {
                mCamera.stopPreview();
            }
            mCamera.setDisplayOrientation(calcDisplayOrientation(displayOrientation));
            if (needsToStopPreview) {
                mCamera.startPreview();
            }
        }
    }

    @Override
    void setPreviewSize(int width, int height) {

        if (mPreviewWidth == width && mPreviewHeight == height) {
            return;
        }
        mPreviewWidth = width;
        mPreviewHeight = height;
        if (isCameraOpened()) {
            if (mShowingPreview) {
                mCamera.stopPreview();
            }
            mCameraParameters.setPreviewSize(width, height);
            mCamera.setParameters(mCameraParameters);
            if (mShowingPreview) {
                mCamera.startPreview();
            }
        }


    }

    @Override
    void setPreviewFormat(int pixel_format) {
        if (mPixelFormat == pixel_format) {
            return;
        }
        mPixelFormat = pixel_format;
        if (isCameraOpened()) {
            if (mShowingPreview) {
                mCamera.stopPreview();
            }
            mCameraParameters.setPreviewFormat(pixel_format);
            mCamera.setParameters(mCameraParameters);
            if (mShowingPreview) {
                mCamera.startPreview();
            }
        }
    }

    @Override
    void setDrawView(ViewGroup parent) {
        mDrawView = new DrawView(parent.getContext());
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT
                , ViewGroup.LayoutParams.MATCH_PARENT);
        parent.addView(mDrawView, layoutParams);
    }

    private void setUpPreview() {

        if (!isCameraOpened()) {
            return;
        }

        try {
            if (mPreview.getOutputClass() == SurfaceHolder.class) {
                final boolean needsToStopPreview = (mShowingPreview && Build.VERSION.SDK_INT < 14);
                if (needsToStopPreview) {
                    mCamera.stopPreview();
                }
                mCamera.setPreviewDisplay(SurfaceHolder.class.cast(mPreview.getDisplaySurface()));
                if (needsToStopPreview) {
                    mCamera.startPreview();
                }
            } else if (mPreview.getOutputClass() == SurfaceTexture.class) {
                mCamera.setPreviewTexture(SurfaceTexture.class.cast(mPreview.getDisplaySurface()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void adjustCameraParameters() {
        if (mShowingPreview) {
            mCamera.stopPreview();
        }
        setAutoFocusInternal(mAutoFocus);
        setTouchFocusInternal(mTouchFocus);
        setPreviewInternal();
        mCamera.setParameters(mCameraParameters);
        if (mShowingPreview) {
            mCamera.startPreview();
        }
    }

    private void chooseCamera() {
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, mCameraInfo);
            if (mCameraInfo.facing == mFacing) {
                mCameraId = i;
                return;
            }
        }
        mCameraId = INVALID_CAMERA_ID;
    }

    private void openCamera() {
        if (mCamera != null) {
            releaseCamera();
        }
        if (mCameraId == INVALID_CAMERA_ID) {
            return;
        }
        mCamera = Camera.open(mCameraId);
        mCamera.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                mCallback.onPreviewFrame(data);
            }
        });
        mCameraParameters = mCamera.getParameters();
        adjustCameraParameters();
        mCamera.setDisplayOrientation(calcDisplayOrientation(mDisplayOrientation));
        mCallback.onCameraOpened();
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
            mCallback.onCameraClosed();
        }
    }

    private int calcDisplayOrientation(int screenOrientationDegrees) {
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - (mCameraInfo.orientation + screenOrientationDegrees) % 360) % 360;
        } else {  // back-facing
            return (mCameraInfo.orientation - screenOrientationDegrees + 360) % 360;
        }
    }

    private boolean setAutoFocusInternal(boolean autoFocus) {
        mAutoFocus = autoFocus;
        if (autoFocus) {
            mTouchFocus = false;
        }
        if (isCameraOpened()) {
            final List<String> modes = mCameraParameters.getSupportedFocusModes();
            if (autoFocus && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
            } else if (modes.size() > 0) {
                mCameraParameters.setFocusMode(modes.get(0));
            }
            return true;
        }
        return false;
    }

    private boolean setTouchFocusInternal(final boolean touchFocus) {
        mTouchFocus = touchFocus;
        if (touchFocus) {
            mAutoFocus = false;
        }
        if (isCameraOpened() && touchFocus) {
            if (mAutoFocusCallback == null) {
                mAutoFocusCallback = new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        if (success) {
                            camera.cancelAutoFocus();
                            if (mDrawView != null) {
                                mDrawView.drawRect(null);
                            }
                        }
                    }
                };
            }
            final View preview = mPreview.getView();
            preview.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (!mTouchFocus) {
                        return false;
                    }
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        final float x = event.getX();
                        final float y = event.getY();
                        final Rect touchRect = new Rect();
                        touchRect.left = (int) (x - mTouchFocusWidth / 2);
                        touchRect.right = (int) (x + mTouchFocusHeight / 2);
                        touchRect.top = (int) (y - mTouchFocusHeight / 2);
                        touchRect.bottom = (int) (y + mTouchFocusHeight / 2);

                        final Rect targetRect = new Rect();
                        //区域被映射为 2000 * 2000 的矩形，区域的左上角坐标为 (-1000,-1000)，区域的右下角坐标
                        //为 (1000,1000)
                        targetRect.left = clamp(touchRect.left * 2000 / mPreview.getWidth() - 1000);
                        targetRect.right = clamp(touchRect.right * 2000 / mPreview.getWidth() - 1000);
                        targetRect.top = clamp(touchRect.top * 2000 / mPreview.getHeight() - 1000);
                        targetRect.bottom = clamp(touchRect.bottom * 2000 / mPreview.getHeight() - 1000);
                        //配置焦点区域
                        mCameraParameters.setFocusAreas(Collections.singletonList(new Camera.Area(targetRect, 1000)));
                        mCameraParameters.setMeteringAreas(Collections.singletonList(new Camera.Area(targetRect, 1000)));
                        mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                        mCamera.setParameters(mCameraParameters);
                        mCamera.autoFocus(mAutoFocusCallback);
                        if (mDrawView != null) {
                            mDrawView.drawRect(touchRect);
                        }
                        return true;
                    }
                    return false;
                }
            });
        }
        return false;
    }

    private void setPreviewInternal() {
        if (isCameraOpened()) {
            if (mShowingPreview) {
                mCamera.stopPreview();
            }
            if (mPixelFormat != 0) {
                mCameraParameters.setPreviewFormat(mPixelFormat);
            }
            if (mPreviewWidth != 0 && mPreviewHeight != 0) {
                mCameraParameters.setPreviewSize(mPreviewWidth, mPreviewHeight);
            }
            if (mShowingPreview) {
                mCamera.startPreview();
            }
        }
    }

    private int clamp(int x) {
        if (x > 1000) {
            return 1000;
        }
        if (x < -1000) {
            return -1000;
        }
        return x;
    }

}
