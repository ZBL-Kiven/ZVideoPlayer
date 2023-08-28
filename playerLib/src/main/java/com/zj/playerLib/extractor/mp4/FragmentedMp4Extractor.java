package com.zj.playerLib.extractor.mp4;

import android.util.Pair;
import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.zj.playerLib.C;
import com.zj.playerLib.Format;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.drm.DrmInitData.SchemeData;
import com.zj.playerLib.extractor.ChunkIndex;
import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.ExtractorsFactory;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.extractor.SeekMap;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.SeekMap.Unseekable;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.extractor.mp4.Atom.ContainerAtom;
import com.zj.playerLib.extractor.mp4.Atom.LeafAtom;
import com.zj.playerLib.text.cea.CeaUtil;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.NalUnitUtil;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.TimestampAdjuster;
import com.zj.playerLib.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class FragmentedMp4Extractor implements Extractor {
    public static final ExtractorsFactory FACTORY = () -> {
        return new Extractor[]{new FragmentedMp4Extractor()};
    };
    public static final int FLAG_WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME = 1;
    public static final int FLAG_WORKAROUND_IGNORE_TFDT_BOX = 2;
    public static final int FLAG_ENABLE_EMSG_TRACK = 4;
    private static final int FLAG_SIDELOADED = 8;
    public static final int FLAG_WORKAROUND_IGNORE_EDIT_LISTS = 16;
    private static final String TAG = "FragmentedMp4Extractor";
    private static final int SAMPLE_GROUP_TYPE_seig = Util.getIntegerCodeForString("seig");
    private static final byte[] PIFF_SAMPLE_ENCRYPTION_BOX_EXTENDED_TYPE = new byte[]{-94, 57, 79, 82, 90, -101, 79, 20, -94, 68, 108, 66, 124, 100, -115, -12};
    private static final Format EMSG_FORMAT = Format.createSampleFormat(null, "application/x-emsg", Long.MAX_VALUE);
    private static final int STATE_READING_ATOM_HEADER = 0;
    private static final int STATE_READING_ATOM_PAYLOAD = 1;
    private static final int STATE_READING_ENCRYPTION_DATA = 2;
    private static final int STATE_READING_SAMPLE_START = 3;
    private static final int STATE_READING_SAMPLE_CONTINUE = 4;
    private final int flags;
    @Nullable
    private final Track sideloadedTrack;
    private final List<Format> closedCaptionFormats;
    @Nullable
    private final DrmInitData sideloadedDrmInitData;
    private final SparseArray<TrackBundle> trackBundles;
    private final ParsableByteArray nalStartCode;
    private final ParsableByteArray nalPrefix;
    private final ParsableByteArray nalBuffer;
    @Nullable
    private final TimestampAdjuster timestampAdjuster;
    private final ParsableByteArray atomHeader;
    private final byte[] extendedTypeScratch;
    private final ArrayDeque<ContainerAtom> containerAtoms;
    private final ArrayDeque<MetadataSampleInfo> pendingMetadataSampleInfos;
    @Nullable
    private final TrackOutput additionalEmsgTrackOutput;
    private int parserState;
    private int atomType;
    private long atomSize;
    private int atomHeaderBytesRead;
    private ParsableByteArray atomData;
    private long endOfMdatPosition;
    private int pendingMetadataSampleBytes;
    private long pendingSeekTimeUs;
    private long durationUs;
    private long segmentIndexEarliestPresentationTimeUs;
    private TrackBundle currentTrackBundle;
    private int sampleSize;
    private int sampleBytesWritten;
    private int sampleCurrentNalBytesRemaining;
    private boolean processSeiNalUnitPayload;
    private ExtractorOutput extractorOutput;
    private TrackOutput[] emsgTrackOutputs;
    private TrackOutput[] cea608TrackOutputs;
    private boolean haveOutputSeekMap;

    public FragmentedMp4Extractor() {
        this(0);
    }

    public FragmentedMp4Extractor(int flags) {
        this(flags, null);
    }

    public FragmentedMp4Extractor(int flags, @Nullable TimestampAdjuster timestampAdjuster) {
        this(flags, timestampAdjuster, null, null);
    }

    public FragmentedMp4Extractor(int flags, @Nullable TimestampAdjuster timestampAdjuster, @Nullable Track sideloadedTrack, @Nullable DrmInitData sideloadedDrmInitData) {
        this(flags, timestampAdjuster, sideloadedTrack, sideloadedDrmInitData, Collections.emptyList());
    }

    public FragmentedMp4Extractor(int flags, @Nullable TimestampAdjuster timestampAdjuster, @Nullable Track sideloadedTrack, @Nullable DrmInitData sideloadedDrmInitData, List<Format> closedCaptionFormats) {
        this(flags, timestampAdjuster, sideloadedTrack, sideloadedDrmInitData, closedCaptionFormats, null);
    }

    public FragmentedMp4Extractor(int flags, @Nullable TimestampAdjuster timestampAdjuster, @Nullable Track sideloadedTrack, @Nullable DrmInitData sideloadedDrmInitData, List<Format> closedCaptionFormats, @Nullable TrackOutput additionalEmsgTrackOutput) {
        this.flags = flags | (sideloadedTrack != null ? 8 : 0);
        this.timestampAdjuster = timestampAdjuster;
        this.sideloadedTrack = sideloadedTrack;
        this.sideloadedDrmInitData = sideloadedDrmInitData;
        this.closedCaptionFormats = Collections.unmodifiableList(closedCaptionFormats);
        this.additionalEmsgTrackOutput = additionalEmsgTrackOutput;
        this.atomHeader = new ParsableByteArray(16);
        this.nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
        this.nalPrefix = new ParsableByteArray(5);
        this.nalBuffer = new ParsableByteArray();
        this.extendedTypeScratch = new byte[16];
        this.containerAtoms = new ArrayDeque();
        this.pendingMetadataSampleInfos = new ArrayDeque();
        this.trackBundles = new SparseArray();
        this.durationUs = -Long.MAX_VALUE;
        this.pendingSeekTimeUs = -Long.MAX_VALUE;
        this.segmentIndexEarliestPresentationTimeUs = -Long.MAX_VALUE;
        this.enterReadingAtomHeaderState();
    }

    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        return Sniffer.sniffFragmented(input);
    }

    public void init(ExtractorOutput output) {
        this.extractorOutput = output;
        if (this.sideloadedTrack != null) {
            TrackBundle bundle = new TrackBundle(output.track(0, this.sideloadedTrack.type));
            bundle.init(this.sideloadedTrack, new DefaultSampleValues(0, 0, 0, 0));
            this.trackBundles.put(0, bundle);
            this.maybeInitExtraTracks();
            this.extractorOutput.endTracks();
        }

    }

    public void seek(long position, long timeUs) {
        int trackCount = this.trackBundles.size();

        for(int i = 0; i < trackCount; ++i) {
            this.trackBundles.valueAt(i).reset();
        }

        this.pendingMetadataSampleInfos.clear();
        this.pendingMetadataSampleBytes = 0;
        this.pendingSeekTimeUs = timeUs;
        this.containerAtoms.clear();
        this.enterReadingAtomHeaderState();
    }

    public void release() {
    }

    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        while(true) {
            switch(this.parserState) {
            case 0:
                if (this.readAtomHeader(input)) {
                    break;
                }

                return -1;
            case 1:
                this.readAtomPayload(input);
                break;
            case 2:
                this.readEncryptionData(input);
                break;
            default:
                if (this.readSample(input)) {
                    return 0;
                }
            }
        }
    }

    private void enterReadingAtomHeaderState() {
        this.parserState = 0;
        this.atomHeaderBytesRead = 0;
    }

    private boolean readAtomHeader(ExtractorInput input) throws IOException, InterruptedException {
        if (this.atomHeaderBytesRead == 0) {
            if (!input.readFully(this.atomHeader.data, 0, 8, true)) {
                return false;
            }

            this.atomHeaderBytesRead = 8;
            this.atomHeader.setPosition(0);
            this.atomSize = this.atomHeader.readUnsignedInt();
            this.atomType = this.atomHeader.readInt();
        }

        long atomPosition;
        if (this.atomSize == 1L) {
            int headerBytesRemaining = 8;
            input.readFully(this.atomHeader.data, 8, headerBytesRemaining);
            this.atomHeaderBytesRead += headerBytesRemaining;
            this.atomSize = this.atomHeader.readUnsignedLongToLong();
        } else if (this.atomSize == 0L) {
            atomPosition = input.getLength();
            if (atomPosition == -1L && !this.containerAtoms.isEmpty()) {
                atomPosition = this.containerAtoms.peek().endPosition;
            }

            if (atomPosition != -1L) {
                this.atomSize = atomPosition - input.getPosition() + (long)this.atomHeaderBytesRead;
            }
        }

        if (this.atomSize < (long)this.atomHeaderBytesRead) {
            throw new ParserException("Atom size less than header length (unsupported).");
        } else {
            atomPosition = input.getPosition() - (long)this.atomHeaderBytesRead;
            if (this.atomType == Atom.TYPE_moof) {
                int trackCount = this.trackBundles.size();

                for(int i = 0; i < trackCount; ++i) {
                    TrackFragment fragment = this.trackBundles.valueAt(i).fragment;
                    fragment.atomPosition = atomPosition;
                    fragment.auxiliaryDataPosition = atomPosition;
                    fragment.dataPosition = atomPosition;
                }
            }

            if (this.atomType == Atom.TYPE_mdat) {
                this.currentTrackBundle = null;
                this.endOfMdatPosition = atomPosition + this.atomSize;
                if (!this.haveOutputSeekMap) {
                    this.extractorOutput.seekMap(new Unseekable(this.durationUs, atomPosition));
                    this.haveOutputSeekMap = true;
                }

                this.parserState = 2;
                return true;
            } else {
                if (shouldParseContainerAtom(this.atomType)) {
                    long endPosition = input.getPosition() + this.atomSize - 8L;
                    this.containerAtoms.push(new ContainerAtom(this.atomType, endPosition));
                    if (this.atomSize == (long)this.atomHeaderBytesRead) {
                        this.processAtomEnded(endPosition);
                    } else {
                        this.enterReadingAtomHeaderState();
                    }
                } else if (shouldParseLeafAtom(this.atomType)) {
                    if (this.atomHeaderBytesRead != 8) {
                        throw new ParserException("Leaf atom defines extended atom size (unsupported).");
                    }

                    if (this.atomSize > 2147483647L) {
                        throw new ParserException("Leaf atom with length > 2147483647 (unsupported).");
                    }

                    this.atomData = new ParsableByteArray((int)this.atomSize);
                    System.arraycopy(this.atomHeader.data, 0, this.atomData.data, 0, 8);
                    this.parserState = 1;
                } else {
                    if (this.atomSize > 2147483647L) {
                        throw new ParserException("Skipping atom with length > 2147483647 (unsupported).");
                    }

                    this.atomData = null;
                    this.parserState = 1;
                }

                return true;
            }
        }
    }

    private void readAtomPayload(ExtractorInput input) throws IOException, InterruptedException {
        int atomPayloadSize = (int)this.atomSize - this.atomHeaderBytesRead;
        if (this.atomData != null) {
            input.readFully(this.atomData.data, 8, atomPayloadSize);
            this.onLeafAtomRead(new LeafAtom(this.atomType, this.atomData), input.getPosition());
        } else {
            input.skipFully(atomPayloadSize);
        }

        this.processAtomEnded(input.getPosition());
    }

    private void processAtomEnded(long atomEndPosition) throws ParserException {
        while(!this.containerAtoms.isEmpty() && this.containerAtoms.peek().endPosition == atomEndPosition) {
            this.onContainerAtomRead(this.containerAtoms.pop());
        }

        this.enterReadingAtomHeaderState();
    }

    private void onLeafAtomRead(LeafAtom leaf, long inputPosition) throws ParserException {
        if (!this.containerAtoms.isEmpty()) {
            this.containerAtoms.peek().add(leaf);
        } else if (leaf.type == Atom.TYPE_sidx) {
            Pair<Long, ChunkIndex> result = parseSidx(leaf.data, inputPosition);
            this.segmentIndexEarliestPresentationTimeUs = result.first;
            this.extractorOutput.seekMap(result.second);
            this.haveOutputSeekMap = true;
        } else if (leaf.type == Atom.TYPE_emsg) {
            this.onEmsgLeafAtomRead(leaf.data);
        }

    }

    private void onContainerAtomRead(ContainerAtom container) throws ParserException {
        if (container.type == Atom.TYPE_moov) {
            this.onMoovContainerAtomRead(container);
        } else if (container.type == Atom.TYPE_moof) {
            this.onMoofContainerAtomRead(container);
        } else if (!this.containerAtoms.isEmpty()) {
            this.containerAtoms.peek().add(container);
        }

    }

    private void onMoovContainerAtomRead(ContainerAtom moov) throws ParserException {
        Assertions.checkState(this.sideloadedTrack == null, "Unexpected moov box.");
        DrmInitData drmInitData = this.sideloadedDrmInitData != null ? this.sideloadedDrmInitData : getDrmInitDataFromAtoms(moov.leafChildren);
        ContainerAtom mvex = moov.getContainerAtomOfType(Atom.TYPE_mvex);
        SparseArray<DefaultSampleValues> defaultSampleValuesArray = new SparseArray();
        long duration = -Long.MAX_VALUE;
        int mvexChildrenSize = mvex.leafChildren.size();

        for(int i = 0; i < mvexChildrenSize; ++i) {
            LeafAtom atom = mvex.leafChildren.get(i);
            if (atom.type == Atom.TYPE_trex) {
                Pair<Integer, DefaultSampleValues> trexData = parseTrex(atom.data);
                defaultSampleValuesArray.put(trexData.first, trexData.second);
            } else if (atom.type == Atom.TYPE_mehd) {
                duration = parseMehd(atom.data);
            }
        }

        SparseArray<Track> tracks = new SparseArray();
        int moovContainerChildrenSize = moov.containerChildren.size();

        Track track;
        int trackCount;
        for(trackCount = 0; trackCount < moovContainerChildrenSize; ++trackCount) {
            ContainerAtom atom = moov.containerChildren.get(trackCount);
            if (atom.type == Atom.TYPE_trak) {
                track = AtomParsers.parseTrak(atom, moov.getLeafAtomOfType(Atom.TYPE_mvhd), duration, drmInitData, (this.flags & 16) != 0, false);
                if (track != null) {
                    tracks.put(track.id, track);
                }
            }
        }

        trackCount = tracks.size();
        int i;
        if (this.trackBundles.size() == 0) {
            for(i = 0; i < trackCount; ++i) {
                track = tracks.valueAt(i);
                TrackBundle trackBundle = new TrackBundle(this.extractorOutput.track(i, track.type));
                trackBundle.init(track, this.getDefaultSampleValues(defaultSampleValuesArray, track.id));
                this.trackBundles.put(track.id, trackBundle);
                this.durationUs = Math.max(this.durationUs, track.durationUs);
            }

            this.maybeInitExtraTracks();
            this.extractorOutput.endTracks();
        } else {
            Assertions.checkState(this.trackBundles.size() == trackCount);

            for(i = 0; i < trackCount; ++i) {
                track = tracks.valueAt(i);
                this.trackBundles.get(track.id).init(track, this.getDefaultSampleValues(defaultSampleValuesArray, track.id));
            }
        }

    }

    private DefaultSampleValues getDefaultSampleValues(SparseArray<DefaultSampleValues> defaultSampleValuesArray, int trackId) {
        return defaultSampleValuesArray.size() == 1 ? defaultSampleValuesArray.valueAt(0) : Assertions.checkNotNull(defaultSampleValuesArray.get(trackId));
    }

    private void onMoofContainerAtomRead(ContainerAtom moof) throws ParserException {
        parseMoof(moof, this.trackBundles, this.flags, this.extendedTypeScratch);
        DrmInitData drmInitData = this.sideloadedDrmInitData != null ? null : getDrmInitDataFromAtoms(moof.leafChildren);
        int trackCount;
        int i;
        if (drmInitData != null) {
            trackCount = this.trackBundles.size();

            for(i = 0; i < trackCount; ++i) {
                this.trackBundles.valueAt(i).updateDrmInitData(drmInitData);
            }
        }

        if (this.pendingSeekTimeUs != -Long.MAX_VALUE) {
            trackCount = this.trackBundles.size();

            for(i = 0; i < trackCount; ++i) {
                this.trackBundles.valueAt(i).seek(this.pendingSeekTimeUs);
            }

            this.pendingSeekTimeUs = -Long.MAX_VALUE;
        }

    }

    private void maybeInitExtraTracks() {
        int i;
        if (this.emsgTrackOutputs == null) {
            this.emsgTrackOutputs = new TrackOutput[2];
            i = 0;
            if (this.additionalEmsgTrackOutput != null) {
                this.emsgTrackOutputs[i++] = this.additionalEmsgTrackOutput;
            }

            if ((this.flags & 4) != 0) {
                this.emsgTrackOutputs[i++] = this.extractorOutput.track(this.trackBundles.size(), 4);
            }

            this.emsgTrackOutputs = Arrays.copyOf(this.emsgTrackOutputs, i);
            TrackOutput[] var2 = this.emsgTrackOutputs;
            int var3 = var2.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                TrackOutput eventMessageTrackOutput = var2[var4];
                eventMessageTrackOutput.format(EMSG_FORMAT);
            }
        }

        if (this.cea608TrackOutputs == null) {
            this.cea608TrackOutputs = new TrackOutput[this.closedCaptionFormats.size()];

            for(i = 0; i < this.cea608TrackOutputs.length; ++i) {
                TrackOutput output = this.extractorOutput.track(this.trackBundles.size() + 1 + i, 3);
                output.format(this.closedCaptionFormats.get(i));
                this.cea608TrackOutputs[i] = output;
            }
        }

    }

    private void onEmsgLeafAtomRead(ParsableByteArray atom) {
        if (this.emsgTrackOutputs != null && this.emsgTrackOutputs.length != 0) {
            atom.setPosition(12);
            int sampleSize = atom.bytesLeft();
            atom.readNullTerminatedString();
            atom.readNullTerminatedString();
            long timescale = atom.readUnsignedInt();
            long presentationTimeDeltaUs = Util.scaleLargeTimestamp(atom.readUnsignedInt(), 1000000L, timescale);
            TrackOutput[] var7 = this.emsgTrackOutputs;
            int var8 = var7.length;

            for(int var9 = 0; var9 < var8; ++var9) {
                TrackOutput emsgTrackOutput = var7[var9];
                atom.setPosition(12);
                emsgTrackOutput.sampleData(atom, sampleSize);
            }

            if (this.segmentIndexEarliestPresentationTimeUs != -Long.MAX_VALUE) {
                long sampleTimeUs = this.segmentIndexEarliestPresentationTimeUs + presentationTimeDeltaUs;
                if (this.timestampAdjuster != null) {
                    sampleTimeUs = this.timestampAdjuster.adjustSampleTimestamp(sampleTimeUs);
                }

                TrackOutput[] var15 = this.emsgTrackOutputs;
                int var13 = var15.length;

                for(int var11 = 0; var11 < var13; ++var11) {
                    TrackOutput emsgTrackOutput = var15[var11];
                    emsgTrackOutput.sampleMetadata(sampleTimeUs, 1, sampleSize, 0, null);
                }
            } else {
                this.pendingMetadataSampleInfos.addLast(new MetadataSampleInfo(presentationTimeDeltaUs, sampleSize));
                this.pendingMetadataSampleBytes += sampleSize;
            }

        }
    }

    private static Pair<Integer, DefaultSampleValues> parseTrex(ParsableByteArray trex) {
        trex.setPosition(12);
        int trackId = trex.readInt();
        int defaultSampleDescriptionIndex = trex.readUnsignedIntToInt() - 1;
        int defaultSampleDuration = trex.readUnsignedIntToInt();
        int defaultSampleSize = trex.readUnsignedIntToInt();
        int defaultSampleFlags = trex.readInt();
        return Pair.create(trackId, new DefaultSampleValues(defaultSampleDescriptionIndex, defaultSampleDuration, defaultSampleSize, defaultSampleFlags));
    }

    private static long parseMehd(ParsableByteArray mehd) {
        mehd.setPosition(8);
        int fullAtom = mehd.readInt();
        int version = Atom.parseFullAtomVersion(fullAtom);
        return version == 0 ? mehd.readUnsignedInt() : mehd.readUnsignedLongToLong();
    }

    private static void parseMoof(ContainerAtom moof, SparseArray<TrackBundle> trackBundleArray, int flags, byte[] extendedTypeScratch) throws ParserException {
        int moofContainerChildrenSize = moof.containerChildren.size();

        for(int i = 0; i < moofContainerChildrenSize; ++i) {
            ContainerAtom child = moof.containerChildren.get(i);
            if (child.type == Atom.TYPE_traf) {
                parseTraf(child, trackBundleArray, flags, extendedTypeScratch);
            }
        }

    }

    private static void parseTraf(ContainerAtom traf, SparseArray<TrackBundle> trackBundleArray, int flags, byte[] extendedTypeScratch) throws ParserException {
        LeafAtom tfhd = traf.getLeafAtomOfType(Atom.TYPE_tfhd);
        TrackBundle trackBundle = parseTfhd(tfhd.data, trackBundleArray);
        if (trackBundle != null) {
            TrackFragment fragment = trackBundle.fragment;
            long decodeTime = fragment.nextFragmentDecodeTime;
            trackBundle.reset();
            LeafAtom tfdtAtom = traf.getLeafAtomOfType(Atom.TYPE_tfdt);
            if (tfdtAtom != null && (flags & 2) == 0) {
                decodeTime = parseTfdt(traf.getLeafAtomOfType(Atom.TYPE_tfdt).data);
            }

            parseTruns(traf, trackBundle, decodeTime, flags);
            TrackEncryptionBox encryptionBox = trackBundle.track.getSampleDescriptionEncryptionBox(fragment.header.sampleDescriptionIndex);
            LeafAtom saiz = traf.getLeafAtomOfType(Atom.TYPE_saiz);
            if (saiz != null) {
                parseSaiz(encryptionBox, saiz.data, fragment);
            }

            LeafAtom saio = traf.getLeafAtomOfType(Atom.TYPE_saio);
            if (saio != null) {
                parseSaio(saio.data, fragment);
            }

            LeafAtom senc = traf.getLeafAtomOfType(Atom.TYPE_senc);
            if (senc != null) {
                parseSenc(senc.data, fragment);
            }

            LeafAtom sbgp = traf.getLeafAtomOfType(Atom.TYPE_sbgp);
            LeafAtom sgpd = traf.getLeafAtomOfType(Atom.TYPE_sgpd);
            if (sbgp != null && sgpd != null) {
                parseSgpd(sbgp.data, sgpd.data, encryptionBox != null ? encryptionBox.schemeType : null, fragment);
            }

            int leafChildrenSize = traf.leafChildren.size();

            for(int i = 0; i < leafChildrenSize; ++i) {
                LeafAtom atom = traf.leafChildren.get(i);
                if (atom.type == Atom.TYPE_uuid) {
                    parseUuid(atom.data, fragment, extendedTypeScratch);
                }
            }

        }
    }

    private static void parseTruns(ContainerAtom traf, TrackBundle trackBundle, long decodeTime, int flags) {
        int trunCount = 0;
        int totalSampleCount = 0;
        List<LeafAtom> leafChildren = traf.leafChildren;
        int leafChildrenSize = leafChildren.size();

        int trunIndex;
        for(trunIndex = 0; trunIndex < leafChildrenSize; ++trunIndex) {
            LeafAtom atom = leafChildren.get(trunIndex);
            if (atom.type == Atom.TYPE_trun) {
                ParsableByteArray trunData = atom.data;
                trunData.setPosition(12);
                int trunSampleCount = trunData.readUnsignedIntToInt();
                if (trunSampleCount > 0) {
                    totalSampleCount += trunSampleCount;
                    ++trunCount;
                }
            }
        }

        trackBundle.currentTrackRunIndex = 0;
        trackBundle.currentSampleInTrackRun = 0;
        trackBundle.currentSampleIndex = 0;
        trackBundle.fragment.initTables(trunCount, totalSampleCount);
        trunIndex = 0;
        int trunStartPosition = 0;

        for(int i = 0; i < leafChildrenSize; ++i) {
            LeafAtom trun = leafChildren.get(i);
            if (trun.type == Atom.TYPE_trun) {
                trunStartPosition = parseTrun(trackBundle, trunIndex++, decodeTime, flags, trun.data, trunStartPosition);
            }
        }

    }

    private static void parseSaiz(TrackEncryptionBox encryptionBox, ParsableByteArray saiz, TrackFragment out) throws ParserException {
        int vectorSize = encryptionBox.perSampleIvSize;
        saiz.setPosition(8);
        int fullAtom = saiz.readInt();
        int flags = Atom.parseFullAtomFlags(fullAtom);
        if ((flags & 1) == 1) {
            saiz.skipBytes(8);
        }

        int defaultSampleInfoSize = saiz.readUnsignedByte();
        int sampleCount = saiz.readUnsignedIntToInt();
        if (sampleCount != out.sampleCount) {
            throw new ParserException("Length mismatch: " + sampleCount + ", " + out.sampleCount);
        } else {
            int totalSize = 0;
            if (defaultSampleInfoSize == 0) {
                boolean[] sampleHasSubsampleEncryptionTable = out.sampleHasSubsampleEncryptionTable;

                for(int i = 0; i < sampleCount; ++i) {
                    int sampleInfoSize = saiz.readUnsignedByte();
                    totalSize += sampleInfoSize;
                    sampleHasSubsampleEncryptionTable[i] = sampleInfoSize > vectorSize;
                }
            } else {
                boolean subsampleEncryption = defaultSampleInfoSize > vectorSize;
                totalSize += defaultSampleInfoSize * sampleCount;
                Arrays.fill(out.sampleHasSubsampleEncryptionTable, 0, sampleCount, subsampleEncryption);
            }

            out.initEncryptionData(totalSize);
        }
    }

    private static void parseSaio(ParsableByteArray saio, TrackFragment out) throws ParserException {
        saio.setPosition(8);
        int fullAtom = saio.readInt();
        int flags = Atom.parseFullAtomFlags(fullAtom);
        if ((flags & 1) == 1) {
            saio.skipBytes(8);
        }

        int entryCount = saio.readUnsignedIntToInt();
        if (entryCount != 1) {
            throw new ParserException("Unexpected saio entry count: " + entryCount);
        } else {
            int version = Atom.parseFullAtomVersion(fullAtom);
            out.auxiliaryDataPosition += version == 0 ? saio.readUnsignedInt() : saio.readUnsignedLongToLong();
        }
    }

    private static TrackBundle parseTfhd(ParsableByteArray tfhd, SparseArray<TrackBundle> trackBundles) {
        tfhd.setPosition(8);
        int fullAtom = tfhd.readInt();
        int atomFlags = Atom.parseFullAtomFlags(fullAtom);
        int trackId = tfhd.readInt();
        TrackBundle trackBundle = getTrackBundle(trackBundles, trackId);
        if (trackBundle == null) {
            return null;
        } else {
            if ((atomFlags & 1) != 0) {
                long baseDataPosition = tfhd.readUnsignedLongToLong();
                trackBundle.fragment.dataPosition = baseDataPosition;
                trackBundle.fragment.auxiliaryDataPosition = baseDataPosition;
            }

            DefaultSampleValues defaultSampleValues = trackBundle.defaultSampleValues;
            int defaultSampleDescriptionIndex = (atomFlags & 2) != 0 ? tfhd.readUnsignedIntToInt() - 1 : defaultSampleValues.sampleDescriptionIndex;
            int defaultSampleDuration = (atomFlags & 8) != 0 ? tfhd.readUnsignedIntToInt() : defaultSampleValues.duration;
            int defaultSampleSize = (atomFlags & 16) != 0 ? tfhd.readUnsignedIntToInt() : defaultSampleValues.size;
            int defaultSampleFlags = (atomFlags & 32) != 0 ? tfhd.readUnsignedIntToInt() : defaultSampleValues.flags;
            trackBundle.fragment.header = new DefaultSampleValues(defaultSampleDescriptionIndex, defaultSampleDuration, defaultSampleSize, defaultSampleFlags);
            return trackBundle;
        }
    }

    @Nullable
    private static FragmentedMp4Extractor.TrackBundle getTrackBundle(SparseArray<TrackBundle> trackBundles, int trackId) {
        return trackBundles.size() == 1 ? trackBundles.valueAt(0) : trackBundles.get(trackId);
    }

    private static long parseTfdt(ParsableByteArray tfdt) {
        tfdt.setPosition(8);
        int fullAtom = tfdt.readInt();
        int version = Atom.parseFullAtomVersion(fullAtom);
        return version == 1 ? tfdt.readUnsignedLongToLong() : tfdt.readUnsignedInt();
    }

    private static int parseTrun(TrackBundle trackBundle, int index, long decodeTime, int flags, ParsableByteArray trun, int trackRunStart) {
        trun.setPosition(8);
        int fullAtom = trun.readInt();
        int atomFlags = Atom.parseFullAtomFlags(fullAtom);
        Track track = trackBundle.track;
        TrackFragment fragment = trackBundle.fragment;
        DefaultSampleValues defaultSampleValues = fragment.header;
        fragment.trunLength[index] = trun.readUnsignedIntToInt();
        fragment.trunDataPosition[index] = fragment.dataPosition;
        if ((atomFlags & 1) != 0) {
            long[] var10000 = fragment.trunDataPosition;
            var10000[index] += trun.readInt();
        }

        boolean firstSampleFlagsPresent = (atomFlags & 4) != 0;
        int firstSampleFlags = defaultSampleValues.flags;
        if (firstSampleFlagsPresent) {
            firstSampleFlags = trun.readUnsignedIntToInt();
        }

        boolean sampleDurationsPresent = (atomFlags & 256) != 0;
        boolean sampleSizesPresent = (atomFlags & 512) != 0;
        boolean sampleFlagsPresent = (atomFlags & 1024) != 0;
        boolean sampleCompositionTimeOffsetsPresent = (atomFlags & 2048) != 0;
        long edtsOffset = 0L;
        if (track.editListDurations != null && track.editListDurations.length == 1 && track.editListDurations[0] == 0L) {
            edtsOffset = Util.scaleLargeTimestamp(track.editListMediaTimes[0], 1000L, track.timescale);
        }

        int[] sampleSizeTable = fragment.sampleSizeTable;
        int[] sampleCompositionTimeOffsetTable = fragment.sampleCompositionTimeOffsetTable;
        long[] sampleDecodingTimeTable = fragment.sampleDecodingTimeTable;
        boolean[] sampleIsSyncFrameTable = fragment.sampleIsSyncFrameTable;
        boolean workaroundEveryVideoFrameIsSyncFrame = track.type == 2 && (flags & 1) != 0;
        int trackRunEnd = trackRunStart + fragment.trunLength[index];
        long timescale = track.timescale;
        long cumulativeTime = index > 0 ? fragment.nextFragmentDecodeTime : decodeTime;

        for(int i = trackRunStart; i < trackRunEnd; ++i) {
            int sampleDuration = sampleDurationsPresent ? trun.readUnsignedIntToInt() : defaultSampleValues.duration;
            int sampleSize = sampleSizesPresent ? trun.readUnsignedIntToInt() : defaultSampleValues.size;
            int sampleFlags = i == 0 && firstSampleFlagsPresent ? firstSampleFlags : (sampleFlagsPresent ? trun.readInt() : defaultSampleValues.flags);
            if (sampleCompositionTimeOffsetsPresent) {
                int sampleOffset = trun.readInt();
                sampleCompositionTimeOffsetTable[i] = (int)((long)sampleOffset * 1000L / timescale);
            } else {
                sampleCompositionTimeOffsetTable[i] = 0;
            }

            sampleDecodingTimeTable[i] = Util.scaleLargeTimestamp(cumulativeTime, 1000L, timescale) - edtsOffset;
            sampleSizeTable[i] = sampleSize;
            sampleIsSyncFrameTable[i] = (sampleFlags >> 16 & 1) == 0 && (!workaroundEveryVideoFrameIsSyncFrame || i == 0);
            cumulativeTime += sampleDuration;
        }

        fragment.nextFragmentDecodeTime = cumulativeTime;
        return trackRunEnd;
    }

    private static void parseUuid(ParsableByteArray uuid, TrackFragment out, byte[] extendedTypeScratch) throws ParserException {
        uuid.setPosition(8);
        uuid.readBytes(extendedTypeScratch, 0, 16);
        if (Arrays.equals(extendedTypeScratch, PIFF_SAMPLE_ENCRYPTION_BOX_EXTENDED_TYPE)) {
            parseSenc(uuid, 16, out);
        }
    }

    private static void parseSenc(ParsableByteArray senc, TrackFragment out) throws ParserException {
        parseSenc(senc, 0, out);
    }

    private static void parseSenc(ParsableByteArray senc, int offset, TrackFragment out) throws ParserException {
        senc.setPosition(8 + offset);
        int fullAtom = senc.readInt();
        int flags = Atom.parseFullAtomFlags(fullAtom);
        if ((flags & 1) != 0) {
            throw new ParserException("Overriding TrackEncryptionBox parameters is unsupported.");
        } else {
            boolean subsampleEncryption = (flags & 2) != 0;
            int sampleCount = senc.readUnsignedIntToInt();
            if (sampleCount != out.sampleCount) {
                throw new ParserException("Length mismatch: " + sampleCount + ", " + out.sampleCount);
            } else {
                Arrays.fill(out.sampleHasSubsampleEncryptionTable, 0, sampleCount, subsampleEncryption);
                out.initEncryptionData(senc.bytesLeft());
                out.fillEncryptionData(senc);
            }
        }
    }

    private static void parseSgpd(ParsableByteArray sbgp, ParsableByteArray sgpd, String schemeType, TrackFragment out) throws ParserException {
        sbgp.setPosition(8);
        int sbgpFullAtom = sbgp.readInt();
        if (sbgp.readInt() == SAMPLE_GROUP_TYPE_seig) {
            if (Atom.parseFullAtomVersion(sbgpFullAtom) == 1) {
                sbgp.skipBytes(4);
            }

            if (sbgp.readInt() != 1) {
                throw new ParserException("Entry count in sbgp != 1 (unsupported).");
            } else {
                sgpd.setPosition(8);
                int sgpdFullAtom = sgpd.readInt();
                if (sgpd.readInt() == SAMPLE_GROUP_TYPE_seig) {
                    int sgpdVersion = Atom.parseFullAtomVersion(sgpdFullAtom);
                    if (sgpdVersion == 1) {
                        if (sgpd.readUnsignedInt() == 0L) {
                            throw new ParserException("Variable length description in sgpd found (unsupported)");
                        }
                    } else if (sgpdVersion >= 2) {
                        sgpd.skipBytes(4);
                    }

                    if (sgpd.readUnsignedInt() != 1L) {
                        throw new ParserException("Entry count in sgpd != 1 (unsupported).");
                    } else {
                        sgpd.skipBytes(1);
                        int patternByte = sgpd.readUnsignedByte();
                        int cryptByteBlock = (patternByte & 240) >> 4;
                        int skipByteBlock = patternByte & 15;
                        boolean isProtected = sgpd.readUnsignedByte() == 1;
                        if (isProtected) {
                            int perSampleIvSize = sgpd.readUnsignedByte();
                            byte[] keyId = new byte[16];
                            sgpd.readBytes(keyId, 0, keyId.length);
                            byte[] constantIv = null;
                            if (isProtected && perSampleIvSize == 0) {
                                int constantIvSize = sgpd.readUnsignedByte();
                                constantIv = new byte[constantIvSize];
                                sgpd.readBytes(constantIv, 0, constantIvSize);
                            }

                            out.definesEncryptionData = true;
                            out.trackEncryptionBox = new TrackEncryptionBox(isProtected, schemeType, perSampleIvSize, keyId, cryptByteBlock, skipByteBlock, constantIv);
                        }
                    }
                }
            }
        }
    }

    private static Pair<Long, ChunkIndex> parseSidx(ParsableByteArray atom, long inputPosition) throws ParserException {
        atom.setPosition(8);
        int fullAtom = atom.readInt();
        int version = Atom.parseFullAtomVersion(fullAtom);
        atom.skipBytes(4);
        long timescale = atom.readUnsignedInt();
        long earliestPresentationTime;
        long offset;
        if (version == 0) {
            earliestPresentationTime = atom.readUnsignedInt();
            offset = inputPosition + atom.readUnsignedInt();
        } else {
            earliestPresentationTime = atom.readUnsignedLongToLong();
            offset = inputPosition + atom.readUnsignedLongToLong();
        }

        long earliestPresentationTimeUs = Util.scaleLargeTimestamp(earliestPresentationTime, 1000000L, timescale);
        atom.skipBytes(2);
        int referenceCount = atom.readUnsignedShort();
        int[] sizes = new int[referenceCount];
        long[] offsets = new long[referenceCount];
        long[] durationsUs = new long[referenceCount];
        long[] timesUs = new long[referenceCount];
        long time = earliestPresentationTime;
        long timeUs = earliestPresentationTimeUs;

        for(int i = 0; i < referenceCount; ++i) {
            int firstInt = atom.readInt();
            int type = -2147483648 & firstInt;
            if (type != 0) {
                throw new ParserException("Unhandled indirect reference");
            }

            long referenceDuration = atom.readUnsignedInt();
            sizes[i] = 2147483647 & firstInt;
            offsets[i] = offset;
            timesUs[i] = timeUs;
            time += referenceDuration;
            timeUs = Util.scaleLargeTimestamp(time, 1000000L, timescale);
            durationsUs[i] = timeUs - timesUs[i];
            atom.skipBytes(4);
            offset += sizes[i];
        }

        return Pair.create(earliestPresentationTimeUs, new ChunkIndex(sizes, offsets, durationsUs, timesUs));
    }

    private void readEncryptionData(ExtractorInput input) throws IOException, InterruptedException {
        TrackBundle nextTrackBundle = null;
        long nextDataOffset = Long.MAX_VALUE;
        int trackBundlesSize = this.trackBundles.size();

        int bytesToSkip;
        for(bytesToSkip = 0; bytesToSkip < trackBundlesSize; ++bytesToSkip) {
            TrackFragment trackFragment = this.trackBundles.valueAt(bytesToSkip).fragment;
            if (trackFragment.sampleEncryptionDataNeedsFill && trackFragment.auxiliaryDataPosition < nextDataOffset) {
                nextDataOffset = trackFragment.auxiliaryDataPosition;
                nextTrackBundle = this.trackBundles.valueAt(bytesToSkip);
            }
        }

        if (nextTrackBundle == null) {
            this.parserState = 3;
        } else {
            bytesToSkip = (int)(nextDataOffset - input.getPosition());
            if (bytesToSkip < 0) {
                throw new ParserException("Offset to encryption data was negative.");
            } else {
                input.skipFully(bytesToSkip);
                nextTrackBundle.fragment.fillEncryptionData(input);
            }
        }
    }

    private boolean readSample(ExtractorInput input) throws IOException, InterruptedException {
        int bytesToSkip;
        if (this.parserState == 3) {
            if (this.currentTrackBundle == null) {
                TrackBundle currentTrackBundle = getNextFragmentRun(this.trackBundles);
                if (currentTrackBundle == null) {
                    bytesToSkip = (int)(this.endOfMdatPosition - input.getPosition());
                    if (bytesToSkip < 0) {
                        throw new ParserException("Offset to end of mdat was negative.");
                    }

                    input.skipFully(bytesToSkip);
                    this.enterReadingAtomHeaderState();
                    return false;
                }
                long nextDataPosition = currentTrackBundle.fragment.trunDataPosition[currentTrackBundle.currentTrackRunIndex];
                bytesToSkip = (int)(nextDataPosition - input.getPosition());
                if (bytesToSkip < 0) {
                    Log.w("FragmentedMp4Extractor", "Ignoring negative offset to sample data.");
                    bytesToSkip = 0;
                }

                input.skipFully(bytesToSkip);
                this.currentTrackBundle = currentTrackBundle;
            }

            this.sampleSize = this.currentTrackBundle.fragment.sampleSizeTable[this.currentTrackBundle.currentSampleIndex];
            if (this.currentTrackBundle.currentSampleIndex < this.currentTrackBundle.firstSampleToOutputIndex) {
                input.skipFully(this.sampleSize);
                this.currentTrackBundle.skipSampleEncryptionData();
                if (!this.currentTrackBundle.next()) {
                    this.currentTrackBundle = null;
                }

                this.parserState = 3;
                return true;
            }

            if (this.currentTrackBundle.track.sampleTransformation == 1) {
                this.sampleSize -= 8;
                input.skipFully(8);
            }

            this.sampleBytesWritten = this.currentTrackBundle.outputSampleEncryptionData();
            this.sampleSize += this.sampleBytesWritten;
            this.parserState = 4;
            this.sampleCurrentNalBytesRemaining = 0;
        }

        TrackFragment fragment = this.currentTrackBundle.fragment;
        Track track = this.currentTrackBundle.track;
        TrackOutput output = this.currentTrackBundle.output;
        bytesToSkip = this.currentTrackBundle.currentSampleIndex;
        long sampleTimeUs = fragment.getSamplePresentationTime(bytesToSkip) * 1000L;
        if (this.timestampAdjuster != null) {
            sampleTimeUs = this.timestampAdjuster.adjustSampleTimestamp(sampleTimeUs);
        }

        int sampleFlags;
        if (track.nalUnitLengthFieldLength != 0) {
            byte[] nalPrefixData = this.nalPrefix.data;
            nalPrefixData[0] = 0;
            nalPrefixData[1] = 0;
            nalPrefixData[2] = 0;
            int nalUnitPrefixLength = track.nalUnitLengthFieldLength + 1;
            int nalUnitLengthFieldLengthDiff = 4 - track.nalUnitLengthFieldLength;

            label87:
            while(true) {
                while(true) {
                    if (this.sampleBytesWritten >= this.sampleSize) {
                        break label87;
                    }

                    if (this.sampleCurrentNalBytesRemaining == 0) {
                        input.readFully(nalPrefixData, nalUnitLengthFieldLengthDiff, nalUnitPrefixLength);
                        this.nalPrefix.setPosition(0);
                        this.sampleCurrentNalBytesRemaining = this.nalPrefix.readUnsignedIntToInt() - 1;
                        this.nalStartCode.setPosition(0);
                        output.sampleData(this.nalStartCode, 4);
                        output.sampleData(this.nalPrefix, 1);
                        this.processSeiNalUnitPayload = this.cea608TrackOutputs.length > 0 && NalUnitUtil.isNalUnitSei(track.format.sampleMimeType, nalPrefixData[4]);
                        this.sampleBytesWritten += 5;
                        this.sampleSize += nalUnitLengthFieldLengthDiff;
                    } else {
                        int writtenBytes;
                        if (this.processSeiNalUnitPayload) {
                            this.nalBuffer.reset(this.sampleCurrentNalBytesRemaining);
                            input.readFully(this.nalBuffer.data, 0, this.sampleCurrentNalBytesRemaining);
                            output.sampleData(this.nalBuffer, this.sampleCurrentNalBytesRemaining);
                            writtenBytes = this.sampleCurrentNalBytesRemaining;
                            int unescapedLength = NalUnitUtil.unescapeStream(this.nalBuffer.data, this.nalBuffer.limit());
                            this.nalBuffer.setPosition("video/hevc".equals(track.format.sampleMimeType) ? 1 : 0);
                            this.nalBuffer.setLimit(unescapedLength);
                            CeaUtil.consume(sampleTimeUs, this.nalBuffer, this.cea608TrackOutputs);
                        } else {
                            writtenBytes = output.sampleData(input, this.sampleCurrentNalBytesRemaining, false);
                        }

                        this.sampleBytesWritten += writtenBytes;
                        this.sampleCurrentNalBytesRemaining -= writtenBytes;
                    }
                }
            }
        } else {
            while(this.sampleBytesWritten < this.sampleSize) {
                sampleFlags = output.sampleData(input, this.sampleSize - this.sampleBytesWritten, false);
                this.sampleBytesWritten += sampleFlags;
            }
        }

        sampleFlags = fragment.sampleIsSyncFrameTable[bytesToSkip] ? 1 : 0;
        CryptoData cryptoData = null;
        TrackEncryptionBox encryptionBox = this.currentTrackBundle.getEncryptionBoxIfEncrypted();
        if (encryptionBox != null) {
            sampleFlags |= 1073741824;
            cryptoData = encryptionBox.cryptoData;
        }

        output.sampleMetadata(sampleTimeUs, sampleFlags, this.sampleSize, 0, cryptoData);
        this.outputPendingMetadataSamples(sampleTimeUs);
        if (!this.currentTrackBundle.next()) {
            this.currentTrackBundle = null;
        }

        this.parserState = 3;
        return true;
    }

    private void outputPendingMetadataSamples(long sampleTimeUs) {
        label20:
        while(true) {
            if (!this.pendingMetadataSampleInfos.isEmpty()) {
                MetadataSampleInfo sampleInfo = this.pendingMetadataSampleInfos.removeFirst();
                this.pendingMetadataSampleBytes -= sampleInfo.size;
                long metadataTimeUs = sampleTimeUs + sampleInfo.presentationTimeDeltaUs;
                if (this.timestampAdjuster != null) {
                    metadataTimeUs = this.timestampAdjuster.adjustSampleTimestamp(metadataTimeUs);
                }

                TrackOutput[] var6 = this.emsgTrackOutputs;
                int var7 = var6.length;
                int var8 = 0;

                while(true) {
                    if (var8 >= var7) {
                        continue label20;
                    }

                    TrackOutput emsgTrackOutput = var6[var8];
                    emsgTrackOutput.sampleMetadata(metadataTimeUs, 1, sampleInfo.size, this.pendingMetadataSampleBytes, null);
                    ++var8;
                }
            }

            return;
        }
    }

    private static TrackBundle getNextFragmentRun(SparseArray<TrackBundle> trackBundles) {
        TrackBundle nextTrackBundle = null;
        long nextTrackRunOffset = Long.MAX_VALUE;
        int trackBundlesSize = trackBundles.size();

        for(int i = 0; i < trackBundlesSize; ++i) {
            TrackBundle trackBundle = trackBundles.valueAt(i);
            if (trackBundle.currentTrackRunIndex != trackBundle.fragment.trunCount) {
                long trunOffset = trackBundle.fragment.trunDataPosition[trackBundle.currentTrackRunIndex];
                if (trunOffset < nextTrackRunOffset) {
                    nextTrackBundle = trackBundle;
                    nextTrackRunOffset = trunOffset;
                }
            }
        }

        return nextTrackBundle;
    }

    private static DrmInitData getDrmInitDataFromAtoms(List<LeafAtom> leafChildren) {
        ArrayList<SchemeData> schemeDatas = null;
        int leafChildrenSize = leafChildren.size();

        for(int i = 0; i < leafChildrenSize; ++i) {
            LeafAtom child = leafChildren.get(i);
            if (child.type == Atom.TYPE_pssh) {
                if (schemeDatas == null) {
                    schemeDatas = new ArrayList();
                }

                byte[] psshData = child.data.data;
                UUID uuid = PsshAtomUtil.parseUuid(psshData);
                if (uuid == null) {
                    Log.w("FragmentedMp4Extractor", "Skipped pssh atom (failed to extract uuid)");
                } else {
                    schemeDatas.add(new SchemeData(uuid, "video/mp4", psshData));
                }
            }
        }

        return schemeDatas == null ? null : new DrmInitData(schemeDatas);
    }

    private static boolean shouldParseLeafAtom(int atom) {
        return atom == Atom.TYPE_hdlr || atom == Atom.TYPE_mdhd || atom == Atom.TYPE_mvhd || atom == Atom.TYPE_sidx || atom == Atom.TYPE_stsd || atom == Atom.TYPE_tfdt || atom == Atom.TYPE_tfhd || atom == Atom.TYPE_tkhd || atom == Atom.TYPE_trex || atom == Atom.TYPE_trun || atom == Atom.TYPE_pssh || atom == Atom.TYPE_saiz || atom == Atom.TYPE_saio || atom == Atom.TYPE_senc || atom == Atom.TYPE_uuid || atom == Atom.TYPE_sbgp || atom == Atom.TYPE_sgpd || atom == Atom.TYPE_elst || atom == Atom.TYPE_mehd || atom == Atom.TYPE_emsg;
    }

    private static boolean shouldParseContainerAtom(int atom) {
        return atom == Atom.TYPE_moov || atom == Atom.TYPE_trak || atom == Atom.TYPE_mdia || atom == Atom.TYPE_minf || atom == Atom.TYPE_stbl || atom == Atom.TYPE_moof || atom == Atom.TYPE_traf || atom == Atom.TYPE_mvex || atom == Atom.TYPE_edts;
    }

    private static final class TrackBundle {
        public final TrackOutput output;
        public final TrackFragment fragment;
        public Track track;
        public DefaultSampleValues defaultSampleValues;
        public int currentSampleIndex;
        public int currentSampleInTrackRun;
        public int currentTrackRunIndex;
        public int firstSampleToOutputIndex;
        private final ParsableByteArray encryptionSignalByte;
        private final ParsableByteArray defaultInitializationVector;

        public TrackBundle(TrackOutput output) {
            this.output = output;
            this.fragment = new TrackFragment();
            this.encryptionSignalByte = new ParsableByteArray(1);
            this.defaultInitializationVector = new ParsableByteArray();
        }

        public void init(Track track, DefaultSampleValues defaultSampleValues) {
            this.track = Assertions.checkNotNull(track);
            this.defaultSampleValues = Assertions.checkNotNull(defaultSampleValues);
            this.output.format(track.format);
            this.reset();
        }

        public void updateDrmInitData(DrmInitData drmInitData) {
            TrackEncryptionBox encryptionBox = this.track.getSampleDescriptionEncryptionBox(this.fragment.header.sampleDescriptionIndex);
            String schemeType = encryptionBox != null ? encryptionBox.schemeType : null;
            this.output.format(this.track.format.copyWithDrmInitData(drmInitData.copyWithSchemeType(schemeType)));
        }

        public void reset() {
            this.fragment.reset();
            this.currentSampleIndex = 0;
            this.currentTrackRunIndex = 0;
            this.currentSampleInTrackRun = 0;
            this.firstSampleToOutputIndex = 0;
        }

        public void seek(long timeUs) {
            long timeMs = C.usToMs(timeUs);

            for(int searchIndex = this.currentSampleIndex; searchIndex < this.fragment.sampleCount && this.fragment.getSamplePresentationTime(searchIndex) < timeMs; ++searchIndex) {
                if (this.fragment.sampleIsSyncFrameTable[searchIndex]) {
                    this.firstSampleToOutputIndex = searchIndex;
                }
            }

        }

        public boolean next() {
            ++this.currentSampleIndex;
            ++this.currentSampleInTrackRun;
            if (this.currentSampleInTrackRun == this.fragment.trunLength[this.currentTrackRunIndex]) {
                ++this.currentTrackRunIndex;
                this.currentSampleInTrackRun = 0;
                return false;
            } else {
                return true;
            }
        }

        public int outputSampleEncryptionData() {
            TrackEncryptionBox encryptionBox = this.getEncryptionBoxIfEncrypted();
            if (encryptionBox == null) {
                return 0;
            } else {
                ParsableByteArray initializationVectorData;
                int vectorSize;
                if (encryptionBox.perSampleIvSize != 0) {
                    initializationVectorData = this.fragment.sampleEncryptionData;
                    vectorSize = encryptionBox.perSampleIvSize;
                } else {
                    byte[] initVectorData = encryptionBox.defaultInitializationVector;
                    this.defaultInitializationVector.reset(initVectorData, initVectorData.length);
                    initializationVectorData = this.defaultInitializationVector;
                    vectorSize = initVectorData.length;
                }

                boolean subsampleEncryption = this.fragment.sampleHasSubsampleEncryptionTable(this.currentSampleIndex);
                this.encryptionSignalByte.data[0] = (byte)(vectorSize | (subsampleEncryption ? 128 : 0));
                this.encryptionSignalByte.setPosition(0);
                this.output.sampleData(this.encryptionSignalByte, 1);
                this.output.sampleData(initializationVectorData, vectorSize);
                if (!subsampleEncryption) {
                    return 1 + vectorSize;
                } else {
                    ParsableByteArray subsampleEncryptionData = this.fragment.sampleEncryptionData;
                    int subsampleCount = subsampleEncryptionData.readUnsignedShort();
                    subsampleEncryptionData.skipBytes(-2);
                    int subsampleDataLength = 2 + 6 * subsampleCount;
                    this.output.sampleData(subsampleEncryptionData, subsampleDataLength);
                    return 1 + vectorSize + subsampleDataLength;
                }
            }
        }

        private void skipSampleEncryptionData() {
            TrackEncryptionBox encryptionBox = this.getEncryptionBoxIfEncrypted();
            if (encryptionBox != null) {
                ParsableByteArray sampleEncryptionData = this.fragment.sampleEncryptionData;
                if (encryptionBox.perSampleIvSize != 0) {
                    sampleEncryptionData.skipBytes(encryptionBox.perSampleIvSize);
                }

                if (this.fragment.sampleHasSubsampleEncryptionTable(this.currentSampleIndex)) {
                    sampleEncryptionData.skipBytes(6 * sampleEncryptionData.readUnsignedShort());
                }

            }
        }

        private TrackEncryptionBox getEncryptionBoxIfEncrypted() {
            int sampleDescriptionIndex = this.fragment.header.sampleDescriptionIndex;
            TrackEncryptionBox encryptionBox = this.fragment.trackEncryptionBox != null ? this.fragment.trackEncryptionBox : this.track.getSampleDescriptionEncryptionBox(sampleDescriptionIndex);
            return encryptionBox != null && encryptionBox.isEncrypted ? encryptionBox : null;
        }
    }

    private static final class MetadataSampleInfo {
        public final long presentationTimeDeltaUs;
        public final int size;

        public MetadataSampleInfo(long presentationTimeDeltaUs, int size) {
            this.presentationTimeDeltaUs = presentationTimeDeltaUs;
            this.size = size;
        }
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface Flags {
    }
}
