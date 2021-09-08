//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream;

import java.io.IOException;

public final class DataSourceException extends IOException {
    public static final int POSITION_OUT_OF_RANGE = 0;
    public final int reason;

    public DataSourceException(int reason) {
        this.reason = reason;
    }
}
