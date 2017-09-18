package com.leo.cameraview;

import android.support.annotation.NonNull;

/**
 * Created on 2017/9/15 下午4:59.
 * leo linxiaotao1993@vip.qq.com
 */

public final class Size implements Comparable<Size> {

    private final int mWidth;
    private final int mHeight;

    public Size(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (obj instanceof Size) {
            Size size = (Size) obj;
            return mWidth == size.getWidth() && mHeight == size.getHeight();
        }
        return false;
    }

    @Override
    public String toString() {
        return mWidth + "x" + mHeight;
    }

    @Override
    public int hashCode() {
        return mHeight ^ ((mWidth << (Integer.SIZE / 2)) | (mWidth >>> (Integer.SIZE / 2)));
    }


    @Override
    public int compareTo(@NonNull Size o) {
        return mWidth * mHeight - o.getWidth() * o.getHeight();
    }
}
