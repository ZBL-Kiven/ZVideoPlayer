package com.zj.playerLib.metadata.emsg;

import android.os.Parcel;

import androidx.annotation.Nullable;

import com.zj.playerLib.metadata.Metadata.Entry;
import com.zj.playerLib.util.Util;

import java.util.Arrays;

public final class EventMessage implements Entry {
    public final String schemeIdUri;
    public final String value;
    public final long durationMs;
    public final long presentationTimeUs;
    public final long id;
    public final byte[] messageData;
    private int hashCode;
    public static final Creator<EventMessage> CREATOR = new Creator<EventMessage>() {
        public EventMessage createFromParcel(Parcel in) {
            return new EventMessage(in);
        }

        public EventMessage[] newArray(int size) {
            return new EventMessage[size];
        }
    };

    public EventMessage(String schemeIdUri, String value, long durationMs, long id, byte[] messageData, long presentationTimeUs) {
        this.schemeIdUri = schemeIdUri;
        this.value = value;
        this.durationMs = durationMs;
        this.id = id;
        this.messageData = messageData;
        this.presentationTimeUs = presentationTimeUs;
    }

    EventMessage(Parcel in) {
        this.schemeIdUri = Util.castNonNull(in.readString());
        this.value = Util.castNonNull(in.readString());
        this.presentationTimeUs = in.readLong();
        this.durationMs = in.readLong();
        this.id = in.readLong();
        this.messageData = Util.castNonNull(in.createByteArray());
    }

    public int hashCode() {
        if (this.hashCode == 0) {
            int result = 17;
            result = 31 * result + (this.schemeIdUri != null ? this.schemeIdUri.hashCode() : 0);
            result = 31 * result + (this.value != null ? this.value.hashCode() : 0);
            result = 31 * result + (int)(this.presentationTimeUs ^ this.presentationTimeUs >>> 32);
            result = 31 * result + (int)(this.durationMs ^ this.durationMs >>> 32);
            result = 31 * result + (int)(this.id ^ this.id >>> 32);
            result = 31 * result + Arrays.hashCode(this.messageData);
            this.hashCode = result;
        }

        return this.hashCode;
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            EventMessage other = (EventMessage)obj;
            return this.presentationTimeUs == other.presentationTimeUs && this.durationMs == other.durationMs && this.id == other.id && Util.areEqual(this.schemeIdUri, other.schemeIdUri) && Util.areEqual(this.value, other.value) && Arrays.equals(this.messageData, other.messageData);
        } else {
            return false;
        }
    }

    public String toString() {
        return "EMSG: scheme=" + this.schemeIdUri + ", id=" + this.id + ", value=" + this.value;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.schemeIdUri);
        dest.writeString(this.value);
        dest.writeLong(this.presentationTimeUs);
        dest.writeLong(this.durationMs);
        dest.writeLong(this.id);
        dest.writeByteArray(this.messageData);
    }
}
