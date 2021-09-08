//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.metadata.scte35;

import android.os.Parcel;

public final class SpliceNullCommand extends SpliceCommand {
    public static final Creator<SpliceNullCommand> CREATOR = new Creator<SpliceNullCommand>() {
        public SpliceNullCommand createFromParcel(Parcel in) {
            return new SpliceNullCommand();
        }

        public SpliceNullCommand[] newArray(int size) {
            return new SpliceNullCommand[size];
        }
    };

    public SpliceNullCommand() {
    }

    public void writeToParcel(Parcel dest, int flags) {
    }
}
