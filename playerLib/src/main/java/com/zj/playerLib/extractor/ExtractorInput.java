//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor;

import java.io.IOException;

public interface ExtractorInput {
    int read(byte[] var1, int var2, int var3) throws IOException, InterruptedException;

    boolean readFully(byte[] var1, int var2, int var3, boolean var4) throws IOException, InterruptedException;

    void readFully(byte[] var1, int var2, int var3) throws IOException, InterruptedException;

    int skip(int var1) throws IOException, InterruptedException;

    boolean skipFully(int var1, boolean var2) throws IOException, InterruptedException;

    void skipFully(int var1) throws IOException, InterruptedException;

    boolean peekFully(byte[] var1, int var2, int var3, boolean var4) throws IOException, InterruptedException;

    void peekFully(byte[] var1, int var2, int var3) throws IOException, InterruptedException;

    boolean advancePeekPosition(int var1, boolean var2) throws IOException, InterruptedException;

    void advancePeekPosition(int var1) throws IOException, InterruptedException;

    void resetPeekPosition();

    long getPeekPosition();

    long getPosition();

    long getLength();

    <E extends Throwable> void setRetryPosition(long var1, E var3) throws E;
}
