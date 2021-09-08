//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source;

import android.net.Uri;
import android.os.Handler;

import androidx.annotation.Nullable;

import com.zj.playerLib.Format;
import com.zj.playerLib.FormatHolder;
import com.zj.playerLib.SeekParameters;
import com.zj.playerLib.decoder.DecoderInputBuffer;
import com.zj.playerLib.extractor.DefaultExtractorInput;
import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.extractor.SeekMap;
import com.zj.playerLib.extractor.SeekMap.SeekPoints;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.source.MediaSourceEventListener.EventDispatcher;
import com.zj.playerLib.source.SampleQueue.UpstreamFormatChangedListener;
import com.zj.playerLib.trackselection.TrackSelection;
import com.zj.playerLib.upstream.Allocator;
import com.zj.playerLib.upstream.DataSource;
import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.upstream.LoadErrorHandlingPolicy;
import com.zj.playerLib.upstream.Loader;
import com.zj.playerLib.upstream.Loader.Callback;
import com.zj.playerLib.upstream.Loader.LoadErrorAction;
import com.zj.playerLib.upstream.Loader.Loadable;
import com.zj.playerLib.upstream.Loader.ReleaseCallback;
import com.zj.playerLib.upstream.StatsDataSource;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.ConditionVariable;
import com.zj.playerLib.util.MimeTypes;
import com.zj.playerLib.util.Util;

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

final class ExtractorMediaPeriod implements MediaPeriod, ExtractorOutput, Callback<ExtractorMediaPeriod.ExtractingLoadable>, ReleaseCallback, UpstreamFormatChangedListener {
    private static final long DEFAULT_LAST_SAMPLE_DURATION_US = 10000L;
    private final Uri uri;
    private final DataSource dataSource;
    private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private final EventDispatcher eventDispatcher;
    private final Listener listener;
    private final Allocator allocator;
    @Nullable
    private final String customCacheKey;
    private final long continueLoadingCheckIntervalBytes;
    private final Loader loader;
    private final ExtractorHolder extractorHolder;
    private final ConditionVariable loadCondition;
    private final Runnable maybeFinishPrepareRunnable;
    private final Runnable onContinueLoadingRequestedRunnable;
    private final Handler handler;
    @Nullable
    private MediaPeriod.Callback callback;
    @Nullable
    private SeekMap seekMap;
    private SampleQueue[] sampleQueues;
    private int[] sampleQueueTrackIds;
    private boolean sampleQueuesBuilt;
    private boolean prepared;
    @Nullable
    private ExtractorMediaPeriod.PreparedState preparedState;
    private boolean haveAudioVideoTracks;
    private int dataType;
    private boolean seenFirstTrackSelection;
    private boolean notifyDiscontinuity;
    private boolean notifiedReadingStarted;
    private int enabledTrackCount;
    private long durationUs;
    private long length;
    private long lastSeekPositionUs;
    private long pendingResetPositionUs;
    private boolean pendingDeferredRetry;
    private int extractedSamplesCountAtStartOfLoad;
    private boolean loadingFinished;
    private boolean released;

    public ExtractorMediaPeriod(Uri uri, DataSource dataSource, Extractor[] extractors, LoadErrorHandlingPolicy loadErrorHandlingPolicy, EventDispatcher eventDispatcher, Listener listener, Allocator allocator, @Nullable String customCacheKey, int continueLoadingCheckIntervalBytes) {
        this.uri = uri;
        this.dataSource = dataSource;
        this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
        this.eventDispatcher = eventDispatcher;
        this.listener = listener;
        this.allocator = allocator;
        this.customCacheKey = customCacheKey;
        this.continueLoadingCheckIntervalBytes = (long) continueLoadingCheckIntervalBytes;
        this.loader = new Loader("Loader:ExtractorMediaPeriod");
        this.extractorHolder = new ExtractorHolder(extractors);
        this.loadCondition = new ConditionVariable();
        this.maybeFinishPrepareRunnable = this::maybeFinishPrepare;
        this.onContinueLoadingRequestedRunnable = () -> {
            if (!this.released) {
                ((Callback) Assertions.checkNotNull(this.callback)).onContinueLoadingRequested(this);
            }

        };
        this.handler = new Handler();
        this.sampleQueueTrackIds = new int[0];
        this.sampleQueues = new SampleQueue[0];
        this.pendingResetPositionUs = -9223372036854775807L;
        this.length = -1L;
        this.durationUs = -9223372036854775807L;
        this.dataType = 1;
        eventDispatcher.mediaPeriodCreated();
    }

    public void release() {
        if (this.prepared) {
            SampleQueue[] var1 = this.sampleQueues;
            int var2 = var1.length;

            for (int var3 = 0; var3 < var2; ++var3) {
                SampleQueue sampleQueue = var1[var3];
                sampleQueue.discardToEnd();
            }
        }

        this.loader.release(this);
        this.handler.removeCallbacksAndMessages((Object) null);
        this.callback = null;
        this.released = true;
        this.eventDispatcher.mediaPeriodReleased();
    }

    public void onLoaderReleased() {
        SampleQueue[] var1 = this.sampleQueues;
        int var2 = var1.length;

        for (int var3 = 0; var3 < var2; ++var3) {
            SampleQueue sampleQueue = var1[var3];
            sampleQueue.reset();
        }

        this.extractorHolder.release();
    }

    public void prepare(Callback callback, long positionUs) {
        this.callback = callback;
        this.loadCondition.open();
        this.startLoading();
    }

    public void maybeThrowPrepareError() throws IOException {
        this.maybeThrowError();
    }

    public TrackGroupArray getTrackGroups() {
        return this.getPreparedState().tracks;
    }

    public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags, SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
        PreparedState preparedState = this.getPreparedState();
        TrackGroupArray tracks = preparedState.tracks;
        boolean[] trackEnabledStates = preparedState.trackEnabledStates;
        int oldEnabledTrackCount = this.enabledTrackCount;
        for (int i = 0; i < selections.length; ++i) {
            if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
                i = ((SampleStreamImpl) streams[i]).track;
                Assertions.checkState(trackEnabledStates[i]);
                --this.enabledTrackCount;
                trackEnabledStates[i] = false;
                streams[i] = null;
            }
        }

        boolean seekRequired = this.seenFirstTrackSelection ? oldEnabledTrackCount == 0 : positionUs != 0L;

        int track;
        SampleQueue sampleQueue;
        for (int i = 0; i < selections.length; ++i) {
            if (streams[i] == null && selections[i] != null) {
                TrackSelection selection = selections[i];
                Assertions.checkState(selection.length() == 1);
                Assertions.checkState(selection.getIndexInTrackGroup(0) == 0);
                track = tracks.indexOf(selection.getTrackGroup());
                Assertions.checkState(!trackEnabledStates[track]);
                ++this.enabledTrackCount;
                trackEnabledStates[track] = true;
                streams[i] = new SampleStreamImpl(track);
                streamResetFlags[i] = true;
                if (!seekRequired) {
                    sampleQueue = this.sampleQueues[track];
                    sampleQueue.rewind();
                    seekRequired = sampleQueue.advanceTo(positionUs, true, true) == -1 && sampleQueue.getReadIndex() != 0;
                }
            }
        }

        if (this.enabledTrackCount == 0) {
            this.pendingDeferredRetry = false;
            this.notifyDiscontinuity = false;
            SampleQueue[] var17;
            int var18;
            if (this.loader.isLoading()) {
                var17 = this.sampleQueues;
                var18 = var17.length;

                for (track = 0; track < var18; ++track) {
                    sampleQueue = var17[track];
                    sampleQueue.discardToEnd();
                }

                this.loader.cancelLoading();
            } else {
                var17 = this.sampleQueues;
                var18 = var17.length;

                for (track = 0; track < var18; ++track) {
                    sampleQueue = var17[track];
                    sampleQueue.reset();
                }
            }
        } else if (seekRequired) {
            positionUs = this.seekToUs(positionUs);
            for (int i = 0; i < streams.length; ++i) {
                if (streams[i] != null) {
                    streamResetFlags[i] = true;
                }
            }
        }

        this.seenFirstTrackSelection = true;
        return positionUs;
    }

    public void discardBuffer(long positionUs, boolean toKeyframe) {
        if (!this.isPendingReset()) {
            boolean[] trackEnabledStates = this.getPreparedState().trackEnabledStates;
            int trackCount = this.sampleQueues.length;

            for (int i = 0; i < trackCount; ++i) {
                this.sampleQueues[i].discardTo(positionUs, toKeyframe, trackEnabledStates[i]);
            }

        }
    }

    public void reevaluateBuffer(long positionUs) {
    }

    public boolean continueLoading(long playbackPositionUs) {
        if (!this.loadingFinished && !this.pendingDeferredRetry && (!this.prepared || this.enabledTrackCount != 0)) {
            boolean continuedLoading = this.loadCondition.open();
            if (!this.loader.isLoading()) {
                this.startLoading();
                continuedLoading = true;
            }

            return continuedLoading;
        } else {
            return false;
        }
    }

    public long getNextLoadPositionUs() {
        return this.enabledTrackCount == 0 ? -9223372036854775808L : this.getBufferedPositionUs();
    }

    public long readDiscontinuity() {
        if (!this.notifiedReadingStarted) {
            this.eventDispatcher.readingStarted();
            this.notifiedReadingStarted = true;
        }

        if (!this.notifyDiscontinuity || !this.loadingFinished && this.getExtractedSamplesCount() <= this.extractedSamplesCountAtStartOfLoad) {
            return -9223372036854775807L;
        } else {
            this.notifyDiscontinuity = false;
            return this.lastSeekPositionUs;
        }
    }

    public long getBufferedPositionUs() {
        boolean[] trackIsAudioVideoFlags = this.getPreparedState().trackIsAudioVideoFlags;
        if (this.loadingFinished) {
            return -9223372036854775808L;
        } else if (this.isPendingReset()) {
            return this.pendingResetPositionUs;
        } else {
            long largestQueuedTimestampUs;
            if (this.haveAudioVideoTracks) {
                largestQueuedTimestampUs = 9223372036854775807L;
                int trackCount = this.sampleQueues.length;

                for (int i = 0; i < trackCount; ++i) {
                    if (trackIsAudioVideoFlags[i]) {
                        largestQueuedTimestampUs = Math.min(largestQueuedTimestampUs, this.sampleQueues[i].getLargestQueuedTimestampUs());
                    }
                }
            } else {
                largestQueuedTimestampUs = this.getLargestQueuedTimestampUs();
            }

            return largestQueuedTimestampUs == -9223372036854775808L ? this.lastSeekPositionUs : largestQueuedTimestampUs;
        }
    }

    public long seekToUs(long positionUs) {
        PreparedState preparedState = this.getPreparedState();
        SeekMap seekMap = preparedState.seekMap;
        boolean[] trackIsAudioVideoFlags = preparedState.trackIsAudioVideoFlags;
        positionUs = seekMap.isSeekable() ? positionUs : 0L;
        this.notifyDiscontinuity = false;
        this.lastSeekPositionUs = positionUs;
        if (this.isPendingReset()) {
            this.pendingResetPositionUs = positionUs;
            return positionUs;
        } else if (this.dataType != 7 && this.seekInsideBufferUs(trackIsAudioVideoFlags, positionUs)) {
            return positionUs;
        } else {
            this.pendingDeferredRetry = false;
            this.pendingResetPositionUs = positionUs;
            this.loadingFinished = false;
            if (this.loader.isLoading()) {
                this.loader.cancelLoading();
            } else {
                SampleQueue[] var6 = this.sampleQueues;
                int var7 = var6.length;

                for (int var8 = 0; var8 < var7; ++var8) {
                    SampleQueue sampleQueue = var6[var8];
                    sampleQueue.reset();
                }
            }

            return positionUs;
        }
    }

    public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
        SeekMap seekMap = this.getPreparedState().seekMap;
        if (!seekMap.isSeekable()) {
            return 0L;
        } else {
            SeekPoints seekPoints = seekMap.getSeekPoints(positionUs);
            return Util.resolveSeekPositionUs(positionUs, seekParameters, seekPoints.first.timeUs, seekPoints.second.timeUs);
        }
    }

    boolean isReady(int track) {
        return !this.suppressRead() && (this.loadingFinished || this.sampleQueues[track].hasNextSample());
    }

    void maybeThrowError() throws IOException {
        this.loader.maybeThrowError(this.loadErrorHandlingPolicy.getMinimumLoadableRetryCount(this.dataType));
    }

    int readData(int track, FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired) {
        if (this.suppressRead()) {
            return -3;
        } else {
            this.maybeNotifyDownstreamFormat(track);
            int result = this.sampleQueues[track].read(formatHolder, buffer, formatRequired, this.loadingFinished, this.lastSeekPositionUs);
            if (result == -3) {
                this.maybeStartDeferredRetry(track);
            }

            return result;
        }
    }

    int skipData(int track, long positionUs) {
        if (this.suppressRead()) {
            return 0;
        } else {
            this.maybeNotifyDownstreamFormat(track);
            SampleQueue sampleQueue = this.sampleQueues[track];
            int skipCount;
            if (this.loadingFinished && positionUs > sampleQueue.getLargestQueuedTimestampUs()) {
                skipCount = sampleQueue.advanceToEnd();
            } else {
                skipCount = sampleQueue.advanceTo(positionUs, true, true);
                if (skipCount == -1) {
                    skipCount = 0;
                }
            }

            if (skipCount == 0) {
                this.maybeStartDeferredRetry(track);
            }

            return skipCount;
        }
    }

    private void maybeNotifyDownstreamFormat(int track) {
        PreparedState preparedState = this.getPreparedState();
        boolean[] trackNotifiedDownstreamFormats = preparedState.trackNotifiedDownstreamFormats;
        if (!trackNotifiedDownstreamFormats[track]) {
            Format trackFormat = preparedState.tracks.get(track).getFormat(0);
            this.eventDispatcher.downstreamFormatChanged(MimeTypes.getTrackType(trackFormat.sampleMimeType), trackFormat, 0, (Object) null, this.lastSeekPositionUs);
            trackNotifiedDownstreamFormats[track] = true;
        }

    }

    private void maybeStartDeferredRetry(int track) {
        boolean[] trackIsAudioVideoFlags = this.getPreparedState().trackIsAudioVideoFlags;
        if (this.pendingDeferredRetry && trackIsAudioVideoFlags[track] && !this.sampleQueues[track].hasNextSample()) {
            this.pendingResetPositionUs = 0L;
            this.pendingDeferredRetry = false;
            this.notifyDiscontinuity = true;
            this.lastSeekPositionUs = 0L;
            this.extractedSamplesCountAtStartOfLoad = 0;
            SampleQueue[] var3 = this.sampleQueues;
            int var4 = var3.length;

            for (int var5 = 0; var5 < var4; ++var5) {
                SampleQueue sampleQueue = var3[var5];
                sampleQueue.reset();
            }

            ((Callback) Assertions.checkNotNull(this.callback)).onContinueLoadingRequested(this);
        }
    }

    private boolean suppressRead() {
        return this.notifyDiscontinuity || this.isPendingReset();
    }

    public void onLoadCompleted(ExtractingLoadable loadable, long elapsedRealtimeMs, long loadDurationMs) {
        if (this.durationUs == -9223372036854775807L) {
            SeekMap seekMap = (SeekMap) Assertions.checkNotNull(this.seekMap);
            long largestQueuedTimestampUs = this.getLargestQueuedTimestampUs();
            this.durationUs = largestQueuedTimestampUs == -9223372036854775808L ? 0L : largestQueuedTimestampUs + 10000L;
            this.listener.onSourceInfoRefreshed(this.durationUs, seekMap.isSeekable());
        }

        this.eventDispatcher.loadCompleted(loadable.dataSpec, loadable.dataSource.getLastOpenedUri(), loadable.dataSource.getLastResponseHeaders(), 1, -1, (Format) null, 0, (Object) null, loadable.seekTimeUs, this.durationUs, elapsedRealtimeMs, loadDurationMs, loadable.dataSource.getBytesRead());
        this.copyLengthFromLoader(loadable);
        this.loadingFinished = true;
        ((Callback) Assertions.checkNotNull(this.callback)).onContinueLoadingRequested(this);
    }

    public void onLoadCanceled(ExtractingLoadable loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {
        this.eventDispatcher.loadCanceled(loadable.dataSpec, loadable.dataSource.getLastOpenedUri(), loadable.dataSource.getLastResponseHeaders(), 1, -1, (Format) null, 0, (Object) null, loadable.seekTimeUs, this.durationUs, elapsedRealtimeMs, loadDurationMs, loadable.dataSource.getBytesRead());
        if (!released) {
            this.copyLengthFromLoader(loadable);
            SampleQueue[] var7 = this.sampleQueues;
            int var8 = var7.length;

            for (int var9 = 0; var9 < var8; ++var9) {
                SampleQueue sampleQueue = var7[var9];
                sampleQueue.reset();
            }

            if (this.enabledTrackCount > 0) {
                ((Callback) Assertions.checkNotNull(this.callback)).onContinueLoadingRequested(this);
            }
        }

    }

    public LoadErrorAction onLoadError(ExtractingLoadable loadable, long elapsedRealtimeMs, long loadDurationMs, IOException error, int errorCount) {
        this.copyLengthFromLoader(loadable);
        long retryDelayMs = this.loadErrorHandlingPolicy.getRetryDelayMsFor(this.dataType, this.durationUs, error, errorCount);
        LoadErrorAction loadErrorAction;
        if (retryDelayMs == -9223372036854775807L) {
            loadErrorAction = Loader.DONT_RETRY_FATAL;
        } else {
            int extractedSamplesCount = this.getExtractedSamplesCount();
            boolean madeProgress = extractedSamplesCount > this.extractedSamplesCountAtStartOfLoad;
            loadErrorAction = this.configureRetry(loadable, extractedSamplesCount) ? Loader.createRetryAction(madeProgress, retryDelayMs) : Loader.DONT_RETRY;
        }

        this.eventDispatcher.loadError(loadable.dataSpec, loadable.dataSource.getLastOpenedUri(), loadable.dataSource.getLastResponseHeaders(), 1, -1, (Format) null, 0, (Object) null, loadable.seekTimeUs, this.durationUs, elapsedRealtimeMs, loadDurationMs, loadable.dataSource.getBytesRead(), error, !loadErrorAction.isRetry());
        return loadErrorAction;
    }

    public TrackOutput track(int id, int type) {
        int trackCount = this.sampleQueues.length;

        for (int i = 0; i < trackCount; ++i) {
            if (this.sampleQueueTrackIds[i] == id) {
                return this.sampleQueues[i];
            }
        }

        SampleQueue trackOutput = new SampleQueue(this.allocator);
        trackOutput.setUpstreamFormatChangeListener(this);
        this.sampleQueueTrackIds = Arrays.copyOf(this.sampleQueueTrackIds, trackCount + 1);
        this.sampleQueueTrackIds[trackCount] = id;
        SampleQueue[] sampleQueues = (SampleQueue[]) Arrays.copyOf(this.sampleQueues, trackCount + 1);
        sampleQueues[trackCount] = trackOutput;
        this.sampleQueues = (SampleQueue[]) Util.castNonNullTypeArray(sampleQueues);
        return trackOutput;
    }

    public void endTracks() {
        this.sampleQueuesBuilt = true;
        this.handler.post(this.maybeFinishPrepareRunnable);
    }

    public void seekMap(SeekMap seekMap) {
        this.seekMap = seekMap;
        this.handler.post(this.maybeFinishPrepareRunnable);
    }

    public void onUpstreamFormatChanged(Format format) {
        this.handler.post(this.maybeFinishPrepareRunnable);
    }

    private void maybeFinishPrepare() {
        SeekMap seekMap = this.seekMap;
        if (!this.released && !this.prepared && this.sampleQueuesBuilt && seekMap != null) {
            SampleQueue[] var2 = this.sampleQueues;
            int var3 = var2.length;

            for (int var4 = 0; var4 < var3; ++var4) {
                SampleQueue sampleQueue = var2[var4];
                if (sampleQueue.getUpstreamFormat() == null) {
                    return;
                }
            }

            this.loadCondition.close();
            int trackCount = this.sampleQueues.length;
            TrackGroup[] trackArray = new TrackGroup[trackCount];
            boolean[] trackIsAudioVideoFlags = new boolean[trackCount];
            this.durationUs = seekMap.getDurationUs();

            for (int i = 0; i < trackCount; ++i) {
                Format trackFormat = this.sampleQueues[i].getUpstreamFormat();
                trackArray[i] = new TrackGroup(new Format[]{trackFormat});
                String mimeType = trackFormat.sampleMimeType;
                boolean isAudioVideo = MimeTypes.isVideo(mimeType) || MimeTypes.isAudio(mimeType);
                trackIsAudioVideoFlags[i] = isAudioVideo;
                this.haveAudioVideoTracks |= isAudioVideo;
            }

            this.dataType = this.length == -1L && seekMap.getDurationUs() == -9223372036854775807L ? 7 : 1;
            this.preparedState = new PreparedState(seekMap, new TrackGroupArray(trackArray), trackIsAudioVideoFlags);
            this.prepared = true;
            this.listener.onSourceInfoRefreshed(this.durationUs, seekMap.isSeekable());
            ((Callback) Assertions.checkNotNull(this.callback)).onPrepared(this);
        }
    }

    private PreparedState getPreparedState() {
        return (PreparedState) Assertions.checkNotNull(this.preparedState);
    }

    private void copyLengthFromLoader(ExtractingLoadable loadable) {
        if (this.length == -1L) {
            this.length = loadable.length;
        }

    }

    private void startLoading() {
        ExtractingLoadable loadable = new ExtractingLoadable(this.uri, this.dataSource, this.extractorHolder, this, this.loadCondition);
        if (this.prepared) {
            SeekMap seekMap = this.getPreparedState().seekMap;
            Assertions.checkState(this.isPendingReset());
            if (this.durationUs != -9223372036854775807L && this.pendingResetPositionUs >= this.durationUs) {
                this.loadingFinished = true;
                this.pendingResetPositionUs = -9223372036854775807L;
                return;
            }

            loadable.setLoadPosition(seekMap.getSeekPoints(this.pendingResetPositionUs).first.position, this.pendingResetPositionUs);
            this.pendingResetPositionUs = -9223372036854775807L;
        }

        this.extractedSamplesCountAtStartOfLoad = this.getExtractedSamplesCount();
        long elapsedRealtimeMs = this.loader.startLoading(loadable, this, this.loadErrorHandlingPolicy.getMinimumLoadableRetryCount(this.dataType));
        this.eventDispatcher.loadStarted(loadable.dataSpec, 1, -1, (Format) null, 0, (Object) null, loadable.seekTimeUs, this.durationUs, elapsedRealtimeMs);
    }

    private boolean configureRetry(ExtractingLoadable loadable, int currentExtractedSampleCount) {
        if (this.length == -1L && (this.seekMap == null || this.seekMap.getDurationUs() == -9223372036854775807L)) {
            if (this.prepared && !this.suppressRead()) {
                this.pendingDeferredRetry = true;
                return false;
            } else {
                this.notifyDiscontinuity = this.prepared;
                this.lastSeekPositionUs = 0L;
                this.extractedSamplesCountAtStartOfLoad = 0;
                SampleQueue[] var3 = this.sampleQueues;
                int var4 = var3.length;

                for (int var5 = 0; var5 < var4; ++var5) {
                    SampleQueue sampleQueue = var3[var5];
                    sampleQueue.reset();
                }

                loadable.setLoadPosition(0L, 0L);
                return true;
            }
        } else {
            this.extractedSamplesCountAtStartOfLoad = currentExtractedSampleCount;
            return true;
        }
    }

    private boolean seekInsideBufferUs(boolean[] trackIsAudioVideoFlags, long positionUs) {
        int trackCount = this.sampleQueues.length;

        for (int i = 0; i < trackCount; ++i) {
            SampleQueue sampleQueue = this.sampleQueues[i];
            sampleQueue.rewind();
            boolean seekInsideQueue = sampleQueue.advanceTo(positionUs, true, false) != -1;
            if (!seekInsideQueue && (trackIsAudioVideoFlags[i] || !this.haveAudioVideoTracks)) {
                return false;
            }
        }

        return true;
    }

    private int getExtractedSamplesCount() {
        int extractedSamplesCount = 0;
        SampleQueue[] var2 = this.sampleQueues;
        int var3 = var2.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            SampleQueue sampleQueue = var2[var4];
            extractedSamplesCount += sampleQueue.getWriteIndex();
        }

        return extractedSamplesCount;
    }

    private long getLargestQueuedTimestampUs() {
        long largestQueuedTimestampUs = -9223372036854775808L;
        SampleQueue[] var3 = this.sampleQueues;
        int var4 = var3.length;

        for (int var5 = 0; var5 < var4; ++var5) {
            SampleQueue sampleQueue = var3[var5];
            largestQueuedTimestampUs = Math.max(largestQueuedTimestampUs, sampleQueue.getLargestQueuedTimestampUs());
        }

        return largestQueuedTimestampUs;
    }

    private boolean isPendingReset() {
        return this.pendingResetPositionUs != -9223372036854775807L;
    }

    private static final class PreparedState {
        public final SeekMap seekMap;
        public final TrackGroupArray tracks;
        public final boolean[] trackIsAudioVideoFlags;
        public final boolean[] trackEnabledStates;
        public final boolean[] trackNotifiedDownstreamFormats;

        public PreparedState(SeekMap seekMap, TrackGroupArray tracks, boolean[] trackIsAudioVideoFlags) {
            this.seekMap = seekMap;
            this.tracks = tracks;
            this.trackIsAudioVideoFlags = trackIsAudioVideoFlags;
            this.trackEnabledStates = new boolean[tracks.length];
            this.trackNotifiedDownstreamFormats = new boolean[tracks.length];
        }
    }

    private static final class ExtractorHolder {
        private final Extractor[] extractors;
        @Nullable
        private Extractor extractor;

        public ExtractorHolder(Extractor[] extractors) {
            this.extractors = extractors;
        }

        public Extractor selectExtractor(ExtractorInput input, ExtractorOutput output, Uri uri) throws IOException, InterruptedException {
            if (this.extractor != null) {
                return this.extractor;
            } else {
                Extractor[] var4 = this.extractors;
                int var5 = var4.length;

                for (int var6 = 0; var6 < var5; ++var6) {
                    Extractor extractor = var4[var6];

                    try {
                        if (extractor.sniff(input)) {
                            this.extractor = extractor;
                            break;
                        }
                    } catch (EOFException var12) {
                    } finally {
                        input.resetPeekPosition();
                    }
                }

                if (this.extractor == null) {
                    throw new UnrecognizedInputFormatException("None of the available extractors (" + Util.getCommaDelimitedSimpleClassNames(this.extractors) + ") could read the stream.", uri);
                } else {
                    this.extractor.init(output);
                    return this.extractor;
                }
            }
        }

        public void release() {
            if (this.extractor != null) {
                this.extractor.release();
                this.extractor = null;
            }

        }
    }

    final class ExtractingLoadable implements Loadable {
        private final Uri uri;
        private final StatsDataSource dataSource;
        private final ExtractorHolder extractorHolder;
        private final ExtractorOutput extractorOutput;
        private final ConditionVariable loadCondition;
        private final PositionHolder positionHolder;
        private volatile boolean loadCanceled;
        private boolean pendingExtractorSeek;
        private long seekTimeUs;
        private DataSpec dataSpec;
        private long length;

        public ExtractingLoadable(Uri uri, DataSource dataSource, ExtractorHolder extractorHolder, ExtractorOutput extractorOutput, ConditionVariable loadCondition) {
            this.uri = uri;
            this.dataSource = new StatsDataSource(dataSource);
            this.extractorHolder = extractorHolder;
            this.extractorOutput = extractorOutput;
            this.loadCondition = loadCondition;
            this.positionHolder = new PositionHolder();
            this.pendingExtractorSeek = true;
            this.length = -1L;
            this.dataSpec = new DataSpec(uri, this.positionHolder.position, -1L, ExtractorMediaPeriod.this.customCacheKey);
        }

        public void cancelLoad() {
            this.loadCanceled = true;
        }

        public void load() throws IOException, InterruptedException {
            int result = 0;

            while (result == 0 && !this.loadCanceled) {
                DefaultExtractorInput input = null;

                try {
                    long position = this.positionHolder.position;
                    this.dataSpec = new DataSpec(this.uri, position, -1L, ExtractorMediaPeriod.this.customCacheKey);
                    this.length = this.dataSource.open(this.dataSpec);
                    if (this.length != -1L) {
                        this.length += position;
                    }

                    Uri uri = (Uri) Assertions.checkNotNull(this.dataSource.getUri());
                    input = new DefaultExtractorInput(this.dataSource, position, this.length);
                    Extractor extractor = this.extractorHolder.selectExtractor(input, this.extractorOutput, uri);
                    if (this.pendingExtractorSeek) {
                        extractor.seek(position, this.seekTimeUs);
                        this.pendingExtractorSeek = false;
                    }

                    while (result == 0 && !this.loadCanceled) {
                        this.loadCondition.block();
                        result = extractor.read(input, this.positionHolder);
                        if (input.getPosition() > position + ExtractorMediaPeriod.this.continueLoadingCheckIntervalBytes) {
                            position = input.getPosition();
                            this.loadCondition.close();
                            ExtractorMediaPeriod.this.handler.post(ExtractorMediaPeriod.this.onContinueLoadingRequestedRunnable);
                        }
                    }
                } finally {
                    if (result == 1) {
                        result = 0;
                    } else if (input != null) {
                        this.positionHolder.position = input.getPosition();
                    }

                    Util.closeQuietly(this.dataSource);
                }
            }

        }

        private void setLoadPosition(long position, long timeUs) {
            this.positionHolder.position = position;
            this.seekTimeUs = timeUs;
            this.pendingExtractorSeek = true;
        }
    }

    private final class SampleStreamImpl implements SampleStream {
        private final int track;

        public SampleStreamImpl(int track) {
            this.track = track;
        }

        public boolean isReady() {
            return ExtractorMediaPeriod.this.isReady(this.track);
        }

        public void maybeThrowError() throws IOException {
            ExtractorMediaPeriod.this.maybeThrowError();
        }

        public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired) {
            return ExtractorMediaPeriod.this.readData(this.track, formatHolder, buffer, formatRequired);
        }

        public int skipData(long positionUs) {
            return ExtractorMediaPeriod.this.skipData(this.track, positionUs);
        }
    }

    interface Listener {
        void onSourceInfoRefreshed(long var1, boolean var3);
    }
}
