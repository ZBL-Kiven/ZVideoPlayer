//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source.chunk;

import androidx.annotation.Nullable;
import com.zj.playerLib.Format;
import com.zj.playerLib.FormatHolder;
import com.zj.playerLib.SeekParameters;
import com.zj.playerLib.decoder.DecoderInputBuffer;
import com.zj.playerLib.source.SampleQueue;
import com.zj.playerLib.source.SampleStream;
import com.zj.playerLib.source.SequenceableLoader;
import com.zj.playerLib.source.MediaSourceEventListener.EventDispatcher;
import com.zj.playerLib.upstream.Allocator;
import com.zj.playerLib.upstream.DefaultLoadErrorHandlingPolicy;
import com.zj.playerLib.upstream.LoadErrorHandlingPolicy;
import com.zj.playerLib.upstream.Loader;
import com.zj.playerLib.upstream.Loader.Callback;
import com.zj.playerLib.upstream.Loader.LoadErrorAction;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChunkSampleStream<T extends ChunkSource> implements SampleStream, SequenceableLoader, Callback<Chunk>, Loader.ReleaseCallback {
    private static final String TAG = "ChunkSampleStream";
    public final int primaryTrackType;
    private final int[] embeddedTrackTypes;
    private final Format[] embeddedTrackFormats;
    private final boolean[] embeddedTracksSelected;
    private final T chunkSource;
    private final Callback<ChunkSampleStream<T>> callback;
    private final EventDispatcher eventDispatcher;
    private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private final Loader loader;
    private final ChunkHolder nextChunkHolder;
    private final ArrayList<BaseMediaChunk> mediaChunks;
    private final List<BaseMediaChunk> readOnlyMediaChunks;
    private final SampleQueue primarySampleQueue;
    private final SampleQueue[] embeddedSampleQueues;
    private final BaseMediaChunkOutput mediaChunkOutput;
    private Format primaryDownstreamTrackFormat;
    @Nullable
    private ChunkSampleStream.ReleaseCallback<T> releaseCallback;
    private long pendingResetPositionUs;
    private long lastSeekPositionUs;
    private int nextNotifyPrimaryFormatMediaChunkIndex;
    long decodeOnlyUntilPositionUs;
    boolean loadingFinished;

    /** @deprecated */
    @Deprecated
    public ChunkSampleStream(int primaryTrackType, int[] embeddedTrackTypes, Format[] embeddedTrackFormats, T chunkSource, Callback<ChunkSampleStream<T>> callback, Allocator allocator, long positionUs, int minLoadableRetryCount, EventDispatcher eventDispatcher) {
        this(primaryTrackType, embeddedTrackTypes, embeddedTrackFormats, chunkSource, callback, allocator, positionUs, new DefaultLoadErrorHandlingPolicy(minLoadableRetryCount), eventDispatcher);
    }

    public ChunkSampleStream(int primaryTrackType, int[] embeddedTrackTypes, Format[] embeddedTrackFormats, T chunkSource, Callback<ChunkSampleStream<T>> callback, Allocator allocator, long positionUs, LoadErrorHandlingPolicy loadErrorHandlingPolicy, EventDispatcher eventDispatcher) {
        this.primaryTrackType = primaryTrackType;
        this.embeddedTrackTypes = embeddedTrackTypes;
        this.embeddedTrackFormats = embeddedTrackFormats;
        this.chunkSource = chunkSource;
        this.callback = callback;
        this.eventDispatcher = eventDispatcher;
        this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
        this.loader = new Loader("Loader:ChunkSampleStream");
        this.nextChunkHolder = new ChunkHolder();
        this.mediaChunks = new ArrayList();
        this.readOnlyMediaChunks = Collections.unmodifiableList(this.mediaChunks);
        int embeddedTrackCount = embeddedTrackTypes == null ? 0 : embeddedTrackTypes.length;
        this.embeddedSampleQueues = new SampleQueue[embeddedTrackCount];
        this.embeddedTracksSelected = new boolean[embeddedTrackCount];
        int[] trackTypes = new int[1 + embeddedTrackCount];
        SampleQueue[] sampleQueues = new SampleQueue[1 + embeddedTrackCount];
        this.primarySampleQueue = new SampleQueue(allocator);
        trackTypes[0] = primaryTrackType;
        sampleQueues[0] = this.primarySampleQueue;

        for(int i = 0; i < embeddedTrackCount; ++i) {
            SampleQueue sampleQueue = new SampleQueue(allocator);
            this.embeddedSampleQueues[i] = sampleQueue;
            sampleQueues[i + 1] = sampleQueue;
            trackTypes[i + 1] = embeddedTrackTypes[i];
        }

        this.mediaChunkOutput = new BaseMediaChunkOutput(trackTypes, sampleQueues);
        this.pendingResetPositionUs = positionUs;
        this.lastSeekPositionUs = positionUs;
    }

    public void discardBuffer(long positionUs, boolean toKeyframe) {
        if (!this.isPendingReset()) {
            int oldFirstSampleIndex = this.primarySampleQueue.getFirstIndex();
            this.primarySampleQueue.discardTo(positionUs, toKeyframe, true);
            int newFirstSampleIndex = this.primarySampleQueue.getFirstIndex();
            if (newFirstSampleIndex > oldFirstSampleIndex) {
                long discardToUs = this.primarySampleQueue.getFirstTimestampUs();

                for(int i = 0; i < this.embeddedSampleQueues.length; ++i) {
                    this.embeddedSampleQueues[i].discardTo(discardToUs, toKeyframe, this.embeddedTracksSelected[i]);
                }
            }

            this.discardDownstreamMediaChunks(newFirstSampleIndex);
        }
    }

    public EmbeddedSampleStream selectEmbeddedTrack(long positionUs, int trackType) {
        for(int i = 0; i < this.embeddedSampleQueues.length; ++i) {
            if (this.embeddedTrackTypes[i] == trackType) {
                Assertions.checkState(!this.embeddedTracksSelected[i]);
                this.embeddedTracksSelected[i] = true;
                this.embeddedSampleQueues[i].rewind();
                this.embeddedSampleQueues[i].advanceTo(positionUs, true, true);
                return new EmbeddedSampleStream(this, this.embeddedSampleQueues[i], i);
            }
        }

        throw new IllegalStateException();
    }

    public T getChunkSource() {
        return this.chunkSource;
    }

    public long getBufferedPositionUs() {
        if (this.loadingFinished) {
            return -9223372036854775808L;
        } else if (this.isPendingReset()) {
            return this.pendingResetPositionUs;
        } else {
            long bufferedPositionUs = this.lastSeekPositionUs;
            BaseMediaChunk lastMediaChunk = this.getLastMediaChunk();
            BaseMediaChunk lastCompletedMediaChunk = lastMediaChunk.isLoadCompleted() ? lastMediaChunk : (this.mediaChunks.size() > 1 ? (BaseMediaChunk)this.mediaChunks.get(this.mediaChunks.size() - 2) : null);
            if (lastCompletedMediaChunk != null) {
                bufferedPositionUs = Math.max(bufferedPositionUs, lastCompletedMediaChunk.endTimeUs);
            }

            return Math.max(bufferedPositionUs, this.primarySampleQueue.getLargestQueuedTimestampUs());
        }
    }

    public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
        return this.chunkSource.getAdjustedSeekPositionUs(positionUs, seekParameters);
    }

    public void seekToUs(long positionUs) {
        this.lastSeekPositionUs = positionUs;
        if (this.isPendingReset()) {
            this.pendingResetPositionUs = positionUs;
        } else {
            BaseMediaChunk seekToMediaChunk = null;

            for(int i = 0; i < this.mediaChunks.size(); ++i) {
                BaseMediaChunk mediaChunk = (BaseMediaChunk)this.mediaChunks.get(i);
                long mediaChunkStartTimeUs = mediaChunk.startTimeUs;
                if (mediaChunkStartTimeUs == positionUs && mediaChunk.clippedStartTimeUs == -9223372036854775807L) {
                    seekToMediaChunk = mediaChunk;
                    break;
                }

                if (mediaChunkStartTimeUs > positionUs) {
                    break;
                }
            }

            this.primarySampleQueue.rewind();
            boolean seekInsideBuffer;
            if (seekToMediaChunk != null) {
                seekInsideBuffer = this.primarySampleQueue.setReadPosition(seekToMediaChunk.getFirstSampleIndex(0));
                this.decodeOnlyUntilPositionUs = 0L;
            } else {
                seekInsideBuffer = this.primarySampleQueue.advanceTo(positionUs, true, positionUs < this.getNextLoadPositionUs()) != -1;
                this.decodeOnlyUntilPositionUs = this.lastSeekPositionUs;
            }

            int var7;
            SampleQueue embeddedSampleQueue;
            SampleQueue[] var10;
            int var11;
            if (seekInsideBuffer) {
                this.nextNotifyPrimaryFormatMediaChunkIndex = this.primarySampleIndexToMediaChunkIndex(this.primarySampleQueue.getReadIndex(), 0);
                var10 = this.embeddedSampleQueues;
                var11 = var10.length;

                for(var7 = 0; var7 < var11; ++var7) {
                    embeddedSampleQueue = var10[var7];
                    embeddedSampleQueue.rewind();
                    embeddedSampleQueue.advanceTo(positionUs, true, false);
                }
            } else {
                this.pendingResetPositionUs = positionUs;
                this.loadingFinished = false;
                this.mediaChunks.clear();
                this.nextNotifyPrimaryFormatMediaChunkIndex = 0;
                if (this.loader.isLoading()) {
                    this.loader.cancelLoading();
                } else {
                    this.primarySampleQueue.reset();
                    var10 = this.embeddedSampleQueues;
                    var11 = var10.length;

                    for(var7 = 0; var7 < var11; ++var7) {
                        embeddedSampleQueue = var10[var7];
                        embeddedSampleQueue.reset();
                    }
                }
            }

        }
    }

    public void release() {
        this.release((ReleaseCallback)null);
    }

    public void release(@Nullable ChunkSampleStream.ReleaseCallback<T> callback) {
        this.releaseCallback = callback;
        this.primarySampleQueue.discardToEnd();
        SampleQueue[] var2 = this.embeddedSampleQueues;
        int var3 = var2.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            SampleQueue embeddedSampleQueue = var2[var4];
            embeddedSampleQueue.discardToEnd();
        }

        this.loader.release(this);
    }

    public void onLoaderReleased() {
        this.primarySampleQueue.reset();
        SampleQueue[] var1 = this.embeddedSampleQueues;
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            SampleQueue embeddedSampleQueue = var1[var3];
            embeddedSampleQueue.reset();
        }

        if (this.releaseCallback != null) {
            this.releaseCallback.onSampleStreamReleased(this);
        }

    }

    public boolean isReady() {
        return this.loadingFinished || !this.isPendingReset() && this.primarySampleQueue.hasNextSample();
    }

    public void maybeThrowError() throws IOException {
        this.loader.maybeThrowError();
        if (!this.loader.isLoading()) {
            this.chunkSource.maybeThrowError();
        }

    }

    public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired) {
        if (this.isPendingReset()) {
            return -3;
        } else {
            this.maybeNotifyPrimaryTrackFormatChanged();
            return this.primarySampleQueue.read(formatHolder, buffer, formatRequired, this.loadingFinished, this.decodeOnlyUntilPositionUs);
        }
    }

    public int skipData(long positionUs) {
        if (this.isPendingReset()) {
            return 0;
        } else {
            int skipCount;
            if (this.loadingFinished && positionUs > this.primarySampleQueue.getLargestQueuedTimestampUs()) {
                skipCount = this.primarySampleQueue.advanceToEnd();
            } else {
                skipCount = this.primarySampleQueue.advanceTo(positionUs, true, true);
                if (skipCount == -1) {
                    skipCount = 0;
                }
            }

            this.maybeNotifyPrimaryTrackFormatChanged();
            return skipCount;
        }
    }

    public void onLoadCompleted(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs) {
        this.chunkSource.onChunkLoadCompleted(loadable);
        this.eventDispatcher.loadCompleted(loadable.dataSpec, loadable.getUri(), loadable.getResponseHeaders(), loadable.type, this.primaryTrackType, loadable.trackFormat, loadable.trackSelectionReason, loadable.trackSelectionData, loadable.startTimeUs, loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs, loadable.bytesLoaded());
        this.callback.onContinueLoadingRequested(this);
    }

    public void onLoadCanceled(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {
        this.eventDispatcher.loadCanceled(loadable.dataSpec, loadable.getUri(), loadable.getResponseHeaders(), loadable.type, this.primaryTrackType, loadable.trackFormat, loadable.trackSelectionReason, loadable.trackSelectionData, loadable.startTimeUs, loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs, loadable.bytesLoaded());
        if (!released) {
            this.primarySampleQueue.reset();
            SampleQueue[] var7 = this.embeddedSampleQueues;
            int var8 = var7.length;

            for(int var9 = 0; var9 < var8; ++var9) {
                SampleQueue embeddedSampleQueue = var7[var9];
                embeddedSampleQueue.reset();
            }

            this.callback.onContinueLoadingRequested(this);
        }

    }

    public LoadErrorAction onLoadError(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs, IOException error, int errorCount) {
        long bytesLoaded = loadable.bytesLoaded();
        boolean isMediaChunk = this.isMediaChunk(loadable);
        int lastChunkIndex = this.mediaChunks.size() - 1;
        boolean cancelable = bytesLoaded == 0L || !isMediaChunk || !this.haveReadFromMediaChunk(lastChunkIndex);
        long blacklistDurationMs = cancelable ? this.loadErrorHandlingPolicy.getBlacklistDurationMsFor(loadable.type, loadDurationMs, error, errorCount) : -9223372036854775807L;
        LoadErrorAction loadErrorAction = null;
        if (this.chunkSource.onChunkLoadError(loadable, cancelable, error, blacklistDurationMs)) {
            if (cancelable) {
                loadErrorAction = Loader.DONT_RETRY;
                if (isMediaChunk) {
                    BaseMediaChunk removed = this.discardUpstreamMediaChunksFromIndex(lastChunkIndex);
                    Assertions.checkState(removed == loadable);
                    if (this.mediaChunks.isEmpty()) {
                        this.pendingResetPositionUs = this.lastSeekPositionUs;
                    }
                }
            } else {
                Log.w("ChunkSampleStream", "Ignoring attempt to cancel non-cancelable load.");
            }
        }

        if (loadErrorAction == null) {
            long retryDelayMs = this.loadErrorHandlingPolicy.getRetryDelayMsFor(loadable.type, loadDurationMs, error, errorCount);
            loadErrorAction = retryDelayMs != -9223372036854775807L ? Loader.createRetryAction(false, retryDelayMs) : Loader.DONT_RETRY_FATAL;
        }

        boolean canceled = !loadErrorAction.isRetry();
        this.eventDispatcher.loadError(loadable.dataSpec, loadable.getUri(), loadable.getResponseHeaders(), loadable.type, this.primaryTrackType, loadable.trackFormat, loadable.trackSelectionReason, loadable.trackSelectionData, loadable.startTimeUs, loadable.endTimeUs, elapsedRealtimeMs, loadDurationMs, bytesLoaded, error, canceled);
        if (canceled) {
            this.callback.onContinueLoadingRequested(this);
        }

        return loadErrorAction;
    }

    public boolean continueLoading(long positionUs) {
        if (!this.loadingFinished && !this.loader.isLoading()) {
            boolean pendingReset = this.isPendingReset();
            List chunkQueue;
            long loadPositionUs;
            if (pendingReset) {
                chunkQueue = Collections.emptyList();
                loadPositionUs = this.pendingResetPositionUs;
            } else {
                chunkQueue = this.readOnlyMediaChunks;
                loadPositionUs = this.getLastMediaChunk().endTimeUs;
            }

            this.chunkSource.getNextChunk(positionUs, loadPositionUs, chunkQueue, this.nextChunkHolder);
            boolean endOfStream = this.nextChunkHolder.endOfStream;
            Chunk loadable = this.nextChunkHolder.chunk;
            this.nextChunkHolder.clear();
            if (endOfStream) {
                this.pendingResetPositionUs = -9223372036854775807L;
                this.loadingFinished = true;
                return true;
            } else if (loadable == null) {
                return false;
            } else {
                if (this.isMediaChunk(loadable)) {
                    BaseMediaChunk mediaChunk = (BaseMediaChunk)loadable;
                    if (pendingReset) {
                        boolean resetToMediaChunk = mediaChunk.startTimeUs == this.pendingResetPositionUs;
                        this.decodeOnlyUntilPositionUs = resetToMediaChunk ? 0L : this.pendingResetPositionUs;
                        this.pendingResetPositionUs = -9223372036854775807L;
                    }

                    mediaChunk.init(this.mediaChunkOutput);
                    this.mediaChunks.add(mediaChunk);
                }

                long elapsedRealtimeMs = this.loader.startLoading(loadable, this, this.loadErrorHandlingPolicy.getMinimumLoadableRetryCount(loadable.type));
                this.eventDispatcher.loadStarted(loadable.dataSpec, loadable.type, this.primaryTrackType, loadable.trackFormat, loadable.trackSelectionReason, loadable.trackSelectionData, loadable.startTimeUs, loadable.endTimeUs, elapsedRealtimeMs);
                return true;
            }
        } else {
            return false;
        }
    }

    public long getNextLoadPositionUs() {
        if (this.isPendingReset()) {
            return this.pendingResetPositionUs;
        } else {
            return this.loadingFinished ? -9223372036854775808L : this.getLastMediaChunk().endTimeUs;
        }
    }

    public void reevaluateBuffer(long positionUs) {
        if (!this.loader.isLoading() && !this.isPendingReset()) {
            int currentQueueSize = this.mediaChunks.size();
            int preferredQueueSize = this.chunkSource.getPreferredQueueSize(positionUs, this.readOnlyMediaChunks);
            if (currentQueueSize > preferredQueueSize) {
                int newQueueSize = currentQueueSize;

                for(int i = preferredQueueSize; i < currentQueueSize; ++i) {
                    if (!this.haveReadFromMediaChunk(i)) {
                        newQueueSize = i;
                        break;
                    }
                }

                if (newQueueSize != currentQueueSize) {
                    long endTimeUs = this.getLastMediaChunk().endTimeUs;
                    BaseMediaChunk firstRemovedChunk = this.discardUpstreamMediaChunksFromIndex(newQueueSize);
                    if (this.mediaChunks.isEmpty()) {
                        this.pendingResetPositionUs = this.lastSeekPositionUs;
                    }

                    this.loadingFinished = false;
                    this.eventDispatcher.upstreamDiscarded(this.primaryTrackType, firstRemovedChunk.startTimeUs, endTimeUs);
                }
            }
        }
    }

    private boolean isMediaChunk(Chunk chunk) {
        return chunk instanceof BaseMediaChunk;
    }

    private boolean haveReadFromMediaChunk(int mediaChunkIndex) {
        BaseMediaChunk mediaChunk = (BaseMediaChunk)this.mediaChunks.get(mediaChunkIndex);
        if (this.primarySampleQueue.getReadIndex() > mediaChunk.getFirstSampleIndex(0)) {
            return true;
        } else {
            for(int i = 0; i < this.embeddedSampleQueues.length; ++i) {
                if (this.embeddedSampleQueues[i].getReadIndex() > mediaChunk.getFirstSampleIndex(i + 1)) {
                    return true;
                }
            }

            return false;
        }
    }

    boolean isPendingReset() {
        return this.pendingResetPositionUs != -9223372036854775807L;
    }

    private void discardDownstreamMediaChunks(int discardToSampleIndex) {
        int discardToMediaChunkIndex = this.primarySampleIndexToMediaChunkIndex(discardToSampleIndex, 0);
        discardToMediaChunkIndex = Math.min(discardToMediaChunkIndex, this.nextNotifyPrimaryFormatMediaChunkIndex);
        if (discardToMediaChunkIndex > 0) {
            Util.removeRange(this.mediaChunks, 0, discardToMediaChunkIndex);
            this.nextNotifyPrimaryFormatMediaChunkIndex -= discardToMediaChunkIndex;
        }

    }

    private void maybeNotifyPrimaryTrackFormatChanged() {
        int readSampleIndex = this.primarySampleQueue.getReadIndex();
        int notifyToMediaChunkIndex = this.primarySampleIndexToMediaChunkIndex(readSampleIndex, this.nextNotifyPrimaryFormatMediaChunkIndex - 1);

        while(this.nextNotifyPrimaryFormatMediaChunkIndex <= notifyToMediaChunkIndex) {
            this.maybeNotifyPrimaryTrackFormatChanged(this.nextNotifyPrimaryFormatMediaChunkIndex++);
        }

    }

    private void maybeNotifyPrimaryTrackFormatChanged(int mediaChunkReadIndex) {
        BaseMediaChunk currentChunk = (BaseMediaChunk)this.mediaChunks.get(mediaChunkReadIndex);
        Format trackFormat = currentChunk.trackFormat;
        if (!trackFormat.equals(this.primaryDownstreamTrackFormat)) {
            this.eventDispatcher.downstreamFormatChanged(this.primaryTrackType, trackFormat, currentChunk.trackSelectionReason, currentChunk.trackSelectionData, currentChunk.startTimeUs);
        }

        this.primaryDownstreamTrackFormat = trackFormat;
    }

    private int primarySampleIndexToMediaChunkIndex(int primarySampleIndex, int minChunkIndex) {
        for(int i = minChunkIndex + 1; i < this.mediaChunks.size(); ++i) {
            if (((BaseMediaChunk)this.mediaChunks.get(i)).getFirstSampleIndex(0) > primarySampleIndex) {
                return i - 1;
            }
        }

        return this.mediaChunks.size() - 1;
    }

    private BaseMediaChunk getLastMediaChunk() {
        return (BaseMediaChunk)this.mediaChunks.get(this.mediaChunks.size() - 1);
    }

    private BaseMediaChunk discardUpstreamMediaChunksFromIndex(int chunkIndex) {
        BaseMediaChunk firstRemovedChunk = (BaseMediaChunk)this.mediaChunks.get(chunkIndex);
        Util.removeRange(this.mediaChunks, chunkIndex, this.mediaChunks.size());
        this.nextNotifyPrimaryFormatMediaChunkIndex = Math.max(this.nextNotifyPrimaryFormatMediaChunkIndex, this.mediaChunks.size());
        this.primarySampleQueue.discardUpstreamSamples(firstRemovedChunk.getFirstSampleIndex(0));

        for(int i = 0; i < this.embeddedSampleQueues.length; ++i) {
            this.embeddedSampleQueues[i].discardUpstreamSamples(firstRemovedChunk.getFirstSampleIndex(i + 1));
        }

        return firstRemovedChunk;
    }

    public final class EmbeddedSampleStream implements SampleStream {
        public final ChunkSampleStream<T> parent;
        private final SampleQueue sampleQueue;
        private final int index;
        private boolean notifiedDownstreamFormat;

        public EmbeddedSampleStream(ChunkSampleStream<T> parent, SampleQueue sampleQueue, int index) {
            this.parent = parent;
            this.sampleQueue = sampleQueue;
            this.index = index;
        }

        public boolean isReady() {
            return ChunkSampleStream.this.loadingFinished || !ChunkSampleStream.this.isPendingReset() && this.sampleQueue.hasNextSample();
        }

        public int skipData(long positionUs) {
            if (ChunkSampleStream.this.isPendingReset()) {
                return 0;
            } else {
                this.maybeNotifyDownstreamFormat();
                int skipCount;
                if (ChunkSampleStream.this.loadingFinished && positionUs > this.sampleQueue.getLargestQueuedTimestampUs()) {
                    skipCount = this.sampleQueue.advanceToEnd();
                } else {
                    skipCount = this.sampleQueue.advanceTo(positionUs, true, true);
                    if (skipCount == -1) {
                        skipCount = 0;
                    }
                }

                return skipCount;
            }
        }

        public void maybeThrowError() throws IOException {
        }

        public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired) {
            if (ChunkSampleStream.this.isPendingReset()) {
                return -3;
            } else {
                this.maybeNotifyDownstreamFormat();
                return this.sampleQueue.read(formatHolder, buffer, formatRequired, ChunkSampleStream.this.loadingFinished, ChunkSampleStream.this.decodeOnlyUntilPositionUs);
            }
        }

        public void release() {
            Assertions.checkState(ChunkSampleStream.this.embeddedTracksSelected[this.index]);
            ChunkSampleStream.this.embeddedTracksSelected[this.index] = false;
        }

        private void maybeNotifyDownstreamFormat() {
            if (!this.notifiedDownstreamFormat) {
                ChunkSampleStream.this.eventDispatcher.downstreamFormatChanged(ChunkSampleStream.this.embeddedTrackTypes[this.index], ChunkSampleStream.this.embeddedTrackFormats[this.index], 0, (Object)null, ChunkSampleStream.this.lastSeekPositionUs);
                this.notifiedDownstreamFormat = true;
            }

        }
    }

    public interface ReleaseCallback<T extends ChunkSource> {
        void onSampleStreamReleased(ChunkSampleStream<T> var1);
    }
}
