package com.zj.playerLib.metadata.id3;

import android.os.Parcel;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Util;

import java.util.Arrays;

public final class ChapterTocFrame extends Id3Frame {
    public static final String ID = "CTOC";
    public final String elementId;
    public final boolean isRoot;
    public final boolean isOrdered;
    public final String[] children;
    private final Id3Frame[] subFrames;
    public static final Creator<ChapterTocFrame> CREATOR = new Creator<ChapterTocFrame>() {
        public ChapterTocFrame createFromParcel(Parcel in) {
            return new ChapterTocFrame(in);
        }

        public ChapterTocFrame[] newArray(int size) {
            return new ChapterTocFrame[size];
        }
    };

    public ChapterTocFrame(String elementId, boolean isRoot, boolean isOrdered, String[] children, Id3Frame[] subFrames) {
        super("CTOC");
        this.elementId = elementId;
        this.isRoot = isRoot;
        this.isOrdered = isOrdered;
        this.children = children;
        this.subFrames = subFrames;
    }

    ChapterTocFrame(Parcel in) {
        super("CTOC");
        this.elementId = Util.castNonNull(in.readString());
        this.isRoot = in.readByte() != 0;
        this.isOrdered = in.readByte() != 0;
        this.children = in.createStringArray();
        int subFrameCount = in.readInt();
        this.subFrames = new Id3Frame[subFrameCount];

        for(int i = 0; i < subFrameCount; ++i) {
            this.subFrames[i] = in.readParcelable(Id3Frame.class.getClassLoader());
        }

    }

    public int getSubFrameCount() {
        return this.subFrames.length;
    }

    public Id3Frame getSubFrame(int index) {
        return this.subFrames[index];
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            ChapterTocFrame other = (ChapterTocFrame)obj;
            return this.isRoot == other.isRoot && this.isOrdered == other.isOrdered && Util.areEqual(this.elementId, other.elementId) && Arrays.equals(this.children, other.children) && Arrays.equals(this.subFrames, other.subFrames);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 17;
        result = 31 * result + (this.isRoot ? 1 : 0);
        result = 31 * result + (this.isOrdered ? 1 : 0);
        result = 31 * result + (this.elementId != null ? this.elementId.hashCode() : 0);
        return result;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.elementId);
        dest.writeByte((byte)(this.isRoot ? 1 : 0));
        dest.writeByte((byte)(this.isOrdered ? 1 : 0));
        dest.writeStringArray(this.children);
        dest.writeInt(this.subFrames.length);
        Id3Frame[] var3 = this.subFrames;
        int var4 = var3.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            Id3Frame subFrame = var3[var5];
            dest.writeParcelable(subFrame, 0);
        }

    }
}
