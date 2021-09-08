//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.util;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class ReusableBufferedOutputStream extends BufferedOutputStream {
    private boolean closed;

    public ReusableBufferedOutputStream(OutputStream out) {
        super(out);
    }

    public ReusableBufferedOutputStream(OutputStream out, int size) {
        super(out, size);
    }

    public void close() throws IOException {
        this.closed = true;
        this.flush();
        this.out.close();
    }

    public void reset(OutputStream out) {
        Assertions.checkState(this.closed);
        this.out = out;
        this.count = 0;
        this.closed = false;
    }
}
