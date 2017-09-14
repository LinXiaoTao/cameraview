package com.leo.cameraview.utils;

import android.content.Context;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;

/**
 * Created on 2017/9/9 下午3:25.
 * leo linxiaotao1993@vip.qq.com
 */

public abstract class DisplayOrientationDetector {

    private final OrientationEventListener mOrientationEventListener;
    private Display mDisplay;
    private int mLastKnownDisplayOrientation = 0;

    private static final SparseIntArray DISPLAY_ORIENTATIONS = new SparseIntArray();

    static {
        DISPLAY_ORIENTATIONS.put(Surface.ROTATION_0, 0);
        DISPLAY_ORIENTATIONS.put(Surface.ROTATION_90, 90);
        DISPLAY_ORIENTATIONS.put(Surface.ROTATION_180, 180);
        DISPLAY_ORIENTATIONS.put(Surface.ROTATION_270, 270);
    }

    public DisplayOrientationDetector(Context context) {
        mOrientationEventListener = new OrientationEventListener(context) {

            private int mLastKnownRotation = -1;

            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN
                        || mDisplay == null) {
                    return;
                }
                final int rotation = mDisplay.getRotation();
                if (mLastKnownRotation != rotation) {
                    mLastKnownRotation = rotation;
                    dispatchOnDisplayOrientationChanged(DISPLAY_ORIENTATIONS.get(rotation));
                }

            }
        };
    }

    public void enable(Display display) {
        mDisplay = display;
        mOrientationEventListener.enable();
        dispatchOnDisplayOrientationChanged(DISPLAY_ORIENTATIONS.get(display.getRotation()));
    }

    public void disable() {
        mOrientationEventListener.disable();
        mDisplay = null;
    }

    public DisplayOrientationDetector setLastKnownDisplayOrientation(int lastKnownDisplayOrientation) {
        mLastKnownDisplayOrientation = lastKnownDisplayOrientation;
        return this;
    }

    private void dispatchOnDisplayOrientationChanged(int displayOrientation) {
        mLastKnownDisplayOrientation = displayOrientation;
        onDisplayOrientationChanged(displayOrientation);
    }

    public abstract void onDisplayOrientationChanged(int displayOrientation);

}
