package com.zj.playerLib.source;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import com.zj.playerLib.Format;
import com.zj.playerLib.util.Assertions;

import java.util.Arrays;

public final class TrackGroup implements Parcelable {
    public final int length;
    private final Format[] formats;
    private int hashCode;
    public static final Creator<TrackGroup> CREATOR = new Creator<TrackGroup>() {
        public TrackGroup createFromParcel(Parcel in) {
            return new TrackGroup(in);
        }

        public TrackGroup[] newArray(int size) {
            return new TrackGroup[size];
        }
    };

    public TrackGroup(Format... formats) {
        Assertions.checkState(formats.length > 0);
        this.formats = formats;
        this.length = formats.length;
    }

    TrackGroup(Parcel in) {
        this.length = in.readInt();
        this.formats = new Format[this.length];

        for (int i = 0; i < this.length; ++i) {
            this.formats[i] = in.readParcelable(Format.class.getClassLoader());
        }

    }

    public Format getFormat(int index) {
        return this.formats[index];
    }

    public int indexOf(Format format) {
        for (int i = 0; i < this.formats.length; ++i) {
            if (format == this.formats[i]) {
                return i;
            }
        }

        return -1;
    }

    public int hashCode() {
        if (this.hashCode == 0) {
            int result = 17;
            result = 31 * result + Arrays.hashCode(this.formats);
            this.hashCode = result;
        }

        return this.hashCode;
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            TrackGroup other = (TrackGroup) obj;
            return this.length == other.length && Arrays.equals(this.formats, other.formats);
        } else {
            return false;
        }
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.length);

        for (int i = 0; i < this.length; ++i) {
            dest.writeParcelable(this.formats[i], 0);
        }

    }
}
