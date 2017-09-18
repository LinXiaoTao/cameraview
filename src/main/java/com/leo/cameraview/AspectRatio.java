package com.leo.cameraview;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.util.SparseArrayCompat;

/**
 * 宽和高的比例
 * Created on 2017/9/15 下午4:43.
 * leo linxiaotao1993@vip.qq.com
 */

public final class AspectRatio implements Parcelable, Comparable<AspectRatio> {

    /** 以 x 为键，保存 以 y 为键的宽高比例 */
    private final static SparseArrayCompat<SparseArrayCompat<AspectRatio>> CACHE = new SparseArrayCompat<>(16);

    private final int mX;
    private final int mY;

    public static AspectRatio of(int x, int y) {
        int gcd = gcd(x, y);
        x /= gcd;
        y /= gcd;
        SparseArrayCompat<AspectRatio> arrayX = CACHE.get(x);
        if (arrayX == null) {
            AspectRatio ratio = new AspectRatio(x, y);
            arrayX = new SparseArrayCompat<>();
            arrayX.put(y, ratio);
            CACHE.put(x, arrayX);
            return ratio;
        } else {
            AspectRatio ratio = arrayX.get(y);
            if (ratio == null) {
                ratio = new AspectRatio(x, y);
                arrayX.put(y, ratio);
            }
            return ratio;
        }
    }

    /**
     * 解析 "x:y" 格式字符串的宽高比例
     *
     * @param s "x:y"
     * @return 宽高比例
     */
    public static AspectRatio parse(String s) {
        int position = s.indexOf(':');
        if (position == -1) {
            throw new IllegalArgumentException("错误的宽高比例：" + s);
        }
        try {
            int x = Integer.parseInt(s.substring(0, position));
            int y = Integer.parseInt(s.substring(position + 1));
            return AspectRatio.of(x, y);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("错误的宽高比例：" + s);
        }
    }

    public int getX() {
        return mX;
    }

    public int getY() {
        return mY;
    }

    public boolean matches(Size size) {
        int gcd = gcd(size.getWidth(), size.getHeight());
        int x = size.getWidth() / gcd;
        int y = size.getHeight() / gcd;
        return mX == x && mY == y;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (obj instanceof AspectRatio) {
            AspectRatio ratio = (AspectRatio) obj;
            return mX == ratio.getX() && mY == ratio.getY();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mY ^ ((mX << (Integer.SIZE / 2)) | (mX >>> (Integer.SIZE / 2)));
    }

    @Override
    public String toString() {
        return mX + ":" + mY;
    }

    public float toFloat() {
        return (float) mX / mY;
    }

    /**
     * 逆向
     *
     * @return
     */
    public AspectRatio inverse() {
        return AspectRatio.of(mY, mX);
    }


    private AspectRatio(int x, int y) {
        mX = x;
        mY = y;
    }

    /**
     * 使用"辗转相除法"求 a 和 b 的最大公约数
     *
     * @param a a
     * @param b b
     * @return 最大公约数
     */
    private static int gcd(int a, int b) {
        while (b != 0) {
            int c = b;
            b = a % b;
            a = c;
        }
        return a;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mX);
        dest.writeInt(this.mY);
    }

    protected AspectRatio(Parcel in) {
        this.mX = in.readInt();
        this.mY = in.readInt();
    }

    public static final Parcelable.Creator<AspectRatio> CREATOR = new Parcelable.Creator<AspectRatio>() {
        @Override
        public AspectRatio createFromParcel(Parcel source) {
            return new AspectRatio(source);
        }

        @Override
        public AspectRatio[] newArray(int size) {
            return new AspectRatio[size];
        }
    };

    @Override
    public int compareTo(@NonNull AspectRatio o) {
        if (equals(o)) {
            return 0;
        } else if (toFloat() - o.toFloat() > 0) {
            return 1;
        }
        return -1;
    }
}
