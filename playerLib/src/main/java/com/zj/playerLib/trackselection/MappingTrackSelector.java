package com.zj.playerLib.trackselection;

import android.util.Pair;

import androidx.annotation.Nullable;

import com.zj.playerLib.PlaybackException;
import com.zj.playerLib.RendererCapabilities;
import com.zj.playerLib.RendererConfiguration;
import com.zj.playerLib.source.TrackGroup;
import com.zj.playerLib.source.TrackGroupArray;
import com.zj.playerLib.util.Util;

import java.util.Arrays;

public abstract class MappingTrackSelector extends TrackSelector {
    @Nullable
    private MappingTrackSelector.MappedTrackInfo currentMappedTrackInfo;

    public MappingTrackSelector() {
    }

    @Nullable
    public final MappingTrackSelector.MappedTrackInfo getCurrentMappedTrackInfo() {
        return this.currentMappedTrackInfo;
    }

    public final void onSelectionActivated(Object info) {
        this.currentMappedTrackInfo = (MappedTrackInfo)info;
    }

    public final TrackSelectorResult selectTracks(RendererCapabilities[] rendererCapabilities, TrackGroupArray trackGroups) throws PlaybackException {
        int[] rendererTrackGroupCounts = new int[rendererCapabilities.length + 1];
        TrackGroup[][] rendererTrackGroups = new TrackGroup[rendererCapabilities.length + 1][];
        int[][][] rendererFormatSupports = new int[rendererCapabilities.length + 1][][];

        for(int i = 0; i < rendererTrackGroups.length; ++i) {
            rendererTrackGroups[i] = new TrackGroup[trackGroups.length];
            rendererFormatSupports[i] = new int[trackGroups.length][];
        }

        int[] rendererMixedMimeTypeAdaptationSupports = getMixedMimeTypeAdaptationSupports(rendererCapabilities);

        int unmappedTrackGroupCount;
        for(int groupIndex = 0; groupIndex < trackGroups.length; ++groupIndex) {
            TrackGroup group = trackGroups.get(groupIndex);
            unmappedTrackGroupCount = findRenderer(rendererCapabilities, group);
            int[] rendererFormatSupport = unmappedTrackGroupCount == rendererCapabilities.length ? new int[group.length] : getFormatSupport(rendererCapabilities[unmappedTrackGroupCount], group);
            int rendererTrackGroupCount = rendererTrackGroupCounts[unmappedTrackGroupCount];
            rendererTrackGroups[unmappedTrackGroupCount][rendererTrackGroupCount] = group;
            rendererFormatSupports[unmappedTrackGroupCount][rendererTrackGroupCount] = rendererFormatSupport;
            int var10002 = rendererTrackGroupCounts[unmappedTrackGroupCount]++;
        }

        TrackGroupArray[] rendererTrackGroupArrays = new TrackGroupArray[rendererCapabilities.length];
        int[] rendererTrackTypes = new int[rendererCapabilities.length];

        for(unmappedTrackGroupCount = 0; unmappedTrackGroupCount < rendererCapabilities.length; ++unmappedTrackGroupCount) {
            int rendererTrackGroupCount = rendererTrackGroupCounts[unmappedTrackGroupCount];
            rendererTrackGroupArrays[unmappedTrackGroupCount] = new TrackGroupArray(Util.nullSafeArrayCopy(rendererTrackGroups[unmappedTrackGroupCount], rendererTrackGroupCount));
            rendererFormatSupports[unmappedTrackGroupCount] = Util.nullSafeArrayCopy(rendererFormatSupports[unmappedTrackGroupCount], rendererTrackGroupCount);
            rendererTrackTypes[unmappedTrackGroupCount] = rendererCapabilities[unmappedTrackGroupCount].getTrackType();
        }

        unmappedTrackGroupCount = rendererTrackGroupCounts[rendererCapabilities.length];
        TrackGroupArray unmappedTrackGroupArray = new TrackGroupArray(Util.nullSafeArrayCopy(rendererTrackGroups[rendererCapabilities.length], unmappedTrackGroupCount));
        MappedTrackInfo mappedTrackInfo = new MappedTrackInfo(rendererTrackTypes, rendererTrackGroupArrays, rendererMixedMimeTypeAdaptationSupports, rendererFormatSupports, unmappedTrackGroupArray);
        Pair<RendererConfiguration[], TrackSelection[]> result = this.selectTracks(mappedTrackInfo, rendererFormatSupports, rendererMixedMimeTypeAdaptationSupports);
        return new TrackSelectorResult(result.first, result.second, mappedTrackInfo);
    }

    protected abstract Pair<RendererConfiguration[], TrackSelection[]> selectTracks(MappedTrackInfo var1, int[][][] var2, int[] var3) throws PlaybackException;

    private static int findRenderer(RendererCapabilities[] rendererCapabilities, TrackGroup group) throws PlaybackException {
        int bestRendererIndex = rendererCapabilities.length;
        int bestFormatSupportLevel = 0;

        for(int rendererIndex = 0; rendererIndex < rendererCapabilities.length; ++rendererIndex) {
            RendererCapabilities rendererCapability = rendererCapabilities[rendererIndex];

            for(int trackIndex = 0; trackIndex < group.length; ++trackIndex) {
                int formatSupportLevel = rendererCapability.supportsFormat(group.getFormat(trackIndex)) & 7;
                if (formatSupportLevel > bestFormatSupportLevel) {
                    bestRendererIndex = rendererIndex;
                    bestFormatSupportLevel = formatSupportLevel;
                    if (formatSupportLevel == 4) {
                        return rendererIndex;
                    }
                }
            }
        }

        return bestRendererIndex;
    }

    private static int[] getFormatSupport(RendererCapabilities rendererCapabilities, TrackGroup group) throws PlaybackException {
        int[] formatSupport = new int[group.length];

        for(int i = 0; i < group.length; ++i) {
            formatSupport[i] = rendererCapabilities.supportsFormat(group.getFormat(i));
        }

        return formatSupport;
    }

    private static int[] getMixedMimeTypeAdaptationSupports(RendererCapabilities[] rendererCapabilities) throws PlaybackException {
        int[] mixedMimeTypeAdaptationSupport = new int[rendererCapabilities.length];

        for(int i = 0; i < mixedMimeTypeAdaptationSupport.length; ++i) {
            mixedMimeTypeAdaptationSupport[i] = rendererCapabilities[i].supportsMixedMimeTypeAdaptation();
        }

        return mixedMimeTypeAdaptationSupport;
    }

    public static final class MappedTrackInfo {
        public static final int RENDERER_SUPPORT_NO_TRACKS = 0;
        public static final int RENDERER_SUPPORT_UNSUPPORTED_TRACKS = 1;
        public static final int RENDERER_SUPPORT_EXCEEDS_CAPABILITIES_TRACKS = 2;
        public static final int RENDERER_SUPPORT_PLAYABLE_TRACKS = 3;
        /** @deprecated */
        @Deprecated
        public final int length;
        private final int rendererCount;
        private final int[] rendererTrackTypes;
        private final TrackGroupArray[] rendererTrackGroups;
        private final int[] rendererMixedMimeTypeAdaptiveSupports;
        private final int[][][] rendererFormatSupports;
        private final TrackGroupArray unmappedTrackGroups;

        MappedTrackInfo(int[] rendererTrackTypes, TrackGroupArray[] rendererTrackGroups, int[] rendererMixedMimeTypeAdaptiveSupports, int[][][] rendererFormatSupports, TrackGroupArray unmappedTrackGroups) {
            this.rendererTrackTypes = rendererTrackTypes;
            this.rendererTrackGroups = rendererTrackGroups;
            this.rendererFormatSupports = rendererFormatSupports;
            this.rendererMixedMimeTypeAdaptiveSupports = rendererMixedMimeTypeAdaptiveSupports;
            this.unmappedTrackGroups = unmappedTrackGroups;
            this.rendererCount = rendererTrackTypes.length;
            this.length = this.rendererCount;
        }

        public int getRendererCount() {
            return this.rendererCount;
        }

        public int getRendererType(int rendererIndex) {
            return this.rendererTrackTypes[rendererIndex];
        }

        public TrackGroupArray getTrackGroups(int rendererIndex) {
            return this.rendererTrackGroups[rendererIndex];
        }

        public int getRendererSupport(int rendererIndex) {
            int bestRendererSupport = 0;
            int[][] rendererFormatSupport = this.rendererFormatSupports[rendererIndex];

            for(int i = 0; i < rendererFormatSupport.length; ++i) {
                for(int j = 0; j < rendererFormatSupport[i].length; ++j) {
                    byte trackRendererSupport;
                    switch(rendererFormatSupport[i][j] & 7) {
                    case 3:
                        trackRendererSupport = 2;
                        break;
                    case 4:
                        return 3;
                    default:
                        trackRendererSupport = 1;
                    }

                    bestRendererSupport = Math.max(bestRendererSupport, trackRendererSupport);
                }
            }

            return bestRendererSupport;
        }

        /** @deprecated */
        @Deprecated
        public int getTrackTypeRendererSupport(int trackType) {
            return this.getTypeSupport(trackType);
        }

        public int getTypeSupport(int trackType) {
            int bestRendererSupport = 0;

            for(int i = 0; i < this.rendererCount; ++i) {
                if (this.rendererTrackTypes[i] == trackType) {
                    bestRendererSupport = Math.max(bestRendererSupport, this.getRendererSupport(i));
                }
            }

            return bestRendererSupport;
        }

        /** @deprecated */
        @Deprecated
        public int getTrackFormatSupport(int rendererIndex, int groupIndex, int trackIndex) {
            return this.getTrackSupport(rendererIndex, groupIndex, trackIndex);
        }

        public int getTrackSupport(int rendererIndex, int groupIndex, int trackIndex) {
            return this.rendererFormatSupports[rendererIndex][groupIndex][trackIndex] & 7;
        }

        public int getAdaptiveSupport(int rendererIndex, int groupIndex, boolean includeCapabilitiesExceededTracks) {
            int trackCount = this.rendererTrackGroups[rendererIndex].get(groupIndex).length;
            int[] trackIndices = new int[trackCount];
            int trackIndexCount = 0;

            for(int i = 0; i < trackCount; ++i) {
                int fixedSupport = this.getTrackSupport(rendererIndex, groupIndex, i);
                if (fixedSupport == 4 || includeCapabilitiesExceededTracks && fixedSupport == 3) {
                    trackIndices[trackIndexCount++] = i;
                }
            }

            trackIndices = Arrays.copyOf(trackIndices, trackIndexCount);
            return this.getAdaptiveSupport(rendererIndex, groupIndex, trackIndices);
        }

        public int getAdaptiveSupport(int rendererIndex, int groupIndex, int[] trackIndices) {
            int handledTrackCount = 0;
            int adaptiveSupport = 16;
            boolean multipleMimeTypes = false;
            String firstSampleMimeType = null;

            for(int i = 0; i < trackIndices.length; ++i) {
                int trackIndex = trackIndices[i];
                String sampleMimeType = this.rendererTrackGroups[rendererIndex].get(groupIndex).getFormat(trackIndex).sampleMimeType;
                if (handledTrackCount++ == 0) {
                    firstSampleMimeType = sampleMimeType;
                } else {
                    multipleMimeTypes |= !Util.areEqual(firstSampleMimeType, sampleMimeType);
                }

                adaptiveSupport = Math.min(adaptiveSupport, this.rendererFormatSupports[rendererIndex][groupIndex][i] & 24);
            }

            return multipleMimeTypes ? Math.min(adaptiveSupport, this.rendererMixedMimeTypeAdaptiveSupports[rendererIndex]) : adaptiveSupport;
        }

        /** @deprecated */
        @Deprecated
        public TrackGroupArray getUnassociatedTrackGroups() {
            return this.getUnmappedTrackGroups();
        }

        public TrackGroupArray getUnmappedTrackGroups() {
            return this.unmappedTrackGroups;
        }
    }
}
