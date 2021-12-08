package com.zj.playerLib.metadata;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

public final class Metadata implements Parcelable {
    private final Entry[] entries;
    public static final Creator<Metadata> CREATOR = new Creator<Metadata>() {
        public Metadata createFromParcel(Parcel in) {
            return new Metadata(in);
        }

        public Metadata[] newArray(int size) {
            return new Metadata[0];
        }
    };

    public Metadata(Entry... entries) {
        this.entries = entries == null ? new Entry[0] : entries;
    }

    public Metadata(List<? extends Entry> entries) {
        if (entries != null) {
            this.entries = new Entry[entries.size()];
            entries.toArray(this.entries);
        } else {
            this.entries = new Entry[0];
        }

    }

    Metadata(Parcel in) {
        this.entries = new Entry[in.readInt()];

        for(int i = 0; i < this.entries.length; ++i) {
            this.entries[i] = in.readParcelable(Entry.class.getClassLoader());
        }

    }

    public int length() {
        return this.entries.length;
    }

    public Entry get(int index) {
        return this.entries[index];
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            Metadata other = (Metadata)obj;
            return Arrays.equals(this.entries, other.entries);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Arrays.hashCode(this.entries);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.entries.length);
        Entry[] var3 = this.entries;
        int var4 = var3.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            Entry entry = var3[var5];
            dest.writeParcelable(entry, 0);
        }

    }

    public interface Entry extends Parcelable {
    }
}
