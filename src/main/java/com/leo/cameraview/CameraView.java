package com.leo.cameraview;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.leo.cameraview.utils.DisplayOrientationDetector;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

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

    private CameraViewImpl mImpl;
    private final CallbackBridge mCallbacks;
    private final DisplayOrientationDetector mDisplayOrientationDetector;

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
        final PreviewImpl preview = createPreviewImpl(context);
        mImpl = new Camera1(mCallbacks, preview);
        mImpl.setDrawView(this);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CameraView, defStyle, 0);
        setFacing(a.getInt(R.styleable.CameraView_facing, FACING_BACK));
        setAutoFocus(a.getBoolean(R.styleable.CameraView_autoFocus, false));
        setTouchFocus(a.getBoolean(R.styleable.CameraView_touchFocus, false));
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

    public void start() {
        post(new Runnable() {
            @Override
            public void run() {
                setVisibility(VISIBLE);
            }
        });
        if (!mImpl.start()) {
            Parcelable state = onSaveInstanceState();
            mImpl = new Camera1(mCallbacks, createPreviewImpl(getContext()));
            onRestoreInstanceState(state);
            mImpl.start();
        }
    }

    public void stop() {
        post(new Runnable() {
            @Override
            public void run() {
                setVisibility(GONE);
            }
        });
        mImpl.stop();
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

    public void setPreviewSize(int width, int height) {
        mImpl.setPreviewSize(width, height);
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

    protected static class SavedState extends BaseSavedState {

        @Facing
        int facing;
        boolean autoFocus;

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

    }

}
