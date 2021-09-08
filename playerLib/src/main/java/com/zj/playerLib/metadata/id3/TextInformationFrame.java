//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.metadata.id3;

import android.os.Parcel;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Util;

public final class TextInformationFrame extends Id3Frame {
    @Nullable
    public final String description;
    public final String value;
    public static final Creator<TextInformationFrame> CREATOR = new Creator<TextInformationFrame>() {
        public TextInformationFrame createFromParcel(Parcel in) {
            return new TextInformationFrame(in);
        }

        public TextInformationFrame[] newArray(int size) {
            return new TextInformationFrame[size];
        }
    };

    public TextInformationFrame(String id, @Nullable String description, String value) {
        super(id);
        this.description = description;
        this.value = value;
    }

    TextInformationFrame(Parcel in) {
        super((String)Util.castNonNull(in.readString()));
        this.description = in.readString();
        this.value = (String)Util.castNonNull(in.readString());
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            TextInformationFrame other = (TextInformationFrame)obj;
            return this.id.equals(other.id) && Util.areEqual(this.description, other.description) && Util.areEqual(this.value, other.value);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 17;
        result = 31 * result + this.id.hashCode();
        result = 31 * result + (this.description != null ? this.description.hashCode() : 0);
        result = 31 * result + (this.value != null ? this.value.hashCode() : 0);
        return result;
    }

    public String toString() {
        return this.id + ": value=" + this.value;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.id);
        dest.writeString(this.description);
        dest.writeString(this.value);
    }
}
