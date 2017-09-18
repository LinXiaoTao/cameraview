package com.leo.cameraview;

import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.support.v4.util.SparseArrayCompat;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private Size mPreviewSize;
    /** 摄像头图像数据尺寸 */
    private Size mPictureSize;
    /** 摄像头预览帧数据格式 */
    private int mPixelFormat;
    private int mFlash;
    private int mDisplayOrientation;
    private boolean mTargetStartPreview;
    private DrawView mDrawView;
    private final AtomicBoolean isPictureCaptureInProgress = new AtomicBoolean(false);
    /** 支持的预览宽高比例 */
    private final SizeMap mPreviewSizes = new SizeMap();
    /** 支持的图像宽高比例 */
    private final SizeMap mPictureSizes = new SizeMap();
    /** 预览数据的宽高比例 */
    private AspectRatio mAspectRatio;

    private static final int INVALID_CAMERA_ID = -1;

    private static final SparseArrayCompat<String> FLASH_MODES = new SparseArrayCompat<>();

    static {
        FLASH_MODES.put(Constants.FLASH_OFF, Camera.Parameters.FLASH_MODE_OFF);
        FLASH_MODES.put(Constants.FLASH_ON, Camera.Parameters.FLASH_MODE_ON);
        FLASH_MODES.put(Constants.FLASH_TORCH, Camera.Parameters.FLASH_MODE_TORCH);
        FLASH_MODES.put(Constants.FLASH_AUTO, Camera.Parameters.FLASH_MODE_AUTO);
        FLASH_MODES.put(Constants.FLASH_RED_EYE, Camera.Parameters.FLASH_MODE_RED_EYE);
    }

    Camera1(Callback callback, PreviewImpl preview) {
        super(callback, preview);
        preview.setCallback(new PreviewImpl.Callback() {
            @Override
            public void onSurfaceChanged() {
                if (mCamera != null) {
                    adjustCameraParameters();
                    if (mTargetStartPreview && !mShowingPreview) {
                        setUpPreview();
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
    void takePicture() {
        if (!isCameraOpened()) {
            return;
        }
        if (getAutoFocus()) {
            mCamera.cancelAutoFocus();
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    takePictureInternal();
                }
            });
        } else {
            takePictureInternal();
        }
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
        // TODO: 2017/9/15 待完善
        if (mTouchFocus == touchFocus) {
            return;
        }

        if (setTouchFocusInternal(touchFocus)) {
            mCamera.setParameters(mCameraParameters);
        }

    }

    @Override
    void setFlash(int flash) {
        if (flash == mFlash) {
            return;
        }
        if (setFlashInternal(flash)) {
            mCamera.setParameters(mCameraParameters);
        }
    }

    @Override
    int getFlash() {
        return mFlash;
    }

    @Override
    void setDisplayOrientation(int displayOrientation) {
        if (mDisplayOrientation == displayOrientation) {
            return;
        }
        mDisplayOrientation = displayOrientation;
        if (isCameraOpened()) {
            mCameraParameters.setRotation(calcCameraRotation(displayOrientation));
            mCamera.setParameters(mCameraParameters);
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
    void setPreviewSize(Size previewSize) {

        if (mPreviewSize != null && mPreviewSize.equals(previewSize)) {
            return;
        }

        mPreviewSize = previewSize;
        if (isCameraOpened() && mPreviewSize != null) {
            if (mShowingPreview) {
                mCamera.stopPreview();
            }
            mCameraParameters.setPreviewSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mCamera.setParameters(mCameraParameters);
            if (mShowingPreview) {
                mCamera.startPreview();
            }
        }


    }

    @Override
    Size getPreviewSize() {
        return mPreviewSize;
    }

    @Override
    void setPictureSize(Size pictureSize) {
        if (mPictureSize != null && mPictureSize.equals(pictureSize)) {
            return;
        }
        mPictureSize = pictureSize;
        if (isCameraOpened() && mPictureSize != null) {
            if (mShowingPreview) {
                mCamera.stopPreview();
            }
            mCameraParameters.setPictureSize(mPictureSize.getWidth(), mPictureSize.getHeight());
            mCamera.setParameters(mCameraParameters);
            if (mShowingPreview) {
                mCamera.startPreview();
            }
        }

    }

    @Override
    boolean setAspectRatio(AspectRatio ratio) {
        if (mAspectRatio == null || !isCameraOpened()) {
            mAspectRatio = ratio;
            return true;
        } else if (!mAspectRatio.equals(ratio)) {
            final Set<Size> sizes = mPreviewSizes.sizes(ratio);
            if (sizes == null) {
                throw new UnsupportedOperationException(ratio + " is not supported");
            } else {
                mAspectRatio = ratio;
                adjustCameraParameters();
                return true;
            }
        }

        return false;
    }

    @Override
    AspectRatio getAspectRatio() {
        return mAspectRatio;
    }

    @Override
    Set<AspectRatio> getSupportedAspectRatios() {
        SizeMap idealAspectRatios = mPreviewSizes;
        for (AspectRatio aspectRatio : idealAspectRatios.ratios()) {
            if (mPictureSizes.sizes(aspectRatio) == null) {
                idealAspectRatios.remove(aspectRatio);
            }
        }
        return idealAspectRatios.ratios();
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

    private AspectRatio chooseAspectRatio() {
        AspectRatio r = null;
        for (AspectRatio ratio : mPreviewSizes.ratios()) {
            r = ratio;
            if (ratio.equals(Constants.DEFAULT_ASPECT_RATIO)) {
                return ratio;
            }
        }
        return r;
    }

    private void adjustCameraParameters() {
        Size previewSize = mPreviewSize;
        if (mPreviewSize == null) {
            SortedSet<Size> sizes = mPreviewSizes.sizes(mAspectRatio);
            if (sizes == null) {
                mAspectRatio = chooseAspectRatio();
                sizes = mPreviewSizes.sizes(mAspectRatio);
            }
            previewSize = chooseOptimalSize(sizes);
        }
        Size pictureSize = mPictureSize;
        if (mPictureSize == null) {
            //largest picture size
            pictureSize = mPictureSizes.sizes(mAspectRatio).last();
        }
        if (mShowingPreview) {
            mCamera.stopPreview();
        }
        mCameraParameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
        mCameraParameters.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
        mCameraParameters.setRotation(calcCameraRotation(mDisplayOrientation));
        setAutoFocusInternal(mAutoFocus);
        setFlashInternal(mFlash);
        setTouchFocusInternal(mTouchFocus);
        mCamera.setParameters(mCameraParameters);
        if (mShowingPreview) {
            mCamera.startPreview();
        }
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private Size chooseOptimalSize(SortedSet<Size> sizes) {
        if (!mPreview.isReady()) {
            //return the smallest size
            return sizes.first();
        }
        int desiredWidth;
        int desiredHeight;
        final int surfaceWidth = mPreview.getWidth();
        final int surfaceHeight = mPreview.getHeight();
        if (isLandscape(mDisplayOrientation)) {
            desiredWidth = surfaceHeight;
            desiredHeight = surfaceWidth;
        } else {
            desiredWidth = surfaceWidth;
            desiredHeight = surfaceHeight;
        }
        Size result = null;
        for (Size size : sizes) {
            if (desiredWidth <= size.getWidth() && desiredHeight <= size.getHeight()) {
                return size;
            }
            result = size;
        }
        return result;
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
        mCameraParameters = mCamera.getParameters();
        //supprted preview sizes
        mPreviewSizes.clear();
        for (Camera.Size size : mCameraParameters.getSupportedPreviewSizes()) {
            mPreviewSizes.add(new Size(size.width, size.height));
        }
        //supprted picture sizes
        mPictureSizes.clear();
        for (Camera.Size size : mCameraParameters.getSupportedPictureSizes()) {
            mPictureSizes.add(new Size(size.width, size.height));
        }
        if (mAspectRatio == null) {
            mAspectRatio = Constants.DEFAULT_ASPECT_RATIO;
        }
        mCamera.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                mCallback.onPreviewFrame(data);
            }
        });
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

    private int calcCameraRotation(int screenOrientationDegrees) {
        int result = 0;
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (mCameraInfo.orientation + screenOrientationDegrees) % 360;
        } else {
            final int landscapeFlip = isLandscape(screenOrientationDegrees) ? 180 : 0;
            result = (mCameraInfo.orientation + screenOrientationDegrees + landscapeFlip) % 360;
        }
        System.out.println(String.format(Locale.getDefault(), "screenOrientationDegrees：%d;calcCameraRotation：%d"
                , screenOrientationDegrees, result));
        return result;
    }

    private boolean isLandscape(int screenOrientationDegrees) {
        return (screenOrientationDegrees == Constants.LANDSCAPE_90 || screenOrientationDegrees == Constants.LANDSCAPE_270);
    }

    private int calcDisplayOrientation(int screenOrientationDegrees) {
        int result = 0;
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (360 - (mCameraInfo.orientation + screenOrientationDegrees) % 360) % 360;
        } else {  // back-facing
            result = (mCameraInfo.orientation - screenOrientationDegrees + 360) % 360;
        }
        System.out.println(String.format(Locale.getDefault(), "screenOrientationDegrees：%d;calcDisplayOrientation：%d"
                , screenOrientationDegrees, result));
        return result;
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
                    if (!mTouchFocus || !isCameraOpened()) {
                        return false;
                    }
                    //check
                    int maxNumFocusAreas = mCameraParameters.getMaxNumFocusAreas();
                    int maxNumMeteringAreas = mCameraParameters.getMaxNumMeteringAreas();
                    if (maxNumFocusAreas == 0 && maxNumMeteringAreas == 0) {
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
                        if (maxNumFocusAreas > 0) {
                            mCameraParameters.setFocusAreas(Collections.singletonList(new Camera.Area(targetRect, 1000)));
                        }
                        if (maxNumMeteringAreas > 0) {
                            mCameraParameters.setMeteringAreas(Collections.singletonList(new Camera.Area(targetRect, 1000)));
                        }
                        System.out.println("Focus Areas Rect：" + targetRect.toShortString());
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


    private int clamp(int x) {
        if (x > 1000) {
            return 1000;
        }
        if (x < -1000) {
            return -1000;
        }
        return x;
    }

    private void takePictureInternal() {
        if (!isPictureCaptureInProgress.getAndSet(true)) {
            mCamera.takePicture(null, null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    mCallback.onPictureTaken(data);
                    isPictureCaptureInProgress.set(false);
                    camera.cancelAutoFocus();
                    camera.startPreview();
                }
            });
        }
    }

    private boolean setFlashInternal(int flash) {
        if (isCameraOpened()) {
            List<String> modes = mCameraParameters.getSupportedFlashModes();
            String mode = FLASH_MODES.get(flash);
            if (modes != null && modes.contains(mode)) {
                mCameraParameters.setFlashMode(mode);
                mFlash = flash;
                return true;
            }
            String currentMode = FLASH_MODES.get(mFacing);
            if (modes == null || !modes.contains(currentMode)) {
                mCameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                mFlash = Constants.FLASH_OFF;
                return true;
            }
            return false;
        } else {
            mFlash = flash;
            return false;
        }
    }

}
