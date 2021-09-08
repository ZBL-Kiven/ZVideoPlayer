//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream.cache;

import com.zj.playerLib.upstream.cache.Cache.CacheException;

import java.util.Comparator;
import java.util.TreeSet;

public final class LeastRecentlyUsedCacheEvictor implements CacheEvictor, Comparator<CacheSpan> {
    private final long maxBytes;
    private final TreeSet<CacheSpan> leastRecentlyUsed;
    private long currentSize;

    public LeastRecentlyUsedCacheEvictor(long maxBytes) {
        this.maxBytes = maxBytes;
        this.leastRecentlyUsed = new TreeSet(this);
    }

    public void onCacheInitialized() {
    }

    public void onStartFile(Cache cache, String key, long position, long maxLength) {
        this.evictCache(cache, maxLength);
    }

    public void onSpanAdded(Cache cache, CacheSpan span) {
        this.leastRecentlyUsed.add(span);
        this.currentSize += span.length;
        this.evictCache(cache, 0L);
    }

    public void onSpanRemoved(Cache cache, CacheSpan span) {
        this.leastRecentlyUsed.remove(span);
        this.currentSize -= span.length;
    }

    public void onSpanTouched(Cache cache, CacheSpan oldSpan, CacheSpan newSpan) {
        this.onSpanRemoved(cache, oldSpan);
        this.onSpanAdded(cache, newSpan);
    }

    public int compare(CacheSpan lhs, CacheSpan rhs) {
        long lastAccessTimestampDelta = lhs.lastAccessTimestamp - rhs.lastAccessTimestamp;
        if (lastAccessTimestampDelta == 0L) {
            return lhs.compareTo(rhs);
        } else {
            return lhs.lastAccessTimestamp < rhs.lastAccessTimestamp ? -1 : 1;
        }
    }

    private void evictCache(Cache cache, long requiredSpace) {
        while(this.currentSize + requiredSpace > this.maxBytes && !this.leastRecentlyUsed.isEmpty()) {
            try {
                cache.removeSpan((CacheSpan)this.leastRecentlyUsed.first());
            } catch (CacheException var5) {
            }
        }

    }
}
