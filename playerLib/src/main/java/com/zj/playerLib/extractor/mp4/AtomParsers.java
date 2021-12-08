package com.zj.playerLib.extractor.mp4;

import android.util.Pair;

import com.zj.playerLib.Format;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.audio.Ac3Util;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.GaplessInfoHolder;
import com.zj.playerLib.extractor.mp4.Atom.ContainerAtom;
import com.zj.playerLib.extractor.mp4.Atom.LeafAtom;
import com.zj.playerLib.extractor.mp4.FixedSampleSizeRechunker.Results;
import com.zj.playerLib.metadata.Metadata;
import com.zj.playerLib.metadata.Metadata.Entry;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.CodecSpecificDataUtil;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.MimeTypes;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;
import com.zj.playerLib.video.AvcConfig;
import com.zj.playerLib.video.ColorInfo;
import com.zj.playerLib.video.HevcConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class AtomParsers {
    private static final int TYPE_vide = Util.getIntegerCodeForString("vide");
    private static final int TYPE_soun = Util.getIntegerCodeForString("soun");
    private static final int TYPE_text = Util.getIntegerCodeForString("text");
    private static final int TYPE_sbtl = Util.getIntegerCodeForString("sbtl");
    private static final int TYPE_subt = Util.getIntegerCodeForString("subt");
    private static final int TYPE_clcp = Util.getIntegerCodeForString("clcp");
    private static final int TYPE_meta = Util.getIntegerCodeForString("meta");
    private static final int MAX_GAPLESS_TRIM_SIZE_SAMPLES = 3;
    private static final byte[] opusMagic = Util.getUtf8Bytes("OpusHead");

    public static Track parseTrak(ContainerAtom trak, LeafAtom mvhd, long duration, DrmInitData drmInitData, boolean ignoreEditLists, boolean isQuickTime) throws ParserException {
        ContainerAtom mdia = trak.getContainerAtomOfType(Atom.TYPE_mdia);
        int trackType = parseHdlr(mdia.getLeafAtomOfType(Atom.TYPE_hdlr).data);
        if (trackType == -1) {
            return null;
        } else {
            TkhdData tkhdData = parseTkhd(trak.getLeafAtomOfType(Atom.TYPE_tkhd).data);
            if (duration == -Long.MAX_VALUE) {
                duration = tkhdData.duration;
            }

            long movieTimescale = parseMvhd(mvhd.data);
            long durationUs;
            if (duration == -Long.MAX_VALUE) {
                durationUs = -Long.MAX_VALUE;
            } else {
                durationUs = Util.scaleLargeTimestamp(duration, 1000000L, movieTimescale);
            }

            ContainerAtom stbl = mdia.getContainerAtomOfType(Atom.TYPE_minf).getContainerAtomOfType(Atom.TYPE_stbl);
            Pair<Long, String> mdhdData = parseMdhd(mdia.getLeafAtomOfType(Atom.TYPE_mdhd).data);
            StsdData stsdData = parseStsd(stbl.getLeafAtomOfType(Atom.TYPE_stsd).data, tkhdData.id, tkhdData.rotationDegrees, mdhdData.second, drmInitData, isQuickTime);
            long[] editListDurations = null;
            long[] editListMediaTimes = null;
            if (!ignoreEditLists) {
                Pair<long[], long[]> edtsData = parseEdts(trak.getContainerAtomOfType(Atom.TYPE_edts));
                editListDurations = edtsData.first;
                editListMediaTimes = edtsData.second;
            }

            return stsdData.format == null ? null : new Track(tkhdData.id, trackType, mdhdData.first, movieTimescale, durationUs, stsdData.format, stsdData.requiredSampleTransformation, stsdData.trackEncryptionBoxes, stsdData.nalUnitLengthFieldLength, editListDurations, editListMediaTimes);
        }
    }

    public static TrackSampleTable parseStbl(Track track, ContainerAtom stblAtom, GaplessInfoHolder gaplessInfoHolder) throws ParserException {
        LeafAtom stszAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_stsz);
        SampleSizeBox sampleSizeBox;
        if (stszAtom != null) {
            sampleSizeBox = new StszSampleSizeBox(stszAtom);
        } else {
            LeafAtom stz2Atom = stblAtom.getLeafAtomOfType(Atom.TYPE_stz2);
            if (stz2Atom == null) {
                throw new ParserException("Track has no sample table size information");
            }

            sampleSizeBox = new Stz2SampleSizeBox(stz2Atom);
        }

        int sampleCount = sampleSizeBox.getSampleCount();
        if (sampleCount == 0) {
            return new TrackSampleTable(track, new long[0], new int[0], 0, new long[0], new int[0], -Long.MAX_VALUE);
        } else {
            boolean chunkOffsetsAreLongs = false;
            LeafAtom chunkOffsetsAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_stco);
            if (chunkOffsetsAtom == null) {
                chunkOffsetsAreLongs = true;
                chunkOffsetsAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_co64);
            }

            ParsableByteArray chunkOffsets = chunkOffsetsAtom.data;
            ParsableByteArray stsc = stblAtom.getLeafAtomOfType(Atom.TYPE_stsc).data;
            ParsableByteArray stts = stblAtom.getLeafAtomOfType(Atom.TYPE_stts).data;
            LeafAtom stssAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_stss);
            ParsableByteArray stss = stssAtom != null ? stssAtom.data : null;
            LeafAtom cttsAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_ctts);
            ParsableByteArray ctts = cttsAtom != null ? cttsAtom.data : null;
            ChunkIterator chunkIterator = new ChunkIterator(stsc, chunkOffsets, chunkOffsetsAreLongs);
            stts.setPosition(12);
            int remainingTimestampDeltaChanges = stts.readUnsignedIntToInt() - 1;
            int remainingSamplesAtTimestampDelta = stts.readUnsignedIntToInt();
            int timestampDeltaInTimeUnits = stts.readUnsignedIntToInt();
            int remainingSamplesAtTimestampOffset = 0;
            int remainingTimestampOffsetChanges = 0;
            int timestampOffset = 0;
            if (ctts != null) {
                ctts.setPosition(12);
                remainingTimestampOffsetChanges = ctts.readUnsignedIntToInt();
            }

            int nextSynchronizationSampleIndex = -1;
            int remainingSynchronizationSamples = 0;
            if (stss != null) {
                stss.setPosition(12);
                remainingSynchronizationSamples = stss.readUnsignedIntToInt();
                if (remainingSynchronizationSamples > 0) {
                    nextSynchronizationSampleIndex = stss.readUnsignedIntToInt() - 1;
                } else {
                    stss = null;
                }
            }

            boolean isFixedSampleSizeRawAudio = sampleSizeBox.isFixedSampleSize() && "audio/raw".equals(track.format.sampleMimeType) && remainingTimestampDeltaChanges == 0 && remainingTimestampOffsetChanges == 0 && remainingSynchronizationSamples == 0;
            int maximumSize = 0;
            long timestampTimeUnits = 0L;
            long[] offsets;
            int[] sizes;
            long[] timestamps;
            int[] flags;
            long duration;
            long offset;
            int remainingSamplesInChunk;
            int editedSampleCount;
            if (!isFixedSampleSizeRawAudio) {
                offsets = new long[sampleCount];
                sizes = new int[sampleCount];
                timestamps = new long[sampleCount];
                flags = new int[sampleCount];
                offset = 0L;
                remainingSamplesInChunk = 0;

                for (editedSampleCount = 0; editedSampleCount < sampleCount; ++editedSampleCount) {
                    boolean chunkDataComplete;
                    for (chunkDataComplete = true; remainingSamplesInChunk == 0 && (chunkDataComplete = chunkIterator.moveNext()); remainingSamplesInChunk = chunkIterator.numSamples) {
                        offset = chunkIterator.offset;
                    }

                    if (!chunkDataComplete) {
                        Log.w("AtomParsers", "Unexpected end of chunk data");
                        sampleCount = editedSampleCount;
                        offsets = Arrays.copyOf(offsets, editedSampleCount);
                        sizes = Arrays.copyOf(sizes, editedSampleCount);
                        timestamps = Arrays.copyOf(timestamps, editedSampleCount);
                        flags = Arrays.copyOf(flags, editedSampleCount);
                        break;
                    }

                    if (ctts != null) {
                        while (remainingSamplesAtTimestampOffset == 0 && remainingTimestampOffsetChanges > 0) {
                            remainingSamplesAtTimestampOffset = ctts.readUnsignedIntToInt();
                            timestampOffset = ctts.readInt();
                            --remainingTimestampOffsetChanges;
                        }

                        --remainingSamplesAtTimestampOffset;
                    }

                    offsets[editedSampleCount] = offset;
                    sizes[editedSampleCount] = sampleSizeBox.readNextSampleSize();
                    if (sizes[editedSampleCount] > maximumSize) {
                        maximumSize = sizes[editedSampleCount];
                    }

                    timestamps[editedSampleCount] = timestampTimeUnits + (long) timestampOffset;
                    flags[editedSampleCount] = stss == null ? 1 : 0;
                    if (editedSampleCount == nextSynchronizationSampleIndex) {
                        flags[editedSampleCount] = 1;
                        --remainingSynchronizationSamples;
                        if (remainingSynchronizationSamples > 0) {
                            nextSynchronizationSampleIndex = stss.readUnsignedIntToInt() - 1;
                        }
                    }

                    timestampTimeUnits += timestampDeltaInTimeUnits;
                    --remainingSamplesAtTimestampDelta;
                    if (remainingSamplesAtTimestampDelta == 0 && remainingTimestampDeltaChanges > 0) {
                        remainingSamplesAtTimestampDelta = stts.readUnsignedIntToInt();
                        timestampDeltaInTimeUnits = stts.readInt();
                        --remainingTimestampDeltaChanges;
                    }

                    offset += sizes[editedSampleCount];
                    --remainingSamplesInChunk;
                }

                duration = timestampTimeUnits + (long) timestampOffset;

                boolean isCttsValid;
                for (isCttsValid = true; remainingTimestampOffsetChanges > 0; --remainingTimestampOffsetChanges) {
                    if (ctts.readUnsignedIntToInt() != 0) {
                        isCttsValid = false;
                        break;
                    }

                    ctts.readInt();
                }

                if (remainingSynchronizationSamples != 0 || remainingSamplesAtTimestampDelta != 0 || remainingSamplesInChunk != 0 || remainingTimestampDeltaChanges != 0 || remainingSamplesAtTimestampOffset != 0 || !isCttsValid) {
                    Log.w("AtomParsers", "Inconsistent stbl box for track " + track.id + ": remainingSynchronizationSamples " + remainingSynchronizationSamples + ", remainingSamplesAtTimestampDelta " + remainingSamplesAtTimestampDelta + ", remainingSamplesInChunk " + remainingSamplesInChunk + ", remainingTimestampDeltaChanges " + remainingTimestampDeltaChanges + ", remainingSamplesAtTimestampOffset " + remainingSamplesAtTimestampOffset + (!isCttsValid ? ", ctts invalid" : ""));
                }
            } else {
                long[] chunkOffsetsBytes = new long[chunkIterator.length];

                int[] chunkSampleCounts;
                for (chunkSampleCounts = new int[chunkIterator.length]; chunkIterator.moveNext(); chunkSampleCounts[chunkIterator.index] = chunkIterator.numSamples) {
                    chunkOffsetsBytes[chunkIterator.index] = chunkIterator.offset;
                }

                remainingSamplesInChunk = Util.getPcmFrameSize(track.format.pcmEncoding, track.format.channelCount);
                Results rechunkedResults = FixedSampleSizeRechunker.rechunk(remainingSamplesInChunk, chunkOffsetsBytes, chunkSampleCounts, timestampDeltaInTimeUnits);
                offsets = rechunkedResults.offsets;
                sizes = rechunkedResults.sizes;
                maximumSize = rechunkedResults.maximumSize;
                timestamps = rechunkedResults.timestamps;
                flags = rechunkedResults.flags;
                duration = rechunkedResults.duration;
            }

            offset = Util.scaleLargeTimestamp(duration, 1000000L, track.timescale);
            if (track.editListDurations != null && !gaplessInfoHolder.hasGaplessInfo()) {
                long editStartTime;
                if (track.editListDurations.length == 1 && track.type == 1 && timestamps.length >= 2) {
                    editStartTime = track.editListMediaTimes[0];
                    long editEndTime = editStartTime + Util.scaleLargeTimestamp(track.editListDurations[0], track.timescale, track.movieTimescale);
                    if (canApplyEditWithGaplessInfo(timestamps, duration, editStartTime, editEndTime)) {
                        long paddingTimeUnits = duration - editEndTime;
                        long encoderDelay = Util.scaleLargeTimestamp(editStartTime - timestamps[0], track.format.sampleRate, track.timescale);
                        long encoderPadding = Util.scaleLargeTimestamp(paddingTimeUnits, track.format.sampleRate, track.timescale);
                        if ((encoderDelay != 0L || encoderPadding != 0L) && encoderDelay <= 2147483647L && encoderPadding <= 2147483647L) {
                            gaplessInfoHolder.encoderDelay = (int) encoderDelay;
                            gaplessInfoHolder.encoderPadding = (int) encoderPadding;
                            Util.scaleLargeTimestampsInPlace(timestamps, 1000000L, track.timescale);
                            long editedDurationUs = Util.scaleLargeTimestamp(track.editListDurations[0], 1000000L, track.movieTimescale);
                            return new TrackSampleTable(track, offsets, sizes, maximumSize, timestamps, flags, editedDurationUs);
                        }
                    }
                }

                int nextSampleIndex;
                if (track.editListDurations.length == 1 && track.editListDurations[0] == 0L) {
                    editStartTime = track.editListMediaTimes[0];

                    for (nextSampleIndex = 0; nextSampleIndex < timestamps.length; ++nextSampleIndex) {
                        timestamps[nextSampleIndex] = Util.scaleLargeTimestamp(timestamps[nextSampleIndex] - editStartTime, 1000000L, track.timescale);
                    }

                    offset = Util.scaleLargeTimestamp(duration - editStartTime, 1000000L, track.timescale);
                    return new TrackSampleTable(track, offsets, sizes, maximumSize, timestamps, flags, offset);
                } else {
                    boolean omitClippedSample = track.type == 1;
                    editedSampleCount = 0;
                    nextSampleIndex = 0;
                    boolean copyMetadata = false;
                    int[] startIndices = new int[track.editListDurations.length];
                    int[] endIndices = new int[track.editListDurations.length];

                    for (int i = 0; i < track.editListDurations.length; ++i) {
                        long editMediaTime = track.editListMediaTimes[i];
                        if (editMediaTime != -1L) {
                            long editDuration = Util.scaleLargeTimestamp(track.editListDurations[i], track.timescale, track.movieTimescale);
                            startIndices[i] = Util.binarySearchCeil(timestamps, editMediaTime, true, true);

                            int var10002;
                            for (endIndices[i] = Util.binarySearchCeil(timestamps, editMediaTime + editDuration, omitClippedSample, false); startIndices[i] < endIndices[i] && (flags[startIndices[i]] & 1) == 0; var10002 = startIndices[i]++) {
                            }

                            editedSampleCount += endIndices[i] - startIndices[i];
                            copyMetadata |= nextSampleIndex != startIndices[i];
                            nextSampleIndex = endIndices[i];
                        }
                    }

                    copyMetadata |= editedSampleCount != sampleCount;
                    long[] editedOffsets = copyMetadata ? new long[editedSampleCount] : offsets;
                    int[] editedSizes = copyMetadata ? new int[editedSampleCount] : sizes;
                    int editedMaximumSize = copyMetadata ? 0 : maximumSize;
                    int[] editedFlags = copyMetadata ? new int[editedSampleCount] : flags;
                    long[] editedTimestamps = new long[editedSampleCount];
                    long pts = 0L;
                    int sampleIndex = 0;

                    for (int i = 0; i < track.editListDurations.length; ++i) {
                        long editMediaTime = track.editListMediaTimes[i];
                        int startIndex = startIndices[i];
                        int endIndex = endIndices[i];
                        int j;
                        if (copyMetadata) {
                            j = endIndex - startIndex;
                            System.arraycopy(offsets, startIndex, editedOffsets, sampleIndex, j);
                            System.arraycopy(sizes, startIndex, editedSizes, sampleIndex, j);
                            System.arraycopy(flags, startIndex, editedFlags, sampleIndex, j);
                        }

                        for (j = startIndex; j < endIndex; ++j) {
                            long ptsUs = Util.scaleLargeTimestamp(pts, 1000000L, track.movieTimescale);
                            long timeInSegmentUs = Util.scaleLargeTimestamp(timestamps[j] - editMediaTime, 1000000L, track.timescale);
                            editedTimestamps[sampleIndex] = ptsUs + timeInSegmentUs;
                            if (copyMetadata && editedSizes[sampleIndex] > editedMaximumSize) {
                                editedMaximumSize = sizes[j];
                            }

                            ++sampleIndex;
                        }

                        pts += track.editListDurations[i];
                    }

                    long editedDurationUs = Util.scaleLargeTimestamp(pts, 1000000L, track.movieTimescale);
                    return new TrackSampleTable(track, editedOffsets, editedSizes, editedMaximumSize, editedTimestamps, editedFlags, editedDurationUs);
                }
            } else {
                Util.scaleLargeTimestampsInPlace(timestamps, 1000000L, track.timescale);
                return new TrackSampleTable(track, offsets, sizes, maximumSize, timestamps, flags, offset);
            }
        }
    }

    public static Metadata parseUdta(LeafAtom udtaAtom, boolean isQuickTime) {
        if (isQuickTime) {
            return null;
        } else {
            ParsableByteArray udtaData = udtaAtom.data;
            udtaData.setPosition(8);

            while (udtaData.bytesLeft() >= 8) {
                int atomPosition = udtaData.getPosition();
                int atomSize = udtaData.readInt();
                int atomType = udtaData.readInt();
                if (atomType == Atom.TYPE_meta) {
                    udtaData.setPosition(atomPosition);
                    return parseMetaAtom(udtaData, atomPosition + atomSize);
                }

                udtaData.skipBytes(atomSize - 8);
            }

            return null;
        }
    }

    private static Metadata parseMetaAtom(ParsableByteArray meta, int limit) {
        meta.skipBytes(12);

        while (meta.getPosition() < limit) {
            int atomPosition = meta.getPosition();
            int atomSize = meta.readInt();
            int atomType = meta.readInt();
            if (atomType == Atom.TYPE_ilst) {
                meta.setPosition(atomPosition);
                return parseIlst(meta, atomPosition + atomSize);
            }

            meta.skipBytes(atomSize - 8);
        }

        return null;
    }

    private static Metadata parseIlst(ParsableByteArray ilst, int limit) {
        ilst.skipBytes(8);
        ArrayList<Entry> entries = new ArrayList<>();

        while (ilst.getPosition() < limit) {
            Entry entry = MetadataUtil.parseIlstElement(ilst);
            if (entry != null) {
                entries.add(entry);
            }
        }

        return entries.isEmpty() ? null : new Metadata(entries);
    }

    private static long parseMvhd(ParsableByteArray mvhd) {
        mvhd.setPosition(8);
        int fullAtom = mvhd.readInt();
        int version = Atom.parseFullAtomVersion(fullAtom);
        mvhd.skipBytes(version == 0 ? 8 : 16);
        return mvhd.readUnsignedInt();
    }

    private static TkhdData parseTkhd(ParsableByteArray tkhd) {
        tkhd.setPosition(8);
        int fullAtom = tkhd.readInt();
        int version = Atom.parseFullAtomVersion(fullAtom);
        tkhd.skipBytes(version == 0 ? 8 : 16);
        int trackId = tkhd.readInt();
        tkhd.skipBytes(4);
        boolean durationUnknown = true;
        int durationPosition = tkhd.getPosition();
        int durationByteCount = version == 0 ? 4 : 8;

        for (int i = 0; i < durationByteCount; ++i) {
            if (tkhd.data[durationPosition + i] != -1) {
                durationUnknown = false;
                break;
            }
        }

        long duration;
        if (durationUnknown) {
            tkhd.skipBytes(durationByteCount);
            duration = -Long.MAX_VALUE;
        } else {
            duration = version == 0 ? tkhd.readUnsignedInt() : tkhd.readUnsignedLongToLong();
            if (duration == 0L) {
                duration = -Long.MAX_VALUE;
            }
        }

        tkhd.skipBytes(16);
        int a00 = tkhd.readInt();
        int a01 = tkhd.readInt();
        tkhd.skipBytes(4);
        int a10 = tkhd.readInt();
        int a11 = tkhd.readInt();
        int fixedOne = 65536;
        short rotationDegrees;
        if (a00 == 0 && a01 == fixedOne && a10 == -fixedOne && a11 == 0) {
            rotationDegrees = 90;
        } else if (a00 == 0 && a01 == -fixedOne && a10 == fixedOne && a11 == 0) {
            rotationDegrees = 270;
        } else if (a00 == -fixedOne && a01 == 0 && a10 == 0 && a11 == -fixedOne) {
            rotationDegrees = 180;
        } else {
            rotationDegrees = 0;
        }

        return new TkhdData(trackId, duration, rotationDegrees);
    }

    private static int parseHdlr(ParsableByteArray hdlr) {
        hdlr.setPosition(16);
        int trackType = hdlr.readInt();
        if (trackType == TYPE_soun) {
            return 1;
        } else if (trackType == TYPE_vide) {
            return 2;
        } else if (trackType != TYPE_text && trackType != TYPE_sbtl && trackType != TYPE_subt && trackType != TYPE_clcp) {
            return trackType == TYPE_meta ? 4 : -1;
        } else {
            return 3;
        }
    }

    private static Pair<Long, String> parseMdhd(ParsableByteArray mdhd) {
        mdhd.setPosition(8);
        int fullAtom = mdhd.readInt();
        int version = Atom.parseFullAtomVersion(fullAtom);
        mdhd.skipBytes(version == 0 ? 8 : 16);
        long timescale = mdhd.readUnsignedInt();
        mdhd.skipBytes(version == 0 ? 4 : 8);
        int languageCode = mdhd.readUnsignedShort();
        String language = "" + (char) ((languageCode >> 10 & 31) + 96) + (char) ((languageCode >> 5 & 31) + 96) + (char) ((languageCode & 31) + 96);
        return Pair.create(timescale, language);
    }

    private static StsdData parseStsd(ParsableByteArray stsd, int trackId, int rotationDegrees, String language, DrmInitData drmInitData, boolean isQuickTime) throws ParserException {
        stsd.setPosition(12);
        int numberOfEntries = stsd.readInt();
        StsdData out = new StsdData(numberOfEntries);

        for (int i = 0; i < numberOfEntries; ++i) {
            int childStartPosition = stsd.getPosition();
            int childAtomSize = stsd.readInt();
            Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
            int childAtomType = stsd.readInt();
            if (childAtomType != Atom.TYPE_avc1 && childAtomType != Atom.TYPE_avc3 && childAtomType != Atom.TYPE_encv && childAtomType != Atom.TYPE_mp4v && childAtomType != Atom.TYPE_hvc1 && childAtomType != Atom.TYPE_hev1 && childAtomType != Atom.TYPE_s263 && childAtomType != Atom.TYPE_vp08 && childAtomType != Atom.TYPE_vp09) {
                if (childAtomType != Atom.TYPE_mp4a && childAtomType != Atom.TYPE_enca && childAtomType != Atom.TYPE_ac_3 && childAtomType != Atom.TYPE_ec_3 && childAtomType != Atom.TYPE_dtsc && childAtomType != Atom.TYPE_dtse && childAtomType != Atom.TYPE_dtsh && childAtomType != Atom.TYPE_dtsl && childAtomType != Atom.TYPE_samr && childAtomType != Atom.TYPE_sawb && childAtomType != Atom.TYPE_lpcm && childAtomType != Atom.TYPE_sowt && childAtomType != Atom.TYPE__mp3 && childAtomType != Atom.TYPE_alac && childAtomType != Atom.TYPE_alaw && childAtomType != Atom.TYPE_ulaw && childAtomType != Atom.TYPE_Opus && childAtomType != Atom.TYPE_fLaC) {
                    if (childAtomType != Atom.TYPE_TTML && childAtomType != Atom.TYPE_tx3g && childAtomType != Atom.TYPE_wvtt && childAtomType != Atom.TYPE_stpp && childAtomType != Atom.TYPE_c608) {
                        if (childAtomType == Atom.TYPE_camm) {
                            out.format = Format.createSampleFormat(Integer.toString(trackId), "application/x-camera-motion", null, -1, null);
                        }
                    } else {
                        parseTextSampleEntry(stsd, childAtomType, childStartPosition, childAtomSize, trackId, language, out);
                    }
                } else {
                    parseAudioSampleEntry(stsd, childAtomType, childStartPosition, childAtomSize, trackId, language, isQuickTime, drmInitData, out, i);
                }
            } else {
                parseVideoSampleEntry(stsd, childAtomType, childStartPosition, childAtomSize, trackId, rotationDegrees, drmInitData, out, i);
            }

            stsd.setPosition(childStartPosition + childAtomSize);
        }

        return out;
    }

    private static void parseTextSampleEntry(ParsableByteArray parent, int atomType, int position, int atomSize, int trackId, String language, StsdData out) throws ParserException {
        parent.setPosition(position + 8 + 8);
        List<byte[]> initializationData = null;
        long subSampleOffsetUs = Long.MAX_VALUE;
        String mimeType;
        if (atomType == Atom.TYPE_TTML) {
            mimeType = "application/ttml+xml";
        } else if (atomType == Atom.TYPE_tx3g) {
            mimeType = "application/x-quicktime-tx3g";
            int sampleDescriptionLength = atomSize - 8 - 8;
            byte[] sampleDescriptionData = new byte[sampleDescriptionLength];
            parent.readBytes(sampleDescriptionData, 0, sampleDescriptionLength);
            initializationData = Collections.singletonList(sampleDescriptionData);
        } else if (atomType == Atom.TYPE_wvtt) {
            mimeType = "application/x-mp4-vtt";
        } else if (atomType == Atom.TYPE_stpp) {
            mimeType = "application/ttml+xml";
            subSampleOffsetUs = 0L;
        } else {
            if (atomType != Atom.TYPE_c608) {
                throw new IllegalStateException();
            }

            mimeType = "application/x-mp4-cea-608";
            out.requiredSampleTransformation = 1;
        }

        out.format = Format.createTextSampleFormat(Integer.toString(trackId), mimeType, null, -1, 0, language, -1, null, subSampleOffsetUs, initializationData);
    }

    private static void parseVideoSampleEntry(ParsableByteArray parent, int atomType, int position, int size, int trackId, int rotationDegrees, DrmInitData drmInitData, StsdData out, int entryIndex) throws ParserException {
        parent.setPosition(position + 8 + 8);
        parent.skipBytes(16);
        int width = parent.readUnsignedShort();
        int height = parent.readUnsignedShort();
        boolean pixelWidthHeightRatioFromPasp = false;
        float pixelWidthHeightRatio = 1.0F;
        parent.skipBytes(50);
        int childPosition = parent.getPosition();
        if (atomType == Atom.TYPE_encv) {
            Pair<Integer, TrackEncryptionBox> sampleEntryEncryptionData = parseSampleEntryEncryptionData(parent, position, size);
            if (sampleEntryEncryptionData != null) {
                atomType = sampleEntryEncryptionData.first;
                drmInitData = drmInitData == null ? null : drmInitData.copyWithSchemeType(sampleEntryEncryptionData.second.schemeType);
                out.trackEncryptionBoxes[entryIndex] = sampleEntryEncryptionData.second;
            }

            parent.setPosition(childPosition);
        }

        List<byte[]> initializationData = null;
        String mimeType = null;
        byte[] projectionData = null;

        byte stereoMode;
        int childAtomSize;
        for (stereoMode = -1; childPosition - position < size; childPosition += childAtomSize) {
            parent.setPosition(childPosition);
            int childStartPosition = parent.getPosition();
            childAtomSize = parent.readInt();
            if (childAtomSize == 0 && parent.getPosition() - position == size) {
                break;
            }

            Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
            int childAtomType = parent.readInt();
            if (childAtomType == Atom.TYPE_avcC) {
                Assertions.checkState(mimeType == null);
                mimeType = "video/avc";
                parent.setPosition(childStartPosition + 8);
                AvcConfig avcConfig = AvcConfig.parse(parent);
                initializationData = avcConfig.initializationData;
                out.nalUnitLengthFieldLength = avcConfig.nalUnitLengthFieldLength;
                if (!pixelWidthHeightRatioFromPasp) {
                    pixelWidthHeightRatio = avcConfig.pixelWidthAspectRatio;
                }
            } else if (childAtomType == Atom.TYPE_hvcC) {
                Assertions.checkState(mimeType == null);
                mimeType = "video/hevc";
                parent.setPosition(childStartPosition + 8);
                HevcConfig hevcConfig = HevcConfig.parse(parent);
                initializationData = hevcConfig.initializationData;
                out.nalUnitLengthFieldLength = hevcConfig.nalUnitLengthFieldLength;
            } else if (childAtomType == Atom.TYPE_vpcC) {
                Assertions.checkState(mimeType == null);
                mimeType = atomType == Atom.TYPE_vp08 ? "video/x-vnd.on2.vp8" : "video/x-vnd.on2.vp9";
            } else if (childAtomType == Atom.TYPE_d263) {
                Assertions.checkState(mimeType == null);
                mimeType = "video/3gpp";
            } else if (childAtomType == Atom.TYPE_esds) {
                Assertions.checkState(mimeType == null);
                Pair<String, byte[]> mimeTypeAndInitializationData = parseEsdsFromParent(parent, childStartPosition);
                mimeType = mimeTypeAndInitializationData.first;
                initializationData = Collections.singletonList(mimeTypeAndInitializationData.second);
            } else if (childAtomType == Atom.TYPE_pasp) {
                pixelWidthHeightRatio = parsePaspFromParent(parent, childStartPosition);
                pixelWidthHeightRatioFromPasp = true;
            } else if (childAtomType == Atom.TYPE_sv3d) {
                projectionData = parseProjFromParent(parent, childStartPosition, childAtomSize);
            } else if (childAtomType == Atom.TYPE_st3d) {
                int version = parent.readUnsignedByte();
                parent.skipBytes(3);
                if (version == 0) {
                    int layout = parent.readUnsignedByte();
                    switch (layout) {
                        case 0:
                            stereoMode = 0;
                            break;
                        case 1:
                            stereoMode = 1;
                            break;
                        case 2:
                            stereoMode = 2;
                            break;
                        case 3:
                            stereoMode = 3;
                    }
                }
            }
        }

        if (mimeType != null) {
            out.format = Format.createVideoSampleFormat(Integer.toString(trackId), mimeType, null, -1, -1, width, height, -1.0F, initializationData, rotationDegrees, pixelWidthHeightRatio, projectionData, stereoMode, null, drmInitData);
        }
    }

    private static Pair<long[], long[]> parseEdts(ContainerAtom edtsAtom) {
        LeafAtom elst;
        if (edtsAtom != null && (elst = edtsAtom.getLeafAtomOfType(Atom.TYPE_elst)) != null) {
            ParsableByteArray elstData = elst.data;
            elstData.setPosition(8);
            int fullAtom = elstData.readInt();
            int version = Atom.parseFullAtomVersion(fullAtom);
            int entryCount = elstData.readUnsignedIntToInt();
            long[] editListDurations = new long[entryCount];
            long[] editListMediaTimes = new long[entryCount];

            for (int i = 0; i < entryCount; ++i) {
                editListDurations[i] = version == 1 ? elstData.readUnsignedLongToLong() : elstData.readUnsignedInt();
                editListMediaTimes[i] = version == 1 ? elstData.readLong() : (long) elstData.readInt();
                int mediaRateInteger = elstData.readShort();
                if (mediaRateInteger != 1) {
                    throw new IllegalArgumentException("Unsupported media rate.");
                }

                elstData.skipBytes(2);
            }
            return Pair.create(editListDurations, editListMediaTimes);
        } else {
            return Pair.create(null, null);
        }
    }

    private static float parsePaspFromParent(ParsableByteArray parent, int position) {
        parent.setPosition(position + 8);
        int hSpacing = parent.readUnsignedIntToInt();
        int vSpacing = parent.readUnsignedIntToInt();
        return (float) hSpacing / (float) vSpacing;
    }

    private static void parseAudioSampleEntry(ParsableByteArray parent, int atomType, int position, int size, int trackId, String language, boolean isQuickTime, DrmInitData drmInitData, StsdData out, int entryIndex) throws ParserException {
        parent.setPosition(position + 8 + 8);
        int quickTimeSoundDescriptionVersion = 0;
        if (isQuickTime) {
            quickTimeSoundDescriptionVersion = parent.readUnsignedShort();
            parent.skipBytes(6);
        } else {
            parent.skipBytes(8);
        }

        int channelCount;
        int sampleRate;
        if (quickTimeSoundDescriptionVersion != 0 && quickTimeSoundDescriptionVersion != 1) {
            if (quickTimeSoundDescriptionVersion != 2) {
                return;
            }

            parent.skipBytes(16);
            sampleRate = (int) Math.round(parent.readDouble());
            channelCount = parent.readUnsignedIntToInt();
            parent.skipBytes(20);
        } else {
            channelCount = parent.readUnsignedShort();
            parent.skipBytes(6);
            sampleRate = parent.readUnsignedFixedPoint1616();
            if (quickTimeSoundDescriptionVersion == 1) {
                parent.skipBytes(16);
            }
        }

        int childPosition = parent.getPosition();
        if (atomType == Atom.TYPE_enca) {
            Pair<Integer, TrackEncryptionBox> sampleEntryEncryptionData = parseSampleEntryEncryptionData(parent, position, size);
            if (sampleEntryEncryptionData != null) {
                atomType = sampleEntryEncryptionData.first;
                drmInitData = drmInitData == null ? null : drmInitData.copyWithSchemeType(sampleEntryEncryptionData.second.schemeType);
                out.trackEncryptionBoxes[entryIndex] = sampleEntryEncryptionData.second;
            }

            parent.setPosition(childPosition);
        }

        String mimeType = null;
        if (atomType == Atom.TYPE_ac_3) {
            mimeType = "audio/ac3";
        } else if (atomType == Atom.TYPE_ec_3) {
            mimeType = "audio/eac3";
        } else if (atomType == Atom.TYPE_dtsc) {
            mimeType = "audio/vnd.dts";
        } else if (atomType != Atom.TYPE_dtsh && atomType != Atom.TYPE_dtsl) {
            if (atomType == Atom.TYPE_dtse) {
                mimeType = "audio/vnd.dts.hd;profile=lbr";
            } else if (atomType == Atom.TYPE_samr) {
                mimeType = "audio/3gpp";
            } else if (atomType == Atom.TYPE_sawb) {
                mimeType = "audio/amr-wb";
            } else if (atomType != Atom.TYPE_lpcm && atomType != Atom.TYPE_sowt) {
                if (atomType == Atom.TYPE__mp3) {
                    mimeType = "audio/mpeg";
                } else if (atomType == Atom.TYPE_alac) {
                    mimeType = "audio/alac";
                } else if (atomType == Atom.TYPE_alaw) {
                    mimeType = "audio/g711-alaw";
                } else if (atomType == Atom.TYPE_ulaw) {
                    mimeType = "audio/g711-mlaw";
                } else if (atomType == Atom.TYPE_Opus) {
                    mimeType = "audio/opus";
                } else if (atomType == Atom.TYPE_fLaC) {
                    mimeType = "audio/flac";
                }
            } else {
                mimeType = "audio/raw";
            }
        } else {
            mimeType = "audio/vnd.dts.hd";
        }

        byte[] initializationData;
        int childAtomSize;
        for (initializationData = null; childPosition - position < size; childPosition += childAtomSize) {
            parent.setPosition(childPosition);
            childAtomSize = parent.readInt();
            Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
            int childAtomType = parent.readInt();
            int childAtomBodySize;
            if (childAtomType == Atom.TYPE_esds || isQuickTime && childAtomType == Atom.TYPE_wave) {
                childAtomBodySize = childAtomType == Atom.TYPE_esds ? childPosition : findEsdsPosition(parent, childPosition, childAtomSize);
                if (childAtomBodySize != -1) {
                    Pair<String, byte[]> mimeTypeAndInitializationData = parseEsdsFromParent(parent, childAtomBodySize);
                    mimeType = mimeTypeAndInitializationData.first;
                    initializationData = mimeTypeAndInitializationData.second;
                    if ("audio/mp4a-latm".equals(mimeType)) {
                        Pair<Integer, Integer> audioSpecificConfig = CodecSpecificDataUtil.parseAacAudioSpecificConfig(initializationData);
                        sampleRate = audioSpecificConfig.first;
                        channelCount = audioSpecificConfig.second;
                    }
                }
            } else if (childAtomType == Atom.TYPE_dac3) {
                parent.setPosition(8 + childPosition);
                out.format = Ac3Util.parseAc3AnnexFFormat(parent, Integer.toString(trackId), language, drmInitData);
            } else if (childAtomType == Atom.TYPE_dec3) {
                parent.setPosition(8 + childPosition);
                out.format = Ac3Util.parseEAc3AnnexFFormat(parent, Integer.toString(trackId), language, drmInitData);
            } else if (childAtomType == Atom.TYPE_ddts) {
                out.format = Format.createAudioSampleFormat(Integer.toString(trackId), mimeType, null, -1, -1, channelCount, sampleRate, null, drmInitData, 0, language);
            } else if (childAtomType == Atom.TYPE_alac) {
                initializationData = new byte[childAtomSize];
                parent.setPosition(childPosition);
                parent.readBytes(initializationData, 0, childAtomSize);
            } else if (childAtomType == Atom.TYPE_dOps) {
                childAtomBodySize = childAtomSize - 8;
                initializationData = new byte[opusMagic.length + childAtomBodySize];
                System.arraycopy(opusMagic, 0, initializationData, 0, opusMagic.length);
                parent.setPosition(childPosition + 8);
                parent.readBytes(initializationData, opusMagic.length, childAtomBodySize);
            } else if (childAtomSize == Atom.TYPE_dfLa) {
                childAtomBodySize = childAtomSize - 12;
                initializationData = new byte[childAtomBodySize];
                parent.setPosition(childPosition + 12);
                parent.readBytes(initializationData, 0, childAtomBodySize);
            }
        }

        if (out.format == null && mimeType != null) {
            childAtomSize = "audio/raw".equals(mimeType) ? 2 : -1;
            out.format = Format.createAudioSampleFormat(Integer.toString(trackId), mimeType, null, -1, -1, channelCount, sampleRate, childAtomSize, initializationData == null ? null : Collections.singletonList(initializationData), drmInitData, 0, language);
        }

    }

    private static int findEsdsPosition(ParsableByteArray parent, int position, int size) {
        int childAtomSize;
        for (int childAtomPosition = parent.getPosition(); childAtomPosition - position < size; childAtomPosition += childAtomSize) {
            parent.setPosition(childAtomPosition);
            childAtomSize = parent.readInt();
            Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
            int childType = parent.readInt();
            if (childType == Atom.TYPE_esds) {
                return childAtomPosition;
            }
        }

        return -1;
    }

    private static Pair<String, byte[]> parseEsdsFromParent(ParsableByteArray parent, int position) {
        parent.setPosition(position + 8 + 4);
        parent.skipBytes(1);
        parseExpandableClassSize(parent);
        parent.skipBytes(2);
        int flags = parent.readUnsignedByte();
        if ((flags & 128) != 0) {
            parent.skipBytes(2);
        }

        if ((flags & 64) != 0) {
            parent.skipBytes(parent.readUnsignedShort());
        }

        if ((flags & 32) != 0) {
            parent.skipBytes(2);
        }

        parent.skipBytes(1);
        parseExpandableClassSize(parent);
        int objectTypeIndication = parent.readUnsignedByte();
        String mimeType = MimeTypes.getMimeTypeFromMp4ObjectType(objectTypeIndication);
        if (!"audio/mpeg".equals(mimeType) && !"audio/vnd.dts".equals(mimeType) && !"audio/vnd.dts.hd".equals(mimeType)) {
            parent.skipBytes(12);
            parent.skipBytes(1);
            int initializationDataSize = parseExpandableClassSize(parent);
            byte[] initializationData = new byte[initializationDataSize];
            parent.readBytes(initializationData, 0, initializationDataSize);
            return Pair.create(mimeType, initializationData);
        } else {
            return Pair.create(mimeType, null);
        }
    }

    private static Pair<Integer, TrackEncryptionBox> parseSampleEntryEncryptionData(ParsableByteArray parent, int position, int size) {
        int childAtomSize;
        for (int childPosition = parent.getPosition(); childPosition - position < size; childPosition += childAtomSize) {
            parent.setPosition(childPosition);
            childAtomSize = parent.readInt();
            Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
            int childAtomType = parent.readInt();
            if (childAtomType == Atom.TYPE_sinf) {
                Pair<Integer, TrackEncryptionBox> result = parseCommonEncryptionSinfFromParent(parent, childPosition, childAtomSize);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    static Pair<Integer, TrackEncryptionBox> parseCommonEncryptionSinfFromParent(ParsableByteArray parent, int position, int size) {
        int childPosition = position + 8;
        int schemeInformationBoxPosition = -1;
        int schemeInformationBoxSize = 0;
        String schemeType = null;

        Integer dataFormat;
        int childAtomSize;
        for (dataFormat = null; childPosition - position < size; childPosition += childAtomSize) {
            parent.setPosition(childPosition);
            childAtomSize = parent.readInt();
            int childAtomType = parent.readInt();
            if (childAtomType == Atom.TYPE_frma) {
                dataFormat = parent.readInt();
            } else if (childAtomType == Atom.TYPE_schm) {
                parent.skipBytes(4);
                schemeType = parent.readString(4);
            } else if (childAtomType == Atom.TYPE_schi) {
                schemeInformationBoxPosition = childPosition;
                schemeInformationBoxSize = childAtomSize;
            }
        }

        if (!"cenc".equals(schemeType) && !"cbc1".equals(schemeType) && !"cens".equals(schemeType) && !"cbcs".equals(schemeType)) {
            return null;
        } else {
            Assertions.checkArgument(dataFormat != null, "frma atom is mandatory");
            Assertions.checkArgument(schemeInformationBoxPosition != -1, "schi atom is mandatory");
            TrackEncryptionBox encryptionBox = parseSchiFromParent(parent, schemeInformationBoxPosition, schemeInformationBoxSize, schemeType);
            Assertions.checkArgument(encryptionBox != null, "tenc atom is mandatory");
            return Pair.create(dataFormat, encryptionBox);
        }
    }

    private static TrackEncryptionBox parseSchiFromParent(ParsableByteArray parent, int position, int size, String schemeType) {
        int childAtomSize;
        for (int childPosition = position + 8; childPosition - position < size; childPosition += childAtomSize) {
            parent.setPosition(childPosition);
            childAtomSize = parent.readInt();
            int childAtomType = parent.readInt();
            if (childAtomType == Atom.TYPE_tenc) {
                int fullAtom = parent.readInt();
                int version = Atom.parseFullAtomVersion(fullAtom);
                parent.skipBytes(1);
                int defaultCryptByteBlock = 0;
                int defaultSkipByteBlock = 0;
                if (version == 0) {
                    parent.skipBytes(1);
                } else {
                    int patternByte = parent.readUnsignedByte();
                    defaultCryptByteBlock = (patternByte & 240) >> 4;
                    defaultSkipByteBlock = patternByte & 15;
                }

                boolean defaultIsProtected = parent.readUnsignedByte() == 1;
                int defaultPerSampleIvSize = parent.readUnsignedByte();
                byte[] defaultKeyId = new byte[16];
                parent.readBytes(defaultKeyId, 0, defaultKeyId.length);
                byte[] constantIv = null;
                if (defaultIsProtected && defaultPerSampleIvSize == 0) {
                    int constantIvSize = parent.readUnsignedByte();
                    constantIv = new byte[constantIvSize];
                    parent.readBytes(constantIv, 0, constantIvSize);
                }

                return new TrackEncryptionBox(defaultIsProtected, schemeType, defaultPerSampleIvSize, defaultKeyId, defaultCryptByteBlock, defaultSkipByteBlock, constantIv);
            }
        }

        return null;
    }

    private static byte[] parseProjFromParent(ParsableByteArray parent, int position, int size) {
        int childAtomSize;
        for (int childPosition = position + 8; childPosition - position < size; childPosition += childAtomSize) {
            parent.setPosition(childPosition);
            childAtomSize = parent.readInt();
            int childAtomType = parent.readInt();
            if (childAtomType == Atom.TYPE_proj) {
                return Arrays.copyOfRange(parent.data, childPosition, childPosition + childAtomSize);
            }
        }

        return null;
    }

    private static int parseExpandableClassSize(ParsableByteArray data) {
        int currentByte = data.readUnsignedByte();

        int size;
        for (size = currentByte & 127; (currentByte & 128) == 128; size = size << 7 | currentByte & 127) {
            currentByte = data.readUnsignedByte();
        }

        return size;
    }

    private static boolean canApplyEditWithGaplessInfo(long[] timestamps, long duration, long editStartTime, long editEndTime) {
        int lastIndex = timestamps.length - 1;
        int latestDelayIndex = Util.constrainValue(3, 0, lastIndex);
        int earliestPaddingIndex = Util.constrainValue(timestamps.length - 3, 0, lastIndex);
        return timestamps[0] <= editStartTime && editStartTime < timestamps[latestDelayIndex] && timestamps[earliestPaddingIndex] < editEndTime && editEndTime <= duration;
    }

    private AtomParsers() {
    }

    static final class Stz2SampleSizeBox implements SampleSizeBox {
        private final ParsableByteArray data;
        private final int sampleCount;
        private final int fieldSize;
        private int sampleIndex;
        private int currentByte;

        public Stz2SampleSizeBox(LeafAtom stz2Atom) {
            this.data = stz2Atom.data;
            this.data.setPosition(12);
            this.fieldSize = this.data.readUnsignedIntToInt() & 255;
            this.sampleCount = this.data.readUnsignedIntToInt();
        }

        public int getSampleCount() {
            return this.sampleCount;
        }

        public int readNextSampleSize() {
            if (this.fieldSize == 8) {
                return this.data.readUnsignedByte();
            } else if (this.fieldSize == 16) {
                return this.data.readUnsignedShort();
            } else if (this.sampleIndex++ % 2 == 0) {
                this.currentByte = this.data.readUnsignedByte();
                return (this.currentByte & 240) >> 4;
            } else {
                return this.currentByte & 15;
            }
        }

        public boolean isFixedSampleSize() {
            return false;
        }
    }

    static final class StszSampleSizeBox implements SampleSizeBox {
        private final int fixedSampleSize;
        private final int sampleCount;
        private final ParsableByteArray data;

        public StszSampleSizeBox(LeafAtom stszAtom) {
            this.data = stszAtom.data;
            this.data.setPosition(12);
            this.fixedSampleSize = this.data.readUnsignedIntToInt();
            this.sampleCount = this.data.readUnsignedIntToInt();
        }

        public int getSampleCount() {
            return this.sampleCount;
        }

        public int readNextSampleSize() {
            return this.fixedSampleSize == 0 ? this.data.readUnsignedIntToInt() : this.fixedSampleSize;
        }

        public boolean isFixedSampleSize() {
            return this.fixedSampleSize != 0;
        }
    }

    private interface SampleSizeBox {
        int getSampleCount();

        int readNextSampleSize();

        boolean isFixedSampleSize();
    }

    private static final class StsdData {
        public static final int STSD_HEADER_SIZE = 8;
        public final TrackEncryptionBox[] trackEncryptionBoxes;
        public Format format;
        public int nalUnitLengthFieldLength;
        public int requiredSampleTransformation;

        public StsdData(int numberOfEntries) {
            this.trackEncryptionBoxes = new TrackEncryptionBox[numberOfEntries];
            this.requiredSampleTransformation = 0;
        }
    }

    private static final class TkhdData {
        private final int id;
        private final long duration;
        private final int rotationDegrees;

        public TkhdData(int id, long duration, int rotationDegrees) {
            this.id = id;
            this.duration = duration;
            this.rotationDegrees = rotationDegrees;
        }
    }

    private static final class ChunkIterator {
        public final int length;
        public int index;
        public int numSamples;
        public long offset;
        private final boolean chunkOffsetsAreLongs;
        private final ParsableByteArray chunkOffsets;
        private final ParsableByteArray stsc;
        private int nextSamplesPerChunkChangeIndex;
        private int remainingSamplesPerChunkChanges;

        public ChunkIterator(ParsableByteArray stsc, ParsableByteArray chunkOffsets, boolean chunkOffsetsAreLongs) {
            this.stsc = stsc;
            this.chunkOffsets = chunkOffsets;
            this.chunkOffsetsAreLongs = chunkOffsetsAreLongs;
            chunkOffsets.setPosition(12);
            this.length = chunkOffsets.readUnsignedIntToInt();
            stsc.setPosition(12);
            this.remainingSamplesPerChunkChanges = stsc.readUnsignedIntToInt();
            Assertions.checkState(stsc.readInt() == 1, "first_chunk must be 1");
            this.index = -1;
        }

        public boolean moveNext() {
            if (++this.index == this.length) {
                return false;
            } else {
                this.offset = this.chunkOffsetsAreLongs ? this.chunkOffsets.readUnsignedLongToLong() : this.chunkOffsets.readUnsignedInt();
                if (this.index == this.nextSamplesPerChunkChangeIndex) {
                    this.numSamples = this.stsc.readUnsignedIntToInt();
                    this.stsc.skipBytes(4);
                    this.nextSamplesPerChunkChangeIndex = --this.remainingSamplesPerChunkChanges > 0 ? this.stsc.readUnsignedIntToInt() - 1 : -1;
                }

                return true;
            }
        }
    }
}
