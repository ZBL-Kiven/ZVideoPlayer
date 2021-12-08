package com.zj.playerLib.metadata.id3;

import android.os.Parcel;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Util;

public final class InternalFrame extends Id3Frame {
    public static final String ID = "----";
    public final String domain;
    public final String description;
    public final String text;
    public static final Creator<InternalFrame> CREATOR = new Creator<InternalFrame>() {
        public InternalFrame createFromParcel(Parcel in) {
            return new InternalFrame(in);
        }

        public InternalFrame[] newArray(int size) {
            return new InternalFrame[size];
        }
    };

    public InternalFrame(String domain, String description, String text) {
        super("----");
        this.domain = domain;
        this.description = description;
        this.text = text;
    }

    InternalFrame(Parcel in) {
        super("----");
        this.domain = Util.castNonNull(in.readString());
        this.description = Util.castNonNull(in.readString());
        this.text = Util.castNonNull(in.readString());
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            InternalFrame other = (InternalFrame)obj;
            return Util.areEqual(this.description, other.description) && Util.areEqual(this.domain, other.domain) && Util.areEqual(this.text, other.text);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 17;
        result = 31 * result + (this.domain != null ? this.domain.hashCode() : 0);
        result = 31 * result + (this.description != null ? this.description.hashCode() : 0);
        result = 31 * result + (this.text != null ? this.text.hashCode() : 0);
        return result;
    }

    public String toString() {
        return this.id + ": domain=" + this.domain + ", description=" + this.description;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.id);
        dest.writeString(this.domain);
        dest.writeString(this.text);
    }
}
