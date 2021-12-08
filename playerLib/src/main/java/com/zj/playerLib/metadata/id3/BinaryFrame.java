package com.zj.playerLib.metadata.id3;

import android.os.Parcel;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Util;

import java.util.Arrays;

public final class BinaryFrame extends Id3Frame {
    public final byte[] data;
    public static final Creator<BinaryFrame> CREATOR = new Creator<BinaryFrame>() {
        public BinaryFrame createFromParcel(Parcel in) {
            return new BinaryFrame(in);
        }

        public BinaryFrame[] newArray(int size) {
            return new BinaryFrame[size];
        }
    };

    public BinaryFrame(String id, byte[] data) {
        super(id);
        this.data = data;
    }

    BinaryFrame(Parcel in) {
        super(Util.castNonNull(in.readString()));
        this.data = Util.castNonNull(in.createByteArray());
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            BinaryFrame other = (BinaryFrame)obj;
            return this.id.equals(other.id) && Arrays.equals(this.data, other.data);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 17;
        result = 31 * result + this.id.hashCode();
        result = 31 * result + Arrays.hashCode(this.data);
        return result;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.id);
        dest.writeByteArray(this.data);
    }
}
