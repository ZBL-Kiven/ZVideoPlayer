//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream.cache;

import com.zj.playerLib.upstream.DataSink;
import com.zj.playerLib.upstream.DataSink.Factory;

public final class CacheDataSinkFactory implements Factory {
    private final Cache cache;
    private final long maxCacheFileSize;
    private final int bufferSize;

    public CacheDataSinkFactory(Cache cache, long maxCacheFileSize) {
        this(cache, maxCacheFileSize, 20480);
    }

    public CacheDataSinkFactory(Cache cache, long maxCacheFileSize, int bufferSize) {
        this.cache = cache;
        this.maxCacheFileSize = maxCacheFileSize;
        this.bufferSize = bufferSize;
    }

    public DataSink createDataSink() {
        return new CacheDataSink(this.cache, this.maxCacheFileSize, this.bufferSize);
    }
}
