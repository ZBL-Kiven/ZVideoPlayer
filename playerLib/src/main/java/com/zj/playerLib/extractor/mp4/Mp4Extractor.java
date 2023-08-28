package com.zj.playerLib.extractor.mp4;

import com.zj.playerLib.Format;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.ExtractorsFactory;
import com.zj.playerLib.extractor.GaplessInfoHolder;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.extractor.SeekMap;
import com.zj.playerLib.extractor.SeekPoint;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.extractor.mp4.Atom.ContainerAtom;
import com.zj.playerLib.extractor.mp4.Atom.LeafAtom;
import com.zj.playerLib.metadata.Metadata;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.NalUnitUtil;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class Mp4Extractor implements Extractor, SeekMap {
    public static final ExtractorsFactory FACTORY = () -> {
        return new Extractor[]{new Mp4Extractor()};
    };
    public static final int FLAG_WORKAROUND_IGNORE_EDIT_LISTS = 1;
    private static final int STATE_READING_ATOM_HEADER = 0;
    private static final int STATE_READING_ATOM_PAYLOAD = 1;
    private static final int STATE_READING_SAMPLE = 2;
    private static final int BRAND_QUICKTIME = Util.getIntegerCodeForString("qt  ");
    private static final long RELOAD_MINIMUM_SEEK_DISTANCE = 262144L;
    private static final long MAXIMUM_READ_AHEAD_BYTES_STREAM = 10485760L;
    private final int flags;
    private final ParsableByteArray nalStartCode;
    private final ParsableByteArray nalLength;
    private final ParsableByteArray atomHeader;
    private final ArrayDeque<ContainerAtom> containerAtoms;
    private int parserState;
    private int atomType;
    private long atomSize;
    private int atomHeaderBytesRead;
    private ParsableByteArray atomData;
    private int sampleTrackIndex;
    private int sampleBytesWritten;
    private int sampleCurrentNalBytesRemaining;
    private ExtractorOutput extractorOutput;
    private Mp4Track[] tracks;
    private long[][] accumulatedSampleSizes;
    private int firstVideoTrackIndex;
    private long durationUs;
    private boolean isQuickTime;

    public Mp4Extractor() {
        this(0);
    }

    public Mp4Extractor(int flags) {
        this.flags = flags;
        this.atomHeader = new ParsableByteArray(16);
        this.containerAtoms = new ArrayDeque();
        this.nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
        this.nalLength = new ParsableByteArray(4);
        this.sampleTrackIndex = -1;
    }

    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        return Sniffer.sniffUnfragmented(input);
    }

    public void init(ExtractorOutput output) {
        this.extractorOutput = output;
    }

    public void seek(long position, long timeUs) {
        this.containerAtoms.clear();
        this.atomHeaderBytesRead = 0;
        this.sampleTrackIndex = -1;
        this.sampleBytesWritten = 0;
        this.sampleCurrentNalBytesRemaining = 0;
        if (position == 0L) {
            this.enterReadingAtomHeaderState();
        } else if (this.tracks != null) {
            this.updateSampleIndices(timeUs);
        }

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
                if (!this.readAtomPayload(input, seekPosition)) {
                    break;
                }

                return 1;
            case 2:
                return this.readSample(input, seekPosition);
            default:
                throw new IllegalStateException();
            }
        }
    }

    public boolean isSeekable() {
        return true;
    }

    public long getDurationUs() {
        return this.durationUs;
    }

    public SeekPoints getSeekPoints(long timeUs) {
        if (this.tracks.length == 0) {
            return new SeekPoints(SeekPoint.START);
        } else {
            long secondTimeUs = -Long.MAX_VALUE;
            long secondOffset = -1L;
            long firstTimeUs;
            long firstOffset;
            if (this.firstVideoTrackIndex != -1) {
                TrackSampleTable sampleTable = this.tracks[this.firstVideoTrackIndex].sampleTable;
                int sampleIndex = getSynchronizationSampleIndex(sampleTable, timeUs);
                if (sampleIndex == -1) {
                    return new SeekPoints(SeekPoint.START);
                }

                long sampleTimeUs = sampleTable.timestampsUs[sampleIndex];
                firstTimeUs = sampleTimeUs;
                firstOffset = sampleTable.offsets[sampleIndex];
                if (sampleTimeUs < timeUs && sampleIndex < sampleTable.sampleCount - 1) {
                    int secondSampleIndex = sampleTable.getIndexOfLaterOrEqualSynchronizationSample(timeUs);
                    if (secondSampleIndex != -1 && secondSampleIndex != sampleIndex) {
                        secondTimeUs = sampleTable.timestampsUs[secondSampleIndex];
                        secondOffset = sampleTable.offsets[secondSampleIndex];
                    }
                }
            } else {
                firstTimeUs = timeUs;
                firstOffset = Long.MAX_VALUE;
            }

            for(int i = 0; i < this.tracks.length; ++i) {
                if (i != this.firstVideoTrackIndex) {
                    TrackSampleTable sampleTable = this.tracks[i].sampleTable;
                    firstOffset = maybeAdjustSeekOffset(sampleTable, firstTimeUs, firstOffset);
                    if (secondTimeUs != -Long.MAX_VALUE) {
                        secondOffset = maybeAdjustSeekOffset(sampleTable, secondTimeUs, secondOffset);
                    }
                }
            }

            SeekPoint firstSeekPoint = new SeekPoint(firstTimeUs, firstOffset);
            if (secondTimeUs == -Long.MAX_VALUE) {
                return new SeekPoints(firstSeekPoint);
            } else {
                SeekPoint secondSeekPoint = new SeekPoint(secondTimeUs, secondOffset);
                return new SeekPoints(firstSeekPoint, secondSeekPoint);
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

        long endPosition;
        if (this.atomSize == 1L) {
            int headerBytesRemaining = 8;
            input.readFully(this.atomHeader.data, 8, headerBytesRemaining);
            this.atomHeaderBytesRead += headerBytesRemaining;
            this.atomSize = this.atomHeader.readUnsignedLongToLong();
        } else if (this.atomSize == 0L) {
            endPosition = input.getLength();
            if (endPosition == -1L && !this.containerAtoms.isEmpty()) {
                endPosition = this.containerAtoms.peek().endPosition;
            }

            if (endPosition != -1L) {
                this.atomSize = endPosition - input.getPosition() + (long)this.atomHeaderBytesRead;
            }
        }

        if (this.atomSize < (long)this.atomHeaderBytesRead) {
            throw new ParserException("Atom size less than header length (unsupported).");
        } else {
            if (shouldParseContainerAtom(this.atomType)) {
                endPosition = input.getPosition() + this.atomSize - (long)this.atomHeaderBytesRead;
                this.containerAtoms.push(new ContainerAtom(this.atomType, endPosition));
                if (this.atomSize == (long)this.atomHeaderBytesRead) {
                    this.processAtomEnded(endPosition);
                } else {
                    this.enterReadingAtomHeaderState();
                }
            } else if (shouldParseLeafAtom(this.atomType)) {
                Assertions.checkState(this.atomHeaderBytesRead == 8);
                Assertions.checkState(this.atomSize <= 2147483647L);
                this.atomData = new ParsableByteArray((int)this.atomSize);
                System.arraycopy(this.atomHeader.data, 0, this.atomData.data, 0, 8);
                this.parserState = 1;
            } else {
                this.atomData = null;
                this.parserState = 1;
            }

            return true;
        }
    }

    private boolean readAtomPayload(ExtractorInput input, PositionHolder positionHolder) throws IOException, InterruptedException {
        long atomPayloadSize = this.atomSize - (long)this.atomHeaderBytesRead;
        long atomEndPosition = input.getPosition() + atomPayloadSize;
        boolean seekRequired = false;
        if (this.atomData != null) {
            input.readFully(this.atomData.data, this.atomHeaderBytesRead, (int)atomPayloadSize);
            if (this.atomType == Atom.TYPE_ftyp) {
                this.isQuickTime = processFtypAtom(this.atomData);
            } else if (!this.containerAtoms.isEmpty()) {
                this.containerAtoms.peek().add(new LeafAtom(this.atomType, this.atomData));
            }
        } else if (atomPayloadSize < 262144L) {
            input.skipFully((int)atomPayloadSize);
        } else {
            positionHolder.position = input.getPosition() + atomPayloadSize;
            seekRequired = true;
        }

        this.processAtomEnded(atomEndPosition);
        return seekRequired && this.parserState != 2;
    }

    private void processAtomEnded(long atomEndPosition) throws ParserException {
        while(!this.containerAtoms.isEmpty() && this.containerAtoms.peek().endPosition == atomEndPosition) {
            ContainerAtom containerAtom = this.containerAtoms.pop();
            if (containerAtom.type == Atom.TYPE_moov) {
                this.processMoovAtom(containerAtom);
                this.containerAtoms.clear();
                this.parserState = 2;
            } else if (!this.containerAtoms.isEmpty()) {
                this.containerAtoms.peek().add(containerAtom);
            }
        }

        if (this.parserState != 2) {
            this.enterReadingAtomHeaderState();
        }

    }

    private void processMoovAtom(ContainerAtom moov) throws ParserException {
        int firstVideoTrackIndex = -1;
        long durationUs = -Long.MAX_VALUE;
        List<Mp4Track> tracks = new ArrayList();
        Metadata metadata = null;
        GaplessInfoHolder gaplessInfoHolder = new GaplessInfoHolder();
        LeafAtom udta = moov.getLeafAtomOfType(Atom.TYPE_udta);
        if (udta != null) {
            metadata = AtomParsers.parseUdta(udta, this.isQuickTime);
            if (metadata != null) {
                gaplessInfoHolder.setFromMetadata(metadata);
            }
        }

        boolean ignoreEditLists = (this.flags & 1) != 0;
        ArrayList<TrackSampleTable> trackSampleTables = this.getTrackSampleTables(moov, gaplessInfoHolder, ignoreEditLists);
        int trackCount = trackSampleTables.size();

        for(int i = 0; i < trackCount; ++i) {
            TrackSampleTable trackSampleTable = trackSampleTables.get(i);
            Track track = trackSampleTable.track;
            Mp4Track mp4Track = new Mp4Track(track, trackSampleTable, this.extractorOutput.track(i, track.type));
            int maxInputSize = trackSampleTable.maximumSize + 30;
            Format format = track.format.copyWithMaxInputSize(maxInputSize);
            if (track.type == 1) {
                if (gaplessInfoHolder.hasGaplessInfo()) {
                    format = format.copyWithGapLessInfo(gaplessInfoHolder.encoderDelay, gaplessInfoHolder.encoderPadding);
                }

                if (metadata != null) {
                    format = format.copyWithMetadata(metadata);
                }
            }

            mp4Track.trackOutput.format(format);
            durationUs = Math.max(durationUs, track.durationUs != -Long.MAX_VALUE ? track.durationUs : trackSampleTable.durationUs);
            if (track.type == 2 && firstVideoTrackIndex == -1) {
                firstVideoTrackIndex = tracks.size();
            }

            tracks.add(mp4Track);
        }

        this.firstVideoTrackIndex = firstVideoTrackIndex;
        this.durationUs = durationUs;
        this.tracks = tracks.toArray(new Mp4Track[tracks.size()]);
        this.accumulatedSampleSizes = calculateAccumulatedSampleSizes(this.tracks);
        this.extractorOutput.endTracks();
        this.extractorOutput.seekMap(this);
    }

    private ArrayList<TrackSampleTable> getTrackSampleTables(ContainerAtom moov, GaplessInfoHolder gaplessInfoHolder, boolean ignoreEditLists) throws ParserException {
        ArrayList<TrackSampleTable> trackSampleTables = new ArrayList();

        for(int i = 0; i < moov.containerChildren.size(); ++i) {
            ContainerAtom atom = moov.containerChildren.get(i);
            if (atom.type == Atom.TYPE_trak) {
                Track track = AtomParsers.parseTrak(atom, moov.getLeafAtomOfType(Atom.TYPE_mvhd), -Long.MAX_VALUE, null, ignoreEditLists, this.isQuickTime);
                if (track != null) {
                    ContainerAtom stblAtom = atom.getContainerAtomOfType(Atom.TYPE_mdia).getContainerAtomOfType(Atom.TYPE_minf).getContainerAtomOfType(Atom.TYPE_stbl);
                    TrackSampleTable trackSampleTable = AtomParsers.parseStbl(track, stblAtom, gaplessInfoHolder);
                    if (trackSampleTable.sampleCount != 0) {
                        trackSampleTables.add(trackSampleTable);
                    }
                }
            }
        }

        return trackSampleTables;
    }

    private int readSample(ExtractorInput input, PositionHolder positionHolder) throws IOException, InterruptedException {
        long inputPosition = input.getPosition();
        if (this.sampleTrackIndex == -1) {
            this.sampleTrackIndex = this.getTrackIndexOfNextReadSample(inputPosition);
            if (this.sampleTrackIndex == -1) {
                return -1;
            }
        }

        Mp4Track track = this.tracks[this.sampleTrackIndex];
        TrackOutput trackOutput = track.trackOutput;
        int sampleIndex = track.sampleIndex;
        long position = track.sampleTable.offsets[sampleIndex];
        int sampleSize = track.sampleTable.sizes[sampleIndex];
        long skipAmount = position - inputPosition + (long)this.sampleBytesWritten;
        if (skipAmount >= 0L && skipAmount < 262144L) {
            if (track.track.sampleTransformation == 1) {
                skipAmount += 8L;
                sampleSize -= 8;
            }

            input.skipFully((int)skipAmount);
            if (track.track.nalUnitLengthFieldLength != 0) {
                byte[] nalLengthData = this.nalLength.data;
                nalLengthData[0] = 0;
                nalLengthData[1] = 0;
                nalLengthData[2] = 0;
                int nalUnitLengthFieldLength = track.track.nalUnitLengthFieldLength;
                int nalUnitLengthFieldLengthDiff = 4 - track.track.nalUnitLengthFieldLength;

                while(this.sampleBytesWritten < sampleSize) {
                    if (this.sampleCurrentNalBytesRemaining == 0) {
                        input.readFully(this.nalLength.data, nalUnitLengthFieldLengthDiff, nalUnitLengthFieldLength);
                        this.nalLength.setPosition(0);
                        this.sampleCurrentNalBytesRemaining = this.nalLength.readUnsignedIntToInt();
                        this.nalStartCode.setPosition(0);
                        trackOutput.sampleData(this.nalStartCode, 4);
                        this.sampleBytesWritten += 4;
                        sampleSize += nalUnitLengthFieldLengthDiff;
                    } else {
                        int writtenBytes = trackOutput.sampleData(input, this.sampleCurrentNalBytesRemaining, false);
                        this.sampleBytesWritten += writtenBytes;
                        this.sampleCurrentNalBytesRemaining -= writtenBytes;
                    }
                }
            } else {
                while(this.sampleBytesWritten < sampleSize) {
                    int writtenBytes = trackOutput.sampleData(input, sampleSize - this.sampleBytesWritten, false);
                    this.sampleBytesWritten += writtenBytes;
                    this.sampleCurrentNalBytesRemaining -= writtenBytes;
                }
            }

            trackOutput.sampleMetadata(track.sampleTable.timestampsUs[sampleIndex], track.sampleTable.flags[sampleIndex], sampleSize, 0, null);
            ++track.sampleIndex;
            this.sampleTrackIndex = -1;
            this.sampleBytesWritten = 0;
            this.sampleCurrentNalBytesRemaining = 0;
            return 0;
        } else {
            positionHolder.position = position;
            return 1;
        }
    }

    private int getTrackIndexOfNextReadSample(long inputPosition) {
        long preferredSkipAmount = Long.MAX_VALUE;
        boolean preferredRequiresReload = true;
        int preferredTrackIndex = -1;
        long preferredAccumulatedBytes = Long.MAX_VALUE;
        long minAccumulatedBytes = Long.MAX_VALUE;
        boolean minAccumulatedBytesRequiresReload = true;
        int minAccumulatedBytesTrackIndex = -1;

        for(int trackIndex = 0; trackIndex < this.tracks.length; ++trackIndex) {
            Mp4Track track = this.tracks[trackIndex];
            int sampleIndex = track.sampleIndex;
            if (sampleIndex != track.sampleTable.sampleCount) {
                long sampleOffset = track.sampleTable.offsets[sampleIndex];
                long sampleAccumulatedBytes = this.accumulatedSampleSizes[trackIndex][sampleIndex];
                long skipAmount = sampleOffset - inputPosition;
                boolean requiresReload = skipAmount < 0L || skipAmount >= 262144L;
                if (!requiresReload && preferredRequiresReload || requiresReload == preferredRequiresReload && skipAmount < preferredSkipAmount) {
                    preferredRequiresReload = requiresReload;
                    preferredSkipAmount = skipAmount;
                    preferredTrackIndex = trackIndex;
                    preferredAccumulatedBytes = sampleAccumulatedBytes;
                }

                if (sampleAccumulatedBytes < minAccumulatedBytes) {
                    minAccumulatedBytes = sampleAccumulatedBytes;
                    minAccumulatedBytesRequiresReload = requiresReload;
                    minAccumulatedBytesTrackIndex = trackIndex;
                }
            }
        }

        return minAccumulatedBytes != Long.MAX_VALUE && minAccumulatedBytesRequiresReload && preferredAccumulatedBytes >= minAccumulatedBytes + 10485760L ? minAccumulatedBytesTrackIndex : preferredTrackIndex;
    }

    private void updateSampleIndices(long timeUs) {
        Mp4Track[] var3 = this.tracks;
        int var4 = var3.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            Mp4Track track = var3[var5];
            TrackSampleTable sampleTable = track.sampleTable;
            int sampleIndex = sampleTable.getIndexOfEarlierOrEqualSynchronizationSample(timeUs);
            if (sampleIndex == -1) {
                sampleIndex = sampleTable.getIndexOfLaterOrEqualSynchronizationSample(timeUs);
            }

            track.sampleIndex = sampleIndex;
        }

    }

    private static long[][] calculateAccumulatedSampleSizes(Mp4Track[] tracks) {
        long[][] accumulatedSampleSizes = new long[tracks.length][];
        int[] nextSampleIndex = new int[tracks.length];
        long[] nextSampleTimesUs = new long[tracks.length];
        boolean[] tracksFinished = new boolean[tracks.length];

        for(int i = 0; i < tracks.length; ++i) {
            accumulatedSampleSizes[i] = new long[tracks[i].sampleTable.sampleCount];
            nextSampleTimesUs[i] = tracks[i].sampleTable.timestampsUs[0];
        }

        long accumulatedSampleSize = 0L;
        int finishedTracks = 0;

        while(finishedTracks < tracks.length) {
            long minTimeUs = Long.MAX_VALUE;
            int minTimeTrackIndex = -1;

            int trackSampleIndex;
            for(trackSampleIndex = 0; trackSampleIndex < tracks.length; ++trackSampleIndex) {
                if (!tracksFinished[trackSampleIndex] && nextSampleTimesUs[trackSampleIndex] <= minTimeUs) {
                    minTimeTrackIndex = trackSampleIndex;
                    minTimeUs = nextSampleTimesUs[trackSampleIndex];
                }
            }

            trackSampleIndex = nextSampleIndex[minTimeTrackIndex];
            accumulatedSampleSizes[minTimeTrackIndex][trackSampleIndex] = accumulatedSampleSize;
            accumulatedSampleSize += tracks[minTimeTrackIndex].sampleTable.sizes[trackSampleIndex];
            ++trackSampleIndex;
            nextSampleIndex[minTimeTrackIndex] = trackSampleIndex;
            if (trackSampleIndex < accumulatedSampleSizes[minTimeTrackIndex].length) {
                nextSampleTimesUs[minTimeTrackIndex] = tracks[minTimeTrackIndex].sampleTable.timestampsUs[trackSampleIndex];
            } else {
                tracksFinished[minTimeTrackIndex] = true;
                ++finishedTracks;
            }
        }

        return accumulatedSampleSizes;
    }

    private static long maybeAdjustSeekOffset(TrackSampleTable sampleTable, long seekTimeUs, long offset) {
        int sampleIndex = getSynchronizationSampleIndex(sampleTable, seekTimeUs);
        if (sampleIndex == -1) {
            return offset;
        } else {
            long sampleOffset = sampleTable.offsets[sampleIndex];
            return Math.min(sampleOffset, offset);
        }
    }

    private static int getSynchronizationSampleIndex(TrackSampleTable sampleTable, long timeUs) {
        int sampleIndex = sampleTable.getIndexOfEarlierOrEqualSynchronizationSample(timeUs);
        if (sampleIndex == -1) {
            sampleIndex = sampleTable.getIndexOfLaterOrEqualSynchronizationSample(timeUs);
        }

        return sampleIndex;
    }

    private static boolean processFtypAtom(ParsableByteArray atomData) {
        atomData.setPosition(8);
        int majorBrand = atomData.readInt();
        if (majorBrand == BRAND_QUICKTIME) {
            return true;
        } else {
            atomData.skipBytes(4);

            do {
                if (atomData.bytesLeft() <= 0) {
                    return false;
                }
            } while(atomData.readInt() != BRAND_QUICKTIME);

            return true;
        }
    }

    private static boolean shouldParseLeafAtom(int atom) {
        return atom == Atom.TYPE_mdhd || atom == Atom.TYPE_mvhd || atom == Atom.TYPE_hdlr || atom == Atom.TYPE_stsd || atom == Atom.TYPE_stts || atom == Atom.TYPE_stss || atom == Atom.TYPE_ctts || atom == Atom.TYPE_elst || atom == Atom.TYPE_stsc || atom == Atom.TYPE_stsz || atom == Atom.TYPE_stz2 || atom == Atom.TYPE_stco || atom == Atom.TYPE_co64 || atom == Atom.TYPE_tkhd || atom == Atom.TYPE_ftyp || atom == Atom.TYPE_udta;
    }

    private static boolean shouldParseContainerAtom(int atom) {
        return atom == Atom.TYPE_moov || atom == Atom.TYPE_trak || atom == Atom.TYPE_mdia || atom == Atom.TYPE_minf || atom == Atom.TYPE_stbl || atom == Atom.TYPE_edts;
    }

    private static final class Mp4Track {
        public final Track track;
        public final TrackSampleTable sampleTable;
        public final TrackOutput trackOutput;
        public int sampleIndex;

        public Mp4Track(Track track, TrackSampleTable sampleTable, TrackOutput trackOutput) {
            this.track = track;
            this.sampleTable = sampleTable;
            this.trackOutput = trackOutput;
        }
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface Flags {
    }
}
