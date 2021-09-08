package com.zj.playerLib.offline;

import androidx.annotation.Nullable;
import com.zj.playerLib.upstream.DataSink;
import com.zj.playerLib.upstream.DataSource;
import com.zj.playerLib.upstream.DummyDataSource;
import com.zj.playerLib.upstream.FileDataSource;
import com.zj.playerLib.upstream.PriorityDataSource;
import com.zj.playerLib.upstream.DataSource.Factory;
import com.zj.playerLib.upstream.cache.Cache;
import com.zj.playerLib.upstream.cache.CacheDataSink;
import com.zj.playerLib.upstream.cache.CacheDataSource;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.PriorityTaskManager;

public final class DownloaderConstructorHelper {
    private final Cache cache;
    private final Factory upstreamDataSourceFactory;
    private final Factory cacheReadDataSourceFactory;
    private final DataSink.Factory cacheWriteDataSinkFactory;
    private final PriorityTaskManager priorityTaskManager;

    public DownloaderConstructorHelper(Cache cache, Factory upstreamDataSourceFactory) {
        this(cache, upstreamDataSourceFactory, null, null, null);
    }

    public DownloaderConstructorHelper(Cache cache, Factory upstreamDataSourceFactory, @Nullable Factory cacheReadDataSourceFactory, @Nullable DataSink.Factory cacheWriteDataSinkFactory, @Nullable PriorityTaskManager priorityTaskManager) {
        Assertions.checkNotNull(upstreamDataSourceFactory);
        this.cache = cache;
        this.upstreamDataSourceFactory = upstreamDataSourceFactory;
        this.cacheReadDataSourceFactory = cacheReadDataSourceFactory;
        this.cacheWriteDataSinkFactory = cacheWriteDataSinkFactory;
        this.priorityTaskManager = priorityTaskManager;
    }

    public Cache getCache() {
        return this.cache;
    }

    public PriorityTaskManager getPriorityTaskManager() {
        return this.priorityTaskManager != null ? this.priorityTaskManager : new PriorityTaskManager();
    }

    public CacheDataSource buildCacheDataSource(boolean offline) {
        DataSource cacheReadDataSource = this.cacheReadDataSourceFactory != null ? this.cacheReadDataSourceFactory.createDataSource() : new FileDataSource();
        if (offline) {
            return new CacheDataSource(this.cache, DummyDataSource.INSTANCE, cacheReadDataSource, null, 1, null);
        } else {
            DataSink cacheWriteDataSink = this.cacheWriteDataSinkFactory != null ? this.cacheWriteDataSinkFactory.createDataSink() : new CacheDataSink(this.cache, 2097152L);
            DataSource upstream = this.upstreamDataSourceFactory.createDataSource();
            DataSource upstream1 = this.priorityTaskManager == null ? upstream : new PriorityDataSource(upstream, this.priorityTaskManager, -1000);
            return new CacheDataSource(this.cache, upstream1, cacheReadDataSource, cacheWriteDataSink, 1, null);
        }
    }
}
