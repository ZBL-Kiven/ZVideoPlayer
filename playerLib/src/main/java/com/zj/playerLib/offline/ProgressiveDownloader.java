//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.offline;

import android.net.Uri;

import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.upstream.cache.Cache;
import com.zj.playerLib.upstream.cache.CacheDataSource;
import com.zj.playerLib.upstream.cache.CacheUtil;
import com.zj.playerLib.upstream.cache.CacheUtil.CachingCounters;
import com.zj.playerLib.util.PriorityTaskManager;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProgressiveDownloader implements Downloader {
    private static final int BUFFER_SIZE_BYTES = 131072;
    private final DataSpec dataSpec;
    private final Cache cache;
    private final CacheDataSource dataSource;
    private final PriorityTaskManager priorityTaskManager;
    private final CachingCounters cachingCounters;
    private final AtomicBoolean isCanceled;

    public ProgressiveDownloader(Uri uri, String customCacheKey, DownloaderConstructorHelper constructorHelper) {
        this.dataSpec = new DataSpec(uri, 0L, -1L, customCacheKey, 0);
        this.cache = constructorHelper.getCache();
        this.dataSource = constructorHelper.buildCacheDataSource(false);
        this.priorityTaskManager = constructorHelper.getPriorityTaskManager();
        this.cachingCounters = new CachingCounters();
        this.isCanceled = new AtomicBoolean();
    }

    public void download() throws InterruptedException, IOException {
        this.priorityTaskManager.add(-1000);

        try {
            CacheUtil.cache(this.dataSpec, this.cache, this.dataSource, new byte[131072], this.priorityTaskManager, -1000, this.cachingCounters, this.isCanceled, true);
        } finally {
            this.priorityTaskManager.remove(-1000);
        }

    }

    public void cancel() {
        this.isCanceled.set(true);
    }

    public long getDownloadedBytes() {
        return this.cachingCounters.totalCachedBytes();
    }

    public float getDownloadPercentage() {
        long contentLength = this.cachingCounters.contentLength;
        return contentLength == -1L ? -1.0F : (float)this.cachingCounters.totalCachedBytes() * 100.0F / (float)contentLength;
    }

    public void remove() {
        CacheUtil.remove(this.cache, CacheUtil.getKey(this.dataSpec));
    }
}
