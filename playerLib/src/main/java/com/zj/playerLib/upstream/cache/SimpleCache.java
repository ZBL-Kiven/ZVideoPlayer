//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream.cache;

import android.os.ConditionVariable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

public final class SimpleCache implements Cache {
    private static final String TAG = "SimpleCache";
    private static final HashSet<File> lockedCacheDirs = new HashSet();
    private static boolean cacheFolderLockingDisabled;
    private final File cacheDir;
    private final CacheEvictor evictor;
    private final CachedContentIndex index;
    private final HashMap<String, ArrayList<Listener>> listeners;
    private long totalSpace;
    private boolean released;

    public static synchronized boolean isCacheFolderLocked(File cacheFolder) {
        return lockedCacheDirs.contains(cacheFolder.getAbsoluteFile());
    }

    /** @deprecated */
    @Deprecated
    public static synchronized void disableCacheFolderLocking() {
        cacheFolderLockingDisabled = true;
        lockedCacheDirs.clear();
    }

    public SimpleCache(File cacheDir, CacheEvictor evictor) {
        this(cacheDir, evictor, (byte[])null, false);
    }

    public SimpleCache(File cacheDir, CacheEvictor evictor, byte[] secretKey) {
        this(cacheDir, evictor, secretKey, secretKey != null);
    }

    public SimpleCache(File cacheDir, CacheEvictor evictor, byte[] secretKey, boolean encrypt) {
        this(cacheDir, evictor, new CachedContentIndex(cacheDir, secretKey, encrypt));
    }

    SimpleCache(File cacheDir, CacheEvictor evictor, CachedContentIndex index) {
        if (!lockFolder(cacheDir)) {
            throw new IllegalStateException("Another SimpleCache instance uses the folder: " + cacheDir);
        } else {
            this.cacheDir = cacheDir;
            this.evictor = evictor;
            this.index = index;
            this.listeners = new HashMap();
            final ConditionVariable conditionVariable = new ConditionVariable();
            (new Thread("SimpleCache.initialize()") {
                public void run() {
                    synchronized(SimpleCache.this) {
                        conditionVariable.open();
                        SimpleCache.this.initialize();
                        SimpleCache.this.evictor.onCacheInitialized();
                    }
                }
            }).start();
            conditionVariable.block();
        }
    }

    public synchronized void release() {
        if (!this.released) {
            this.listeners.clear();
            this.removeStaleSpans();

            try {
                this.index.store();
            } catch (CacheException var5) {
                Log.e("SimpleCache", "Storing index file failed", var5);
            } finally {
                unlockFolder(this.cacheDir);
                this.released = true;
            }

        }
    }

    public synchronized NavigableSet<CacheSpan> addListener(String key, Listener listener) {
        Assertions.checkState(!this.released);
        ArrayList<Listener> listenersForKey = (ArrayList)this.listeners.get(key);
        if (listenersForKey == null) {
            listenersForKey = new ArrayList();
            this.listeners.put(key, listenersForKey);
        }

        listenersForKey.add(listener);
        return this.getCachedSpans(key);
    }

    public synchronized void removeListener(String key, Listener listener) {
        if (!this.released) {
            ArrayList<Listener> listenersForKey = (ArrayList)this.listeners.get(key);
            if (listenersForKey != null) {
                listenersForKey.remove(listener);
                if (listenersForKey.isEmpty()) {
                    this.listeners.remove(key);
                }
            }

        }
    }

    @NonNull
    public synchronized NavigableSet<CacheSpan> getCachedSpans(String key) {
        Assertions.checkState(!this.released);
        CachedContent cachedContent = this.index.get(key);
        return cachedContent != null && !cachedContent.isEmpty() ? new TreeSet(cachedContent.getSpans()) : new TreeSet();
    }

    public synchronized Set<String> getKeys() {
        Assertions.checkState(!this.released);
        return new HashSet(this.index.getKeys());
    }

    public synchronized long getCacheSpace() {
        Assertions.checkState(!this.released);
        return this.totalSpace;
    }

    public synchronized SimpleCacheSpan startReadWrite(String key, long position) throws InterruptedException, CacheException {
        while(true) {
            SimpleCacheSpan span = this.startReadWriteNonBlocking(key, position);
            if (span != null) {
                return span;
            }

            this.wait();
        }
    }

    @Nullable
    public synchronized SimpleCacheSpan startReadWriteNonBlocking(String key, long position) throws CacheException {
        Assertions.checkState(!this.released);
        SimpleCacheSpan cacheSpan = this.getSpan(key, position);
        if (cacheSpan.isCached) {
            try {
                SimpleCacheSpan newCacheSpan = this.index.get(key).touch(cacheSpan);
                this.notifySpanTouched(cacheSpan, newCacheSpan);
                return newCacheSpan;
            } catch (CacheException var6) {
                return cacheSpan;
            }
        } else {
            CachedContent cachedContent = this.index.getOrAdd(key);
            if (!cachedContent.isLocked()) {
                cachedContent.setLocked(true);
                return cacheSpan;
            } else {
                return null;
            }
        }
    }

    public synchronized File startFile(String key, long position, long maxLength) throws CacheException {
        Assertions.checkState(!this.released);
        CachedContent cachedContent = this.index.get(key);
        Assertions.checkNotNull(cachedContent);
        Assertions.checkState(cachedContent.isLocked());
        if (!this.cacheDir.exists()) {
            this.cacheDir.mkdirs();
            this.removeStaleSpans();
        }

        this.evictor.onStartFile(this, key, position, maxLength);
        return SimpleCacheSpan.getCacheFile(this.cacheDir, cachedContent.id, position, System.currentTimeMillis());
    }

    public synchronized void commitFile(File file) throws CacheException {
        Assertions.checkState(!this.released);
        SimpleCacheSpan span = SimpleCacheSpan.createCacheEntry(file, this.index);
        Assertions.checkState(span != null);
        CachedContent cachedContent = this.index.get(span.key);
        Assertions.checkNotNull(cachedContent);
        Assertions.checkState(cachedContent.isLocked());
        if (file.exists()) {
            if (file.length() == 0L) {
                file.delete();
            } else {
                long length = ContentMetadataInternal.getContentLength(cachedContent.getMetadata());
                if (length != -1L) {
                    Assertions.checkState(span.position + span.length <= length);
                }

                this.addSpan(span);
                this.index.store();
                this.notifyAll();
            }
        }
    }

    public synchronized void releaseHoleSpan(CacheSpan holeSpan) {
        Assertions.checkState(!this.released);
        CachedContent cachedContent = this.index.get(holeSpan.key);
        Assertions.checkNotNull(cachedContent);
        Assertions.checkState(cachedContent.isLocked());
        cachedContent.setLocked(false);
        this.index.maybeRemove(cachedContent.key);
        this.notifyAll();
    }

    public synchronized void removeSpan(CacheSpan span) {
        Assertions.checkState(!this.released);
        this.removeSpanInternal(span);
    }

    public synchronized boolean isCached(String key, long position, long length) {
        Assertions.checkState(!this.released);
        CachedContent cachedContent = this.index.get(key);
        return cachedContent != null && cachedContent.getCachedBytesLength(position, length) >= length;
    }

    public synchronized long getCachedLength(String key, long position, long length) {
        Assertions.checkState(!this.released);
        CachedContent cachedContent = this.index.get(key);
        return cachedContent != null ? cachedContent.getCachedBytesLength(position, length) : -length;
    }

    public synchronized void setContentLength(String key, long length) throws CacheException {
        ContentMetadataMutations mutations = new ContentMetadataMutations();
        ContentMetadataInternal.setContentLength(mutations, length);
        this.applyContentMetadataMutations(key, mutations);
    }

    public synchronized long getContentLength(String key) {
        return ContentMetadataInternal.getContentLength(this.getContentMetadata(key));
    }

    public synchronized void applyContentMetadataMutations(String key, ContentMetadataMutations mutations) throws CacheException {
        Assertions.checkState(!this.released);
        this.index.applyContentMetadataMutations(key, mutations);
        this.index.store();
    }

    public synchronized ContentMetadata getContentMetadata(String key) {
        Assertions.checkState(!this.released);
        return this.index.getContentMetadata(key);
    }

    private SimpleCacheSpan getSpan(String key, long position) throws CacheException {
        CachedContent cachedContent = this.index.get(key);
        if (cachedContent == null) {
            return SimpleCacheSpan.createOpenHole(key, position);
        } else {
            while(true) {
                SimpleCacheSpan span = cachedContent.getSpan(position);
                if (!span.isCached || span.file.exists()) {
                    return span;
                }

                this.removeStaleSpans();
            }
        }
    }

    private void initialize() {
        if (!this.cacheDir.exists()) {
            this.cacheDir.mkdirs();
        } else {
            this.index.load();
            File[] files = this.cacheDir.listFiles();
            if (files != null) {
                File[] var2 = files;
                int var3 = files.length;

                for(int var4 = 0; var4 < var3; ++var4) {
                    File file = var2[var4];
                    if (!file.getName().equals("cached_content_index.exi")) {
                        SimpleCacheSpan span = file.length() > 0L ? SimpleCacheSpan.createCacheEntry(file, this.index) : null;
                        if (span != null) {
                            this.addSpan(span);
                        } else {
                            file.delete();
                        }
                    }
                }

                this.index.removeEmpty();

                try {
                    this.index.store();
                } catch (CacheException var7) {
                    Log.e("SimpleCache", "Storing index file failed", var7);
                }

            }
        }
    }

    private void addSpan(SimpleCacheSpan span) {
        this.index.getOrAdd(span.key).addSpan(span);
        this.totalSpace += span.length;
        this.notifySpanAdded(span);
    }

    private void removeSpanInternal(CacheSpan span) {
        CachedContent cachedContent = this.index.get(span.key);
        if (cachedContent != null && cachedContent.removeSpan(span)) {
            this.totalSpace -= span.length;
            this.index.maybeRemove(cachedContent.key);
            this.notifySpanRemoved(span);
        }
    }

    private void removeStaleSpans() {
        ArrayList<CacheSpan> spansToBeRemoved = new ArrayList();
        Iterator var2 = this.index.getAll().iterator();

        while(var2.hasNext()) {
            CachedContent cachedContent = (CachedContent)var2.next();
            Iterator var4 = cachedContent.getSpans().iterator();

            while(var4.hasNext()) {
                CacheSpan span = (CacheSpan)var4.next();
                if (!span.file.exists()) {
                    spansToBeRemoved.add(span);
                }
            }
        }

        for(int i = 0; i < spansToBeRemoved.size(); ++i) {
            this.removeSpanInternal((CacheSpan)spansToBeRemoved.get(i));
        }

    }

    private void notifySpanRemoved(CacheSpan span) {
        ArrayList<Listener> keyListeners = (ArrayList)this.listeners.get(span.key);
        if (keyListeners != null) {
            for(int i = keyListeners.size() - 1; i >= 0; --i) {
                ((Listener)keyListeners.get(i)).onSpanRemoved(this, span);
            }
        }

        this.evictor.onSpanRemoved(this, span);
    }

    private void notifySpanAdded(SimpleCacheSpan span) {
        ArrayList<Listener> keyListeners = (ArrayList)this.listeners.get(span.key);
        if (keyListeners != null) {
            for(int i = keyListeners.size() - 1; i >= 0; --i) {
                ((Listener)keyListeners.get(i)).onSpanAdded(this, span);
            }
        }

        this.evictor.onSpanAdded(this, span);
    }

    private void notifySpanTouched(SimpleCacheSpan oldSpan, CacheSpan newSpan) {
        ArrayList<Listener> keyListeners = (ArrayList)this.listeners.get(oldSpan.key);
        if (keyListeners != null) {
            for(int i = keyListeners.size() - 1; i >= 0; --i) {
                ((Listener)keyListeners.get(i)).onSpanTouched(this, oldSpan, newSpan);
            }
        }

        this.evictor.onSpanTouched(this, oldSpan, newSpan);
    }

    private static synchronized boolean lockFolder(File cacheDir) {
        return cacheFolderLockingDisabled ? true : lockedCacheDirs.add(cacheDir.getAbsoluteFile());
    }

    private static synchronized void unlockFolder(File cacheDir) {
        if (!cacheFolderLockingDisabled) {
            lockedCacheDirs.remove(cacheDir.getAbsoluteFile());
        }

    }
}
