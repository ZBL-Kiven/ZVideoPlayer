//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream;

import java.io.IOException;

public interface LoaderErrorThrower {
    void maybeThrowError() throws IOException;

    void maybeThrowError(int var1) throws IOException;

    public static final class Dummy implements LoaderErrorThrower {
        public Dummy() {
        }

        public void maybeThrowError() throws IOException {
        }

        public void maybeThrowError(int minRetryCount) throws IOException {
        }
    }
}
