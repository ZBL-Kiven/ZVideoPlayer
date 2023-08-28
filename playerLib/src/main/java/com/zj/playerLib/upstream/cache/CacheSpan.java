package com.zj.playerLib.upstream.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

public class CacheSpan implements Comparable<CacheSpan> {
    public final String key;
    public final long position;
    public final long length;
    public final boolean isCached;
    @Nullable
    public final File file;
    public final long lastAccessTimestamp;

    public CacheSpan(String key, long position, long length) {
        this(key, position, length, -Long.MAX_VALUE, null);
    }

    public CacheSpan(String key, long position, long length, long lastAccessTimestamp, @Nullable File file) {
        this.key = key;
        this.position = position;
        this.length = length;
        this.isCached = file != null;
        this.file = file;
        this.lastAccessTimestamp = lastAccessTimestamp;
    }

    public boolean isOpenEnded() {
        return this.length == -1L;
    }

    public boolean isHoleSpan() {
        return !this.isCached;
    }

    public int compareTo(@NonNull CacheSpan another) {
        if (!this.key.equals(another.key)) {
            return this.key.compareTo(another.key);
        } else {
            long startOffsetDiff = this.position - another.position;
            return startOffsetDiff == 0L ? 0 : (startOffsetDiff < 0L ? -1 : 1);
        }
    }
}
