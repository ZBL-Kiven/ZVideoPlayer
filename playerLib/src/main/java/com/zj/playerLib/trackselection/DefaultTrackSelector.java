//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.trackselection;

import android.content.Context;
import android.graphics.Point;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zj.playerLib.PlaybackException;
import com.zj.playerLib.Format;
import com.zj.playerLib.RendererConfiguration;
import com.zj.playerLib.source.TrackGroup;
import com.zj.playerLib.source.TrackGroupArray;
import com.zj.playerLib.trackselection.TrackSelection.Factory;
import com.zj.playerLib.upstream.BandwidthMeter;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultTrackSelector extends MappingTrackSelector {
    private static final float FRACTION_TO_CONSIDER_FULLSCREEN = 0.98F;
    private static final int[] NO_TRACKS = new int[0];
    private static final int WITHIN_RENDERER_CAPABILITIES_BONUS = 1000;
    private final Factory adaptiveTrackSelectionFactory;
    private final AtomicReference<Parameters> parametersReference;

    public DefaultTrackSelector() {
        this((Factory)(new AdaptiveTrackSelection.Factory()));
    }

    /** @deprecated */
    @Deprecated
    public DefaultTrackSelector(BandwidthMeter bandwidthMeter) {
        this((Factory)(new AdaptiveTrackSelection.Factory(bandwidthMeter)));
    }

    public DefaultTrackSelector(Factory adaptiveTrackSelectionFactory) {
        this.adaptiveTrackSelectionFactory = adaptiveTrackSelectionFactory;
        this.parametersReference = new AtomicReference(Parameters.DEFAULT);
    }

    public void setParameters(Parameters parameters) {
        Assertions.checkNotNull(parameters);
        if (!((Parameters)this.parametersReference.getAndSet(parameters)).equals(parameters)) {
            this.invalidate();
        }

    }

    public void setParameters(ParametersBuilder parametersBuilder) {
        this.setParameters(parametersBuilder.build());
    }

    public Parameters getParameters() {
        return (Parameters)this.parametersReference.get();
    }

    public ParametersBuilder buildUponParameters() {
        return this.getParameters().buildUpon();
    }

    /** @deprecated */
    @Deprecated
    public final void setRendererDisabled(int rendererIndex, boolean disabled) {
        this.setParameters(this.buildUponParameters().setRendererDisabled(rendererIndex, disabled));
    }

    /** @deprecated */
    @Deprecated
    public final boolean getRendererDisabled(int rendererIndex) {
        return this.getParameters().getRendererDisabled(rendererIndex);
    }

    /** @deprecated */
    @Deprecated
    public final void setSelectionOverride(int rendererIndex, TrackGroupArray groups, SelectionOverride override) {
        this.setParameters(this.buildUponParameters().setSelectionOverride(rendererIndex, groups, override));
    }

    /** @deprecated */
    @Deprecated
    public final boolean hasSelectionOverride(int rendererIndex, TrackGroupArray groups) {
        return this.getParameters().hasSelectionOverride(rendererIndex, groups);
    }

    /** @deprecated */
    @Deprecated
    @Nullable
    public final DefaultTrackSelector.SelectionOverride getSelectionOverride(int rendererIndex, TrackGroupArray groups) {
        return this.getParameters().getSelectionOverride(rendererIndex, groups);
    }

    /** @deprecated */
    @Deprecated
    public final void clearSelectionOverride(int rendererIndex, TrackGroupArray groups) {
        this.setParameters(this.buildUponParameters().clearSelectionOverride(rendererIndex, groups));
    }

    /** @deprecated */
    @Deprecated
    public final void clearSelectionOverrides(int rendererIndex) {
        this.setParameters(this.buildUponParameters().clearSelectionOverrides(rendererIndex));
    }

    /** @deprecated */
    @Deprecated
    public final void clearSelectionOverrides() {
        this.setParameters(this.buildUponParameters().clearSelectionOverrides());
    }

    /** @deprecated */
    @Deprecated
    public void setTunnelingAudioSessionId(int tunnelingAudioSessionId) {
        this.setParameters(this.buildUponParameters().setTunnelingAudioSessionId(tunnelingAudioSessionId));
    }

    protected final Pair<RendererConfiguration[], TrackSelection[]> selectTracks(MappedTrackInfo mappedTrackInfo, int[][][] rendererFormatSupports, int[] rendererMixedMimeTypeAdaptationSupports) throws PlaybackException {
        Parameters params = (Parameters)this.parametersReference.get();
        int rendererCount = mappedTrackInfo.getRendererCount();
        TrackSelection[] rendererTrackSelections = this.selectAllTracks(mappedTrackInfo, rendererFormatSupports, rendererMixedMimeTypeAdaptationSupports, params);

        for(int i = 0; i < rendererCount; ++i) {
            if (params.getRendererDisabled(i)) {
                rendererTrackSelections[i] = null;
            } else {
                TrackGroupArray rendererTrackGroups = mappedTrackInfo.getTrackGroups(i);
                if (params.hasSelectionOverride(i, rendererTrackGroups)) {
                    SelectionOverride override = params.getSelectionOverride(i, rendererTrackGroups);
                    if (override == null) {
                        rendererTrackSelections[i] = null;
                    } else if (override.length == 1) {
                        rendererTrackSelections[i] = new FixedTrackSelection(rendererTrackGroups.get(override.groupIndex), override.tracks[0]);
                    } else {
                        rendererTrackSelections[i] = ((Factory)Assertions.checkNotNull(this.adaptiveTrackSelectionFactory)).createTrackSelection(rendererTrackGroups.get(override.groupIndex), this.getBandwidthMeter(), override.tracks);
                    }
                }
            }
        }

        RendererConfiguration[] rendererConfigurations = new RendererConfiguration[rendererCount];

        for(int i = 0; i < rendererCount; ++i) {
            boolean forceRendererDisabled = params.getRendererDisabled(i);
            boolean rendererEnabled = !forceRendererDisabled && (mappedTrackInfo.getRendererType(i) == 6 || rendererTrackSelections[i] != null);
            rendererConfigurations[i] = rendererEnabled ? RendererConfiguration.DEFAULT : null;
        }

        maybeConfigureRenderersForTunneling(mappedTrackInfo, rendererFormatSupports, rendererConfigurations, rendererTrackSelections, params.tunnelingAudioSessionId);
        return Pair.create(rendererConfigurations, rendererTrackSelections);
    }

    protected TrackSelection[] selectAllTracks(MappedTrackInfo mappedTrackInfo, int[][][] rendererFormatSupports, int[] rendererMixedMimeTypeAdaptationSupports, Parameters params) throws PlaybackException {
        int rendererCount = mappedTrackInfo.getRendererCount();
        TrackSelection[] rendererTrackSelections = new TrackSelection[rendererCount];
        boolean seenVideoRendererWithMappedTracks = false;
        boolean selectedVideoTracks = false;

        for(int i = 0; i < rendererCount; ++i) {
            if (2 == mappedTrackInfo.getRendererType(i)) {
                if (!selectedVideoTracks) {
                    rendererTrackSelections[i] = this.selectVideoTrack(mappedTrackInfo.getTrackGroups(i), rendererFormatSupports[i], rendererMixedMimeTypeAdaptationSupports[i], params, this.adaptiveTrackSelectionFactory);
                    selectedVideoTracks = rendererTrackSelections[i] != null;
                }

                seenVideoRendererWithMappedTracks |= mappedTrackInfo.getTrackGroups(i).length > 0;
            }
        }

        AudioTrackScore selectedAudioTrackScore = null;
        int selectedAudioRendererIndex = -1;
        int selectedTextTrackScore = -2147483648;
        int selectedTextRendererIndex = -1;

        for(int i = 0; i < rendererCount; ++i) {
            int trackType = mappedTrackInfo.getRendererType(i);
            switch(trackType) {
            case 1:
                Pair<TrackSelection, AudioTrackScore> audioSelection = this.selectAudioTrack(mappedTrackInfo.getTrackGroups(i), rendererFormatSupports[i], rendererMixedMimeTypeAdaptationSupports[i], params, seenVideoRendererWithMappedTracks ? null : this.adaptiveTrackSelectionFactory);
                if (audioSelection != null && (selectedAudioTrackScore == null || ((AudioTrackScore)audioSelection.second).compareTo(selectedAudioTrackScore) > 0)) {
                    if (selectedAudioRendererIndex != -1) {
                        rendererTrackSelections[selectedAudioRendererIndex] = null;
                    }

                    rendererTrackSelections[i] = (TrackSelection)audioSelection.first;
                    selectedAudioTrackScore = (AudioTrackScore)audioSelection.second;
                    selectedAudioRendererIndex = i;
                }
            case 2:
                break;
            case 3:
                Pair<TrackSelection, Integer> textSelection = this.selectTextTrack(mappedTrackInfo.getTrackGroups(i), rendererFormatSupports[i], params);
                if (textSelection != null && (Integer)textSelection.second > selectedTextTrackScore) {
                    if (selectedTextRendererIndex != -1) {
                        rendererTrackSelections[selectedTextRendererIndex] = null;
                    }

                    rendererTrackSelections[i] = (TrackSelection)textSelection.first;
                    selectedTextTrackScore = (Integer)textSelection.second;
                    selectedTextRendererIndex = i;
                }
                break;
            default:
                rendererTrackSelections[i] = this.selectOtherTrack(trackType, mappedTrackInfo.getTrackGroups(i), rendererFormatSupports[i], params);
            }
        }

        return rendererTrackSelections;
    }

    @Nullable
    protected TrackSelection selectVideoTrack(TrackGroupArray groups, int[][] formatSupports, int mixedMimeTypeAdaptationSupports, Parameters params, @Nullable Factory adaptiveTrackSelectionFactory) throws PlaybackException {
        TrackSelection selection = null;
        if (!params.forceHighestSupportedBitrate && !params.forceLowestBitrate && adaptiveTrackSelectionFactory != null) {
            selection = selectAdaptiveVideoTrack(groups, formatSupports, mixedMimeTypeAdaptationSupports, params, adaptiveTrackSelectionFactory, this.getBandwidthMeter());
        }

        if (selection == null) {
            selection = selectFixedVideoTrack(groups, formatSupports, params);
        }

        return selection;
    }

    @Nullable
    private static TrackSelection selectAdaptiveVideoTrack(TrackGroupArray groups, int[][] formatSupport, int mixedMimeTypeAdaptationSupports, Parameters params, Factory adaptiveTrackSelectionFactory, BandwidthMeter bandwidthMeter) throws PlaybackException {
        int requiredAdaptiveSupport = params.allowNonSeamlessAdaptiveness ? 24 : 16;
        boolean allowMixedMimeTypes = params.allowMixedMimeAdaptiveness && (mixedMimeTypeAdaptationSupports & requiredAdaptiveSupport) != 0;

        for(int i = 0; i < groups.length; ++i) {
            TrackGroup group = groups.get(i);
            int[] adaptiveTracks = getAdaptiveVideoTracksForGroup(group, formatSupport[i], allowMixedMimeTypes, requiredAdaptiveSupport, params.maxVideoWidth, params.maxVideoHeight, params.maxVideoFrameRate, params.maxVideoBitrate, params.viewportWidth, params.viewportHeight, params.viewportOrientationMayChange);
            if (adaptiveTracks.length > 0) {
                return ((Factory)Assertions.checkNotNull(adaptiveTrackSelectionFactory)).createTrackSelection(group, bandwidthMeter, adaptiveTracks);
            }
        }

        return null;
    }

    private static int[] getAdaptiveVideoTracksForGroup(TrackGroup group, int[] formatSupport, boolean allowMixedMimeTypes, int requiredAdaptiveSupport, int maxVideoWidth, int maxVideoHeight, int maxVideoFrameRate, int maxVideoBitrate, int viewportWidth, int viewportHeight, boolean viewportOrientationMayChange) {
        if (group.length < 2) {
            return NO_TRACKS;
        } else {
            List<Integer> selectedTrackIndices = getViewportFilteredTrackIndices(group, viewportWidth, viewportHeight, viewportOrientationMayChange);
            if (selectedTrackIndices.size() < 2) {
                return NO_TRACKS;
            } else {
                String selectedMimeType = null;
                if (!allowMixedMimeTypes) {
                    HashSet<String> seenMimeTypes = new HashSet();
                    int selectedMimeTypeTrackCount = 0;

                    for(int i = 0; i < selectedTrackIndices.size(); ++i) {
                        int trackIndex = (Integer)selectedTrackIndices.get(i);
                        String sampleMimeType = group.getFormat(trackIndex).sampleMimeType;
                        if (seenMimeTypes.add(sampleMimeType)) {
                            int countForMimeType = getAdaptiveVideoTrackCountForMimeType(group, formatSupport, requiredAdaptiveSupport, sampleMimeType, maxVideoWidth, maxVideoHeight, maxVideoFrameRate, maxVideoBitrate, selectedTrackIndices);
                            if (countForMimeType > selectedMimeTypeTrackCount) {
                                selectedMimeType = sampleMimeType;
                                selectedMimeTypeTrackCount = countForMimeType;
                            }
                        }
                    }
                }

                filterAdaptiveVideoTrackCountForMimeType(group, formatSupport, requiredAdaptiveSupport, selectedMimeType, maxVideoWidth, maxVideoHeight, maxVideoFrameRate, maxVideoBitrate, selectedTrackIndices);
                return selectedTrackIndices.size() < 2 ? NO_TRACKS : Util.toArray(selectedTrackIndices);
            }
        }
    }

    private static int getAdaptiveVideoTrackCountForMimeType(TrackGroup group, int[] formatSupport, int requiredAdaptiveSupport, @Nullable String mimeType, int maxVideoWidth, int maxVideoHeight, int maxVideoFrameRate, int maxVideoBitrate, List<Integer> selectedTrackIndices) {
        int adaptiveTrackCount = 0;

        for(int i = 0; i < selectedTrackIndices.size(); ++i) {
            int trackIndex = (Integer)selectedTrackIndices.get(i);
            if (isSupportedAdaptiveVideoTrack(group.getFormat(trackIndex), mimeType, formatSupport[trackIndex], requiredAdaptiveSupport, maxVideoWidth, maxVideoHeight, maxVideoFrameRate, maxVideoBitrate)) {
                ++adaptiveTrackCount;
            }
        }

        return adaptiveTrackCount;
    }

    private static void filterAdaptiveVideoTrackCountForMimeType(TrackGroup group, int[] formatSupport, int requiredAdaptiveSupport, @Nullable String mimeType, int maxVideoWidth, int maxVideoHeight, int maxVideoFrameRate, int maxVideoBitrate, List<Integer> selectedTrackIndices) {
        for(int i = selectedTrackIndices.size() - 1; i >= 0; --i) {
            int trackIndex = (Integer)selectedTrackIndices.get(i);
            if (!isSupportedAdaptiveVideoTrack(group.getFormat(trackIndex), mimeType, formatSupport[trackIndex], requiredAdaptiveSupport, maxVideoWidth, maxVideoHeight, maxVideoFrameRate, maxVideoBitrate)) {
                selectedTrackIndices.remove(i);
            }
        }

    }

    private static boolean isSupportedAdaptiveVideoTrack(Format format, @Nullable String mimeType, int formatSupport, int requiredAdaptiveSupport, int maxVideoWidth, int maxVideoHeight, int maxVideoFrameRate, int maxVideoBitrate) {
        return isSupported(formatSupport, false) && (formatSupport & requiredAdaptiveSupport) != 0 && (mimeType == null || Util.areEqual(format.sampleMimeType, mimeType)) && (format.width == -1 || format.width <= maxVideoWidth) && (format.height == -1 || format.height <= maxVideoHeight) && (format.frameRate == -1.0F || format.frameRate <= (float)maxVideoFrameRate) && (format.bitrate == -1 || format.bitrate <= maxVideoBitrate);
    }

    @Nullable
    private static TrackSelection selectFixedVideoTrack(TrackGroupArray groups, int[][] formatSupports, Parameters params) {
        TrackGroup selectedGroup = null;
        int selectedTrackIndex = 0;
        int selectedTrackScore = 0;
        int selectedBitrate = -1;
        int selectedPixelCount = -1;

        for(int groupIndex = 0; groupIndex < groups.length; ++groupIndex) {
            TrackGroup trackGroup = groups.get(groupIndex);
            List<Integer> selectedTrackIndices = getViewportFilteredTrackIndices(trackGroup, params.viewportWidth, params.viewportHeight, params.viewportOrientationMayChange);
            int[] trackFormatSupport = formatSupports[groupIndex];

            for(int trackIndex = 0; trackIndex < trackGroup.length; ++trackIndex) {
                if (isSupported(trackFormatSupport[trackIndex], params.exceedRendererCapabilitiesIfNecessary)) {
                    Format format = trackGroup.getFormat(trackIndex);
                    boolean isWithinConstraints = selectedTrackIndices.contains(trackIndex) && (format.width == -1 || format.width <= params.maxVideoWidth) && (format.height == -1 || format.height <= params.maxVideoHeight) && (format.frameRate == -1.0F || format.frameRate <= (float)params.maxVideoFrameRate) && (format.bitrate == -1 || format.bitrate <= params.maxVideoBitrate);
                    if (isWithinConstraints || params.exceedVideoConstraintsIfNecessary) {
                        int trackScore = isWithinConstraints ? 2 : 1;
                        boolean isWithinCapabilities = isSupported(trackFormatSupport[trackIndex], false);
                        if (isWithinCapabilities) {
                            trackScore += 1000;
                        }

                        boolean selectTrack = trackScore > selectedTrackScore;
                        if (trackScore == selectedTrackScore) {
                            if (params.forceLowestBitrate) {
                                selectTrack = compareFormatValues(format.bitrate, selectedBitrate) < 0;
                            } else {
                                int formatPixelCount = format.getPixelCount();
                                int comparisonResult = formatPixelCount != selectedPixelCount ? compareFormatValues(formatPixelCount, selectedPixelCount) : compareFormatValues(format.bitrate, selectedBitrate);
                                selectTrack = isWithinCapabilities && isWithinConstraints ? comparisonResult > 0 : comparisonResult < 0;
                            }
                        }

                        if (selectTrack) {
                            selectedGroup = trackGroup;
                            selectedTrackIndex = trackIndex;
                            selectedTrackScore = trackScore;
                            selectedBitrate = format.bitrate;
                            selectedPixelCount = format.getPixelCount();
                        }
                    }
                }
            }
        }

        return selectedGroup == null ? null : new FixedTrackSelection(selectedGroup, selectedTrackIndex);
    }

    @Nullable
    protected Pair<TrackSelection, AudioTrackScore> selectAudioTrack(TrackGroupArray groups, int[][] formatSupports, int mixedMimeTypeAdaptationSupports, Parameters params, @Nullable Factory adaptiveTrackSelectionFactory) throws PlaybackException {
        int selectedTrackIndex = -1;
        int selectedGroupIndex = -1;
        AudioTrackScore selectedTrackScore = null;

        int[] adaptiveTracks;
        for(int groupIndex = 0; groupIndex < groups.length; ++groupIndex) {
            TrackGroup trackGroup = groups.get(groupIndex);
            adaptiveTracks = formatSupports[groupIndex];

            for(int trackIndex = 0; trackIndex < trackGroup.length; ++trackIndex) {
                if (isSupported(adaptiveTracks[trackIndex], params.exceedRendererCapabilitiesIfNecessary)) {
                    Format format = trackGroup.getFormat(trackIndex);
                    AudioTrackScore trackScore = new AudioTrackScore(format, params, adaptiveTracks[trackIndex]);
                    if (selectedTrackScore == null || trackScore.compareTo(selectedTrackScore) > 0) {
                        selectedGroupIndex = groupIndex;
                        selectedTrackIndex = trackIndex;
                        selectedTrackScore = trackScore;
                    }
                }
            }
        }

        if (selectedGroupIndex == -1) {
            return null;
        } else {
            TrackGroup selectedGroup = groups.get(selectedGroupIndex);
            TrackSelection selection = null;
            if (!params.forceHighestSupportedBitrate && !params.forceLowestBitrate && adaptiveTrackSelectionFactory != null) {
                adaptiveTracks = getAdaptiveAudioTracks(selectedGroup, formatSupports[selectedGroupIndex], params.allowMixedMimeAdaptiveness);
                if (adaptiveTracks.length > 0) {
                    selection = adaptiveTrackSelectionFactory.createTrackSelection(selectedGroup, this.getBandwidthMeter(), adaptiveTracks);
                }
            }

            if (selection == null) {
                selection = new FixedTrackSelection(selectedGroup, selectedTrackIndex);
            }

            return Pair.create(selection, Assertions.checkNotNull(selectedTrackScore));
        }
    }

    private static int[] getAdaptiveAudioTracks(TrackGroup group, int[] formatSupport, boolean allowMixedMimeTypes) {
        int selectedConfigurationTrackCount = 0;
        AudioConfigurationTuple selectedConfiguration = null;
        HashSet<AudioConfigurationTuple> seenConfigurationTuples = new HashSet();

        for(int i = 0; i < group.length; ++i) {
            Format format = group.getFormat(i);
            AudioConfigurationTuple configuration = new AudioConfigurationTuple(format.channelCount, format.sampleRate, allowMixedMimeTypes ? null : format.sampleMimeType);
            if (seenConfigurationTuples.add(configuration)) {
                int configurationCount = getAdaptiveAudioTrackCount(group, formatSupport, configuration);
                if (configurationCount > selectedConfigurationTrackCount) {
                    selectedConfiguration = configuration;
                    selectedConfigurationTrackCount = configurationCount;
                }
            }
        }

        if (selectedConfigurationTrackCount > 1) {
            int[] adaptiveIndices = new int[selectedConfigurationTrackCount];
            int index = 0;

            for(int i = 0; i < group.length; ++i) {
                if (isSupportedAdaptiveAudioTrack(group.getFormat(i), formatSupport[i], (AudioConfigurationTuple)Assertions.checkNotNull(selectedConfiguration))) {
                    adaptiveIndices[index++] = i;
                }
            }

            return adaptiveIndices;
        } else {
            return NO_TRACKS;
        }
    }

    private static int getAdaptiveAudioTrackCount(TrackGroup group, int[] formatSupport, AudioConfigurationTuple configuration) {
        int count = 0;

        for(int i = 0; i < group.length; ++i) {
            if (isSupportedAdaptiveAudioTrack(group.getFormat(i), formatSupport[i], configuration)) {
                ++count;
            }
        }

        return count;
    }

    private static boolean isSupportedAdaptiveAudioTrack(Format format, int formatSupport, AudioConfigurationTuple configuration) {
        return isSupported(formatSupport, false) && format.channelCount == configuration.channelCount && format.sampleRate == configuration.sampleRate && (configuration.mimeType == null || TextUtils.equals(configuration.mimeType, format.sampleMimeType));
    }

    @Nullable
    protected Pair<TrackSelection, Integer> selectTextTrack(TrackGroupArray groups, int[][] formatSupport, Parameters params) throws PlaybackException {
        TrackGroup selectedGroup = null;
        int selectedTrackIndex = 0;
        int selectedTrackScore = 0;

        for(int groupIndex = 0; groupIndex < groups.length; ++groupIndex) {
            TrackGroup trackGroup = groups.get(groupIndex);
            int[] trackFormatSupport = formatSupport[groupIndex];

            for(int trackIndex = 0; trackIndex < trackGroup.length; ++trackIndex) {
                if (isSupported(trackFormatSupport[trackIndex], params.exceedRendererCapabilitiesIfNecessary)) {
                    Format format = trackGroup.getFormat(trackIndex);
                    int maskedSelectionFlags = format.selectionFlags & ~params.disabledTextTrackSelectionFlags;
                    boolean isDefault = (maskedSelectionFlags & 1) != 0;
                    boolean isForced = (maskedSelectionFlags & 2) != 0;
                    boolean preferredLanguageFound = formatHasLanguage(format, params.preferredTextLanguage);
                    int trackScore;
                    if (preferredLanguageFound || params.selectUndeterminedTextLanguage && formatHasNoLanguage(format)) {
                        if (isDefault) {
                            trackScore = 8;
                        } else if (!isForced) {
                            trackScore = 6;
                        } else {
                            trackScore = 4;
                        }
                        trackScore = trackScore + (preferredLanguageFound ? 1 : 0);
                    } else if (isDefault) {
                        trackScore = 3;
                    } else {
                        if (!isForced) {
                            continue;
                        }

                        if (formatHasLanguage(format, params.preferredAudioLanguage)) {
                            trackScore = 2;
                        } else {
                            trackScore = 1;
                        }
                    }

                    if (isSupported(trackFormatSupport[trackIndex], false)) {
                        trackScore += 1000;
                    }

                    if (trackScore > selectedTrackScore) {
                        selectedGroup = trackGroup;
                        selectedTrackIndex = trackIndex;
                        selectedTrackScore = trackScore;
                    }
                }
            }
        }

        return selectedGroup == null ? null : Pair.create(new FixedTrackSelection(selectedGroup, selectedTrackIndex), selectedTrackScore);
    }

    @Nullable
    protected TrackSelection selectOtherTrack(int trackType, TrackGroupArray groups, int[][] formatSupport, Parameters params) throws PlaybackException {
        TrackGroup selectedGroup = null;
        int selectedTrackIndex = 0;
        int selectedTrackScore = 0;

        for(int groupIndex = 0; groupIndex < groups.length; ++groupIndex) {
            TrackGroup trackGroup = groups.get(groupIndex);
            int[] trackFormatSupport = formatSupport[groupIndex];

            for(int trackIndex = 0; trackIndex < trackGroup.length; ++trackIndex) {
                if (isSupported(trackFormatSupport[trackIndex], params.exceedRendererCapabilitiesIfNecessary)) {
                    Format format = trackGroup.getFormat(trackIndex);
                    boolean isDefault = (format.selectionFlags & 1) != 0;
                    int trackScore = isDefault ? 2 : 1;
                    if (isSupported(trackFormatSupport[trackIndex], false)) {
                        trackScore += 1000;
                    }

                    if (trackScore > selectedTrackScore) {
                        selectedGroup = trackGroup;
                        selectedTrackIndex = trackIndex;
                        selectedTrackScore = trackScore;
                    }
                }
            }
        }

        return selectedGroup == null ? null : new FixedTrackSelection(selectedGroup, selectedTrackIndex);
    }

    private static void maybeConfigureRenderersForTunneling(MappedTrackInfo mappedTrackInfo, int[][][] renderererFormatSupports, RendererConfiguration[] rendererConfigurations, TrackSelection[] trackSelections, int tunnelingAudioSessionId) {
        if (tunnelingAudioSessionId != 0) {
            int tunnelingAudioRendererIndex = -1;
            int tunnelingVideoRendererIndex = -1;
            boolean enableTunneling = true;

            for(int i = 0; i < mappedTrackInfo.getRendererCount(); ++i) {
                int rendererType = mappedTrackInfo.getRendererType(i);
                TrackSelection trackSelection = trackSelections[i];
                if ((rendererType == 1 || rendererType == 2) && trackSelection != null && rendererSupportsTunneling(renderererFormatSupports[i], mappedTrackInfo.getTrackGroups(i), trackSelection)) {
                    if (rendererType == 1) {
                        if (tunnelingAudioRendererIndex != -1) {
                            enableTunneling = false;
                            break;
                        }

                        tunnelingAudioRendererIndex = i;
                    } else {
                        if (tunnelingVideoRendererIndex != -1) {
                            enableTunneling = false;
                            break;
                        }

                        tunnelingVideoRendererIndex = i;
                    }
                }
            }

            enableTunneling &= tunnelingAudioRendererIndex != -1 && tunnelingVideoRendererIndex != -1;
            if (enableTunneling) {
                RendererConfiguration tunnelingRendererConfiguration = new RendererConfiguration(tunnelingAudioSessionId);
                rendererConfigurations[tunnelingAudioRendererIndex] = tunnelingRendererConfiguration;
                rendererConfigurations[tunnelingVideoRendererIndex] = tunnelingRendererConfiguration;
            }

        }
    }

    private static boolean rendererSupportsTunneling(int[][] formatSupports, TrackGroupArray trackGroups, TrackSelection selection) {
        if (selection == null) {
            return false;
        } else {
            int trackGroupIndex = trackGroups.indexOf(selection.getTrackGroup());

            for(int i = 0; i < selection.length(); ++i) {
                int trackFormatSupport = formatSupports[trackGroupIndex][selection.getIndexInTrackGroup(i)];
                if ((trackFormatSupport & 32) != 32) {
                    return false;
                }
            }

            return true;
        }
    }

    private static int compareFormatValues(int first, int second) {
        return first == -1 ? (second == -1 ? 0 : -1) : (second == -1 ? 1 : first - second);
    }

    protected static boolean isSupported(int formatSupport, boolean allowExceedsCapabilities) {
        int maskedSupport = formatSupport & 7;
        return maskedSupport == 4 || allowExceedsCapabilities && maskedSupport == 3;
    }

    protected static boolean formatHasNoLanguage(Format format) {
        return TextUtils.isEmpty(format.language) || formatHasLanguage(format, "und");
    }

    protected static boolean formatHasLanguage(Format format, @Nullable String language) {
        return language != null && TextUtils.equals(language, Util.normalizeLanguageCode(format.language));
    }

    private static List<Integer> getViewportFilteredTrackIndices(TrackGroup group, int viewportWidth, int viewportHeight, boolean orientationMayChange) {
        ArrayList<Integer> selectedTrackIndices = new ArrayList(group.length);

        int maxVideoPixelsToRetain;
        for(maxVideoPixelsToRetain = 0; maxVideoPixelsToRetain < group.length; ++maxVideoPixelsToRetain) {
            selectedTrackIndices.add(maxVideoPixelsToRetain);
        }

        if (viewportWidth != 2147483647 && viewportHeight != 2147483647) {
            maxVideoPixelsToRetain = 2147483647;

            int i;
            Format format;
            for(i = 0; i < group.length; ++i) {
                format = group.getFormat(i);
                if (format.width > 0 && format.height > 0) {
                    Point maxVideoSizeInViewport = getMaxVideoSizeInViewport(orientationMayChange, viewportWidth, viewportHeight, format.width, format.height);
                    int videoPixels = format.width * format.height;
                    if (format.width >= (int)((float)maxVideoSizeInViewport.x * 0.98F) && format.height >= (int)((float)maxVideoSizeInViewport.y * 0.98F) && videoPixels < maxVideoPixelsToRetain) {
                        maxVideoPixelsToRetain = videoPixels;
                    }
                }
            }

            if (maxVideoPixelsToRetain != 2147483647) {
                for(i = selectedTrackIndices.size() - 1; i >= 0; --i) {
                    format = group.getFormat((Integer)selectedTrackIndices.get(i));
                    int pixelCount = format.getPixelCount();
                    if (pixelCount == -1 || pixelCount > maxVideoPixelsToRetain) {
                        selectedTrackIndices.remove(i);
                    }
                }
            }

            return selectedTrackIndices;
        } else {
            return selectedTrackIndices;
        }
    }

    private static Point getMaxVideoSizeInViewport(boolean orientationMayChange, int viewportWidth, int viewportHeight, int videoWidth, int videoHeight) {
        if (orientationMayChange && videoWidth > videoHeight != viewportWidth > viewportHeight) {
            int tempViewportWidth = viewportWidth;
            viewportWidth = viewportHeight;
            viewportHeight = tempViewportWidth;
        }

        return videoWidth * viewportHeight >= videoHeight * viewportWidth ? new Point(viewportWidth, Util.ceilDivide(viewportWidth * videoHeight, videoWidth)) : new Point(Util.ceilDivide(viewportHeight * videoWidth, videoHeight), viewportHeight);
    }

    private static int compareInts(int first, int second) {
        return first > second ? 1 : (second > first ? -1 : 0);
    }

    private static final class AudioConfigurationTuple {
        public final int channelCount;
        public final int sampleRate;
        @Nullable
        public final String mimeType;

        public AudioConfigurationTuple(int channelCount, int sampleRate, @Nullable String mimeType) {
            this.channelCount = channelCount;
            this.sampleRate = sampleRate;
            this.mimeType = mimeType;
        }

        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            } else if (obj != null && this.getClass() == obj.getClass()) {
                AudioConfigurationTuple other = (AudioConfigurationTuple)obj;
                return this.channelCount == other.channelCount && this.sampleRate == other.sampleRate && TextUtils.equals(this.mimeType, other.mimeType);
            } else {
                return false;
            }
        }

        public int hashCode() {
            int result = this.channelCount;
            result = 31 * result + this.sampleRate;
            result = 31 * result + (this.mimeType != null ? this.mimeType.hashCode() : 0);
            return result;
        }
    }

    protected static final class AudioTrackScore implements Comparable<AudioTrackScore> {
        private final Parameters parameters;
        private final int withinRendererCapabilitiesScore;
        private final int matchLanguageScore;
        private final int defaultSelectionFlagScore;
        private final int channelCount;
        private final int sampleRate;
        private final int bitrate;

        public AudioTrackScore(Format format, Parameters parameters, int formatSupport) {
            this.parameters = parameters;
            this.withinRendererCapabilitiesScore = DefaultTrackSelector.isSupported(formatSupport, false) ? 1 : 0;
            this.matchLanguageScore = DefaultTrackSelector.formatHasLanguage(format, parameters.preferredAudioLanguage) ? 1 : 0;
            this.defaultSelectionFlagScore = (format.selectionFlags & 1) != 0 ? 1 : 0;
            this.channelCount = format.channelCount;
            this.sampleRate = format.sampleRate;
            this.bitrate = format.bitrate;
        }

        public int compareTo(@NonNull DefaultTrackSelector.AudioTrackScore other) {
            if (this.withinRendererCapabilitiesScore != other.withinRendererCapabilitiesScore) {
                return DefaultTrackSelector.compareInts(this.withinRendererCapabilitiesScore, other.withinRendererCapabilitiesScore);
            } else if (this.matchLanguageScore != other.matchLanguageScore) {
                return DefaultTrackSelector.compareInts(this.matchLanguageScore, other.matchLanguageScore);
            } else if (this.defaultSelectionFlagScore != other.defaultSelectionFlagScore) {
                return DefaultTrackSelector.compareInts(this.defaultSelectionFlagScore, other.defaultSelectionFlagScore);
            } else if (this.parameters.forceLowestBitrate) {
                return DefaultTrackSelector.compareInts(other.bitrate, this.bitrate);
            } else {
                int resultSign = this.withinRendererCapabilitiesScore == 1 ? 1 : -1;
                if (this.channelCount != other.channelCount) {
                    return resultSign * DefaultTrackSelector.compareInts(this.channelCount, other.channelCount);
                } else {
                    return this.sampleRate != other.sampleRate ? resultSign * DefaultTrackSelector.compareInts(this.sampleRate, other.sampleRate) : resultSign * DefaultTrackSelector.compareInts(this.bitrate, other.bitrate);
                }
            }
        }
    }

    public static final class SelectionOverride implements Parcelable {
        public final int groupIndex;
        public final int[] tracks;
        public final int length;
        public static final Creator<SelectionOverride> CREATOR = new Creator<SelectionOverride>() {
            public SelectionOverride createFromParcel(Parcel in) {
                return new SelectionOverride(in);
            }

            public SelectionOverride[] newArray(int size) {
                return new SelectionOverride[size];
            }
        };

        public SelectionOverride(int groupIndex, int... tracks) {
            this.groupIndex = groupIndex;
            this.tracks = Arrays.copyOf(tracks, tracks.length);
            this.length = tracks.length;
            Arrays.sort(this.tracks);
        }

        SelectionOverride(Parcel in) {
            this.groupIndex = in.readInt();
            this.length = in.readByte();
            this.tracks = new int[this.length];
            in.readIntArray(this.tracks);
        }

        public boolean containsTrack(int track) {
            int[] var2 = this.tracks;
            int var3 = var2.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                int overrideTrack = var2[var4];
                if (overrideTrack == track) {
                    return true;
                }
            }

            return false;
        }

        public int hashCode() {
            return 31 * this.groupIndex + Arrays.hashCode(this.tracks);
        }

        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            } else if (obj != null && this.getClass() == obj.getClass()) {
                SelectionOverride other = (SelectionOverride)obj;
                return this.groupIndex == other.groupIndex && Arrays.equals(this.tracks, other.tracks);
            } else {
                return false;
            }
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(this.groupIndex);
            dest.writeInt(this.tracks.length);
            dest.writeIntArray(this.tracks);
        }
    }

    public static final class Parameters implements Parcelable {
        public static final Parameters DEFAULT = new Parameters();
        private final SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides;
        private final SparseBooleanArray rendererDisabledFlags;
        @Nullable
        public final String preferredAudioLanguage;
        @Nullable
        public final String preferredTextLanguage;
        public final boolean selectUndeterminedTextLanguage;
        public final int disabledTextTrackSelectionFlags;
        public final int maxVideoWidth;
        public final int maxVideoHeight;
        public final int maxVideoFrameRate;
        public final int maxVideoBitrate;
        public final boolean exceedVideoConstraintsIfNecessary;
        public final int viewportWidth;
        public final int viewportHeight;
        public final boolean viewportOrientationMayChange;
        public final boolean forceLowestBitrate;
        public final boolean forceHighestSupportedBitrate;
        public final boolean allowMixedMimeAdaptiveness;
        public final boolean allowNonSeamlessAdaptiveness;
        public final boolean exceedRendererCapabilitiesIfNecessary;
        public final int tunnelingAudioSessionId;
        public static final Creator<Parameters> CREATOR = new Creator<Parameters>() {
            public Parameters createFromParcel(Parcel in) {
                return new Parameters(in);
            }

            public Parameters[] newArray(int size) {
                return new Parameters[size];
            }
        };

        private Parameters() {
            this(new SparseArray(), new SparseBooleanArray(), (String)null, (String)null, false, 0, false, false, false, true, 2147483647, 2147483647, 2147483647, 2147483647, true, true, 2147483647, 2147483647, true, 0);
        }

        Parameters(SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides, SparseBooleanArray rendererDisabledFlags, @Nullable String preferredAudioLanguage, @Nullable String preferredTextLanguage, boolean selectUndeterminedTextLanguage, int disabledTextTrackSelectionFlags, boolean forceLowestBitrate, boolean forceHighestSupportedBitrate, boolean allowMixedMimeAdaptiveness, boolean allowNonSeamlessAdaptiveness, int maxVideoWidth, int maxVideoHeight, int maxVideoFrameRate, int maxVideoBitrate, boolean exceedVideoConstraintsIfNecessary, boolean exceedRendererCapabilitiesIfNecessary, int viewportWidth, int viewportHeight, boolean viewportOrientationMayChange, int tunnelingAudioSessionId) {
            this.selectionOverrides = selectionOverrides;
            this.rendererDisabledFlags = rendererDisabledFlags;
            this.preferredAudioLanguage = Util.normalizeLanguageCode(preferredAudioLanguage);
            this.preferredTextLanguage = Util.normalizeLanguageCode(preferredTextLanguage);
            this.selectUndeterminedTextLanguage = selectUndeterminedTextLanguage;
            this.disabledTextTrackSelectionFlags = disabledTextTrackSelectionFlags;
            this.forceLowestBitrate = forceLowestBitrate;
            this.forceHighestSupportedBitrate = forceHighestSupportedBitrate;
            this.allowMixedMimeAdaptiveness = allowMixedMimeAdaptiveness;
            this.allowNonSeamlessAdaptiveness = allowNonSeamlessAdaptiveness;
            this.maxVideoWidth = maxVideoWidth;
            this.maxVideoHeight = maxVideoHeight;
            this.maxVideoFrameRate = maxVideoFrameRate;
            this.maxVideoBitrate = maxVideoBitrate;
            this.exceedVideoConstraintsIfNecessary = exceedVideoConstraintsIfNecessary;
            this.exceedRendererCapabilitiesIfNecessary = exceedRendererCapabilitiesIfNecessary;
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
            this.viewportOrientationMayChange = viewportOrientationMayChange;
            this.tunnelingAudioSessionId = tunnelingAudioSessionId;
        }

        Parameters(Parcel in) {
            this.selectionOverrides = readSelectionOverrides(in);
            this.rendererDisabledFlags = in.readSparseBooleanArray();
            this.preferredAudioLanguage = in.readString();
            this.preferredTextLanguage = in.readString();
            this.selectUndeterminedTextLanguage = Util.readBoolean(in);
            this.disabledTextTrackSelectionFlags = in.readInt();
            this.forceLowestBitrate = Util.readBoolean(in);
            this.forceHighestSupportedBitrate = Util.readBoolean(in);
            this.allowMixedMimeAdaptiveness = Util.readBoolean(in);
            this.allowNonSeamlessAdaptiveness = Util.readBoolean(in);
            this.maxVideoWidth = in.readInt();
            this.maxVideoHeight = in.readInt();
            this.maxVideoFrameRate = in.readInt();
            this.maxVideoBitrate = in.readInt();
            this.exceedVideoConstraintsIfNecessary = Util.readBoolean(in);
            this.exceedRendererCapabilitiesIfNecessary = Util.readBoolean(in);
            this.viewportWidth = in.readInt();
            this.viewportHeight = in.readInt();
            this.viewportOrientationMayChange = Util.readBoolean(in);
            this.tunnelingAudioSessionId = in.readInt();
        }

        public final boolean getRendererDisabled(int rendererIndex) {
            return this.rendererDisabledFlags.get(rendererIndex);
        }

        public final boolean hasSelectionOverride(int rendererIndex, TrackGroupArray groups) {
            Map<TrackGroupArray, SelectionOverride> overrides = (Map)this.selectionOverrides.get(rendererIndex);
            return overrides != null && overrides.containsKey(groups);
        }

        @Nullable
        public final DefaultTrackSelector.SelectionOverride getSelectionOverride(int rendererIndex, TrackGroupArray groups) {
            Map<TrackGroupArray, SelectionOverride> overrides = (Map)this.selectionOverrides.get(rendererIndex);
            return overrides != null ? (SelectionOverride)overrides.get(groups) : null;
        }

        public ParametersBuilder buildUpon() {
            return new ParametersBuilder(this);
        }

        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            } else if (obj != null && this.getClass() == obj.getClass()) {
                Parameters other = (Parameters)obj;
                return this.selectUndeterminedTextLanguage == other.selectUndeterminedTextLanguage && this.disabledTextTrackSelectionFlags == other.disabledTextTrackSelectionFlags && this.forceLowestBitrate == other.forceLowestBitrate && this.forceHighestSupportedBitrate == other.forceHighestSupportedBitrate && this.allowMixedMimeAdaptiveness == other.allowMixedMimeAdaptiveness && this.allowNonSeamlessAdaptiveness == other.allowNonSeamlessAdaptiveness && this.maxVideoWidth == other.maxVideoWidth && this.maxVideoHeight == other.maxVideoHeight && this.maxVideoFrameRate == other.maxVideoFrameRate && this.exceedVideoConstraintsIfNecessary == other.exceedVideoConstraintsIfNecessary && this.exceedRendererCapabilitiesIfNecessary == other.exceedRendererCapabilitiesIfNecessary && this.viewportOrientationMayChange == other.viewportOrientationMayChange && this.viewportWidth == other.viewportWidth && this.viewportHeight == other.viewportHeight && this.maxVideoBitrate == other.maxVideoBitrate && this.tunnelingAudioSessionId == other.tunnelingAudioSessionId && TextUtils.equals(this.preferredAudioLanguage, other.preferredAudioLanguage) && TextUtils.equals(this.preferredTextLanguage, other.preferredTextLanguage) && areRendererDisabledFlagsEqual(this.rendererDisabledFlags, other.rendererDisabledFlags) && areSelectionOverridesEqual(this.selectionOverrides, other.selectionOverrides);
            } else {
                return false;
            }
        }

        public int hashCode() {
            int result = this.selectUndeterminedTextLanguage ? 1 : 0;
            result = 31 * result + this.disabledTextTrackSelectionFlags;
            result = 31 * result + (this.forceLowestBitrate ? 1 : 0);
            result = 31 * result + (this.forceHighestSupportedBitrate ? 1 : 0);
            result = 31 * result + (this.allowMixedMimeAdaptiveness ? 1 : 0);
            result = 31 * result + (this.allowNonSeamlessAdaptiveness ? 1 : 0);
            result = 31 * result + this.maxVideoWidth;
            result = 31 * result + this.maxVideoHeight;
            result = 31 * result + this.maxVideoFrameRate;
            result = 31 * result + (this.exceedVideoConstraintsIfNecessary ? 1 : 0);
            result = 31 * result + (this.exceedRendererCapabilitiesIfNecessary ? 1 : 0);
            result = 31 * result + (this.viewportOrientationMayChange ? 1 : 0);
            result = 31 * result + this.viewportWidth;
            result = 31 * result + this.viewportHeight;
            result = 31 * result + this.maxVideoBitrate;
            result = 31 * result + this.tunnelingAudioSessionId;
            result = 31 * result + (this.preferredAudioLanguage == null ? 0 : this.preferredAudioLanguage.hashCode());
            result = 31 * result + (this.preferredTextLanguage == null ? 0 : this.preferredTextLanguage.hashCode());
            return result;
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            writeSelectionOverridesToParcel(dest, this.selectionOverrides);
            dest.writeSparseBooleanArray(this.rendererDisabledFlags);
            dest.writeString(this.preferredAudioLanguage);
            dest.writeString(this.preferredTextLanguage);
            Util.writeBoolean(dest, this.selectUndeterminedTextLanguage);
            dest.writeInt(this.disabledTextTrackSelectionFlags);
            Util.writeBoolean(dest, this.forceLowestBitrate);
            Util.writeBoolean(dest, this.forceHighestSupportedBitrate);
            Util.writeBoolean(dest, this.allowMixedMimeAdaptiveness);
            Util.writeBoolean(dest, this.allowNonSeamlessAdaptiveness);
            dest.writeInt(this.maxVideoWidth);
            dest.writeInt(this.maxVideoHeight);
            dest.writeInt(this.maxVideoFrameRate);
            dest.writeInt(this.maxVideoBitrate);
            Util.writeBoolean(dest, this.exceedVideoConstraintsIfNecessary);
            Util.writeBoolean(dest, this.exceedRendererCapabilitiesIfNecessary);
            dest.writeInt(this.viewportWidth);
            dest.writeInt(this.viewportHeight);
            Util.writeBoolean(dest, this.viewportOrientationMayChange);
            dest.writeInt(this.tunnelingAudioSessionId);
        }

        private static SparseArray<Map<TrackGroupArray, SelectionOverride>> readSelectionOverrides(Parcel in) {
            int renderersWithOverridesCount = in.readInt();
            SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides = new SparseArray(renderersWithOverridesCount);

            for(int i = 0; i < renderersWithOverridesCount; ++i) {
                int rendererIndex = in.readInt();
                int overrideCount = in.readInt();
                Map<TrackGroupArray, SelectionOverride> overrides = new HashMap(overrideCount);

                for(int j = 0; j < overrideCount; ++j) {
                    TrackGroupArray trackGroups = (TrackGroupArray)in.readParcelable(TrackGroupArray.class.getClassLoader());
                    SelectionOverride override = (SelectionOverride)in.readParcelable(SelectionOverride.class.getClassLoader());
                    overrides.put(trackGroups, override);
                }

                selectionOverrides.put(rendererIndex, overrides);
            }

            return selectionOverrides;
        }

        private static void writeSelectionOverridesToParcel(Parcel dest, SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides) {
            int renderersWithOverridesCount = selectionOverrides.size();
            dest.writeInt(renderersWithOverridesCount);

            for(int i = 0; i < renderersWithOverridesCount; ++i) {
                int rendererIndex = selectionOverrides.keyAt(i);
                Map<TrackGroupArray, SelectionOverride> overrides = (Map)selectionOverrides.valueAt(i);
                int overrideCount = overrides.size();
                dest.writeInt(rendererIndex);
                dest.writeInt(overrideCount);
                Iterator var7 = overrides.entrySet().iterator();

                while(var7.hasNext()) {
                    Entry<TrackGroupArray, SelectionOverride> override = (Entry)var7.next();
                    dest.writeParcelable((Parcelable)override.getKey(), 0);
                    dest.writeParcelable((Parcelable)override.getValue(), 0);
                }
            }

        }

        private static boolean areRendererDisabledFlagsEqual(SparseBooleanArray first, SparseBooleanArray second) {
            int firstSize = first.size();
            if (second.size() != firstSize) {
                return false;
            } else {
                for(int indexInFirst = 0; indexInFirst < firstSize; ++indexInFirst) {
                    if (second.indexOfKey(first.keyAt(indexInFirst)) < 0) {
                        return false;
                    }
                }

                return true;
            }
        }

        private static boolean areSelectionOverridesEqual(SparseArray<Map<TrackGroupArray, SelectionOverride>> first, SparseArray<Map<TrackGroupArray, SelectionOverride>> second) {
            int firstSize = first.size();
            if (second.size() != firstSize) {
                return false;
            } else {
                for(int indexInFirst = 0; indexInFirst < firstSize; ++indexInFirst) {
                    int indexInSecond = second.indexOfKey(first.keyAt(indexInFirst));
                    if (indexInSecond < 0 || !areSelectionOverridesEqual((Map)first.valueAt(indexInFirst), (Map)second.valueAt(indexInSecond))) {
                        return false;
                    }
                }

                return true;
            }
        }

        private static boolean areSelectionOverridesEqual(Map<TrackGroupArray, SelectionOverride> first, Map<TrackGroupArray, SelectionOverride> second) {
            int firstSize = first.size();
            if (second.size() != firstSize) {
                return false;
            } else {
                Iterator var3 = first.entrySet().iterator();

                Entry firstEntry;
                TrackGroupArray key;
                do {
                    if (!var3.hasNext()) {
                        return true;
                    }

                    firstEntry = (Entry)var3.next();
                    key = (TrackGroupArray)firstEntry.getKey();
                } while(second.containsKey(key) && Util.areEqual(firstEntry.getValue(), second.get(key)));

                return false;
            }
        }
    }

    public static final class ParametersBuilder {
        private final SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides;
        private final SparseBooleanArray rendererDisabledFlags;
        @Nullable
        private String preferredAudioLanguage;
        @Nullable
        private String preferredTextLanguage;
        private boolean selectUndeterminedTextLanguage;
        private int disabledTextTrackSelectionFlags;
        private boolean forceLowestBitrate;
        private boolean forceHighestSupportedBitrate;
        private boolean allowMixedMimeAdaptiveness;
        private boolean allowNonSeamlessAdaptiveness;
        private int maxVideoWidth;
        private int maxVideoHeight;
        private int maxVideoFrameRate;
        private int maxVideoBitrate;
        private boolean exceedVideoConstraintsIfNecessary;
        private boolean exceedRendererCapabilitiesIfNecessary;
        private int viewportWidth;
        private int viewportHeight;
        private boolean viewportOrientationMayChange;
        private int tunnelingAudioSessionId;

        public ParametersBuilder() {
            this(Parameters.DEFAULT);
        }

        private ParametersBuilder(Parameters initialValues) {
            this.selectionOverrides = cloneSelectionOverrides(initialValues.selectionOverrides);
            this.rendererDisabledFlags = initialValues.rendererDisabledFlags.clone();
            this.preferredAudioLanguage = initialValues.preferredAudioLanguage;
            this.preferredTextLanguage = initialValues.preferredTextLanguage;
            this.selectUndeterminedTextLanguage = initialValues.selectUndeterminedTextLanguage;
            this.disabledTextTrackSelectionFlags = initialValues.disabledTextTrackSelectionFlags;
            this.forceLowestBitrate = initialValues.forceLowestBitrate;
            this.forceHighestSupportedBitrate = initialValues.forceHighestSupportedBitrate;
            this.allowMixedMimeAdaptiveness = initialValues.allowMixedMimeAdaptiveness;
            this.allowNonSeamlessAdaptiveness = initialValues.allowNonSeamlessAdaptiveness;
            this.maxVideoWidth = initialValues.maxVideoWidth;
            this.maxVideoHeight = initialValues.maxVideoHeight;
            this.maxVideoFrameRate = initialValues.maxVideoFrameRate;
            this.maxVideoBitrate = initialValues.maxVideoBitrate;
            this.exceedVideoConstraintsIfNecessary = initialValues.exceedVideoConstraintsIfNecessary;
            this.exceedRendererCapabilitiesIfNecessary = initialValues.exceedRendererCapabilitiesIfNecessary;
            this.viewportWidth = initialValues.viewportWidth;
            this.viewportHeight = initialValues.viewportHeight;
            this.viewportOrientationMayChange = initialValues.viewportOrientationMayChange;
            this.tunnelingAudioSessionId = initialValues.tunnelingAudioSessionId;
        }

        public ParametersBuilder setPreferredAudioLanguage(String preferredAudioLanguage) {
            this.preferredAudioLanguage = preferredAudioLanguage;
            return this;
        }

        public ParametersBuilder setPreferredTextLanguage(String preferredTextLanguage) {
            this.preferredTextLanguage = preferredTextLanguage;
            return this;
        }

        public ParametersBuilder setSelectUndeterminedTextLanguage(boolean selectUndeterminedTextLanguage) {
            this.selectUndeterminedTextLanguage = selectUndeterminedTextLanguage;
            return this;
        }

        public ParametersBuilder setDisabledTextTrackSelectionFlags(int disabledTextTrackSelectionFlags) {
            this.disabledTextTrackSelectionFlags = disabledTextTrackSelectionFlags;
            return this;
        }

        public ParametersBuilder setForceLowestBitrate(boolean forceLowestBitrate) {
            this.forceLowestBitrate = forceLowestBitrate;
            return this;
        }

        public ParametersBuilder setForceHighestSupportedBitrate(boolean forceHighestSupportedBitrate) {
            this.forceHighestSupportedBitrate = forceHighestSupportedBitrate;
            return this;
        }

        public ParametersBuilder setAllowMixedMimeAdaptiveness(boolean allowMixedMimeAdaptiveness) {
            this.allowMixedMimeAdaptiveness = allowMixedMimeAdaptiveness;
            return this;
        }

        public ParametersBuilder setAllowNonSeamlessAdaptiveness(boolean allowNonSeamlessAdaptiveness) {
            this.allowNonSeamlessAdaptiveness = allowNonSeamlessAdaptiveness;
            return this;
        }

        public ParametersBuilder setMaxVideoSizeSd() {
            return this.setMaxVideoSize(1279, 719);
        }

        public ParametersBuilder clearVideoSizeConstraints() {
            return this.setMaxVideoSize(2147483647, 2147483647);
        }

        public ParametersBuilder setMaxVideoSize(int maxVideoWidth, int maxVideoHeight) {
            this.maxVideoWidth = maxVideoWidth;
            this.maxVideoHeight = maxVideoHeight;
            return this;
        }

        public ParametersBuilder setMaxVideoFrameRate(int maxVideoFrameRate) {
            this.maxVideoFrameRate = maxVideoFrameRate;
            return this;
        }

        public ParametersBuilder setMaxVideoBitrate(int maxVideoBitrate) {
            this.maxVideoBitrate = maxVideoBitrate;
            return this;
        }

        public ParametersBuilder setExceedVideoConstraintsIfNecessary(boolean exceedVideoConstraintsIfNecessary) {
            this.exceedVideoConstraintsIfNecessary = exceedVideoConstraintsIfNecessary;
            return this;
        }

        public ParametersBuilder setExceedRendererCapabilitiesIfNecessary(boolean exceedRendererCapabilitiesIfNecessary) {
            this.exceedRendererCapabilitiesIfNecessary = exceedRendererCapabilitiesIfNecessary;
            return this;
        }

        public ParametersBuilder setViewportSizeToPhysicalDisplaySize(Context context, boolean viewportOrientationMayChange) {
            Point viewportSize = Util.getPhysicalDisplaySize(context);
            return this.setViewportSize(viewportSize.x, viewportSize.y, viewportOrientationMayChange);
        }

        public ParametersBuilder clearViewportSizeConstraints() {
            return this.setViewportSize(2147483647, 2147483647, true);
        }

        public ParametersBuilder setViewportSize(int viewportWidth, int viewportHeight, boolean viewportOrientationMayChange) {
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
            this.viewportOrientationMayChange = viewportOrientationMayChange;
            return this;
        }

        public final ParametersBuilder setRendererDisabled(int rendererIndex, boolean disabled) {
            if (this.rendererDisabledFlags.get(rendererIndex) == disabled) {
                return this;
            } else {
                if (disabled) {
                    this.rendererDisabledFlags.put(rendererIndex, true);
                } else {
                    this.rendererDisabledFlags.delete(rendererIndex);
                }

                return this;
            }
        }

        public final ParametersBuilder setSelectionOverride(int rendererIndex, TrackGroupArray groups, SelectionOverride override) {
            Map<TrackGroupArray, SelectionOverride> overrides = (Map)this.selectionOverrides.get(rendererIndex);
            if (overrides == null) {
                overrides = new HashMap();
                this.selectionOverrides.put(rendererIndex, overrides);
            }

            if (((Map)overrides).containsKey(groups) && Util.areEqual(((Map)overrides).get(groups), override)) {
                return this;
            } else {
                ((Map)overrides).put(groups, override);
                return this;
            }
        }

        public final ParametersBuilder clearSelectionOverride(int rendererIndex, TrackGroupArray groups) {
            Map<TrackGroupArray, SelectionOverride> overrides = (Map)this.selectionOverrides.get(rendererIndex);
            if (overrides != null && overrides.containsKey(groups)) {
                overrides.remove(groups);
                if (overrides.isEmpty()) {
                    this.selectionOverrides.remove(rendererIndex);
                }

                return this;
            } else {
                return this;
            }
        }

        public final ParametersBuilder clearSelectionOverrides(int rendererIndex) {
            Map<TrackGroupArray, SelectionOverride> overrides = (Map)this.selectionOverrides.get(rendererIndex);
            if (overrides != null && !overrides.isEmpty()) {
                this.selectionOverrides.remove(rendererIndex);
                return this;
            } else {
                return this;
            }
        }

        public final ParametersBuilder clearSelectionOverrides() {
            if (this.selectionOverrides.size() == 0) {
                return this;
            } else {
                this.selectionOverrides.clear();
                return this;
            }
        }

        public ParametersBuilder setTunnelingAudioSessionId(int tunnelingAudioSessionId) {
            if (this.tunnelingAudioSessionId != tunnelingAudioSessionId) {
                this.tunnelingAudioSessionId = tunnelingAudioSessionId;
                return this;
            } else {
                return this;
            }
        }

        public Parameters build() {
            return new Parameters(this.selectionOverrides, this.rendererDisabledFlags, this.preferredAudioLanguage, this.preferredTextLanguage, this.selectUndeterminedTextLanguage, this.disabledTextTrackSelectionFlags, this.forceLowestBitrate, this.forceHighestSupportedBitrate, this.allowMixedMimeAdaptiveness, this.allowNonSeamlessAdaptiveness, this.maxVideoWidth, this.maxVideoHeight, this.maxVideoFrameRate, this.maxVideoBitrate, this.exceedVideoConstraintsIfNecessary, this.exceedRendererCapabilitiesIfNecessary, this.viewportWidth, this.viewportHeight, this.viewportOrientationMayChange, this.tunnelingAudioSessionId);
        }

        private static SparseArray<Map<TrackGroupArray, SelectionOverride>> cloneSelectionOverrides(SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides) {
            SparseArray<Map<TrackGroupArray, SelectionOverride>> clone = new SparseArray();

            for(int i = 0; i < selectionOverrides.size(); ++i) {
                clone.put(selectionOverrides.keyAt(i), new HashMap((Map)selectionOverrides.valueAt(i)));
            }

            return clone;
        }
    }
}
