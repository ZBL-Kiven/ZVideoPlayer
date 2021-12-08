package com.zj.playerLib.metadata.id3;

import android.os.Parcel;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Util;

import java.util.Arrays;

public final class GeobFrame extends Id3Frame {
    public static final String ID = "GEOB";
    public final String mimeType;
    public final String filename;
    public final String description;
    public final byte[] data;
    public static final Creator<GeobFrame> CREATOR = new Creator<GeobFrame>() {
        public GeobFrame createFromParcel(Parcel in) {
            return new GeobFrame(in);
        }

        public GeobFrame[] newArray(int size) {
            return new GeobFrame[size];
        }
    };

    public GeobFrame(String mimeType, String filename, String description, byte[] data) {
        super("GEOB");
        this.mimeType = mimeType;
        this.filename = filename;
        this.description = description;
        this.data = data;
    }

    GeobFrame(Parcel in) {
        super("GEOB");
        this.mimeType = Util.castNonNull(in.readString());
        this.filename = Util.castNonNull(in.readString());
        this.description = Util.castNonNull(in.readString());
        this.data = Util.castNonNull(in.createByteArray());
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            GeobFrame other = (GeobFrame)obj;
            return Util.areEqual(this.mimeType, other.mimeType) && Util.areEqual(this.filename, other.filename) && Util.areEqual(this.description, other.description) && Arrays.equals(this.data, other.data);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 17;
        result = 31 * result + (this.mimeType != null ? this.mimeType.hashCode() : 0);
        result = 31 * result + (this.filename != null ? this.filename.hashCode() : 0);
        result = 31 * result + (this.description != null ? this.description.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(this.data);
        return result;
    }

    public String toString() {
        return this.id + ": mimeType=" + this.mimeType + ", filename=" + this.filename + ", description=" + this.description;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.mimeType);
        dest.writeString(this.filename);
        dest.writeString(this.description);
        dest.writeByteArray(this.data);
    }
}
