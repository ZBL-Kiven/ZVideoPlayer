package com.zj.playerLib.upstream.cache;

import androidx.annotation.NonNull;

import com.zj.playerLib.extractor.ChunkIndex;
import com.zj.playerLib.upstream.cache.Cache.Listener;
import com.zj.playerLib.util.Log;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.TreeSet;

public final class CachedRegionTracker implements Listener {
    private static final String TAG = "CachedRegionTracker";
    public static final int NOT_CACHED = -1;
    public static final int CACHED_TO_END = -2;
    private final Cache cache;
    private final String cacheKey;
    private final ChunkIndex chunkIndex;
    private final TreeSet<Region> regions;
    private final Region lookupRegion;

    public CachedRegionTracker(Cache cache, String cacheKey, ChunkIndex chunkIndex) {
        this.cache = cache;
        this.cacheKey = cacheKey;
        this.chunkIndex = chunkIndex;
        this.regions = new TreeSet();
        this.lookupRegion = new Region(0L, 0L);
        synchronized(this) {
            NavigableSet<CacheSpan> cacheSpans = cache.addListener(cacheKey, this);
            Iterator spanIterator = cacheSpans.descendingIterator();

            while(spanIterator.hasNext()) {
                CacheSpan span = (CacheSpan)spanIterator.next();
                this.mergeSpan(span);
            }

        }
    }

    public void release() {
        this.cache.removeListener(this.cacheKey, this);
    }

    public synchronized int getRegionEndTimeMs(long byteOffset) {
        this.lookupRegion.startOffset = byteOffset;
        Region floorRegion = this.regions.floor(this.lookupRegion);
        if (floorRegion != null && byteOffset <= floorRegion.endOffset && floorRegion.endOffsetIndex != -1) {
            int index = floorRegion.endOffsetIndex;
            if (index == this.chunkIndex.length - 1 && floorRegion.endOffset == this.chunkIndex.offsets[index] + (long)this.chunkIndex.sizes[index]) {
                return -2;
            } else {
                long segmentFractionUs = this.chunkIndex.durationsUs[index] * (floorRegion.endOffset - this.chunkIndex.offsets[index]) / (long)this.chunkIndex.sizes[index];
                return (int)((this.chunkIndex.timesUs[index] + segmentFractionUs) / 1000L);
            }
        } else {
            return -1;
        }
    }

    public synchronized void onSpanAdded(Cache cache, CacheSpan span) {
        this.mergeSpan(span);
    }

    public synchronized void onSpanRemoved(Cache cache, CacheSpan span) {
        Region removedRegion = new Region(span.position, span.position + span.length);
        Region floorRegion = this.regions.floor(removedRegion);
        if (floorRegion == null) {
            Log.e("CachedRegionTracker", "Removed a span we were not aware of");
        } else {
            this.regions.remove(floorRegion);
            Region newCeilingRegion;
            if (floorRegion.startOffset < removedRegion.startOffset) {
                newCeilingRegion = new Region(floorRegion.startOffset, removedRegion.startOffset);
                int index = Arrays.binarySearch(this.chunkIndex.offsets, newCeilingRegion.endOffset);
                newCeilingRegion.endOffsetIndex = index < 0 ? -index - 2 : index;
                this.regions.add(newCeilingRegion);
            }

            if (floorRegion.endOffset > removedRegion.endOffset) {
                newCeilingRegion = new Region(removedRegion.endOffset + 1L, floorRegion.endOffset);
                newCeilingRegion.endOffsetIndex = floorRegion.endOffsetIndex;
                this.regions.add(newCeilingRegion);
            }

        }
    }

    public void onSpanTouched(Cache cache, CacheSpan oldSpan, CacheSpan newSpan) {
    }

    private void mergeSpan(CacheSpan span) {
        Region newRegion = new Region(span.position, span.position + span.length);
        Region floorRegion = this.regions.floor(newRegion);
        Region ceilingRegion = this.regions.ceiling(newRegion);
        boolean floorConnects = this.regionsConnect(floorRegion, newRegion);
        boolean ceilingConnects = this.regionsConnect(newRegion, ceilingRegion);
        if (ceilingConnects) {
            if (floorConnects) {
                floorRegion.endOffset = ceilingRegion.endOffset;
                floorRegion.endOffsetIndex = ceilingRegion.endOffsetIndex;
            } else {
                newRegion.endOffset = ceilingRegion.endOffset;
                newRegion.endOffsetIndex = ceilingRegion.endOffsetIndex;
                this.regions.add(newRegion);
            }

            this.regions.remove(ceilingRegion);
        } else {
            int index;
            if (floorConnects) {
                floorRegion.endOffset = newRegion.endOffset;

                for(index = floorRegion.endOffsetIndex; index < this.chunkIndex.length - 1 && this.chunkIndex.offsets[index + 1] <= floorRegion.endOffset; ++index) {
                }

                floorRegion.endOffsetIndex = index;
            } else {
                index = Arrays.binarySearch(this.chunkIndex.offsets, newRegion.endOffset);
                newRegion.endOffsetIndex = index < 0 ? -index - 2 : index;
                this.regions.add(newRegion);
            }
        }

    }

    private boolean regionsConnect(Region lower, Region upper) {
        return lower != null && upper != null && lower.endOffset == upper.startOffset;
    }

    private static class Region implements Comparable<Region> {
        public long startOffset;
        public long endOffset;
        public int endOffsetIndex;

        public Region(long position, long endOffset) {
            this.startOffset = position;
            this.endOffset = endOffset;
        }

        public int compareTo(@NonNull CachedRegionTracker.Region another) {
            return this.startOffset < another.startOffset ? -1 : (this.startOffset == another.startOffset ? 0 : 1);
        }
    }
}
