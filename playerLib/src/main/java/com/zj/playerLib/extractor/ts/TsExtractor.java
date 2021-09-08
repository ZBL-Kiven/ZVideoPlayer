//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.ts;

import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.ExtractorsFactory;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.extractor.SeekMap.Unseekable;
import com.zj.playerLib.extractor.ts.TsPayloadReader.DvbSubtitleInfo;
import com.zj.playerLib.extractor.ts.TsPayloadReader.EsInfo;
import com.zj.playerLib.extractor.ts.TsPayloadReader.Factory;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.ParsableBitArray;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.TimestampAdjuster;
import com.zj.playerLib.util.Util;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TsExtractor implements Extractor {

    public static final ExtractorsFactory FACTORY = () -> new Extractor[]{new TsExtractor()};
    public static final int MODE_MULTI_PMT = 0;
    public static final int MODE_SINGLE_PMT = 1;
    public static final int MODE_HLS = 2;
    public static final int TS_STREAM_TYPE_MPA = 3;
    public static final int TS_STREAM_TYPE_MPA_LSF = 4;
    public static final int TS_STREAM_TYPE_AAC_ADTS = 15;
    public static final int TS_STREAM_TYPE_AAC_LATM = 17;
    public static final int TS_STREAM_TYPE_AC3 = 129;
    public static final int TS_STREAM_TYPE_DTS = 138;
    public static final int TS_STREAM_TYPE_HDMV_DTS = 130;
    public static final int TS_STREAM_TYPE_E_AC3 = 135;
    public static final int TS_STREAM_TYPE_H262 = 2;
    public static final int TS_STREAM_TYPE_H264 = 27;
    public static final int TS_STREAM_TYPE_H265 = 36;
    public static final int TS_STREAM_TYPE_ID3 = 21;
    public static final int TS_STREAM_TYPE_SPLICE_INFO = 134;
    public static final int TS_STREAM_TYPE_DVBSUBS = 89;
    public static final int TS_PACKET_SIZE = 188;
    public static final int TS_SYNC_BYTE = 71;
    private static final int TS_PAT_PID = 0;
    private static final int MAX_PID_PLUS_ONE = 8192;
    private static final long AC3_FORMAT_IDENTIFIER = Util.getIntegerCodeForString("AC-3");
    private static final long E_AC3_FORMAT_IDENTIFIER = Util.getIntegerCodeForString("EAC3");
    private static final long HEVC_FORMAT_IDENTIFIER = Util.getIntegerCodeForString("HEVC");
    private static final int BUFFER_SIZE = 9400;
    private static final int SNIFF_TS_PACKET_COUNT = 5;
    private final int mode;
    private final List<TimestampAdjuster> timestampAdjusters;
    private final ParsableByteArray tsPacketBuffer;
    private final SparseIntArray continuityCounters;
    private final Factory payloadReaderFactory;
    private final SparseArray<TsPayloadReader> tsPayloadReaders;
    private final SparseBooleanArray trackIds;
    private final SparseBooleanArray trackPids;
    private final TsDurationReader durationReader;
    private TsBinarySearchSeeker tsBinarySearchSeeker;
    private ExtractorOutput output;
    private int remainingPmts;
    private boolean tracksEnded;
    private boolean hasOutputSeekMap;
    private boolean pendingSeekToStart;
    private TsPayloadReader id3Reader;
    private int bytesSinceLastSync;
    private int pcrPid;

    public TsExtractor() {
        this(0);
    }

    public TsExtractor(int defaultTsPayloadReaderFlags) {
        this(1, defaultTsPayloadReaderFlags);
    }

    public TsExtractor(int mode, int defaultTsPayloadReaderFlags) {
        this(mode, new TimestampAdjuster(0L), new DefaultTsPayloadReaderFactory(defaultTsPayloadReaderFlags));
    }

    public TsExtractor(int mode, TimestampAdjuster timestampAdjuster, Factory payloadReaderFactory) {
        this.payloadReaderFactory = Assertions.checkNotNull(payloadReaderFactory);
        this.mode = mode;
        if (mode != 1 && mode != 2) {
            this.timestampAdjusters = new ArrayList<>();
            this.timestampAdjusters.add(timestampAdjuster);
        } else {
            this.timestampAdjusters = Collections.singletonList(timestampAdjuster);
        }

        this.tsPacketBuffer = new ParsableByteArray(new byte[9400], 0);
        this.trackIds = new SparseBooleanArray();
        this.trackPids = new SparseBooleanArray();
        this.tsPayloadReaders = new SparseArray<>();
        this.continuityCounters = new SparseIntArray();
        this.durationReader = new TsDurationReader();
        this.pcrPid = -1;
        this.resetPayloadReaders();
    }

    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        byte[] buffer = this.tsPacketBuffer.data;
        input.peekFully(buffer, 0, 940);

        for (int startPosCandidate = 0; startPosCandidate < 188; ++startPosCandidate) {
            boolean isSyncBytePatternCorrect = true;

            for (int i = 0; i < 5; ++i) {
                if (buffer[startPosCandidate + i * 188] != 71) {
                    isSyncBytePatternCorrect = false;
                    break;
                }
            }

            if (isSyncBytePatternCorrect) {
                input.skipFully(startPosCandidate);
                return true;
            }
        }

        return false;
    }

    public void init(ExtractorOutput output) {
        this.output = output;
    }

    public void seek(long position, long timeUs) {
        Assertions.checkState(this.mode != 2);
        int timestampAdjustersCount = this.timestampAdjusters.size();

        int i;
        for (i = 0; i < timestampAdjustersCount; ++i) {
            TimestampAdjuster timestampAdjuster = this.timestampAdjusters.get(i);
            boolean hasNotEncounteredFirstTimestamp = timestampAdjuster.getTimestampOffsetUs() == -9223372036854775807L;
            if (hasNotEncounteredFirstTimestamp || timestampAdjuster.getTimestampOffsetUs() != 0L && timestampAdjuster.getFirstSampleTimestampUs() != timeUs) {
                timestampAdjuster.reset();
                timestampAdjuster.setFirstSampleTimestampUs(timeUs);
            }
        }

        if (timeUs != 0L && this.tsBinarySearchSeeker != null) {
            this.tsBinarySearchSeeker.setSeekTargetUs(timeUs);
        }

        this.tsPacketBuffer.reset();
        this.continuityCounters.clear();

        for (i = 0; i < this.tsPayloadReaders.size(); ++i) {
            this.tsPayloadReaders.valueAt(i).seek();
        }

        this.bytesSinceLastSync = 0;
    }

    public void release() {
    }

    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        long inputLength = input.getLength();
        if (this.tracksEnded) {
            boolean canReadDuration = inputLength != -1L && this.mode != 2;
            if (canReadDuration && !this.durationReader.isDurationReadFinished()) {
                return this.durationReader.readDuration(input, seekPosition, this.pcrPid);
            }

            this.maybeOutputSeekMap(inputLength);
            if (this.pendingSeekToStart) {
                this.pendingSeekToStart = false;
                this.seek(0L, 0L);
                if (input.getPosition() != 0L) {
                    seekPosition.position = 0L;
                    return 1;
                }
            }

            if (this.tsBinarySearchSeeker != null && this.tsBinarySearchSeeker.isSeeking()) {
                return this.tsBinarySearchSeeker.handlePendingSeek(input, seekPosition, null);
            }
        }

        if (!this.fillBufferWithAtLeastOnePacket(input)) {
            return -1;
        } else {
            int endOfPacket = this.findEndOfFirstTsPacketInBuffer();
            int limit = this.tsPacketBuffer.limit();
            if (endOfPacket <= limit) {
                int tsPacketHeader = this.tsPacketBuffer.readInt();
                if ((tsPacketHeader & 8388608) == 0) {
                    int packetHeaderFlags = ((tsPacketHeader & 4194304) != 0 ? 1 : 0);
                    int pid = (tsPacketHeader & 2096896) >> 8;
                    boolean adaptationFieldExists = (tsPacketHeader & 32) != 0;
                    boolean payloadExists = (tsPacketHeader & 16) != 0;
                    TsPayloadReader payloadReader = payloadExists ? this.tsPayloadReaders.get(pid) : null;
                    if (payloadReader != null) {
                        int continuityCounter;
                        int previousCounter;
                        if (this.mode != 2) {
                            continuityCounter = tsPacketHeader & 15;
                            previousCounter = this.continuityCounters.get(pid, continuityCounter - 1);
                            this.continuityCounters.put(pid, continuityCounter);
                            if (previousCounter == continuityCounter) {
                                this.tsPacketBuffer.setPosition(endOfPacket);
                                return 0;
                            }

                            if (continuityCounter != (previousCounter + 1 & 15)) {
                                payloadReader.seek();
                            }
                        }

                        if (adaptationFieldExists) {
                            continuityCounter = this.tsPacketBuffer.readUnsignedByte();
                            previousCounter = this.tsPacketBuffer.readUnsignedByte();
                            packetHeaderFlags |= (previousCounter & 64) != 0 ? 2 : 0;
                            this.tsPacketBuffer.skipBytes(continuityCounter - 1);
                        }

                        boolean wereTracksEnded = this.tracksEnded;
                        if (this.shouldConsumePacketPayload(pid)) {
                            this.tsPacketBuffer.setLimit(endOfPacket);
                            payloadReader.consume(this.tsPacketBuffer, packetHeaderFlags);
                            this.tsPacketBuffer.setLimit(limit);
                        }

                        if (this.mode != 2 && !wereTracksEnded && this.tracksEnded && inputLength != -1L) {
                            this.pendingSeekToStart = true;
                        }

                    }
                }
                this.tsPacketBuffer.setPosition(endOfPacket);
            }
            return 0;
        }
    }

    private void maybeOutputSeekMap(long inputLength) {
        if (!this.hasOutputSeekMap) {
            this.hasOutputSeekMap = true;
            if (this.durationReader.getDurationUs() != -9223372036854775807L) {
                this.tsBinarySearchSeeker = new TsBinarySearchSeeker(this.durationReader.getPcrTimestampAdjuster(), this.durationReader.getDurationUs(), inputLength, this.pcrPid);
                this.output.seekMap(this.tsBinarySearchSeeker.getSeekMap());
            } else {
                this.output.seekMap(new Unseekable(this.durationReader.getDurationUs()));
            }
        }

    }

    private boolean fillBufferWithAtLeastOnePacket(ExtractorInput input) throws IOException, InterruptedException {
        byte[] data = this.tsPacketBuffer.data;
        int limit;
        if (9400 - this.tsPacketBuffer.getPosition() < 188) {
            limit = this.tsPacketBuffer.bytesLeft();
            if (limit > 0) {
                System.arraycopy(data, this.tsPacketBuffer.getPosition(), data, 0, limit);
            }

            this.tsPacketBuffer.reset(data, limit);
        }

        while (this.tsPacketBuffer.bytesLeft() < 188) {
            limit = this.tsPacketBuffer.limit();
            int read = input.read(data, limit, 9400 - limit);
            if (read == -1) {
                return false;
            }

            this.tsPacketBuffer.setLimit(limit + read);
        }

        return true;
    }

    private int findEndOfFirstTsPacketInBuffer() throws ParserException {
        int searchStart = this.tsPacketBuffer.getPosition();
        int limit = this.tsPacketBuffer.limit();
        int syncBytePosition = TsUtil.findSyncBytePosition(this.tsPacketBuffer.data, searchStart, limit);
        this.tsPacketBuffer.setPosition(syncBytePosition);
        int endOfPacket = syncBytePosition + 188;
        if (endOfPacket > limit) {
            this.bytesSinceLastSync += syncBytePosition - searchStart;
            if (this.mode == 2 && this.bytesSinceLastSync > 376) {
                throw new ParserException("Cannot find sync byte. Most likely not a Transport Stream.");
            }
        } else {
            this.bytesSinceLastSync = 0;
        }

        return endOfPacket;
    }

    private boolean shouldConsumePacketPayload(int packetPid) {
        return this.mode == 2 || this.tracksEnded || !this.trackPids.get(packetPid, false);
    }

    private void resetPayloadReaders() {
        this.trackIds.clear();
        this.tsPayloadReaders.clear();
        SparseArray<TsPayloadReader> initialPayloadReaders = this.payloadReaderFactory.createInitialPayloadReaders();
        int initialPayloadReadersSize = initialPayloadReaders.size();

        for (int i = 0; i < initialPayloadReadersSize; ++i) {
            this.tsPayloadReaders.put(initialPayloadReaders.keyAt(i), initialPayloadReaders.valueAt(i));
        }

        this.tsPayloadReaders.put(0, new SectionReader(new PatReader()));
        this.id3Reader = null;
    }

    private class PmtReader implements SectionPayloadReader {
        private static final int TS_PMT_DESC_REGISTRATION = 5;
        private static final int TS_PMT_DESC_ISO639_LANG = 10;
        private static final int TS_PMT_DESC_AC3 = 106;
        private static final int TS_PMT_DESC_EAC3 = 122;
        private static final int TS_PMT_DESC_DTS = 123;
        private static final int TS_PMT_DESC_DVBSUBS = 89;
        private final ParsableBitArray pmtScratch = new ParsableBitArray(new byte[5]);
        private final SparseArray<TsPayloadReader> trackIdToReaderScratch = new SparseArray<>();
        private final SparseIntArray trackIdToPidScratch = new SparseIntArray();
        private final int pid;

        public PmtReader(int pid) {
            this.pid = pid;
        }

        public void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
        }

        public void consume(ParsableByteArray sectionData) {
            int tableId = sectionData.readUnsignedByte();
            if (tableId == 2) {
                TimestampAdjuster timestampAdjuster;
                if (TsExtractor.this.mode != 1 && TsExtractor.this.mode != 2 && TsExtractor.this.remainingPmts != 1) {
                    timestampAdjuster = new TimestampAdjuster(TsExtractor.this.timestampAdjusters.get(0).getFirstSampleTimestampUs());
                    TsExtractor.this.timestampAdjusters.add(timestampAdjuster);
                } else {
                    timestampAdjuster = TsExtractor.this.timestampAdjusters.get(0);
                }

                sectionData.skipBytes(2);
                int programNumber = sectionData.readUnsignedShort();
                sectionData.skipBytes(3);
                sectionData.readBytes(this.pmtScratch, 2);
                this.pmtScratch.skipBits(3);
                TsExtractor.this.pcrPid = this.pmtScratch.readBits(13);
                sectionData.readBytes(this.pmtScratch, 2);
                this.pmtScratch.skipBits(4);
                int programInfoLength = this.pmtScratch.readBits(12);
                sectionData.skipBytes(programInfoLength);
                if (TsExtractor.this.mode == 2 && TsExtractor.this.id3Reader == null) {
                    EsInfo dummyEsInfo = new EsInfo(21, null, null, Util.EMPTY_BYTE_ARRAY);
                    TsExtractor.this.id3Reader = TsExtractor.this.payloadReaderFactory.createPayloadReader(21, dummyEsInfo);
                    TsExtractor.this.id3Reader.init(timestampAdjuster, TsExtractor.this.output, new TrackIdGenerator(programNumber, 21, 8192));
                }

                this.trackIdToReaderScratch.clear();
                this.trackIdToPidScratch.clear();
                int remainingEntriesLength = sectionData.bytesLeft();

                while (true) {
                    int elementaryPid;
                    int trackId;
                    TsPayloadReader reader;
                    do {
                        int streamType;
                        EsInfo esInfo;
                        do {
                            int trackIdx;
                            if (remainingEntriesLength <= 0) {
                                streamType = this.trackIdToPidScratch.size();

                                for (elementaryPid = 0; elementaryPid < streamType; ++elementaryPid) {
                                    trackIdx = this.trackIdToPidScratch.keyAt(elementaryPid);
                                    int trackPid = this.trackIdToPidScratch.valueAt(elementaryPid);
                                    TsExtractor.this.trackIds.put(trackIdx, true);
                                    TsExtractor.this.trackPids.put(trackPid, true);
                                    TsPayloadReader readerx = this.trackIdToReaderScratch.valueAt(elementaryPid);
                                    if (readerx != null) {
                                        if (readerx != TsExtractor.this.id3Reader) {
                                            readerx.init(timestampAdjuster, TsExtractor.this.output, new TrackIdGenerator(programNumber, trackIdx, 8192));
                                        }

                                        TsExtractor.this.tsPayloadReaders.put(trackPid, readerx);
                                    }
                                }

                                if (TsExtractor.this.mode == 2) {
                                    if (!TsExtractor.this.tracksEnded) {
                                        TsExtractor.this.output.endTracks();
                                        TsExtractor.this.remainingPmts = 0;
                                        TsExtractor.this.tracksEnded = true;
                                    }
                                } else {
                                    TsExtractor.this.tsPayloadReaders.remove(this.pid);
                                    TsExtractor.this.remainingPmts = TsExtractor.this.mode == 1 ? 0 : TsExtractor.this.remainingPmts - 1;
                                    if (TsExtractor.this.remainingPmts == 0) {
                                        TsExtractor.this.output.endTracks();
                                        TsExtractor.this.tracksEnded = true;
                                    }
                                }

                                return;
                            }

                            sectionData.readBytes(this.pmtScratch, 5);
                            streamType = this.pmtScratch.readBits(8);
                            this.pmtScratch.skipBits(3);
                            elementaryPid = this.pmtScratch.readBits(13);
                            this.pmtScratch.skipBits(4);
                            trackIdx = this.pmtScratch.readBits(12);
                            esInfo = this.readEsInfo(sectionData, trackIdx);
                            if (streamType == 6) {
                                streamType = esInfo.streamType;
                            }

                            remainingEntriesLength -= trackIdx + 5;
                            trackId = TsExtractor.this.mode == 2 ? streamType : elementaryPid;
                        } while (TsExtractor.this.trackIds.get(trackId));

                        reader = TsExtractor.this.mode == 2 && streamType == 21 ? TsExtractor.this.id3Reader : TsExtractor.this.payloadReaderFactory.createPayloadReader(streamType, esInfo);
                    } while (TsExtractor.this.mode == 2 && elementaryPid >= this.trackIdToPidScratch.get(trackId, 8192));

                    this.trackIdToPidScratch.put(trackId, elementaryPid);
                    this.trackIdToReaderScratch.put(trackId, reader);
                }
            }
        }

        private EsInfo readEsInfo(ParsableByteArray data, int length) {
            int descriptorsStartPosition = data.getPosition();
            int descriptorsEndPosition = descriptorsStartPosition + length;
            int streamType = -1;
            String language = null;
            ArrayList<DvbSubtitleInfo> dvbSubtitleInfos;
            int positionOfNextDescriptor;
            for (dvbSubtitleInfos = null; data.getPosition() < descriptorsEndPosition; data.skipBytes(positionOfNextDescriptor - data.getPosition())) {
                int descriptorTag = data.readUnsignedByte();
                int descriptorLength = data.readUnsignedByte();
                positionOfNextDescriptor = data.getPosition() + descriptorLength;
                if (descriptorTag == 5) {
                    long formatIdentifier = data.readUnsignedInt();
                    if (formatIdentifier == TsExtractor.AC3_FORMAT_IDENTIFIER) {
                        streamType = 129;
                    } else if (formatIdentifier == TsExtractor.E_AC3_FORMAT_IDENTIFIER) {
                        streamType = 135;
                    } else if (formatIdentifier == TsExtractor.HEVC_FORMAT_IDENTIFIER) {
                        streamType = 36;
                    }
                } else if (descriptorTag == 106) {
                    streamType = 129;
                } else if (descriptorTag == 122) {
                    streamType = 135;
                } else if (descriptorTag == 123) {
                    streamType = 138;
                } else if (descriptorTag == 10) {
                    language = data.readString(3).trim();
                } else if (descriptorTag == 89) {
                    streamType = 89;
                    dvbSubtitleInfos = new ArrayList<>();
                    while (data.getPosition() < positionOfNextDescriptor) {
                        String dvbLanguage = data.readString(3).trim();
                        int dvbSubtitlingType = data.readUnsignedByte();
                        byte[] initializationData = new byte[4];
                        data.readBytes(initializationData, 0, 4);
                        dvbSubtitleInfos.add(new DvbSubtitleInfo(dvbLanguage, dvbSubtitlingType, initializationData));
                    }
                }
            }
            data.setPosition(descriptorsEndPosition);
            return new EsInfo(streamType, language, dvbSubtitleInfos, Arrays.copyOfRange(data.data, descriptorsStartPosition, descriptorsEndPosition));
        }
    }

    private class PatReader implements SectionPayloadReader {
        private final ParsableBitArray patScratch = new ParsableBitArray(new byte[4]);

        public PatReader() {
        }

        public void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
        }

        public void consume(ParsableByteArray sectionData) {
            int tableId = sectionData.readUnsignedByte();
            if (tableId == 0) {
                sectionData.skipBytes(7);
                int programCount = sectionData.bytesLeft() / 4;

                for (int i = 0; i < programCount; ++i) {
                    sectionData.readBytes(this.patScratch, 4);
                    int programNumber = this.patScratch.readBits(16);
                    this.patScratch.skipBits(3);
                    if (programNumber == 0) {
                        this.patScratch.skipBits(13);
                    } else {
                        int pid = this.patScratch.readBits(13);
                        TsExtractor.this.tsPayloadReaders.put(pid, new SectionReader(TsExtractor.this.new PmtReader(pid)));
                        TsExtractor.this.remainingPmts++;
                    }
                }

                if (TsExtractor.this.mode != 2) {
                    TsExtractor.this.tsPayloadReaders.remove(0);
                }

            }
        }
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {}
}
