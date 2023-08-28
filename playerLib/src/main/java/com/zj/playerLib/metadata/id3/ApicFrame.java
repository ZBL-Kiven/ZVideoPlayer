package com.zj.playerLib.metadata.id3;

import android.os.Parcel;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Util;

import java.util.Arrays;

public final class ApicFrame extends Id3Frame {
    public static final String ID = "APIC";
    public final String mimeType;
    @Nullable
    public final String description;
    public final int pictureType;
    public final byte[] pictureData;
    public static final Creator<ApicFrame> CREATOR = new Creator<ApicFrame>() {
        public ApicFrame createFromParcel(Parcel in) {
            return new ApicFrame(in);
        }

        public ApicFrame[] newArray(int size) {
            return new ApicFrame[size];
        }
    };

    public ApicFrame(String mimeType, @Nullable String description, int pictureType, byte[] pictureData) {
        super("APIC");
        this.mimeType = mimeType;
        this.description = description;
        this.pictureType = pictureType;
        this.pictureData = pictureData;
    }

    ApicFrame(Parcel in) {
        super("APIC");
        this.mimeType = Util.castNonNull(in.readString());
        this.description = Util.castNonNull(in.readString());
        this.pictureType = in.readInt();
        this.pictureData = Util.castNonNull(in.createByteArray());
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            ApicFrame other = (ApicFrame)obj;
            return this.pictureType == other.pictureType && Util.areEqual(this.mimeType, other.mimeType) && Util.areEqual(this.description, other.description) && Arrays.equals(this.pictureData, other.pictureData);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 17;
        result = 31 * result + this.pictureType;
        result = 31 * result + (this.mimeType != null ? this.mimeType.hashCode() : 0);
        result = 31 * result + (this.description != null ? this.description.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(this.pictureData);
        return result;
    }

    public String toString() {
        return this.id + ": mimeType=" + this.mimeType + ", description=" + this.description;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.mimeType);
        dest.writeString(this.description);
        dest.writeInt(this.pictureType);
        dest.writeByteArray(this.pictureData);
    }
}
