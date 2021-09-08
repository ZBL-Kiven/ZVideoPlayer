//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream.cache;

import androidx.annotation.Nullable;

import com.zj.playerLib.upstream.cache.Cache.CacheException;
import com.zj.playerLib.util.Assertions;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.TreeSet;

final class CachedContent {
    private static final int VERSION_METADATA_INTRODUCED = 2;
    private static final int VERSION_MAX = 2147483647;
    public final int id;
    public final String key;
    private final TreeSet<SimpleCacheSpan> cachedSpans;
    private DefaultContentMetadata metadata;
    private boolean locked;

    public static CachedContent readFromStream(int version, DataInputStream input) throws IOException {
        int id = input.readInt();
        String key = input.readUTF();
        CachedContent cachedContent = new CachedContent(id, key);
        if (version < 2) {
            long length = input.readLong();
            ContentMetadataMutations mutations = new ContentMetadataMutations();
            ContentMetadataInternal.setContentLength(mutations, length);
            cachedContent.applyMetadataMutations(mutations);
        } else {
            cachedContent.metadata = DefaultContentMetadata.readFromStream(input);
        }

        return cachedContent;
    }

    public CachedContent(int id, String key) {
        this.id = id;
        this.key = key;
        this.metadata = DefaultContentMetadata.EMPTY;
        this.cachedSpans = new TreeSet();
    }

    public void writeToStream(DataOutputStream output) throws IOException {
        output.writeInt(this.id);
        output.writeUTF(this.key);
        this.metadata.writeToStream(output);
    }

    public ContentMetadata getMetadata() {
        return this.metadata;
    }

    public boolean applyMetadataMutations(ContentMetadataMutations mutations) {
        DefaultContentMetadata oldMetadata = this.metadata;
        this.metadata = this.metadata.copyWithMutationsApplied(mutations);
        return !this.metadata.equals(oldMetadata);
    }

    public boolean isLocked() {
        return this.locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public void addSpan(SimpleCacheSpan span) {
        this.cachedSpans.add(span);
    }

    public TreeSet<SimpleCacheSpan> getSpans() {
        return this.cachedSpans;
    }

    public SimpleCacheSpan getSpan(long position) {
        SimpleCacheSpan lookupSpan = SimpleCacheSpan.createLookup(this.key, position);
        SimpleCacheSpan floorSpan = (SimpleCacheSpan)this.cachedSpans.floor(lookupSpan);
        if (floorSpan != null && floorSpan.position + floorSpan.length > position) {
            return floorSpan;
        } else {
            SimpleCacheSpan ceilSpan = (SimpleCacheSpan)this.cachedSpans.ceiling(lookupSpan);
            return ceilSpan == null ? SimpleCacheSpan.createOpenHole(this.key, position) : SimpleCacheSpan.createClosedHole(this.key, position, ceilSpan.position - position);
        }
    }

    public long getCachedBytesLength(long position, long length) {
        SimpleCacheSpan span = this.getSpan(position);
        if (span.isHoleSpan()) {
            return -Math.min(span.isOpenEnded() ? 9223372036854775807L : span.length, length);
        } else {
            long queryEndPosition = position + length;
            long currentEndPosition = span.position + span.length;
            if (currentEndPosition < queryEndPosition) {
                Iterator var10 = this.cachedSpans.tailSet(span, false).iterator();

                while(var10.hasNext()) {
                    SimpleCacheSpan next = (SimpleCacheSpan)var10.next();
                    if (next.position > currentEndPosition) {
                        break;
                    }

                    currentEndPosition = Math.max(currentEndPosition, next.position + next.length);
                    if (currentEndPosition >= queryEndPosition) {
                        break;
                    }
                }
            }

            return Math.min(currentEndPosition - position, length);
        }
    }

    public SimpleCacheSpan touch(SimpleCacheSpan cacheSpan) throws CacheException {
        SimpleCacheSpan newCacheSpan = cacheSpan.copyWithUpdatedLastAccessTime(this.id);
        if (!cacheSpan.file.renameTo(newCacheSpan.file)) {
            throw new CacheException("Renaming of " + cacheSpan.file + " to " + newCacheSpan.file + " failed.");
        } else {
            Assertions.checkState(this.cachedSpans.remove(cacheSpan));
            this.cachedSpans.add(newCacheSpan);
            return newCacheSpan;
        }
    }

    public boolean isEmpty() {
        return this.cachedSpans.isEmpty();
    }

    public boolean removeSpan(CacheSpan span) {
        if (this.cachedSpans.remove(span)) {
            span.file.delete();
            return true;
        } else {
            return false;
        }
    }

    public int headerHashCode(int version) {
        int result = this.id;
        result = 31 * result + this.key.hashCode();
        if (version < 2) {
            long length = ContentMetadataInternal.getContentLength(this.metadata);
            result = 31 * result + (int)(length ^ length >>> 32);
        } else {
            result = 31 * result + this.metadata.hashCode();
        }

        return result;
    }

    public int hashCode() {
        int result = this.headerHashCode(2147483647);
        result = 31 * result + this.cachedSpans.hashCode();
        return result;
    }

    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            CachedContent that = (CachedContent)o;
            return this.id == that.id && this.key.equals(that.key) && this.cachedSpans.equals(that.cachedSpans) && this.metadata.equals(that.metadata);
        } else {
            return false;
        }
    }
}
