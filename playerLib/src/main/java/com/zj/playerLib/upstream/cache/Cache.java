package com.zj.playerLib.upstream.cache;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.NavigableSet;
import java.util.Set;

public interface Cache {
    void release();

    NavigableSet<CacheSpan> addListener(String var1, Listener var2);

    void removeListener(String var1, Listener var2);

    NavigableSet<CacheSpan> getCachedSpans(String var1);

    Set<String> getKeys();

    long getCacheSpace();

    CacheSpan startReadWrite(String var1, long var2) throws InterruptedException, CacheException;

    @Nullable
    CacheSpan startReadWriteNonBlocking(String var1, long var2) throws CacheException;

    File startFile(String var1, long var2, long var4) throws CacheException;

    void commitFile(File var1) throws CacheException;

    void releaseHoleSpan(CacheSpan var1);

    void removeSpan(CacheSpan var1) throws CacheException;

    boolean isCached(String var1, long var2, long var4);

    long getCachedLength(String var1, long var2, long var4);

    void setContentLength(String var1, long var2) throws CacheException;

    long getContentLength(String var1);

    void applyContentMetadataMutations(String var1, ContentMetadataMutations var2) throws CacheException;

    ContentMetadata getContentMetadata(String var1);

    class CacheException extends IOException {
        public CacheException(String message) {
            super(message);
        }

        public CacheException(Throwable cause) {
            super(cause);
        }
    }

    interface Listener {
        void onSpanAdded(Cache var1, CacheSpan var2);

        void onSpanRemoved(Cache var1, CacheSpan var2);

        void onSpanTouched(Cache var1, CacheSpan var2, CacheSpan var3);
    }
}
