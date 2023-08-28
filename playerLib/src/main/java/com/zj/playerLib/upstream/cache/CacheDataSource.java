package com.zj.playerLib.upstream.cache;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.zj.playerLib.upstream.DataSink;
import com.zj.playerLib.upstream.DataSource;
import com.zj.playerLib.upstream.DataSourceException;
import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.upstream.FileDataSource;
import com.zj.playerLib.upstream.TeeDataSource;
import com.zj.playerLib.upstream.TransferListener;
import com.zj.playerLib.upstream.cache.Cache.CacheException;
import com.zj.playerLib.util.Assertions;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CacheDataSource implements DataSource {
    public static final long DEFAULT_MAX_CACHE_FILE_SIZE = 2097152L;
    public static final int FLAG_BLOCK_ON_CACHE = 1;
    public static final int FLAG_IGNORE_CACHE_ON_ERROR = 2;
    public static final int FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS = 4;
    private static final int CACHE_NOT_IGNORED = -1;
    public static final int CACHE_IGNORED_REASON_ERROR = 0;
    public static final int CACHE_IGNORED_REASON_UNSET_LENGTH = 1;
    private static final long MIN_READ_BEFORE_CHECKING_CACHE = 102400L;
    private final Cache cache;
    private final DataSource cacheReadDataSource;
    @Nullable
    private final DataSource cacheWriteDataSource;
    private final DataSource upstreamDataSource;
    private final CacheKeyFactory cacheKeyFactory;
    @Nullable
    private final CacheDataSource.EventListener eventListener;
    private final boolean blockOnCache;
    private final boolean ignoreCacheOnError;
    private final boolean ignoreCacheForUnsetLengthRequests;
    @Nullable
    private DataSource currentDataSource;
    private boolean currentDataSpecLengthUnset;
    @Nullable
    private Uri uri;
    @Nullable
    private Uri actualUri;
    private int httpMethod;
    private int flags;
    @Nullable
    private String key;
    private long readPosition;
    private long bytesRemaining;
    @Nullable
    private CacheSpan currentHoleSpan;
    private boolean seenCacheError;
    private boolean currentRequestIgnoresCache;
    private long totalCachedBytesRead;
    private long checkCachePosition;

    public CacheDataSource(Cache cache, DataSource upstream) {
        this(cache, upstream, 0, 2097152L);
    }

    public CacheDataSource(Cache cache, DataSource upstream, int flags) {
        this(cache, upstream, flags, 2097152L);
    }

    public CacheDataSource(Cache cache, DataSource upstream, int flags, long maxCacheFileSize) {
        this(cache, upstream, new FileDataSource(), new CacheDataSink(cache, maxCacheFileSize), flags, null);
    }

    public CacheDataSource(Cache cache, DataSource upstream, DataSource cacheReadDataSource, DataSink cacheWriteDataSink, int flags, @Nullable CacheDataSource.EventListener eventListener) {
        this(cache, upstream, cacheReadDataSource, cacheWriteDataSink, flags, eventListener, null);
    }

    public CacheDataSource(Cache cache, DataSource upstream, DataSource cacheReadDataSource, DataSink cacheWriteDataSink, int flags, @Nullable CacheDataSource.EventListener eventListener, @Nullable CacheKeyFactory cacheKeyFactory) {
        this.cache = cache;
        this.cacheReadDataSource = cacheReadDataSource;
        this.cacheKeyFactory = cacheKeyFactory != null ? cacheKeyFactory : CacheUtil.DEFAULT_CACHE_KEY_FACTORY;
        this.blockOnCache = (flags & 1) != 0;
        this.ignoreCacheOnError = (flags & 2) != 0;
        this.ignoreCacheForUnsetLengthRequests = (flags & 4) != 0;
        this.upstreamDataSource = upstream;
        if (cacheWriteDataSink != null) {
            this.cacheWriteDataSource = new TeeDataSource(upstream, cacheWriteDataSink);
        } else {
            this.cacheWriteDataSource = null;
        }

        this.eventListener = eventListener;
    }

    public void addTransferListener(TransferListener transferListener) {
        this.cacheReadDataSource.addTransferListener(transferListener);
        this.upstreamDataSource.addTransferListener(transferListener);
    }

    public long open(DataSpec dataSpec) throws IOException {
        try {
            this.key = this.cacheKeyFactory.buildCacheKey(dataSpec);
            this.uri = dataSpec.uri;
            this.actualUri = getRedirectedUriOrDefault(this.cache, this.key, this.uri);
            this.httpMethod = dataSpec.httpMethod;
            this.flags = dataSpec.flags;
            this.readPosition = dataSpec.position;
            int reason = this.shouldIgnoreCacheForRequest(dataSpec);
            this.currentRequestIgnoresCache = reason != -1;
            if (this.currentRequestIgnoresCache) {
                this.notifyCacheIgnored(reason);
            }

            if (dataSpec.length == -1L && !this.currentRequestIgnoresCache) {
                this.bytesRemaining = this.cache.getContentLength(this.key);
                if (this.bytesRemaining != -1L) {
                    this.bytesRemaining -= dataSpec.position;
                    if (this.bytesRemaining <= 0L) {
                        throw new DataSourceException(0);
                    }
                }
            } else {
                this.bytesRemaining = dataSpec.length;
            }

            this.openNextSource(false);
            return this.bytesRemaining;
        } catch (IOException var3) {
            this.handleBeforeThrow(var3);
            throw var3;
        }
    }

    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) {
            return 0;
        } else if (this.bytesRemaining == 0L) {
            return -1;
        } else {
            try {
                if (this.readPosition >= this.checkCachePosition) {
                    this.openNextSource(true);
                }

                int bytesRead = this.currentDataSource.read(buffer, offset, readLength);
                if (bytesRead != -1) {
                    if (this.isReadingFromCache()) {
                        this.totalCachedBytesRead += bytesRead;
                    }

                    this.readPosition += bytesRead;
                    if (this.bytesRemaining != -1L) {
                        this.bytesRemaining -= bytesRead;
                    }
                } else if (this.currentDataSpecLengthUnset) {
                    this.setNoBytesRemainingAndMaybeStoreLength();
                } else if (this.bytesRemaining > 0L || this.bytesRemaining == -1L) {
                    this.closeCurrentSource();
                    this.openNextSource(false);
                    return this.read(buffer, offset, readLength);
                }

                return bytesRead;
            } catch (IOException var5) {
                if (this.currentDataSpecLengthUnset && isCausedByPositionOutOfRange(var5)) {
                    this.setNoBytesRemainingAndMaybeStoreLength();
                    return -1;
                } else {
                    this.handleBeforeThrow(var5);
                    throw var5;
                }
            }
        }
    }

    @Nullable
    public Uri getUri() {
        return this.actualUri;
    }

    public Map<String, List<String>> getResponseHeaders() {
        return this.isReadingFromUpstream() ? this.upstreamDataSource.getResponseHeaders() : Collections.emptyMap();
    }

    public void close() throws IOException {
        this.uri = null;
        this.actualUri = null;
        this.httpMethod = 1;
        this.notifyBytesRead();

        try {
            this.closeCurrentSource();
        } catch (IOException var2) {
            this.handleBeforeThrow(var2);
            throw var2;
        }
    }

    private void openNextSource(boolean checkCache) throws IOException {
        CacheSpan nextSpan;
        if (this.currentRequestIgnoresCache) {
            nextSpan = null;
        } else if (this.blockOnCache) {
            try {
                nextSpan = this.cache.startReadWrite(this.key, this.readPosition);
            } catch (InterruptedException var10) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
        } else {
            nextSpan = this.cache.startReadWriteNonBlocking(this.key, this.readPosition);
        }

        DataSpec nextDataSpec;
        DataSource nextDataSource;
        long resolvedLength;
        if (nextSpan == null) {
            nextDataSource = this.upstreamDataSource;
            nextDataSpec = new DataSpec(this.uri, this.httpMethod, null, this.readPosition, this.readPosition, this.bytesRemaining, this.key, this.flags);
        } else if (nextSpan.isCached) {
            Uri fileUri = Uri.fromFile(nextSpan.file);
            long filePosition = this.readPosition - nextSpan.position;
            long length = nextSpan.length - filePosition;
            if (this.bytesRemaining != -1L) {
                length = Math.min(length, this.bytesRemaining);
            }

            nextDataSpec = new DataSpec(fileUri, this.readPosition, filePosition, length, this.key, this.flags);
            nextDataSource = this.cacheReadDataSource;
        } else {
            if (nextSpan.isOpenEnded()) {
                resolvedLength = this.bytesRemaining;
            } else {
                resolvedLength = nextSpan.length;
                if (this.bytesRemaining != -1L) {
                    resolvedLength = Math.min(resolvedLength, this.bytesRemaining);
                }
            }

            nextDataSpec = new DataSpec(this.uri, this.httpMethod, null, this.readPosition, this.readPosition, resolvedLength, this.key, this.flags);
            if (this.cacheWriteDataSource != null) {
                nextDataSource = this.cacheWriteDataSource;
            } else {
                nextDataSource = this.upstreamDataSource;
                this.cache.releaseHoleSpan(nextSpan);
                nextSpan = null;
            }
        }

        this.checkCachePosition = !this.currentRequestIgnoresCache && nextDataSource == this.upstreamDataSource ? this.readPosition + 102400L : Long.MAX_VALUE;
        if (checkCache) {
            Assertions.checkState(this.isBypassingCache());
            if (nextDataSource == this.upstreamDataSource) {
                return;
            }

            try {
                this.closeCurrentSource();
            } catch (Throwable var11) {
                if (nextSpan.isHoleSpan()) {
                    this.cache.releaseHoleSpan(nextSpan);
                }

                throw var11;
            }
        }

        if (nextSpan != null && nextSpan.isHoleSpan()) {
            this.currentHoleSpan = nextSpan;
        }

        this.currentDataSource = nextDataSource;
        this.currentDataSpecLengthUnset = nextDataSpec.length == -1L;
        resolvedLength = nextDataSource.open(nextDataSpec);
        ContentMetadataMutations mutations = new ContentMetadataMutations();
        if (this.currentDataSpecLengthUnset && resolvedLength != -1L) {
            this.bytesRemaining = resolvedLength;
            ContentMetadataInternal.setContentLength(mutations, this.readPosition + this.bytesRemaining);
        }

        if (this.isReadingFromUpstream()) {
            this.actualUri = this.currentDataSource.getUri();
            boolean isRedirected = !this.uri.equals(this.actualUri);
            if (isRedirected) {
                ContentMetadataInternal.setRedirectedUri(mutations, this.actualUri);
            } else {
                ContentMetadataInternal.removeRedirectedUri(mutations);
            }
        }

        if (this.isWritingToCache()) {
            this.cache.applyContentMetadataMutations(this.key, mutations);
        }

    }

    private void setNoBytesRemainingAndMaybeStoreLength() throws IOException {
        this.bytesRemaining = 0L;
        if (this.isWritingToCache()) {
            this.cache.setContentLength(this.key, this.readPosition);
        }

    }

    private static Uri getRedirectedUriOrDefault(Cache cache, String key, Uri defaultUri) {
        ContentMetadata contentMetadata = cache.getContentMetadata(key);
        Uri redirectedUri = ContentMetadataInternal.getRedirectedUri(contentMetadata);
        return redirectedUri == null ? defaultUri : redirectedUri;
    }

    private static boolean isCausedByPositionOutOfRange(IOException e) {
        for(Object cause = e; cause != null; cause = ((Throwable)cause).getCause()) {
            if (cause instanceof DataSourceException) {
                int reason = ((DataSourceException)cause).reason;
                if (reason == 0) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isReadingFromUpstream() {
        return !this.isReadingFromCache();
    }

    private boolean isBypassingCache() {
        return this.currentDataSource == this.upstreamDataSource;
    }

    private boolean isReadingFromCache() {
        return this.currentDataSource == this.cacheReadDataSource;
    }

    private boolean isWritingToCache() {
        return this.currentDataSource == this.cacheWriteDataSource;
    }

    private void closeCurrentSource() throws IOException {
        if (this.currentDataSource != null) {
            try {
                this.currentDataSource.close();
            } finally {
                this.currentDataSource = null;
                this.currentDataSpecLengthUnset = false;
                if (this.currentHoleSpan != null) {
                    this.cache.releaseHoleSpan(this.currentHoleSpan);
                    this.currentHoleSpan = null;
                }

            }

        }
    }

    private void handleBeforeThrow(IOException exception) {
        if (this.isReadingFromCache() || exception instanceof CacheException) {
            this.seenCacheError = true;
        }

    }

    private int shouldIgnoreCacheForRequest(DataSpec dataSpec) {
        if (this.ignoreCacheOnError && this.seenCacheError) {
            return 0;
        } else {
            return this.ignoreCacheForUnsetLengthRequests && dataSpec.length == -1L ? 1 : -1;
        }
    }

    private void notifyCacheIgnored(int reason) {
        if (this.eventListener != null) {
            this.eventListener.onCacheIgnored(reason);
        }

    }

    private void notifyBytesRead() {
        if (this.eventListener != null && this.totalCachedBytesRead > 0L) {
            this.eventListener.onCachedBytesRead(this.cache.getCacheSpace(), this.totalCachedBytesRead);
            this.totalCachedBytesRead = 0L;
        }

    }

    public interface EventListener {
        void onCachedBytesRead(long var1, long var3);

        void onCacheIgnored(int var1);
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface CacheIgnoredReason {
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface Flags {
    }
}
