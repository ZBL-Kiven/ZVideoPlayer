package com.zj.playerLib.source;

import androidx.annotation.Nullable;

import com.zj.playerLib.Format;
import com.zj.playerLib.FormatHolder;
import com.zj.playerLib.SeekParameters;
import com.zj.playerLib.decoder.DecoderInputBuffer;
import com.zj.playerLib.source.MediaSourceEventListener.EventDispatcher;
import com.zj.playerLib.trackselection.TrackSelection;
import com.zj.playerLib.upstream.DataSource;
import com.zj.playerLib.upstream.DataSource.Factory;
import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.upstream.LoadErrorHandlingPolicy;
import com.zj.playerLib.upstream.Loader;
import com.zj.playerLib.upstream.Loader.Callback;
import com.zj.playerLib.upstream.Loader.LoadErrorAction;
import com.zj.playerLib.upstream.Loader.Loadable;
import com.zj.playerLib.upstream.StatsDataSource;
import com.zj.playerLib.upstream.TransferListener;
import com.zj.playerLib.util.MimeTypes;
import com.zj.playerLib.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

final class SingleSampleMediaPeriod implements MediaPeriod, Callback<SingleSampleMediaPeriod.SourceLoadable> {
    private static final int INITIAL_SAMPLE_SIZE = 1024;
    private final DataSpec dataSpec;
    private final Factory dataSourceFactory;
    @Nullable
    private final TransferListener transferListener;
    private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private final EventDispatcher eventDispatcher;
    private final TrackGroupArray tracks;
    private final ArrayList<SampleStreamImpl> sampleStreams;
    private final long durationUs;
    final Loader loader;
    final Format format;
    final boolean treatLoadErrorsAsEndOfStream;
    boolean notifiedReadingStarted;
    boolean loadingFinished;
    boolean loadingSucceeded;
    byte[] sampleData;
    int sampleSize;

    public SingleSampleMediaPeriod(DataSpec dataSpec, Factory dataSourceFactory, @Nullable TransferListener transferListener, Format format, long durationUs, LoadErrorHandlingPolicy loadErrorHandlingPolicy, EventDispatcher eventDispatcher, boolean treatLoadErrorsAsEndOfStream) {
        this.dataSpec = dataSpec;
        this.dataSourceFactory = dataSourceFactory;
        this.transferListener = transferListener;
        this.format = format;
        this.durationUs = durationUs;
        this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
        this.eventDispatcher = eventDispatcher;
        this.treatLoadErrorsAsEndOfStream = treatLoadErrorsAsEndOfStream;
        this.tracks = new TrackGroupArray(new TrackGroup(format));
        this.sampleStreams = new ArrayList();
        this.loader = new Loader("Loader:SingleSampleMediaPeriod");
        eventDispatcher.mediaPeriodCreated();
    }

    public void release() {
        this.loader.release();
        this.eventDispatcher.mediaPeriodReleased();
    }

    public void prepare(Callback callback, long positionUs) {
        callback.onPrepared(this);
    }

    public void maybeThrowPrepareError() throws IOException {
    }

    public TrackGroupArray getTrackGroups() {
        return this.tracks;
    }

    public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags, SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
        for(int i = 0; i < selections.length; ++i) {
            if (streams[i] != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
                this.sampleStreams.remove(streams[i]);
                streams[i] = null;
            }

            if (streams[i] == null && selections[i] != null) {
                SampleStreamImpl stream = new SampleStreamImpl();
                this.sampleStreams.add(stream);
                streams[i] = stream;
                streamResetFlags[i] = true;
            }
        }

        return positionUs;
    }

    public void discardBuffer(long positionUs, boolean toKeyframe) {
    }

    public void reevaluateBuffer(long positionUs) {
    }

    public boolean continueLoading(long positionUs) {
        if (!this.loadingFinished && !this.loader.isLoading()) {
            DataSource dataSource = this.dataSourceFactory.createDataSource();
            if (this.transferListener != null) {
                dataSource.addTransferListener(this.transferListener);
            }

            long elapsedRealtimeMs = this.loader.startLoading(new SourceLoadable(this.dataSpec, dataSource), this, this.loadErrorHandlingPolicy.getMinimumLoadableRetryCount(1));
            this.eventDispatcher.loadStarted(this.dataSpec, 1, -1, this.format, 0, null, 0L, this.durationUs, elapsedRealtimeMs);
            return true;
        } else {
            return false;
        }
    }

    public long readDiscontinuity() {
        if (!this.notifiedReadingStarted) {
            this.eventDispatcher.readingStarted();
            this.notifiedReadingStarted = true;
        }

        return -Long.MAX_VALUE;
    }

    public long getNextLoadPositionUs() {
        return !this.loadingFinished && !this.loader.isLoading() ? 0L : -9223372036854775808L;
    }

    public long getBufferedPositionUs() {
        return this.loadingFinished ? -9223372036854775808L : 0L;
    }

    public long seekToUs(long positionUs) {
        for(int i = 0; i < this.sampleStreams.size(); ++i) {
            this.sampleStreams.get(i).reset();
        }

        return positionUs;
    }

    public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
        return positionUs;
    }

    public void onLoadCompleted(SourceLoadable loadable, long elapsedRealtimeMs, long loadDurationMs) {
        this.sampleSize = (int)loadable.dataSource.getBytesRead();
        this.sampleData = loadable.sampleData;
        this.loadingFinished = true;
        this.loadingSucceeded = true;
        this.eventDispatcher.loadCompleted(loadable.dataSpec, loadable.dataSource.getLastOpenedUri(), loadable.dataSource.getLastResponseHeaders(), 1, -1, this.format, 0, null, 0L, this.durationUs, elapsedRealtimeMs, loadDurationMs, this.sampleSize);
    }

    public void onLoadCanceled(SourceLoadable loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {
        this.eventDispatcher.loadCanceled(loadable.dataSpec, loadable.dataSource.getLastOpenedUri(), loadable.dataSource.getLastResponseHeaders(), 1, -1, null, 0, null, 0L, this.durationUs, elapsedRealtimeMs, loadDurationMs, loadable.dataSource.getBytesRead());
    }

    public LoadErrorAction onLoadError(SourceLoadable loadable, long elapsedRealtimeMs, long loadDurationMs, IOException error, int errorCount) {
        long retryDelay = this.loadErrorHandlingPolicy.getRetryDelayMsFor(1, this.durationUs, error, errorCount);
        boolean errorCanBePropagated = retryDelay == -Long.MAX_VALUE || errorCount >= this.loadErrorHandlingPolicy.getMinimumLoadableRetryCount(1);
        LoadErrorAction action;
        if (this.treatLoadErrorsAsEndOfStream && errorCanBePropagated) {
            this.loadingFinished = true;
            action = Loader.DONT_RETRY;
        } else {
            action = retryDelay != -Long.MAX_VALUE ? Loader.createRetryAction(false, retryDelay) : Loader.DONT_RETRY_FATAL;
        }

        this.eventDispatcher.loadError(loadable.dataSpec, loadable.dataSource.getLastOpenedUri(), loadable.dataSource.getLastResponseHeaders(), 1, -1, this.format, 0, null, 0L, this.durationUs, elapsedRealtimeMs, loadDurationMs, loadable.dataSource.getBytesRead(), error, !action.isRetry());
        return action;
    }

    static final class SourceLoadable implements Loadable {
        public final DataSpec dataSpec;
        private final StatsDataSource dataSource;
        private byte[] sampleData;

        public SourceLoadable(DataSpec dataSpec, DataSource dataSource) {
            this.dataSpec = dataSpec;
            this.dataSource = new StatsDataSource(dataSource);
        }

        public void cancelLoad() {
        }

        public void load() throws IOException, InterruptedException {
            this.dataSource.resetBytesRead();

            try {
                this.dataSource.open(this.dataSpec);

                int sampleSize;
                for(int result = 0; result != -1; result = this.dataSource.read(this.sampleData, sampleSize, this.sampleData.length - sampleSize)) {
                    sampleSize = (int)this.dataSource.getBytesRead();
                    if (this.sampleData == null) {
                        this.sampleData = new byte[1024];
                    } else if (sampleSize == this.sampleData.length) {
                        this.sampleData = Arrays.copyOf(this.sampleData, this.sampleData.length * 2);
                    }
                }
            } finally {
                Util.closeQuietly(this.dataSource);
            }

        }
    }

    private final class SampleStreamImpl implements SampleStream {
        private static final int STREAM_STATE_SEND_FORMAT = 0;
        private static final int STREAM_STATE_SEND_SAMPLE = 1;
        private static final int STREAM_STATE_END_OF_STREAM = 2;
        private int streamState;
        private boolean notifiedDownstreamFormat;

        private SampleStreamImpl() {
        }

        public void reset() {
            if (this.streamState == 2) {
                this.streamState = 1;
            }

        }

        public boolean isReady() {
            return SingleSampleMediaPeriod.this.loadingFinished;
        }

        public void maybeThrowError() throws IOException {
            if (!SingleSampleMediaPeriod.this.treatLoadErrorsAsEndOfStream) {
                SingleSampleMediaPeriod.this.loader.maybeThrowError();
            }

        }

        public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean requireFormat) {
            this.maybeNotifyDownstreamFormat();
            if (this.streamState == 2) {
                buffer.addFlag(4);
                return -4;
            } else if (!requireFormat && this.streamState != 0) {
                if (SingleSampleMediaPeriod.this.loadingFinished) {
                    if (SingleSampleMediaPeriod.this.loadingSucceeded) {
                        buffer.timeUs = 0L;
                        buffer.addFlag(1);
                        buffer.ensureSpaceForWrite(SingleSampleMediaPeriod.this.sampleSize);
                        buffer.data.put(SingleSampleMediaPeriod.this.sampleData, 0, SingleSampleMediaPeriod.this.sampleSize);
                    } else {
                        buffer.addFlag(4);
                    }

                    this.streamState = 2;
                    return -4;
                } else {
                    return -3;
                }
            } else {
                formatHolder.format = SingleSampleMediaPeriod.this.format;
                this.streamState = 1;
                return -5;
            }
        }

        public int skipData(long positionUs) {
            this.maybeNotifyDownstreamFormat();
            if (positionUs > 0L && this.streamState != 2) {
                this.streamState = 2;
                return 1;
            } else {
                return 0;
            }
        }

        private void maybeNotifyDownstreamFormat() {
            if (!this.notifiedDownstreamFormat) {
                SingleSampleMediaPeriod.this.eventDispatcher.downstreamFormatChanged(MimeTypes.getTrackType(SingleSampleMediaPeriod.this.format.sampleMimeType), SingleSampleMediaPeriod.this.format, 0, null, 0L);
                this.notifiedDownstreamFormat = true;
            }

        }
    }
}
