package com.zj.playerLib.upstream.cache;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.zj.playerLib.upstream.DataSource;
import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.upstream.cache.Cache.CacheException;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.PriorityTaskManager;
import com.zj.playerLib.util.Util;
import com.zj.playerLib.util.PriorityTaskManager.PriorityTooLowException;
import java.io.EOFException;
import java.io.IOException;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CacheUtil {
    public static final int DEFAULT_BUFFER_SIZE_BYTES = 131072;
    public static final CacheKeyFactory DEFAULT_CACHE_KEY_FACTORY = CacheUtil::getKey;

    public static String generateKey(Uri uri) {
        return uri.toString();
    }

    public static String getKey(DataSpec dataSpec) {
        return dataSpec.key != null ? dataSpec.key : generateKey(dataSpec.uri);
    }

    public static void getCached(DataSpec dataSpec, Cache cache, CachingCounters counters) {
        String key = getKey(dataSpec);
        long start = dataSpec.absoluteStreamPosition;
        long left = dataSpec.length != -1L ? dataSpec.length : cache.getContentLength(key);
        counters.contentLength = left;
        counters.alreadyCachedBytes = 0L;

        long blockLength;
        for(counters.newlyCachedBytes = 0L; left != 0L; left -= left == -1L ? 0L : blockLength) {
            blockLength = cache.getCachedLength(key, start, left != -1L ? left : Long.MAX_VALUE);
            if (blockLength > 0L) {
                counters.alreadyCachedBytes += blockLength;
            } else {
                blockLength = -blockLength;
                if (blockLength == Long.MAX_VALUE) {
                    return;
                }
            }

            start += blockLength;
        }

    }

    public static void cache(DataSpec dataSpec, Cache cache, DataSource upstream, @Nullable CacheUtil.CachingCounters counters, @Nullable AtomicBoolean isCanceled) throws IOException, InterruptedException {
        cache(dataSpec, cache, new CacheDataSource(cache, upstream), new byte[131072], null, 0, counters, isCanceled, false);
    }

    public static void cache(DataSpec dataSpec, Cache cache, CacheDataSource dataSource, byte[] buffer, PriorityTaskManager priorityTaskManager, int priority, @Nullable CacheUtil.CachingCounters counters, @Nullable AtomicBoolean isCanceled, boolean enableEOFException) throws IOException, InterruptedException {
        Assertions.checkNotNull(dataSource);
        Assertions.checkNotNull(buffer);
        if (counters != null) {
            getCached(dataSpec, cache, counters);
        } else {
            counters = new CachingCounters();
        }

        String key = getKey(dataSpec);
        long start = dataSpec.absoluteStreamPosition;

        long blockLength;
        for(long left = dataSpec.length != -1L ? dataSpec.length : cache.getContentLength(key); left != 0L; left -= left == -1L ? 0L : blockLength) {
            throwExceptionIfInterruptedOrCancelled(isCanceled);
            blockLength = cache.getCachedLength(key, start, left != -1L ? left : Long.MAX_VALUE);
            if (blockLength <= 0L) {
                blockLength = -blockLength;
                long read = readAndDiscard(dataSpec, start, blockLength, dataSource, buffer, priorityTaskManager, priority, counters, isCanceled);
                if (read < blockLength) {
                    if (enableEOFException && left != -1L) {
                        throw new EOFException();
                    }
                    break;
                }
            }

            start += blockLength;
        }

    }

    private static long readAndDiscard(DataSpec dataSpec, long absoluteStreamPosition, long length, DataSource dataSource, byte[] buffer, PriorityTaskManager priorityTaskManager, int priority, CachingCounters counters, AtomicBoolean isCanceled) throws IOException, InterruptedException {
        while(true) {
            if (priorityTaskManager != null) {
                priorityTaskManager.proceed(priority);
            }

            try {
                throwExceptionIfInterruptedOrCancelled(isCanceled);
                dataSpec = new DataSpec(dataSpec.uri, dataSpec.httpMethod, dataSpec.httpBody, absoluteStreamPosition, dataSpec.position + absoluteStreamPosition - dataSpec.absoluteStreamPosition, -1L, dataSpec.key, dataSpec.flags | 2);
                long resolvedLength = dataSource.open(dataSpec);
                if (counters.contentLength == -1L && resolvedLength != -1L) {
                    counters.contentLength = dataSpec.absoluteStreamPosition + resolvedLength;
                }

                long totalRead = 0L;

                while(true) {
                    if (totalRead != length) {
                        throwExceptionIfInterruptedOrCancelled(isCanceled);
                        int read = dataSource.read(buffer, 0, length != -1L ? (int)Math.min(buffer.length, length - totalRead) : buffer.length);
                        if (read != -1) {
                            totalRead += read;
                            counters.newlyCachedBytes += read;
                            continue;
                        }

                        if (counters.contentLength == -1L) {
                            counters.contentLength = dataSpec.absoluteStreamPosition + totalRead;
                        }
                    }

                    long var22 = totalRead;
                    return var22;
                }
            } catch (PriorityTooLowException var20) {
            } finally {
                Util.closeQuietly(dataSource);
            }
        }
    }

    public static void remove(Cache cache, String key) {
        NavigableSet<CacheSpan> cachedSpans = cache.getCachedSpans(key);
        Iterator var3 = cachedSpans.iterator();

        while(var3.hasNext()) {
            CacheSpan cachedSpan = (CacheSpan)var3.next();

            try {
                cache.removeSpan(cachedSpan);
            } catch (CacheException var6) {
            }
        }

    }

    private static void throwExceptionIfInterruptedOrCancelled(AtomicBoolean isCanceled) throws InterruptedException {
        if (Thread.interrupted() || isCanceled != null && isCanceled.get()) {
            throw new InterruptedException();
        }
    }

    private CacheUtil() {
    }

    public static class CachingCounters {
        public volatile long alreadyCachedBytes;
        public volatile long newlyCachedBytes;
        public volatile long contentLength = -1L;

        public CachingCounters() {
        }

        public long totalCachedBytes() {
            return this.alreadyCachedBytes + this.newlyCachedBytes;
        }
    }
}
