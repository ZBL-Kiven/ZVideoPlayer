package com.zj.playerLib.metadata.id3;

import android.os.Parcel;

import androidx.annotation.Nullable;
import java.util.Arrays;

public final class MlltFrame extends Id3Frame {
    public static final String ID = "MLLT";
    public final int mpegFramesBetweenReference;
    public final int bytesBetweenReference;
    public final int millisecondsBetweenReference;
    public final int[] bytesDeviations;
    public final int[] millisecondsDeviations;
    public static final Creator<MlltFrame> CREATOR = new Creator<MlltFrame>() {
        public MlltFrame createFromParcel(Parcel in) {
            return new MlltFrame(in);
        }

        public MlltFrame[] newArray(int size) {
            return new MlltFrame[size];
        }
    };

    public MlltFrame(int mpegFramesBetweenReference, int bytesBetweenReference, int millisecondsBetweenReference, int[] bytesDeviations, int[] millisecondsDeviations) {
        super("MLLT");
        this.mpegFramesBetweenReference = mpegFramesBetweenReference;
        this.bytesBetweenReference = bytesBetweenReference;
        this.millisecondsBetweenReference = millisecondsBetweenReference;
        this.bytesDeviations = bytesDeviations;
        this.millisecondsDeviations = millisecondsDeviations;
    }

    MlltFrame(Parcel in) {
        super("MLLT");
        this.mpegFramesBetweenReference = in.readInt();
        this.bytesBetweenReference = in.readInt();
        this.millisecondsBetweenReference = in.readInt();
        this.bytesDeviations = in.createIntArray();
        this.millisecondsDeviations = in.createIntArray();
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            MlltFrame other = (MlltFrame)obj;
            return this.mpegFramesBetweenReference == other.mpegFramesBetweenReference && this.bytesBetweenReference == other.bytesBetweenReference && this.millisecondsBetweenReference == other.millisecondsBetweenReference && Arrays.equals(this.bytesDeviations, other.bytesDeviations) && Arrays.equals(this.millisecondsDeviations, other.millisecondsDeviations);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 17;
        result = 31 * result + this.mpegFramesBetweenReference;
        result = 31 * result + this.bytesBetweenReference;
        result = 31 * result + this.millisecondsBetweenReference;
        result = 31 * result + Arrays.hashCode(this.bytesDeviations);
        result = 31 * result + Arrays.hashCode(this.millisecondsDeviations);
        return result;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mpegFramesBetweenReference);
        dest.writeInt(this.bytesBetweenReference);
        dest.writeInt(this.millisecondsBetweenReference);
        dest.writeIntArray(this.bytesDeviations);
        dest.writeIntArray(this.millisecondsDeviations);
    }

    public int describeContents() {
        return 0;
    }
}
