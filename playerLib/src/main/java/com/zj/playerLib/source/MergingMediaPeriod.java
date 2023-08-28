package com.zj.playerLib.source;

import com.zj.playerLib.SeekParameters;
import com.zj.playerLib.source.MediaPeriod.Callback;
import com.zj.playerLib.trackselection.TrackSelection;
import com.zj.playerLib.util.Assertions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;

final class MergingMediaPeriod implements MediaPeriod, Callback {
    public final MediaPeriod[] periods;
    private final IdentityHashMap<SampleStream, Integer> streamPeriodIndices;
    private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
    private final ArrayList<MediaPeriod> childrenPendingPreparation;
    private Callback callback;
    private TrackGroupArray trackGroups;
    private MediaPeriod[] enabledPeriods;
    private SequenceableLoader compositeSequenceableLoader;

    public MergingMediaPeriod(CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory, MediaPeriod... periods) {
        this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
        this.periods = periods;
        this.childrenPendingPreparation = new ArrayList();
        this.compositeSequenceableLoader = compositeSequenceableLoaderFactory.createCompositeSequenceableLoader();
        this.streamPeriodIndices = new IdentityHashMap();
    }

    public void prepare(Callback callback, long positionUs) {
        this.callback = callback;
        Collections.addAll(this.childrenPendingPreparation, this.periods);
        MediaPeriod[] var4 = this.periods;
        int var5 = var4.length;

        for(int var6 = 0; var6 < var5; ++var6) {
            MediaPeriod period = var4[var6];
            period.prepare(this, positionUs);
        }

    }

    public void maybeThrowPrepareError() throws IOException {
        MediaPeriod[] var1 = this.periods;
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            MediaPeriod period = var1[var3];
            period.maybeThrowPrepareError();
        }

    }

    public TrackGroupArray getTrackGroups() {
        return this.trackGroups;
    }

    public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags, SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
        int[] streamChildIndices = new int[selections.length];
        int[] selectionChildIndices = new int[selections.length];

        for(int i = 0; i < selections.length; ++i) {
            streamChildIndices[i] = streams[i] == null ? -1 : this.streamPeriodIndices.get(streams[i]);
            selectionChildIndices[i] = -1;
            if (selections[i] != null) {
                TrackGroup trackGroup = selections[i].getTrackGroup();

                for(int j = 0; j < this.periods.length; ++j) {
                    if (this.periods[j].getTrackGroups().indexOf(trackGroup) != -1) {
                        selectionChildIndices[i] = j;
                        break;
                    }
                }
            }
        }

        this.streamPeriodIndices.clear();
        SampleStream[] newStreams = new SampleStream[selections.length];
        SampleStream[] childStreams = new SampleStream[selections.length];
        TrackSelection[] childSelections = new TrackSelection[selections.length];
        ArrayList<MediaPeriod> enabledPeriodsList = new ArrayList(this.periods.length);

        for(int i = 0; i < this.periods.length; ++i) {
            for(int j = 0; j < selections.length; ++j) {
                childStreams[j] = streamChildIndices[j] == i ? streams[j] : null;
                childSelections[j] = selectionChildIndices[j] == i ? selections[j] : null;
            }

            long selectPositionUs = this.periods[i].selectTracks(childSelections, mayRetainStreamFlags, childStreams, streamResetFlags, positionUs);
            if (i == 0) {
                positionUs = selectPositionUs;
            } else if (selectPositionUs != positionUs) {
                throw new IllegalStateException("Children enabled at different positions.");
            }

            boolean periodEnabled = false;

            for(int j = 0; j < selections.length; ++j) {
                if (selectionChildIndices[j] == i) {
                    Assertions.checkState(childStreams[j] != null);
                    newStreams[j] = childStreams[j];
                    periodEnabled = true;
                    this.streamPeriodIndices.put(childStreams[j], i);
                } else if (streamChildIndices[j] == i) {
                    Assertions.checkState(childStreams[j] == null);
                }
            }

            if (periodEnabled) {
                enabledPeriodsList.add(this.periods[i]);
            }
        }

        System.arraycopy(newStreams, 0, streams, 0, newStreams.length);
        this.enabledPeriods = new MediaPeriod[enabledPeriodsList.size()];
        enabledPeriodsList.toArray(this.enabledPeriods);
        this.compositeSequenceableLoader = this.compositeSequenceableLoaderFactory.createCompositeSequenceableLoader(this.enabledPeriods);
        return positionUs;
    }

    public void discardBuffer(long positionUs, boolean toKeyframe) {
        MediaPeriod[] var4 = this.enabledPeriods;
        int var5 = var4.length;

        for(int var6 = 0; var6 < var5; ++var6) {
            MediaPeriod period = var4[var6];
            period.discardBuffer(positionUs, toKeyframe);
        }

    }

    public void reevaluateBuffer(long positionUs) {
        this.compositeSequenceableLoader.reevaluateBuffer(positionUs);
    }

    public boolean continueLoading(long positionUs) {
        if (this.childrenPendingPreparation.isEmpty()) {
            return this.compositeSequenceableLoader.continueLoading(positionUs);
        } else {
            int childrenPendingPreparationSize = this.childrenPendingPreparation.size();

            for(int i = 0; i < childrenPendingPreparationSize; ++i) {
                this.childrenPendingPreparation.get(i).continueLoading(positionUs);
            }

            return false;
        }
    }

    public long getNextLoadPositionUs() {
        return this.compositeSequenceableLoader.getNextLoadPositionUs();
    }

    public long readDiscontinuity() {
        long positionUs = this.periods[0].readDiscontinuity();

        for(int i = 1; i < this.periods.length; ++i) {
            if (this.periods[i].readDiscontinuity() != -Long.MAX_VALUE) {
                throw new IllegalStateException("Child reported discontinuity.");
            }
        }

        if (positionUs != -Long.MAX_VALUE) {
            MediaPeriod[] var7 = this.enabledPeriods;
            int var4 = var7.length;

            for(int var5 = 0; var5 < var4; ++var5) {
                MediaPeriod enabledPeriod = var7[var5];
                if (enabledPeriod != this.periods[0] && enabledPeriod.seekToUs(positionUs) != positionUs) {
                    throw new IllegalStateException("Unexpected child seekToUs result.");
                }
            }
        }

        return positionUs;
    }

    public long getBufferedPositionUs() {
        return this.compositeSequenceableLoader.getBufferedPositionUs();
    }

    public long seekToUs(long positionUs) {
        positionUs = this.enabledPeriods[0].seekToUs(positionUs);

        for(int i = 1; i < this.enabledPeriods.length; ++i) {
            if (this.enabledPeriods[i].seekToUs(positionUs) != positionUs) {
                throw new IllegalStateException("Unexpected child seekToUs result.");
            }
        }

        return positionUs;
    }

    public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
        return this.enabledPeriods[0].getAdjustedSeekPositionUs(positionUs, seekParameters);
    }

    public void onPrepared(MediaPeriod preparedPeriod) {
        this.childrenPendingPreparation.remove(preparedPeriod);
        if (this.childrenPendingPreparation.isEmpty()) {
            int totalTrackGroupCount = 0;
            MediaPeriod[] var3 = this.periods;
            int trackGroupIndex = var3.length;

            for(int var5 = 0; var5 < trackGroupIndex; ++var5) {
                MediaPeriod period = var3[var5];
                totalTrackGroupCount += period.getTrackGroups().length;
            }

            TrackGroup[] trackGroupArray = new TrackGroup[totalTrackGroupCount];
            trackGroupIndex = 0;
            MediaPeriod[] var13 = this.periods;
            int var14 = var13.length;

            for(int var7 = 0; var7 < var14; ++var7) {
                MediaPeriod period = var13[var7];
                TrackGroupArray periodTrackGroups = period.getTrackGroups();
                int periodTrackGroupCount = periodTrackGroups.length;

                for(int j = 0; j < periodTrackGroupCount; ++j) {
                    trackGroupArray[trackGroupIndex++] = periodTrackGroups.get(j);
                }
            }

            this.trackGroups = new TrackGroupArray(trackGroupArray);
            this.callback.onPrepared(this);
        }
    }

    public void onContinueLoadingRequested(MediaPeriod ignored) {
        this.callback.onContinueLoadingRequested(this);
    }
}
