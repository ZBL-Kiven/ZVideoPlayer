package com.zj.playerLib.video;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Util;

import java.util.Arrays;

public final class ColorInfo implements Parcelable {
    public final int colorSpace;
    public final int colorRange;
    public final int colorTransfer;
    @Nullable
    public final byte[] hdrStaticInfo;
    private int hashCode;
    public static final Creator<ColorInfo> CREATOR = new Creator<ColorInfo>() {
        public ColorInfo createFromParcel(Parcel in) {
            return new ColorInfo(in);
        }

        public ColorInfo[] newArray(int size) {
            return new ColorInfo[0];
        }
    };

    public ColorInfo(int colorSpace, int colorRange, int colorTransfer, @Nullable byte[] hdrStaticInfo) {
        this.colorSpace = colorSpace;
        this.colorRange = colorRange;
        this.colorTransfer = colorTransfer;
        this.hdrStaticInfo = hdrStaticInfo;
    }

    ColorInfo(Parcel in) {
        this.colorSpace = in.readInt();
        this.colorRange = in.readInt();
        this.colorTransfer = in.readInt();
        boolean hasHdrStaticInfo = Util.readBoolean(in);
        this.hdrStaticInfo = hasHdrStaticInfo ? in.createByteArray() : null;
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            ColorInfo other = (ColorInfo)obj;
            return this.colorSpace == other.colorSpace && this.colorRange == other.colorRange && this.colorTransfer == other.colorTransfer && Arrays.equals(this.hdrStaticInfo, other.hdrStaticInfo);
        } else {
            return false;
        }
    }

    public String toString() {
        return "ColorInfo(" + this.colorSpace + ", " + this.colorRange + ", " + this.colorTransfer + ", " + (this.hdrStaticInfo != null) + ")";
    }

    public int hashCode() {
        if (this.hashCode == 0) {
            int result = 17;
            result = 31 * result + this.colorSpace;
            result = 31 * result + this.colorRange;
            result = 31 * result + this.colorTransfer;
            result = 31 * result + Arrays.hashCode(this.hdrStaticInfo);
            this.hashCode = result;
        }

        return this.hashCode;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.colorSpace);
        dest.writeInt(this.colorRange);
        dest.writeInt(this.colorTransfer);
        Util.writeBoolean(dest, this.hdrStaticInfo != null);
        if (this.hdrStaticInfo != null) {
            dest.writeByteArray(this.hdrStaticInfo);
        }

    }
}
