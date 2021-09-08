//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.offline;

import java.io.IOException;

public interface Downloader {
    void download() throws InterruptedException, IOException;

    void cancel();

    long getDownloadedBytes();

    float getDownloadPercentage();

    void remove() throws InterruptedException;
}
