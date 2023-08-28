package com.zj.playerLib;

import com.zj.playerLib.source.ClippingMediaPeriod;
import com.zj.playerLib.source.EmptySampleStream;
import com.zj.playerLib.source.MediaPeriod;
import com.zj.playerLib.source.MediaSource;
import com.zj.playerLib.source.SampleStream;
import com.zj.playerLib.source.TrackGroupArray;
import com.zj.playerLib.trackselection.TrackSelection;
import com.zj.playerLib.trackselection.TrackSelectionArray;
import com.zj.playerLib.trackselection.TrackSelector;
import com.zj.playerLib.trackselection.TrackSelectorResult;
import com.zj.playerLib.upstream.Allocator;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;

final class MediaPeriodHolder {
    private static final String TAG = "MediaPeriodHolder";
    public final MediaPeriod mediaPeriod;
    public final Object uid;
    public final SampleStream[] sampleStreams;
    public final boolean[] mayRetainStreamFlags;
    public boolean prepared;
    public boolean hasEnabledTracks;
    public MediaPeriodInfo info;
    public MediaPeriodHolder next;
    public TrackGroupArray trackGroups;
    public TrackSelectorResult trackSelectorResult;
    private final RendererCapabilities[] rendererCapabilities;
    private final TrackSelector trackSelector;
    private final MediaSource mediaSource;
    private long rendererPositionOffsetUs;
    private TrackSelectorResult periodTrackSelectorResult;

    public MediaPeriodHolder(RendererCapabilities[] rendererCapabilities, long rendererPositionOffsetUs, TrackSelector trackSelector, Allocator allocator, MediaSource mediaSource, MediaPeriodInfo info) {
        this.rendererCapabilities = rendererCapabilities;
        this.rendererPositionOffsetUs = rendererPositionOffsetUs - info.startPositionUs;
        this.trackSelector = trackSelector;
        this.mediaSource = mediaSource;
        this.uid = Assertions.checkNotNull(info.id.periodUid);
        this.info = info;
        this.sampleStreams = new SampleStream[rendererCapabilities.length];
        this.mayRetainStreamFlags = new boolean[rendererCapabilities.length];
        MediaPeriod mediaPeriod = mediaSource.createPeriod(info.id, allocator);
        if (info.id.endPositionUs != -9223372036854775808L) {
            mediaPeriod = new ClippingMediaPeriod(mediaPeriod, true, 0L, info.id.endPositionUs);
        }

        this.mediaPeriod = mediaPeriod;
    }

    public long toRendererTime(long periodTimeUs) {
        return periodTimeUs + this.getRendererOffset();
    }

    public long toPeriodTime(long rendererTimeUs) {
        return rendererTimeUs - this.getRendererOffset();
    }

    public long getRendererOffset() {
        return this.rendererPositionOffsetUs;
    }

    public long getStartPositionRendererTime() {
        return this.info.startPositionUs + this.rendererPositionOffsetUs;
    }

    public boolean isFullyBuffered() {
        return this.prepared && (!this.hasEnabledTracks || this.mediaPeriod.getBufferedPositionUs() == -9223372036854775808L);
    }

    public long getDurationUs() {
        return this.info.durationUs;
    }

    public long getBufferedPositionUs() {
        if (!this.prepared) {
            return this.info.startPositionUs;
        } else {
            long bufferedPositionUs = this.hasEnabledTracks ? this.mediaPeriod.getBufferedPositionUs() : -9223372036854775808L;
            return bufferedPositionUs == -9223372036854775808L ? this.info.durationUs : bufferedPositionUs;
        }
    }

    public long getNextLoadPositionUs() {
        return !this.prepared ? 0L : this.mediaPeriod.getNextLoadPositionUs();
    }

    public void handlePrepared(float playbackSpeed) throws PlaybackException {
        this.prepared = true;
        this.trackGroups = this.mediaPeriod.getTrackGroups();
        this.selectTracks(playbackSpeed);
        long newStartPositionUs = this.applyTrackSelection(this.info.startPositionUs, false);
        this.rendererPositionOffsetUs += this.info.startPositionUs - newStartPositionUs;
        this.info = this.info.copyWithStartPositionUs(newStartPositionUs);
    }

    public void reevaluateBuffer(long rendererPositionUs) {
        if (this.prepared) {
            this.mediaPeriod.reevaluateBuffer(this.toPeriodTime(rendererPositionUs));
        }

    }

    public void continueLoading(long rendererPositionUs) {
        long loadingPeriodPositionUs = this.toPeriodTime(rendererPositionUs);
        this.mediaPeriod.continueLoading(loadingPeriodPositionUs);
    }

    public boolean selectTracks(float playbackSpeed) throws PlaybackException {
        TrackSelectorResult selectorResult = this.trackSelector.selectTracks(this.rendererCapabilities, this.trackGroups);
        if (selectorResult.isEquivalent(this.periodTrackSelectorResult)) {
            return false;
        } else {
            this.trackSelectorResult = selectorResult;
            TrackSelection[] var3 = this.trackSelectorResult.selections.getAll();
            int var4 = var3.length;

            for(int var5 = 0; var5 < var4; ++var5) {
                TrackSelection trackSelection = var3[var5];
                if (trackSelection != null) {
                    trackSelection.onPlaybackSpeed(playbackSpeed);
                }
            }

            return true;
        }
    }

    public long applyTrackSelection(long positionUs, boolean forceRecreateStreams) {
        return this.applyTrackSelection(positionUs, forceRecreateStreams, new boolean[this.rendererCapabilities.length]);
    }

    public long applyTrackSelection(long positionUs, boolean forceRecreateStreams, boolean[] streamResetFlags) {
        for(int i = 0; i < this.trackSelectorResult.length; ++i) {
            this.mayRetainStreamFlags[i] = !forceRecreateStreams && this.trackSelectorResult.isEquivalent(this.periodTrackSelectorResult, i);
        }

        this.disassociateNoSampleRenderersWithEmptySampleStream(this.sampleStreams);
        this.updatePeriodTrackSelectorResult(this.trackSelectorResult);
        TrackSelectionArray trackSelections = this.trackSelectorResult.selections;
        positionUs = this.mediaPeriod.selectTracks(trackSelections.getAll(), this.mayRetainStreamFlags, this.sampleStreams, streamResetFlags, positionUs);
        this.associateNoSampleRenderersWithEmptySampleStream(this.sampleStreams);
        this.hasEnabledTracks = false;

        for(int i = 0; i < this.sampleStreams.length; ++i) {
            if (this.sampleStreams[i] != null) {
                Assertions.checkState(this.trackSelectorResult.isRendererEnabled(i));
                if (this.rendererCapabilities[i].getTrackType() != 6) {
                    this.hasEnabledTracks = true;
                }
            } else {
                Assertions.checkState(trackSelections.get(i) == null);
            }
        }

        return positionUs;
    }

    public void release() {
        this.updatePeriodTrackSelectorResult(null);

        try {
            if (this.info.id.endPositionUs != -9223372036854775808L) {
                this.mediaSource.releasePeriod(((ClippingMediaPeriod)this.mediaPeriod).mediaPeriod);
            } else {
                this.mediaSource.releasePeriod(this.mediaPeriod);
            }
        } catch (RuntimeException var2) {
            Log.e("MediaPeriodHolder", "Period release failed.", var2);
        }

    }

    private void updatePeriodTrackSelectorResult(TrackSelectorResult trackSelectorResult) {
        if (this.periodTrackSelectorResult != null) {
            this.disableTrackSelectionsInResult(this.periodTrackSelectorResult);
        }

        this.periodTrackSelectorResult = trackSelectorResult;
        if (this.periodTrackSelectorResult != null) {
            this.enableTrackSelectionsInResult(this.periodTrackSelectorResult);
        }

    }

    private void enableTrackSelectionsInResult(TrackSelectorResult trackSelectorResult) {
        for(int i = 0; i < trackSelectorResult.length; ++i) {
            boolean rendererEnabled = trackSelectorResult.isRendererEnabled(i);
            TrackSelection trackSelection = trackSelectorResult.selections.get(i);
            if (rendererEnabled && trackSelection != null) {
                trackSelection.enable();
            }
        }

    }

    private void disableTrackSelectionsInResult(TrackSelectorResult trackSelectorResult) {
        for(int i = 0; i < trackSelectorResult.length; ++i) {
            boolean rendererEnabled = trackSelectorResult.isRendererEnabled(i);
            TrackSelection trackSelection = trackSelectorResult.selections.get(i);
            if (rendererEnabled && trackSelection != null) {
                trackSelection.disable();
            }
        }

    }

    private void disassociateNoSampleRenderersWithEmptySampleStream(SampleStream[] sampleStreams) {
        for(int i = 0; i < this.rendererCapabilities.length; ++i) {
            if (this.rendererCapabilities[i].getTrackType() == 6) {
                sampleStreams[i] = null;
            }
        }

    }

    private void associateNoSampleRenderersWithEmptySampleStream(SampleStream[] sampleStreams) {
        for(int i = 0; i < this.rendererCapabilities.length; ++i) {
            if (this.rendererCapabilities[i].getTrackType() == 6 && this.trackSelectorResult.isRendererEnabled(i)) {
                sampleStreams[i] = new EmptySampleStream();
            }
        }

    }
}
