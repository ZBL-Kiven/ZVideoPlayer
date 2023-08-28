package com.zj.playerLib.metadata.id3;

import android.os.Parcel;

import androidx.annotation.Nullable;
import com.zj.playerLib.util.Util;

public final class UrlLinkFrame extends Id3Frame {
    @Nullable
    public final String description;
    public final String url;
    public static final Creator<UrlLinkFrame> CREATOR = new Creator<UrlLinkFrame>() {
        public UrlLinkFrame createFromParcel(Parcel in) {
            return new UrlLinkFrame(in);
        }

        public UrlLinkFrame[] newArray(int size) {
            return new UrlLinkFrame[size];
        }
    };

    public UrlLinkFrame(String id, @Nullable String description, String url) {
        super(id);
        this.description = description;
        this.url = url;
    }

    UrlLinkFrame(Parcel in) {
        super(Util.castNonNull(in.readString()));
        this.description = in.readString();
        this.url = Util.castNonNull(in.readString());
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            UrlLinkFrame other = (UrlLinkFrame)obj;
            return this.id.equals(other.id) && Util.areEqual(this.description, other.description) && Util.areEqual(this.url, other.url);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 17;
        result = 31 * result + this.id.hashCode();
        result = 31 * result + (this.description != null ? this.description.hashCode() : 0);
        result = 31 * result + (this.url != null ? this.url.hashCode() : 0);
        return result;
    }

    public String toString() {
        return this.id + ": url=" + this.url;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.id);
        dest.writeString(this.description);
        dest.writeString(this.url);
    }
}
