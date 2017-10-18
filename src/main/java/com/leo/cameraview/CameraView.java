package com.leo.cameraview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.support.v4.view.ViewCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.leo.cameraview.utils.DisplayOrientationDetector;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Set;

/**
 * Created on 2017/9/9 上午11:28.
 * leo linxiaotao1993@vip.qq.com
 */

public class CameraView extends FrameLayout {

    public static final int FACING_BACK = 0;
    public static final int FACING_FRONT = 1;

    @IntDef({FACING_BACK, FACING_FRONT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Facing {
    }

    /** Flash will not be fired. */
    public static final int FLASH_OFF = Constants.FLASH_OFF;

    /** Flash will always be fired during snapshot. */
    public static final int FLASH_ON = Constants.FLASH_ON;

    /** Constant emission of light during preview, auto-focus and snapshot. */
    public static final int FLASH_TORCH = Constants.FLASH_TORCH;

    /** Flash will be fired automatically when required. */
    public static final int FLASH_AUTO = Constants.FLASH_AUTO;

    /** Flash will be fired in red-eye reduction mode. */
    public static final int FLASH_RED_EYE = Constants.FLASH_RED_EYE;

    /** The mode for for the camera device's flash control */
    @IntDef({FLASH_OFF, FLASH_ON, FLASH_TORCH, FLASH_AUTO, FLASH_RED_EYE})
    public @interface Flash {
    }

    private CameraViewImpl mImpl;
    private PreviewImpl mPreview;
    private final CallbackBridge mCallbacks;
    private final DisplayOrientationDetector mDisplayOrientationDetector;
    private boolean mAdjustViewBounds;
    private int mPreViewIndex = -1;

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @SuppressWarnings("WrongConstant")
    public CameraView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (isInEditMode()) {
            mCallbacks = null;
            mDisplayOrientationDetector = null;
            return;
        }

        mCallbacks = new CallbackBridge();
        mPreview = createPreviewImpl(context);
        mImpl = new Camera1(mCallbacks, mPreview);
        mImpl.setDrawView(this);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CameraView, defStyle, 0);
        setFacing(a.getInt(R.styleable.CameraView_facing, FACING_BACK));
        String aspectRatio = a.getString(R.styleable.CameraView_aspectRatio);
        if (!TextUtils.isEmpty(aspectRatio)) {
            setAspectRatio(AspectRatio.parse(aspectRatio));
        } else {
            setAspectRatio(Constants.DEFAULT_ASPECT_RATIO);
        }
        setAutoFocus(a.getBoolean(R.styleable.CameraView_autoFocus, false));
        setTouchFocus(a.getBoolean(R.styleable.CameraView_touchFocus, false));
        setFlash(a.getInt(R.styleable.CameraView_flash, Constants.FLASH_AUTO));
        mAdjustViewBounds = a.getBoolean(R.styleable.CameraView_adjustViewBounds, false);
        a.recycle();

        mDisplayOrientationDetector = new DisplayOrientationDetector(context) {
            @Override
            public void onDisplayOrientationChanged(int displayOrientation) {
                mImpl.setDisplayOrientation(displayOrientation);
            }
        };
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            mDisplayOrientationDetector.enable(ViewCompat.getDisplay(this));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!isInEditMode()) {
            mDisplayOrientationDetector.disable();
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState savedState = new SavedState(super.onSaveInstanceState());
        savedState.facing = getFacing();
        return savedState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        setFacing(savedState.facing);
        setAutoFocus(savedState.autoFocus);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isInEditMode()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        if (mAdjustViewBounds) {
            if (!isCameraOpened()) {
                mCallbacks.reserveRequestLayoutOnOpen();
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
            if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
                final AspectRatio ratio = getAspectRatio();
                int height = (int) (MeasureSpec.getSize(widthMeasureSpec) * ratio.toFloat());
                if (heightMode == MeasureSpec.AT_MOST) {
                    height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            } else if (widthMode != MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
                final AspectRatio ratio = getAspectRatio();
                int width = (int) (MeasureSpec.getSize(heightMeasureSpec) * ratio.toFloat());
                if (widthMode == MeasureSpec.AT_MOST) {
                    width = Math.min(width, MeasureSpec.getSize(widthMeasureSpec));
                }
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        AspectRatio ratio = getAspectRatio();
        if (mDisplayOrientationDetector.getLastKnownDisplayOrientation() % 180 == 0) {
            ratio = ratio.inverse();
        }
        if (height < width * ratio.getY() / ratio.getX()) {
            mImpl.getView().measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
                    , MeasureSpec.makeMeasureSpec(width * ratio.getY() / ratio.getX(), MeasureSpec.EXACTLY));
        } else {
            mImpl.getView().measure(MeasureSpec.makeMeasureSpec(height * ratio.getX() / ratio.getY(), MeasureSpec.EXACTLY)
                    , MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

    }

    public void start() {
        post(new Runnable() {
            @Override
            public void run() {
                setVisibility(VISIBLE);
                if (indexOfChild(mPreview.getView()) == -1) {
                    if (mPreViewIndex > -1) {
                        addView(mPreview.getView(), mPreViewIndex);
                    } else {
                        addView(mPreview.getView());
                    }
                }
            }
        });
        if (!mImpl.start()) {
            Parcelable state = onSaveInstanceState();
            mPreview = createPreviewImpl(getContext());
            mImpl = new Camera1(mCallbacks, mPreview);
            onRestoreInstanceState(state);
            mImpl.start();
        }
    }


    public void stop() {
        stop(true);
    }

    public void stop(final boolean gone) {
        post(new Runnable() {
            @Override
            public void run() {
                if (gone) {
                    setVisibility(GONE);
                }
                if (indexOfChild(mPreview.getView()) > -1) {
                    mPreViewIndex = indexOfChild(mPreview.getView());
                    removeView(mPreview.getView());
                }
            }
        });
        mImpl.stop();
    }

    public void show() {
        post(new Runnable() {
            @Override
            public void run() {
                mImpl.getView().setVisibility(VISIBLE);
            }
        });
    }

    public void hide() {
        post(new Runnable() {
            @Override
            public void run() {
                mImpl.getView().setVisibility(GONE);
            }
        });
    }

    public Bitmap getBitmap() {
        return mPreview.getBitmap();
    }

    public void takePicture() {
        mImpl.takePicture();
    }


    public boolean isCameraOpened() {
        return mImpl.isCameraOpened();
    }

    public boolean isShowingPreview() {
        return mImpl.isShowingPreview();
    }

    public void addCallback(Callback callback) {
        mCallbacks.add(callback);
    }

    public void removeCallback(Callback callback) {
        mCallbacks.remove(callback);
    }

    public void setFacing(@Facing int facing) {
        mImpl.setFacing(facing);
    }

    @Facing
    public int getFacing() {
        return mImpl.getFacing();
    }

    public void setAutoFocus(boolean autoFocus) {
        mImpl.setAutoFocus(autoFocus);
    }

    public boolean getAutoFocus() {
        return mImpl.getAutoFocus();
    }

    public void setPreviewSize(Size previewSize) {
        mImpl.setPreviewSize(previewSize);
    }

    public Size getPreviewSize() {
        return mImpl.getPreviewSize();
    }

    public void setPictureSize(Size pictureSize) {
        mImpl.setPictureSize(pictureSize);
    }

    public boolean setAspectRatio(AspectRatio ratio) {
        return mImpl.setAspectRatio(ratio);
    }

    public AspectRatio getAspectRatio() {
        return mImpl.getAspectRatio();
    }

    public Set<AspectRatio> getSupportedAspectRatios() {
        return mImpl.getSupportedAspectRatios();
    }

    public void setPreviewFormat(int pixel_format) {
        mImpl.setPreviewFormat(pixel_format);
    }

    public boolean getTouchFocus() {
        return mImpl.getTouchFocus();
    }

    public void setTouchFocus(boolean touchFocus) {
        mImpl.setTouchFocus(touchFocus);
    }

    public void setFlash(@Flash int flash) {
        mImpl.setFlash(flash);
    }

    public void addDrawView(View drawView) {
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        addView(drawView, layoutParams);
    }

    @Flash
    public int getFlash() {
        return mImpl.getFlash();
    }

    public CameraView setAdjustViewBounds(boolean adjustViewBounds) {
        if (mAdjustViewBounds == adjustViewBounds) {
            return this;
        }
        mAdjustViewBounds = adjustViewBounds;
        requestLayout();
        return this;
    }

    protected static class SavedState extends BaseSavedState {

        @Facing
        int facing;

        boolean autoFocus;

        AspectRatio ration;

        @Flash
        int flash;

        @SuppressWarnings("WrongConstant")
        public SavedState(Parcel source) {
            super(source);
            facing = source.readInt();
            autoFocus = source.readByte() != 0;
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(facing);
            out.writeByte((byte) (autoFocus ? 1 : 0));
        }

        public static final Creator<SavedState> CREATOR
                = ParcelableCompat.newCreator(new ParcelableCompatCreatorCallbacks<SavedState>() {

            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }

        });

    }

    private PreviewImpl createPreviewImpl(Context context) {
        PreviewImpl preview;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            preview = new SurfaceViewPreview(context, this);
        } else {
            preview = new TextureViewPreview(context, this);
        }
        return preview;
    }

    private class CallbackBridge implements CameraViewImpl.Callback {

        private final ArrayList<Callback> mCallbacks = new ArrayList<>();
        private boolean mRequestLayoutOnOpen;

        CallbackBridge() {
        }

        public void add(Callback callback) {
            mCallbacks.add(callback);
        }

        public void remove(Callback callback) {
            mCallbacks.remove(callback);
        }

        @Override
        public void onCameraOpened() {
            if (mRequestLayoutOnOpen) {
                mRequestLayoutOnOpen = false;
                requestLayout();
            }
            for (Callback callback : mCallbacks) {
                callback.onCameraOpened(CameraView.this);
            }
        }

        @Override
        public void onCameraClosed() {
            for (Callback callback : mCallbacks) {
                callback.onCameraClosed(CameraView.this);
            }
        }

        @Override
        public void onStartPreview() {
            for (Callback callback : mCallbacks) {
                callback.onStartPreview(CameraView.this);
            }
        }

        @Override
        public void onStopPreview() {
            for (Callback callback : mCallbacks) {
                callback.onStopPreview(CameraView.this);
            }
        }

        @Override
        public void onPreviewFrame(byte[] data) {
            for (Callback callback : mCallbacks) {
                callback.onPreviewFrame(CameraView.this, data);
            }
        }

        @Override
        public void onPictureTaken(byte[] data) {
            for (Callback callback : mCallbacks) {
                callback.onPictureTaken(CameraView.this, data);
            }
        }

        public void reserveRequestLayoutOnOpen() {
            mRequestLayoutOnOpen = true;
        }
    }

    public abstract static class Callback {

        public void onCameraOpened(CameraView cameraView) {

        }

        public void onCameraClosed(CameraView cameraView) {

        }

        public void onPreviewFrame(CameraView cameraView, byte[] data) {

        }


        public void onStartPreview(CameraView cameraView) {

        }

        public void onStopPreview(CameraView cameraView) {

        }

        public void onPictureTaken(CameraView cameraView, byte[] data) {

        }

    }

}
