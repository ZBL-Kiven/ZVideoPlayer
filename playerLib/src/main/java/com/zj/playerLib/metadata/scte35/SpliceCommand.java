package com.zj.playerLib.metadata.scte35;

import com.zj.playerLib.metadata.Metadata.Entry;

public abstract class SpliceCommand implements Entry {
    public SpliceCommand() {
    }

    public String toString() {
        return "SCTE-35 splice command: type=" + this.getClass().getSimpleName();
    }

    public int describeContents() {
        return 0;
    }
}
