package com.zj.playerLib.metadata.id3;

import android.os.Parcel;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Util;

import java.util.Arrays;

public final class ChapterFrame extends Id3Frame {
    public static final String ID = "CHAP";
    public final String chapterId;
    public final int startTimeMs;
    public final int endTimeMs;
    public final long startOffset;
    public final long endOffset;
    private final Id3Frame[] subFrames;
    public static final Creator<ChapterFrame> CREATOR = new Creator<ChapterFrame>() {
        public ChapterFrame createFromParcel(Parcel in) {
            return new ChapterFrame(in);
        }

        public ChapterFrame[] newArray(int size) {
            return new ChapterFrame[size];
        }
    };

    public ChapterFrame(String chapterId, int startTimeMs, int endTimeMs, long startOffset, long endOffset, Id3Frame[] subFrames) {
        super("CHAP");
        this.chapterId = chapterId;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.subFrames = subFrames;
    }

    ChapterFrame(Parcel in) {
        super("CHAP");
        this.chapterId = Util.castNonNull(in.readString());
        this.startTimeMs = in.readInt();
        this.endTimeMs = in.readInt();
        this.startOffset = in.readLong();
        this.endOffset = in.readLong();
        int subFrameCount = in.readInt();
        this.subFrames = new Id3Frame[subFrameCount];

        for (int i = 0; i < subFrameCount; ++i) {
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
            ChapterFrame other = (ChapterFrame) obj;
            return this.startTimeMs == other.startTimeMs && this.endTimeMs == other.endTimeMs && this.startOffset == other.startOffset && this.endOffset == other.endOffset && Util.areEqual(this.chapterId, other.chapterId) && Arrays.equals(this.subFrames, other.subFrames);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 17;
        result = 31 * result + this.startTimeMs;
        result = 31 * result + this.endTimeMs;
        result = 31 * result + (int) this.startOffset;
        result = 31 * result + (int) this.endOffset;
        result = 31 * result + (this.chapterId != null ? this.chapterId.hashCode() : 0);
        return result;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.chapterId);
        dest.writeInt(this.startTimeMs);
        dest.writeInt(this.endTimeMs);
        dest.writeLong(this.startOffset);
        dest.writeLong(this.endOffset);
        dest.writeInt(this.subFrames.length);
        for (Id3Frame subFrame : this.subFrames) {
            dest.writeParcelable(subFrame, 0);
        }
    }

    public int describeContents() {
        return 0;
    }
}
