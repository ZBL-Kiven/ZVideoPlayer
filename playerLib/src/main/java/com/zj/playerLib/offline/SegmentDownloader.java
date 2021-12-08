package com.zj.playerLib.offline;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.zj.playerLib.upstream.DataSource;
import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.upstream.cache.Cache;
import com.zj.playerLib.upstream.cache.CacheDataSource;
import com.zj.playerLib.upstream.cache.CacheUtil;
import com.zj.playerLib.upstream.cache.CacheUtil.CachingCounters;
import com.zj.playerLib.util.PriorityTaskManager;
import com.zj.playerLib.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class SegmentDownloader<M extends FilterableManifest<M>> implements Downloader {
    private static final int BUFFER_SIZE_BYTES = 131072;
    private final Uri manifestUri;
    private final PriorityTaskManager priorityTaskManager;
    private final Cache cache;
    private final CacheDataSource dataSource;
    private final CacheDataSource offlineDataSource;
    private final ArrayList<StreamKey> streamKeys;
    private final AtomicBoolean isCanceled;
    private volatile int totalSegments;
    private volatile int downloadedSegments;
    private volatile long downloadedBytes;

    public SegmentDownloader(Uri manifestUri, List<StreamKey> streamKeys, DownloaderConstructorHelper constructorHelper) {
        this.manifestUri = manifestUri;
        this.streamKeys = new ArrayList(streamKeys);
        this.cache = constructorHelper.getCache();
        this.dataSource = constructorHelper.buildCacheDataSource(false);
        this.offlineDataSource = constructorHelper.buildCacheDataSource(true);
        this.priorityTaskManager = constructorHelper.getPriorityTaskManager();
        this.totalSegments = -1;
        this.isCanceled = new AtomicBoolean();
    }

    public final void download() throws IOException, InterruptedException {
        this.priorityTaskManager.add(-1000);

        try {
            List<Segment> segments = this.initDownload();
            Collections.sort(segments);
            byte[] buffer = new byte[131072];
            CachingCounters cachingCounters = new CachingCounters();
            for (int i = 0; i < segments.size(); ++i) {
                try {
                    CacheUtil.cache(segments.get(i).dataSpec, this.cache, this.dataSource, buffer, this.priorityTaskManager, -1000, cachingCounters, this.isCanceled, true);
                    ++this.downloadedSegments;
                } finally {
                    this.downloadedBytes += cachingCounters.newlyCachedBytes;
                }
            }
        } finally {
            this.priorityTaskManager.remove(-1000);
        }

    }

    public void cancel() {
        this.isCanceled.set(true);
    }

    public final long getDownloadedBytes() {
        return this.downloadedBytes;
    }

    public final float getDownloadPercentage() {
        int totalSegments = this.totalSegments;
        int downloadedSegments = this.downloadedSegments;
        if (totalSegments != -1 && downloadedSegments != -1) {
            return totalSegments == 0 ? 100.0F : (float) downloadedSegments * 100.0F / (float) totalSegments;
        } else {
            return -1.0F;
        }
    }

    public final void remove() throws InterruptedException {
        try {
            M manifest = this.getManifest(this.offlineDataSource, this.manifestUri);
            List<Segment> segments = this.getSegments(this.offlineDataSource, manifest, true);

            for (int i = 0; i < segments.size(); ++i) {
                this.removeUri(segments.get(i).dataSpec.uri);
            }
        } catch (IOException ignored) {
        } finally {
            this.removeUri(this.manifestUri);
        }

    }

    protected abstract M getManifest(DataSource var1, Uri var2) throws IOException;

    protected abstract List<Segment> getSegments(DataSource var1, M var2, boolean var3) throws InterruptedException, IOException;

    private List<Segment> initDownload() throws IOException, InterruptedException {
        M manifest = this.getManifest(this.dataSource, this.manifestUri);
        if (!this.streamKeys.isEmpty()) {
            manifest = manifest.copy(this.streamKeys);
        }

        List<Segment> segments = this.getSegments(this.dataSource, manifest, false);
        CachingCounters cachingCounters = new CachingCounters();
        this.totalSegments = segments.size();
        this.downloadedSegments = 0;
        this.downloadedBytes = 0L;

        for (int i = segments.size() - 1; i >= 0; --i) {
            Segment segment = segments.get(i);
            CacheUtil.getCached(segment.dataSpec, this.cache, cachingCounters);
            this.downloadedBytes += cachingCounters.alreadyCachedBytes;
            if (cachingCounters.alreadyCachedBytes == cachingCounters.contentLength) {
                ++this.downloadedSegments;
                segments.remove(i);
            }
        }
        return segments;
    }

    private void removeUri(Uri uri) {
        CacheUtil.remove(this.cache, CacheUtil.generateKey(uri));
    }

    protected static class Segment implements Comparable<Segment> {
        public final long startTimeUs;
        public final DataSpec dataSpec;

        public Segment(long startTimeUs, DataSpec dataSpec) {
            this.startTimeUs = startTimeUs;
            this.dataSpec = dataSpec;
        }

        public int compareTo(@NonNull SegmentDownloader.Segment other) {
            return Util.compareLong(this.startTimeUs, other.startTimeUs);
        }
    }
}
