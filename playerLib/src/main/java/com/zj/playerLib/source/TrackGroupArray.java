package com.zj.playerLib.source;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import java.util.Arrays;

public final class TrackGroupArray implements Parcelable {
    public static final TrackGroupArray EMPTY = new TrackGroupArray();
    public final int length;
    private final TrackGroup[] trackGroups;
    private int hashCode;
    public static final Creator<TrackGroupArray> CREATOR = new Creator<TrackGroupArray>() {
        public TrackGroupArray createFromParcel(Parcel in) {
            return new TrackGroupArray(in);
        }

        public TrackGroupArray[] newArray(int size) {
            return new TrackGroupArray[size];
        }
    };

    public TrackGroupArray(TrackGroup... trackGroups) {
        this.trackGroups = trackGroups;
        this.length = trackGroups.length;
    }

    TrackGroupArray(Parcel in) {
        this.length = in.readInt();
        this.trackGroups = new TrackGroup[this.length];

        for(int i = 0; i < this.length; ++i) {
            this.trackGroups[i] = in.readParcelable(TrackGroup.class.getClassLoader());
        }

    }

    public TrackGroup get(int index) {
        return this.trackGroups[index];
    }

    public int indexOf(TrackGroup group) {
        for(int i = 0; i < this.length; ++i) {
            if (this.trackGroups[i] == group) {
                return i;
            }
        }

        return -1;
    }

    public boolean isEmpty() {
        return this.length == 0;
    }

    public int hashCode() {
        if (this.hashCode == 0) {
            this.hashCode = Arrays.hashCode(this.trackGroups);
        }

        return this.hashCode;
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            TrackGroupArray other = (TrackGroupArray)obj;
            return this.length == other.length && Arrays.equals(this.trackGroups, other.trackGroups);
        } else {
            return false;
        }
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.length);

        for(int i = 0; i < this.length; ++i) {
            dest.writeParcelable(this.trackGroups[i], 0);
        }

    }
}
