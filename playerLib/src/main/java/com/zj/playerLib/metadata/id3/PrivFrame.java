package com.zj.playerLib.metadata.id3;

import android.os.Parcel;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Util;

import java.util.Arrays;

public final class PrivFrame extends Id3Frame {
    public static final String ID = "PRIV";
    public final String owner;
    public final byte[] privateData;
    public static final Creator<PrivFrame> CREATOR = new Creator<PrivFrame>() {
        public PrivFrame createFromParcel(Parcel in) {
            return new PrivFrame(in);
        }

        public PrivFrame[] newArray(int size) {
            return new PrivFrame[size];
        }
    };

    public PrivFrame(String owner, byte[] privateData) {
        super("PRIV");
        this.owner = owner;
        this.privateData = privateData;
    }

    PrivFrame(Parcel in) {
        super("PRIV");
        this.owner = Util.castNonNull(in.readString());
        this.privateData = Util.castNonNull(in.createByteArray());
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            PrivFrame other = (PrivFrame)obj;
            return Util.areEqual(this.owner, other.owner) && Arrays.equals(this.privateData, other.privateData);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 17;
        result = 31 * result + (this.owner != null ? this.owner.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(this.privateData);
        return result;
    }

    public String toString() {
        return this.id + ": owner=" + this.owner;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.owner);
        dest.writeByteArray(this.privateData);
    }
}
