//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.metadata.id3;

import com.zj.playerLib.metadata.Metadata.Entry;

public abstract class Id3Frame implements Entry {
    public final String id;

    public Id3Frame(String id) {
        this.id = id;
    }

    public String toString() {
        return this.id;
    }

    public int describeContents() {
        return 0;
    }
}
