//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.metadata.id3;

import android.os.Parcel;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Util;

public final class CommentFrame extends Id3Frame {
    public static final String ID = "COMM";
    public final String language;
    public final String description;
    public final String text;
    public static final Creator<CommentFrame> CREATOR = new Creator<CommentFrame>() {
        public CommentFrame createFromParcel(Parcel in) {
            return new CommentFrame(in);
        }

        public CommentFrame[] newArray(int size) {
            return new CommentFrame[size];
        }
    };

    public CommentFrame(String language, String description, String text) {
        super("COMM");
        this.language = language;
        this.description = description;
        this.text = text;
    }

    CommentFrame(Parcel in) {
        super("COMM");
        this.language = (String)Util.castNonNull(in.readString());
        this.description = (String)Util.castNonNull(in.readString());
        this.text = (String)Util.castNonNull(in.readString());
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            CommentFrame other = (CommentFrame)obj;
            return Util.areEqual(this.description, other.description) && Util.areEqual(this.language, other.language) && Util.areEqual(this.text, other.text);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 17;
        result = 31 * result + (this.language != null ? this.language.hashCode() : 0);
        result = 31 * result + (this.description != null ? this.description.hashCode() : 0);
        result = 31 * result + (this.text != null ? this.text.hashCode() : 0);
        return result;
    }

    public String toString() {
        return this.id + ": language=" + this.language + ", description=" + this.description;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.id);
        dest.writeString(this.language);
        dest.writeString(this.text);
    }
}
