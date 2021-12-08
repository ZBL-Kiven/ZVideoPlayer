package com.zj.playerLib.extractor.mkv;

import android.util.Pair;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import com.zj.playerLib.C;
import com.zj.playerLib.Format;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.audio.Ac3Util;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.drm.DrmInitData.SchemeData;
import com.zj.playerLib.extractor.ChunkIndex;
import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.ExtractorsFactory;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.extractor.SeekMap;
import com.zj.playerLib.extractor.SeekMap.Unseekable;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.LongArray;
import com.zj.playerLib.util.MimeTypes;
import com.zj.playerLib.util.NalUnitUtil;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;
import com.zj.playerLib.video.AvcConfig;
import com.zj.playerLib.video.ColorInfo;
import com.zj.playerLib.video.HevcConfig;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MatroskaExtractor implements Extractor {
    public static final ExtractorsFactory FACTORY = () -> {
        return new Extractor[]{new MatroskaExtractor()};
    };
    public static final int FLAG_DISABLE_SEEK_FOR_CUES = 1;
    private static final String TAG = "MatroskaExtractor";
    private static final int UNSET_ENTRY_ID = -1;
    private static final int BLOCK_STATE_START = 0;
    private static final int BLOCK_STATE_HEADER = 1;
    private static final int BLOCK_STATE_DATA = 2;
    private static final String DOC_TYPE_MATROSKA = "matroska";
    private static final String DOC_TYPE_WEBM = "webm";
    private static final String CODEC_ID_VP8 = "V_VP8";
    private static final String CODEC_ID_VP9 = "V_VP9";
    private static final String CODEC_ID_MPEG2 = "V_MPEG2";
    private static final String CODEC_ID_MPEG4_SP = "V_MPEG4/ISO/SP";
    private static final String CODEC_ID_MPEG4_ASP = "V_MPEG4/ISO/ASP";
    private static final String CODEC_ID_MPEG4_AP = "V_MPEG4/ISO/AP";
    private static final String CODEC_ID_H264 = "V_MPEG4/ISO/AVC";
    private static final String CODEC_ID_H265 = "V_MPEGH/ISO/HEVC";
    private static final String CODEC_ID_FOURCC = "V_MS/VFW/FOURCC";
    private static final String CODEC_ID_THEORA = "V_THEORA";
    private static final String CODEC_ID_VORBIS = "A_VORBIS";
    private static final String CODEC_ID_OPUS = "A_OPUS";
    private static final String CODEC_ID_AAC = "A_AAC";
    private static final String CODEC_ID_MP2 = "A_MPEG/L2";
    private static final String CODEC_ID_MP3 = "A_MPEG/L3";
    private static final String CODEC_ID_AC3 = "A_AC3";
    private static final String CODEC_ID_E_AC3 = "A_EAC3";
    private static final String CODEC_ID_TRUEHD = "A_TRUEHD";
    private static final String CODEC_ID_DTS = "A_DTS";
    private static final String CODEC_ID_DTS_EXPRESS = "A_DTS/EXPRESS";
    private static final String CODEC_ID_DTS_LOSSLESS = "A_DTS/LOSSLESS";
    private static final String CODEC_ID_FLAC = "A_FLAC";
    private static final String CODEC_ID_ACM = "A_MS/ACM";
    private static final String CODEC_ID_PCM_INT_LIT = "A_PCM/INT/LIT";
    private static final String CODEC_ID_SUBRIP = "S_TEXT/UTF8";
    private static final String CODEC_ID_ASS = "S_TEXT/ASS";
    private static final String CODEC_ID_VOBSUB = "S_VOBSUB";
    private static final String CODEC_ID_PGS = "S_HDMV/PGS";
    private static final String CODEC_ID_DVBSUB = "S_DVBSUB";
    private static final int VORBIS_MAX_INPUT_SIZE = 8192;
    private static final int OPUS_MAX_INPUT_SIZE = 5760;
    private static final int ENCRYPTION_IV_SIZE = 8;
    private static final int TRACK_TYPE_AUDIO = 2;
    private static final int ID_EBML = 440786851;
    private static final int ID_EBML_READ_VERSION = 17143;
    private static final int ID_DOC_TYPE = 17026;
    private static final int ID_DOC_TYPE_READ_VERSION = 17029;
    private static final int ID_SEGMENT = 408125543;
    private static final int ID_SEGMENT_INFO = 357149030;
    private static final int ID_SEEK_HEAD = 290298740;
    private static final int ID_SEEK = 19899;
    private static final int ID_SEEK_ID = 21419;
    private static final int ID_SEEK_POSITION = 21420;
    private static final int ID_INFO = 357149030;
    private static final int ID_TIMECODE_SCALE = 2807729;
    private static final int ID_DURATION = 17545;
    private static final int ID_CLUSTER = 524531317;
    private static final int ID_TIME_CODE = 231;
    private static final int ID_SIMPLE_BLOCK = 163;
    private static final int ID_BLOCK_GROUP = 160;
    private static final int ID_BLOCK = 161;
    private static final int ID_BLOCK_DURATION = 155;
    private static final int ID_REFERENCE_BLOCK = 251;
    private static final int ID_TRACKS = 374648427;
    private static final int ID_TRACK_ENTRY = 174;
    private static final int ID_TRACK_NUMBER = 215;
    private static final int ID_TRACK_TYPE = 131;
    private static final int ID_FLAG_DEFAULT = 136;
    private static final int ID_FLAG_FORCED = 21930;
    private static final int ID_DEFAULT_DURATION = 2352003;
    private static final int ID_NAME = 21358;
    private static final int ID_CODEC_ID = 134;
    private static final int ID_CODEC_PRIVATE = 25506;
    private static final int ID_CODEC_DELAY = 22186;
    private static final int ID_SEEK_PRE_ROLL = 22203;
    private static final int ID_VIDEO = 224;
    private static final int ID_PIXEL_WIDTH = 176;
    private static final int ID_PIXEL_HEIGHT = 186;
    private static final int ID_DISPLAY_WIDTH = 21680;
    private static final int ID_DISPLAY_HEIGHT = 21690;
    private static final int ID_DISPLAY_UNIT = 21682;
    private static final int ID_AUDIO = 225;
    private static final int ID_CHANNELS = 159;
    private static final int ID_AUDIO_BIT_DEPTH = 25188;
    private static final int ID_SAMPLING_FREQUENCY = 181;
    private static final int ID_CONTENT_ENCODINGS = 28032;
    private static final int ID_CONTENT_ENCODING = 25152;
    private static final int ID_CONTENT_ENCODING_ORDER = 20529;
    private static final int ID_CONTENT_ENCODING_SCOPE = 20530;
    private static final int ID_CONTENT_COMPRESSION = 20532;
    private static final int ID_CONTENT_COMPRESSION_ALGORITHM = 16980;
    private static final int ID_CONTENT_COMPRESSION_SETTINGS = 16981;
    private static final int ID_CONTENT_ENCRYPTION = 20533;
    private static final int ID_CONTENT_ENCRYPTION_ALGORITHM = 18401;
    private static final int ID_CONTENT_ENCRYPTION_KEY_ID = 18402;
    private static final int ID_CONTENT_ENCRYPTION_AES_SETTINGS = 18407;
    private static final int ID_CONTENT_ENCRYPTION_AES_SETTINGS_CIPHER_MODE = 18408;
    private static final int ID_CUES = 475249515;
    private static final int ID_CUE_POINT = 187;
    private static final int ID_CUE_TIME = 179;
    private static final int ID_CUE_TRACK_POSITIONS = 183;
    private static final int ID_CUE_CLUSTER_POSITION = 241;
    private static final int ID_LANGUAGE = 2274716;
    private static final int ID_PROJECTION = 30320;
    private static final int ID_PROJECTION_PRIVATE = 30322;
    private static final int ID_STEREO_MODE = 21432;
    private static final int ID_COLOUR = 21936;
    private static final int ID_COLOUR_RANGE = 21945;
    private static final int ID_COLOUR_TRANSFER = 21946;
    private static final int ID_COLOUR_PRIMARIES = 21947;
    private static final int ID_MAX_CLL = 21948;
    private static final int ID_MAX_FALL = 21949;
    private static final int ID_MASTERING_METADATA = 21968;
    private static final int ID_PRIMARY_R_CHROMATICITY_X = 21969;
    private static final int ID_PRIMARY_R_CHROMATICITY_Y = 21970;
    private static final int ID_PRIMARY_G_CHROMATICITY_X = 21971;
    private static final int ID_PRIMARY_G_CHROMATICITY_Y = 21972;
    private static final int ID_PRIMARY_B_CHROMATICITY_X = 21973;
    private static final int ID_PRIMARY_B_CHROMATICITY_Y = 21974;
    private static final int ID_WHITE_POINT_CHROMATICITY_X = 21975;
    private static final int ID_WHITE_POINT_CHROMATICITY_Y = 21976;
    private static final int ID_LUMNINANCE_MAX = 21977;
    private static final int ID_LUMNINANCE_MIN = 21978;
    private static final int LACING_NONE = 0;
    private static final int LACING_XIPH = 1;
    private static final int LACING_FIXED_SIZE = 2;
    private static final int LACING_EBML = 3;
    private static final int FOURCC_COMPRESSION_VC1 = 826496599;
    private static final int FOURCC_COMPRESSION_DIVX = 1482049860;
    private static final byte[] SUBRIP_PREFIX = new byte[]{49, 10, 48, 48, 58, 48, 48, 58, 48, 48, 44, 48, 48, 48, 32, 45, 45, 62, 32, 48, 48, 58, 48, 48, 58, 48, 48, 44, 48, 48, 48, 10};
    private static final int SUBRIP_PREFIX_END_TIMECODE_OFFSET = 19;
    private static final byte[] SUBRIP_TIMECODE_EMPTY = new byte[]{32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32};
    private static final long SUBRIP_TIMECODE_LAST_VALUE_SCALING_FACTOR = 1000L;
    private static final String SUBRIP_TIMECODE_FORMAT = "%02d:%02d:%02d,%03d";
    private static final byte[] SSA_DIALOGUE_FORMAT = Util.getUtf8Bytes("Format: Start, End, ReadOrder, Layer, Style, Name, MarginL, MarginR, MarginV, Effect, Text");
    private static final byte[] SSA_PREFIX = new byte[]{68, 105, 97, 108, 111, 103, 117, 101, 58, 32, 48, 58, 48, 48, 58, 48, 48, 58, 48, 48, 44, 48, 58, 48, 48, 58, 48, 48, 58, 48, 48, 44};
    private static final int SSA_PREFIX_END_TIMECODE_OFFSET = 21;
    private static final long SSA_TIMECODE_LAST_VALUE_SCALING_FACTOR = 10000L;
    private static final byte[] SSA_TIMECODE_EMPTY = new byte[]{32, 32, 32, 32, 32, 32, 32, 32, 32, 32};
    private static final String SSA_TIMECODE_FORMAT = "%01d:%02d:%02d:%02d";
    private static final int WAVE_FORMAT_SIZE = 18;
    private static final int WAVE_FORMAT_EXTENSIBLE = 65534;
    private static final int WAVE_FORMAT_PCM = 1;
    private static final UUID WAVE_SUBFORMAT_PCM = new UUID(72057594037932032L, -9223371306706625679L);
    private final EbmlReader reader;
    private final VarintReader varintReader;
    private final SparseArray<Track> tracks;
    private final boolean seekForCuesEnabled;
    private final ParsableByteArray nalStartCode;
    private final ParsableByteArray nalLength;
    private final ParsableByteArray scratch;
    private final ParsableByteArray vorbisNumPageSamples;
    private final ParsableByteArray seekEntryIdBytes;
    private final ParsableByteArray sampleStrippedBytes;
    private final ParsableByteArray subtitleSample;
    private final ParsableByteArray encryptionInitializationVector;
    private final ParsableByteArray encryptionSubsampleData;
    private ByteBuffer encryptionSubsampleDataBuffer;
    private long segmentContentSize;
    private long segmentContentPosition;
    private long timecodeScale;
    private long durationTimecode;
    private long durationUs;
    private Track currentTrack;
    private boolean sentSeekMap;
    private int seekEntryId;
    private long seekEntryPosition;
    private boolean seekForCues;
    private long cuesContentPosition;
    private long seekPositionAfterBuildingCues;
    private long clusterTimecodeUs;
    private LongArray cueTimesUs;
    private LongArray cueClusterPositions;
    private boolean seenClusterPositionForCurrentCuePoint;
    private int blockState;
    private long blockTimeUs;
    private long blockDurationUs;
    private int blockLacingSampleIndex;
    private int blockLacingSampleCount;
    private int[] blockLacingSampleSizes;
    private int blockTrackNumber;
    private int blockTrackNumberLength;
    private int blockFlags;
    private int sampleBytesRead;
    private boolean sampleEncodingHandled;
    private boolean sampleSignalByteRead;
    private boolean sampleInitializationVectorRead;
    private boolean samplePartitionCountRead;
    private byte sampleSignalByte;
    private int samplePartitionCount;
    private int sampleCurrentNalBytesRemaining;
    private int sampleBytesWritten;
    private boolean sampleRead;
    private boolean sampleSeenReferenceBlock;
    private ExtractorOutput extractorOutput;

    public MatroskaExtractor() {
        this(0);
    }

    public MatroskaExtractor(int flags) {
        this(new DefaultEbmlReader(), flags);
    }

    MatroskaExtractor(EbmlReader reader, int flags) {
        this.segmentContentPosition = -1L;
        this.timecodeScale = -Long.MAX_VALUE;
        this.durationTimecode = -Long.MAX_VALUE;
        this.durationUs = -Long.MAX_VALUE;
        this.cuesContentPosition = -1L;
        this.seekPositionAfterBuildingCues = -1L;
        this.clusterTimecodeUs = -Long.MAX_VALUE;
        this.reader = reader;
        this.reader.init(new InnerEbmlReaderOutput());
        this.seekForCuesEnabled = (flags & 1) == 0;
        this.varintReader = new VarintReader();
        this.tracks = new SparseArray();
        this.scratch = new ParsableByteArray(4);
        this.vorbisNumPageSamples = new ParsableByteArray(ByteBuffer.allocate(4).putInt(-1).array());
        this.seekEntryIdBytes = new ParsableByteArray(4);
        this.nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
        this.nalLength = new ParsableByteArray(4);
        this.sampleStrippedBytes = new ParsableByteArray();
        this.subtitleSample = new ParsableByteArray();
        this.encryptionInitializationVector = new ParsableByteArray(8);
        this.encryptionSubsampleData = new ParsableByteArray();
    }

    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        return (new Sniffer()).sniff(input);
    }

    public void init(ExtractorOutput output) {
        this.extractorOutput = output;
    }

    public void seek(long position, long timeUs) {
        this.clusterTimecodeUs = -Long.MAX_VALUE;
        this.blockState = 0;
        this.reader.reset();
        this.varintReader.reset();
        this.resetSample();

        for (int i = 0; i < this.tracks.size(); ++i) {
            this.tracks.valueAt(i).reset();
        }

    }

    public void release() {
    }

    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        this.sampleRead = false;
        boolean continueReading = true;

        while (continueReading && !this.sampleRead) {
            continueReading = this.reader.read(input);
            if (continueReading && this.maybeSeekForCues(seekPosition, input.getPosition())) {
                return 1;
            }
        }

        if (continueReading) {
            return 0;
        } else {
            for (int i = 0; i < this.tracks.size(); ++i) {
                this.tracks.valueAt(i).outputPendingSampleMetadata();
            }

            return -1;
        }
    }

    void startMasterElement(int id, long contentPosition, long contentSize) throws ParserException {
        switch (id) {
            case 160:
                this.sampleSeenReferenceBlock = false;
                break;
            case 174:
                this.currentTrack = new Track();
                break;
            case 187:
                this.seenClusterPositionForCurrentCuePoint = false;
                break;
            case 19899:
                this.seekEntryId = -1;
                this.seekEntryPosition = -1L;
                break;
            case 20533:
                this.currentTrack.hasContentEncryption = true;
                break;
            case 21968:
                this.currentTrack.hasColorInfo = true;
            case 25152:
            default:
                break;
            case 408125543:
                if (this.segmentContentPosition != -1L && this.segmentContentPosition != contentPosition) {
                    throw new ParserException("Multiple Segment elements not supported");
                }

                this.segmentContentPosition = contentPosition;
                this.segmentContentSize = contentSize;
                break;
            case 475249515:
                this.cueTimesUs = new LongArray();
                this.cueClusterPositions = new LongArray();
                break;
            case 524531317:
                if (!this.sentSeekMap) {
                    if (this.seekForCuesEnabled && this.cuesContentPosition != -1L) {
                        this.seekForCues = true;
                    } else {
                        this.extractorOutput.seekMap(new Unseekable(this.durationUs));
                        this.sentSeekMap = true;
                    }
                }
        }

    }

    void endMasterElement(int id) throws ParserException {
        switch (id) {
            case 160:
                if (this.blockState != 2) {
                    return;
                }

                if (!this.sampleSeenReferenceBlock) {
                    this.blockFlags |= 1;
                }

                this.commitSampleToOutput(this.tracks.get(this.blockTrackNumber), this.blockTimeUs);
                this.blockState = 0;
                break;
            case 174:
                if (isCodecSupported(this.currentTrack.codecId)) {
                    this.currentTrack.initializeOutput(this.extractorOutput, this.currentTrack.number);
                    this.tracks.put(this.currentTrack.number, this.currentTrack);
                }

                this.currentTrack = null;
                break;
            case 19899:
                if (this.seekEntryId == -1 || this.seekEntryPosition == -1L) {
                    throw new ParserException("Mandatory element SeekID or SeekPosition not found");
                }

                if (this.seekEntryId == 475249515) {
                    this.cuesContentPosition = this.seekEntryPosition;
                }
                break;
            case 25152:
                if (this.currentTrack.hasContentEncryption) {
                    if (this.currentTrack.cryptoData == null) {
                        throw new ParserException("Encrypted Track found but ContentEncKeyID was not found");
                    }

                    this.currentTrack.drmInitData = new DrmInitData(new SchemeData(C.UUID_NIL, "video/webm", this.currentTrack.cryptoData.encryptionKey));
                }
                break;
            case 28032:
                if (this.currentTrack.hasContentEncryption && this.currentTrack.sampleStrippedBytes != null) {
                    throw new ParserException("Combining encryption and compression is not supported");
                }
                break;
            case 357149030:
                if (this.timecodeScale == -Long.MAX_VALUE) {
                    this.timecodeScale = 1000000L;
                }

                if (this.durationTimecode != -Long.MAX_VALUE) {
                    this.durationUs = this.scaleTimecodeToUs(this.durationTimecode);
                }
                break;
            case 374648427:
                if (this.tracks.size() == 0) {
                    throw new ParserException("No valid tracks were found");
                }

                this.extractorOutput.endTracks();
                break;
            case 475249515:
                if (!this.sentSeekMap) {
                    this.extractorOutput.seekMap(this.buildSeekMap());
                    this.sentSeekMap = true;
                }
        }

    }

    void integerElement(int id, long value) throws ParserException {
        switch (id) {
            case 131:
                this.currentTrack.type = (int) value;
                break;
            case 136:
                this.currentTrack.flagDefault = value == 1L;
                break;
            case 155:
                this.blockDurationUs = this.scaleTimecodeToUs(value);
                break;
            case 159:
                this.currentTrack.channelCount = (int) value;
                break;
            case 176:
                this.currentTrack.width = (int) value;
                break;
            case 179:
                this.cueTimesUs.add(this.scaleTimecodeToUs(value));
                break;
            case 186:
                this.currentTrack.height = (int) value;
                break;
            case 215:
                this.currentTrack.number = (int) value;
                break;
            case 231:
                this.clusterTimecodeUs = this.scaleTimecodeToUs(value);
                break;
            case 241:
                if (!this.seenClusterPositionForCurrentCuePoint) {
                    this.cueClusterPositions.add(value);
                    this.seenClusterPositionForCurrentCuePoint = true;
                }
                break;
            case 251:
                this.sampleSeenReferenceBlock = true;
                break;
            case 16980:
                if (value != 3L) {
                    throw new ParserException("ContentCompAlgo " + value + " not supported");
                }
                break;
            case 17029:
                if (value >= 1L && value <= 2L) {
                    break;
                }

                throw new ParserException("DocTypeReadVersion " + value + " not supported");
            case 17143:
                if (value != 1L) {
                    throw new ParserException("EBMLReadVersion " + value + " not supported");
                }
                break;
            case 18401:
                if (value != 5L) {
                    throw new ParserException("ContentEncAlgo " + value + " not supported");
                }
                break;
            case 18408:
                if (value != 1L) {
                    throw new ParserException("AESSettingsCipherMode " + value + " not supported");
                }
                break;
            case 20529:
                if (value != 0L) {
                    throw new ParserException("ContentEncodingOrder " + value + " not supported");
                }
                break;
            case 20530:
                if (value != 1L) {
                    throw new ParserException("ContentEncodingScope " + value + " not supported");
                }
                break;
            case 21420:
                this.seekEntryPosition = value + this.segmentContentPosition;
                break;
            case 21432:
                int layout = (int) value;
                switch (layout) {
                    case 0:
                        this.currentTrack.stereoMode = 0;
                        return;
                    case 1:
                        this.currentTrack.stereoMode = 2;
                        return;
                    case 3:
                        this.currentTrack.stereoMode = 1;
                        return;
                    case 15:
                        this.currentTrack.stereoMode = 3;
                        return;
                    default:
                        return;
                }
            case 21680:
                this.currentTrack.displayWidth = (int) value;
                break;
            case 21682:
                this.currentTrack.displayUnit = (int) value;
                break;
            case 21690:
                this.currentTrack.displayHeight = (int) value;
                break;
            case 21930:
                this.currentTrack.flagForced = value == 1L;
                break;
            case 21945:
                switch ((int) value) {
                    case 1:
                        this.currentTrack.colorRange = 2;
                        return;
                    case 2:
                        this.currentTrack.colorRange = 1;
                        return;
                    default:
                        return;
                }
            case 21946:
                switch ((int) value) {
                    case 1:
                    case 6:
                    case 7:
                        this.currentTrack.colorTransfer = 3;
                        return;
                    case 16:
                        this.currentTrack.colorTransfer = 6;
                        return;
                    case 18:
                        this.currentTrack.colorTransfer = 7;
                        return;
                    default:
                        return;
                }
            case 21947:
                this.currentTrack.hasColorInfo = true;
                switch ((int) value) {
                    case 1:
                        this.currentTrack.colorSpace = 1;
                        return;
                    case 2:
                    case 3:
                    case 8:
                    default:
                        return;
                    case 4:
                    case 5:
                    case 6:
                    case 7:
                        this.currentTrack.colorSpace = 2;
                        return;
                    case 9:
                        this.currentTrack.colorSpace = 6;
                        return;
                }
            case 21948:
                this.currentTrack.maxContentLuminance = (int) value;
                break;
            case 21949:
                this.currentTrack.maxFrameAverageLuminance = (int) value;
                break;
            case 22186:
                this.currentTrack.codecDelayNs = value;
                break;
            case 22203:
                this.currentTrack.seekPreRollNs = value;
                break;
            case 25188:
                this.currentTrack.audioBitDepth = (int) value;
                break;
            case 2352003:
                this.currentTrack.defaultSampleDurationNs = (int) value;
                break;
            case 2807729:
                this.timecodeScale = value;
        }

    }

    void floatElement(int id, double value) {
        switch (id) {
            case 181:
                this.currentTrack.sampleRate = (int) value;
                break;
            case 17545:
                this.durationTimecode = (long) value;
                break;
            case 21969:
                this.currentTrack.primaryRChromaticityX = (float) value;
                break;
            case 21970:
                this.currentTrack.primaryRChromaticityY = (float) value;
                break;
            case 21971:
                this.currentTrack.primaryGChromaticityX = (float) value;
                break;
            case 21972:
                this.currentTrack.primaryGChromaticityY = (float) value;
                break;
            case 21973:
                this.currentTrack.primaryBChromaticityX = (float) value;
                break;
            case 21974:
                this.currentTrack.primaryBChromaticityY = (float) value;
                break;
            case 21975:
                this.currentTrack.whitePointChromaticityX = (float) value;
                break;
            case 21976:
                this.currentTrack.whitePointChromaticityY = (float) value;
                break;
            case 21977:
                this.currentTrack.maxMasteringLuminance = (float) value;
                break;
            case 21978:
                this.currentTrack.minMasteringLuminance = (float) value;
        }

    }

    void stringElement(int id, String value) throws ParserException {
        switch (id) {
            case 134:
                this.currentTrack.codecId = value;
                break;
            case 17026:
                if (!"webm".equals(value) && !"matroska".equals(value)) {
                    throw new ParserException("DocType " + value + " not supported");
                }
                break;
            case 21358:
                this.currentTrack.name = value;
                break;
            case 2274716:
                this.currentTrack.language = value;
        }

    }

    void binaryElement(int id, int contentSize, ExtractorInput input) throws IOException, InterruptedException {
        switch (id) {
            case 161:
            case 163:
                if (this.blockState == 0) {
                    this.blockTrackNumber = (int) this.varintReader.readUnsignedVarint(input, false, true, 8);
                    this.blockTrackNumberLength = this.varintReader.getLastLength();
                    this.blockDurationUs = -Long.MAX_VALUE;
                    this.blockState = 1;
                    this.scratch.reset();
                }

                Track track = this.tracks.get(this.blockTrackNumber);
                if (track == null) {
                    input.skipFully(contentSize - this.blockTrackNumberLength);
                    this.blockState = 0;
                    return;
                }

                if (this.blockState == 1) {
                    this.readScratch(input, 3);
                    int lacing = (this.scratch.data[2] & 6) >> 1;
                    int totalSamplesSize;
                    if (lacing == 0) {
                        this.blockLacingSampleCount = 1;
                        this.blockLacingSampleSizes = ensureArrayCapacity(this.blockLacingSampleSizes, 1);
                        this.blockLacingSampleSizes[0] = contentSize - this.blockTrackNumberLength - 3;
                    } else {
                        if (id != 163) {
                            throw new ParserException("Lacing only supported in SimpleBlocks.");
                        }

                        this.readScratch(input, 4);
                        this.blockLacingSampleCount = (this.scratch.data[3] & 255) + 1;
                        this.blockLacingSampleSizes = ensureArrayCapacity(this.blockLacingSampleSizes, this.blockLacingSampleCount);
                        if (lacing == 2) {
                            totalSamplesSize = (contentSize - this.blockTrackNumberLength - 4) / this.blockLacingSampleCount;
                            Arrays.fill(this.blockLacingSampleSizes, 0, this.blockLacingSampleCount, totalSamplesSize);
                        } else {
                            int headerSize;
                            int sampleIndex;
                            if (lacing == 1) {
                                totalSamplesSize = 0;
                                headerSize = 4;
                                sampleIndex = 0;

                                while (true) {
                                    if (sampleIndex >= this.blockLacingSampleCount - 1) {
                                        this.blockLacingSampleSizes[this.blockLacingSampleCount - 1] = contentSize - this.blockTrackNumberLength - headerSize - totalSamplesSize;
                                        break;
                                    }

                                    this.blockLacingSampleSizes[sampleIndex] = 0;

                                    int byteValue;
                                    do {
                                        ++headerSize;
                                        this.readScratch(input, headerSize);
                                        byteValue = this.scratch.data[headerSize - 1] & 255;
                                        int[] var10000 = this.blockLacingSampleSizes;
                                        var10000[sampleIndex] += byteValue;
                                    } while (byteValue == 255);

                                    totalSamplesSize += this.blockLacingSampleSizes[sampleIndex];
                                    ++sampleIndex;
                                }
                            } else {
                                if (lacing != 3) {
                                    throw new ParserException("Unexpected lacing value: " + lacing);
                                }

                                totalSamplesSize = 0;
                                headerSize = 4;
                                sampleIndex = 0;

                                while (true) {
                                    if (sampleIndex >= this.blockLacingSampleCount - 1) {
                                        this.blockLacingSampleSizes[this.blockLacingSampleCount - 1] = contentSize - this.blockTrackNumberLength - headerSize - totalSamplesSize;
                                        break;
                                    }

                                    this.blockLacingSampleSizes[sampleIndex] = 0;
                                    ++headerSize;
                                    this.readScratch(input, headerSize);
                                    if (this.scratch.data[headerSize - 1] == 0) {
                                        throw new ParserException("No valid varint length mask found");
                                    }

                                    long readValue = 0L;

                                    int i;
                                    for (i = 0; i < 8; ++i) {
                                        int lengthMask = 1 << 7 - i;
                                        if ((this.scratch.data[headerSize - 1] & lengthMask) != 0) {
                                            int readPosition = headerSize - 1;
                                            headerSize += i;
                                            this.readScratch(input, headerSize);

                                            for (readValue = this.scratch.data[readPosition++] & 255 & ~lengthMask; readPosition < headerSize; readValue |= this.scratch.data[readPosition++] & 255) {
                                                readValue <<= 8;
                                            }

                                            if (sampleIndex > 0) {
                                                readValue -= (1L << 6 + i * 7) - 1L;
                                            }
                                            break;
                                        }
                                    }

                                    if (readValue < -2147483648L || readValue > 2147483647L) {
                                        throw new ParserException("EBML lacing sample size out of range.");
                                    }

                                    i = (int) readValue;
                                    this.blockLacingSampleSizes[sampleIndex] = sampleIndex == 0 ? i : this.blockLacingSampleSizes[sampleIndex - 1] + i;
                                    totalSamplesSize += this.blockLacingSampleSizes[sampleIndex];
                                    ++sampleIndex;
                                }
                            }
                        }
                    }

                    totalSamplesSize = this.scratch.data[0] << 8 | this.scratch.data[1] & 255;
                    this.blockTimeUs = this.clusterTimecodeUs + this.scaleTimecodeToUs(totalSamplesSize);
                    boolean isInvisible = (this.scratch.data[2] & 8) == 8;
                    boolean isKeyframe = track.type == 2 || id == 163 && (this.scratch.data[2] & 128) == 128;
                    this.blockFlags = (isKeyframe ? 1 : 0) | (isInvisible ? -2147483648 : 0);
                    this.blockState = 2;
                    this.blockLacingSampleIndex = 0;
                }

                if (id != 163) {
                    this.writeSampleData(input, track, this.blockLacingSampleSizes[0]);
                    break;
                }

                while (this.blockLacingSampleIndex < this.blockLacingSampleCount) {
                    this.writeSampleData(input, track, this.blockLacingSampleSizes[this.blockLacingSampleIndex]);
                    long sampleTimeUs = this.blockTimeUs + (long) (this.blockLacingSampleIndex * track.defaultSampleDurationNs / 1000);
                    this.commitSampleToOutput(track, sampleTimeUs);
                    ++this.blockLacingSampleIndex;
                }

                this.blockState = 0;
                break;
            case 16981:
                this.currentTrack.sampleStrippedBytes = new byte[contentSize];
                input.readFully(this.currentTrack.sampleStrippedBytes, 0, contentSize);
                break;
            case 18402:
                byte[] encryptionKey = new byte[contentSize];
                input.readFully(encryptionKey, 0, contentSize);
                this.currentTrack.cryptoData = new CryptoData(1, encryptionKey, 0, 0);
                break;
            case 21419:
                Arrays.fill(this.seekEntryIdBytes.data, (byte) 0);
                input.readFully(this.seekEntryIdBytes.data, 4 - contentSize, contentSize);
                this.seekEntryIdBytes.setPosition(0);
                this.seekEntryId = (int) this.seekEntryIdBytes.readUnsignedInt();
                break;
            case 25506:
                this.currentTrack.codecPrivate = new byte[contentSize];
                input.readFully(this.currentTrack.codecPrivate, 0, contentSize);
                break;
            case 30322:
                this.currentTrack.projectionData = new byte[contentSize];
                input.readFully(this.currentTrack.projectionData, 0, contentSize);
                break;
            default:
                throw new ParserException("Unexpected id: " + id);
        }

    }

    private void commitSampleToOutput(Track track, long timeUs) {
        if (track.trueHdSampleRechunker != null) {
            track.trueHdSampleRechunker.sampleMetadata(track, timeUs);
        } else {
            if ("S_TEXT/UTF8".equals(track.codecId)) {
                this.commitSubtitleSample(track, "%02d:%02d:%02d,%03d", 19, 1000L, SUBRIP_TIMECODE_EMPTY);
            } else if ("S_TEXT/ASS".equals(track.codecId)) {
                this.commitSubtitleSample(track, "%01d:%02d:%02d:%02d", 21, 10000L, SSA_TIMECODE_EMPTY);
            }

            track.output.sampleMetadata(timeUs, this.blockFlags, this.sampleBytesWritten, 0, track.cryptoData);
        }

        this.sampleRead = true;
        this.resetSample();
    }

    private void resetSample() {
        this.sampleBytesRead = 0;
        this.sampleBytesWritten = 0;
        this.sampleCurrentNalBytesRemaining = 0;
        this.sampleEncodingHandled = false;
        this.sampleSignalByteRead = false;
        this.samplePartitionCountRead = false;
        this.samplePartitionCount = 0;
        this.sampleSignalByte = 0;
        this.sampleInitializationVectorRead = false;
        this.sampleStrippedBytes.reset();
    }

    private void readScratch(ExtractorInput input, int requiredLength) throws IOException, InterruptedException {
        if (this.scratch.limit() < requiredLength) {
            if (this.scratch.capacity() < requiredLength) {
                this.scratch.reset(Arrays.copyOf(this.scratch.data, Math.max(this.scratch.data.length * 2, requiredLength)), this.scratch.limit());
            }

            input.readFully(this.scratch.data, this.scratch.limit(), requiredLength - this.scratch.limit());
            this.scratch.setLimit(requiredLength);
        }
    }

    private void writeSampleData(ExtractorInput input, Track track, int size) throws IOException, InterruptedException {
        if ("S_TEXT/UTF8".equals(track.codecId)) {
            this.writeSubtitleSampleData(input, SUBRIP_PREFIX, size);
        } else if ("S_TEXT/ASS".equals(track.codecId)) {
            this.writeSubtitleSampleData(input, SSA_PREFIX, size);
        } else {
            TrackOutput output = track.output;
            int samplePartitionDataSize;
            if (!this.sampleEncodingHandled) {
                if (!track.hasContentEncryption) {
                    if (track.sampleStrippedBytes != null) {
                        this.sampleStrippedBytes.reset(track.sampleStrippedBytes, track.sampleStrippedBytes.length);
                    }
                } else {
                    this.blockFlags &= -1073741825;
                    if (!this.sampleSignalByteRead) {
                        input.readFully(this.scratch.data, 0, 1);
                        ++this.sampleBytesRead;
                        if ((this.scratch.data[0] & 128) == 128) {
                            throw new ParserException("Extension bit is set in signal byte");
                        }

                        this.sampleSignalByte = this.scratch.data[0];
                        this.sampleSignalByteRead = true;
                    }

                    boolean isEncrypted = (this.sampleSignalByte & 1) == 1;
                    if (isEncrypted) {
                        boolean hasSubsampleEncryption = (this.sampleSignalByte & 2) == 2;
                        this.blockFlags |= 1073741824;
                        if (!this.sampleInitializationVectorRead) {
                            input.readFully(this.encryptionInitializationVector.data, 0, 8);
                            this.sampleBytesRead += 8;
                            this.sampleInitializationVectorRead = true;
                            this.scratch.data[0] = (byte) (8 | (hasSubsampleEncryption ? 128 : 0));
                            this.scratch.setPosition(0);
                            output.sampleData(this.scratch, 1);
                            ++this.sampleBytesWritten;
                            this.encryptionInitializationVector.setPosition(0);
                            output.sampleData(this.encryptionInitializationVector, 8);
                            this.sampleBytesWritten += 8;
                        }

                        if (hasSubsampleEncryption) {
                            if (!this.samplePartitionCountRead) {
                                input.readFully(this.scratch.data, 0, 1);
                                ++this.sampleBytesRead;
                                this.scratch.setPosition(0);
                                this.samplePartitionCount = this.scratch.readUnsignedByte();
                                this.samplePartitionCountRead = true;
                            }

                            samplePartitionDataSize = this.samplePartitionCount * 4;
                            this.scratch.reset(samplePartitionDataSize);
                            input.readFully(this.scratch.data, 0, samplePartitionDataSize);
                            this.sampleBytesRead += samplePartitionDataSize;
                            short subsampleCount = (short) (1 + this.samplePartitionCount / 2);
                            int subsampleDataSize = 2 + 6 * subsampleCount;
                            if (this.encryptionSubsampleDataBuffer == null || this.encryptionSubsampleDataBuffer.capacity() < subsampleDataSize) {
                                this.encryptionSubsampleDataBuffer = ByteBuffer.allocate(subsampleDataSize);
                            }

                            this.encryptionSubsampleDataBuffer.position(0);
                            this.encryptionSubsampleDataBuffer.putShort(subsampleCount);
                            int partitionOffset = 0;

                            int finalPartitionSize;
                            for (finalPartitionSize = 0; finalPartitionSize < this.samplePartitionCount; ++finalPartitionSize) {
                                int previousPartitionOffset = partitionOffset;
                                partitionOffset = this.scratch.readUnsignedIntToInt();
                                if (finalPartitionSize % 2 == 0) {
                                    this.encryptionSubsampleDataBuffer.putShort((short) (partitionOffset - previousPartitionOffset));
                                } else {
                                    this.encryptionSubsampleDataBuffer.putInt(partitionOffset - previousPartitionOffset);
                                }
                            }

                            finalPartitionSize = size - this.sampleBytesRead - partitionOffset;
                            if (this.samplePartitionCount % 2 == 1) {
                                this.encryptionSubsampleDataBuffer.putInt(finalPartitionSize);
                            } else {
                                this.encryptionSubsampleDataBuffer.putShort((short) finalPartitionSize);
                                this.encryptionSubsampleDataBuffer.putInt(0);
                            }

                            this.encryptionSubsampleData.reset(this.encryptionSubsampleDataBuffer.array(), subsampleDataSize);
                            output.sampleData(this.encryptionSubsampleData, subsampleDataSize);
                            this.sampleBytesWritten += subsampleDataSize;
                        }
                    }
                }

                this.sampleEncodingHandled = true;
            }

            size += this.sampleStrippedBytes.limit();
            if (!"V_MPEG4/ISO/AVC".equals(track.codecId) && !"V_MPEGH/ISO/HEVC".equals(track.codecId)) {
                if (track.trueHdSampleRechunker != null) {
                    Assertions.checkState(this.sampleStrippedBytes.limit() == 0);
                    track.trueHdSampleRechunker.startSample(input, this.blockFlags, size);
                }

                while (this.sampleBytesRead < size) {
                    this.readToOutput(input, output, size - this.sampleBytesRead);
                }
            } else {
                byte[] nalLengthData = this.nalLength.data;
                nalLengthData[0] = 0;
                nalLengthData[1] = 0;
                nalLengthData[2] = 0;
                int nalUnitLengthFieldLength = track.nalUnitLengthFieldLength;
                samplePartitionDataSize = 4 - track.nalUnitLengthFieldLength;

                while (this.sampleBytesRead < size) {
                    if (this.sampleCurrentNalBytesRemaining == 0) {
                        this.readToTarget(input, nalLengthData, samplePartitionDataSize, nalUnitLengthFieldLength);
                        this.nalLength.setPosition(0);
                        this.sampleCurrentNalBytesRemaining = this.nalLength.readUnsignedIntToInt();
                        this.nalStartCode.setPosition(0);
                        output.sampleData(this.nalStartCode, 4);
                        this.sampleBytesWritten += 4;
                    } else {
                        this.sampleCurrentNalBytesRemaining -= this.readToOutput(input, output, this.sampleCurrentNalBytesRemaining);
                    }
                }
            }

            if ("A_VORBIS".equals(track.codecId)) {
                this.vorbisNumPageSamples.setPosition(0);
                output.sampleData(this.vorbisNumPageSamples, 4);
                this.sampleBytesWritten += 4;
            }

        }
    }

    private void writeSubtitleSampleData(ExtractorInput input, byte[] samplePrefix, int size) throws IOException, InterruptedException {
        int sizeWithPrefix = samplePrefix.length + size;
        if (this.subtitleSample.capacity() < sizeWithPrefix) {
            this.subtitleSample.data = Arrays.copyOf(samplePrefix, sizeWithPrefix + size);
        } else {
            System.arraycopy(samplePrefix, 0, this.subtitleSample.data, 0, samplePrefix.length);
        }

        input.readFully(this.subtitleSample.data, samplePrefix.length, size);
        this.subtitleSample.reset(sizeWithPrefix);
    }

    private void commitSubtitleSample(Track track, String timecodeFormat, int endTimecodeOffset, long lastTimecodeValueScalingFactor, byte[] emptyTimecode) {
        setSampleDuration(this.subtitleSample.data, this.blockDurationUs, timecodeFormat, endTimecodeOffset, lastTimecodeValueScalingFactor, emptyTimecode);
        track.output.sampleData(this.subtitleSample, this.subtitleSample.limit());
        this.sampleBytesWritten += this.subtitleSample.limit();
    }

    private static void setSampleDuration(byte[] subripSampleData, long durationUs, String timecodeFormat, int endTimecodeOffset, long lastTimecodeValueScalingFactor, byte[] emptyTimecode) {
        byte[] timeCodeData;
        if (durationUs == -Long.MAX_VALUE) {
            timeCodeData = emptyTimecode;
        } else {
            int hours = (int) (durationUs / 3600000000L);
            durationUs -= (long) (hours * 3600) * 1000000L;
            int minutes = (int) (durationUs / 60000000L);
            durationUs -= (long) (minutes * 60) * 1000000L;
            int seconds = (int) (durationUs / 1000000L);
            durationUs -= (long) seconds * 1000000L;
            int lastValue = (int) (durationUs / lastTimecodeValueScalingFactor);
            timeCodeData = Util.getUtf8Bytes(String.format(Locale.US, timecodeFormat, hours, minutes, seconds, lastValue));
        }

        System.arraycopy(timeCodeData, 0, subripSampleData, endTimecodeOffset, emptyTimecode.length);
    }

    private void readToTarget(ExtractorInput input, byte[] target, int offset, int length) throws IOException, InterruptedException {
        int pendingStrippedBytes = Math.min(length, this.sampleStrippedBytes.bytesLeft());
        input.readFully(target, offset + pendingStrippedBytes, length - pendingStrippedBytes);
        if (pendingStrippedBytes > 0) {
            this.sampleStrippedBytes.readBytes(target, offset, pendingStrippedBytes);
        }

        this.sampleBytesRead += length;
    }

    private int readToOutput(ExtractorInput input, TrackOutput output, int length) throws IOException, InterruptedException {
        int strippedBytesLeft = this.sampleStrippedBytes.bytesLeft();
        int bytesRead;
        if (strippedBytesLeft > 0) {
            bytesRead = Math.min(length, strippedBytesLeft);
            output.sampleData(this.sampleStrippedBytes, bytesRead);
        } else {
            bytesRead = output.sampleData(input, length, false);
        }

        this.sampleBytesRead += bytesRead;
        this.sampleBytesWritten += bytesRead;
        return bytesRead;
    }

    private SeekMap buildSeekMap() {
        if (this.segmentContentPosition != -1L && this.durationUs != -Long.MAX_VALUE && this.cueTimesUs != null && this.cueTimesUs.size() != 0 && this.cueClusterPositions != null && this.cueClusterPositions.size() == this.cueTimesUs.size()) {
            int cuePointsSize = this.cueTimesUs.size();
            int[] sizes = new int[cuePointsSize];
            long[] offsets = new long[cuePointsSize];
            long[] durationsUs = new long[cuePointsSize];
            long[] timesUs = new long[cuePointsSize];

            int i;
            for (i = 0; i < cuePointsSize; ++i) {
                timesUs[i] = this.cueTimesUs.get(i);
                offsets[i] = this.segmentContentPosition + this.cueClusterPositions.get(i);
            }

            for (i = 0; i < cuePointsSize - 1; ++i) {
                sizes[i] = (int) (offsets[i + 1] - offsets[i]);
                durationsUs[i] = timesUs[i + 1] - timesUs[i];
            }

            sizes[cuePointsSize - 1] = (int) (this.segmentContentPosition + this.segmentContentSize - offsets[cuePointsSize - 1]);
            durationsUs[cuePointsSize - 1] = this.durationUs - timesUs[cuePointsSize - 1];
            this.cueTimesUs = null;
            this.cueClusterPositions = null;
            return new ChunkIndex(sizes, offsets, durationsUs, timesUs);
        } else {
            this.cueTimesUs = null;
            this.cueClusterPositions = null;
            return new Unseekable(this.durationUs);
        }
    }

    private boolean maybeSeekForCues(PositionHolder seekPosition, long currentPosition) {
        if (this.seekForCues) {
            this.seekPositionAfterBuildingCues = currentPosition;
            seekPosition.position = this.cuesContentPosition;
            this.seekForCues = false;
            return true;
        } else if (this.sentSeekMap && this.seekPositionAfterBuildingCues != -1L) {
            seekPosition.position = this.seekPositionAfterBuildingCues;
            this.seekPositionAfterBuildingCues = -1L;
            return true;
        } else {
            return false;
        }
    }

    private long scaleTimecodeToUs(long unscaledTimecode) throws ParserException {
        if (this.timecodeScale == -Long.MAX_VALUE) {
            throw new ParserException("Can't scale timecode prior to timecodeScale being set.");
        } else {
            return Util.scaleLargeTimestamp(unscaledTimecode, this.timecodeScale, 1000L);
        }
    }

    private static boolean isCodecSupported(String codecId) {
        return "V_VP8".equals(codecId) || "V_VP9".equals(codecId) || "V_MPEG2".equals(codecId) || "V_MPEG4/ISO/SP".equals(codecId) || "V_MPEG4/ISO/ASP".equals(codecId) || "V_MPEG4/ISO/AP".equals(codecId) || "V_MPEG4/ISO/AVC".equals(codecId) || "V_MPEGH/ISO/HEVC".equals(codecId) || "V_MS/VFW/FOURCC".equals(codecId) || "V_THEORA".equals(codecId) || "A_OPUS".equals(codecId) || "A_VORBIS".equals(codecId) || "A_AAC".equals(codecId) || "A_MPEG/L2".equals(codecId) || "A_MPEG/L3".equals(codecId) || "A_AC3".equals(codecId) || "A_EAC3".equals(codecId) || "A_TRUEHD".equals(codecId) || "A_DTS".equals(codecId) || "A_DTS/EXPRESS".equals(codecId) || "A_DTS/LOSSLESS".equals(codecId) || "A_FLAC".equals(codecId) || "A_MS/ACM".equals(codecId) || "A_PCM/INT/LIT".equals(codecId) || "S_TEXT/UTF8".equals(codecId) || "S_TEXT/ASS".equals(codecId) || "S_VOBSUB".equals(codecId) || "S_HDMV/PGS".equals(codecId) || "S_DVBSUB".equals(codecId);
    }

    private static int[] ensureArrayCapacity(int[] array, int length) {
        if (array == null) {
            return new int[length];
        } else {
            return array.length >= length ? array : new int[Math.max(array.length * 2, length)];
        }
    }

    private static final class Track {
        private static final int DISPLAY_UNIT_PIXELS = 0;
        private static final int MAX_CHROMATICITY = 50000;
        private static final int DEFAULT_MAX_CLL = 1000;
        private static final int DEFAULT_MAX_FALL = 200;
        public String name;
        public String codecId;
        public int number;
        public int type;
        public int defaultSampleDurationNs;
        public boolean hasContentEncryption;
        public byte[] sampleStrippedBytes;
        public CryptoData cryptoData;
        public byte[] codecPrivate;
        public DrmInitData drmInitData;
        public int width;
        public int height;
        public int displayWidth;
        public int displayHeight;
        public int displayUnit;
        public byte[] projectionData;
        public int stereoMode;
        public boolean hasColorInfo;
        public int colorSpace;
        public int colorTransfer;
        public int colorRange;
        public int maxContentLuminance;
        public int maxFrameAverageLuminance;
        public float primaryRChromaticityX;
        public float primaryRChromaticityY;
        public float primaryGChromaticityX;
        public float primaryGChromaticityY;
        public float primaryBChromaticityX;
        public float primaryBChromaticityY;
        public float whitePointChromaticityX;
        public float whitePointChromaticityY;
        public float maxMasteringLuminance;
        public float minMasteringLuminance;
        public int channelCount;
        public int audioBitDepth;
        public int sampleRate;
        public long codecDelayNs;
        public long seekPreRollNs;
        @Nullable
        public MatroskaExtractor.TrueHdSampleRechunker trueHdSampleRechunker;
        public boolean flagForced;
        public boolean flagDefault;
        private String language;
        public TrackOutput output;
        public int nalUnitLengthFieldLength;

        private Track() {
            this.width = -1;
            this.height = -1;
            this.displayWidth = -1;
            this.displayHeight = -1;
            this.displayUnit = 0;
            this.projectionData = null;
            this.stereoMode = -1;
            this.hasColorInfo = false;
            this.colorSpace = -1;
            this.colorTransfer = -1;
            this.colorRange = -1;
            this.maxContentLuminance = 1000;
            this.maxFrameAverageLuminance = 200;
            this.primaryRChromaticityX = -1.0F;
            this.primaryRChromaticityY = -1.0F;
            this.primaryGChromaticityX = -1.0F;
            this.primaryGChromaticityY = -1.0F;
            this.primaryBChromaticityX = -1.0F;
            this.primaryBChromaticityY = -1.0F;
            this.whitePointChromaticityX = -1.0F;
            this.whitePointChromaticityY = -1.0F;
            this.maxMasteringLuminance = -1.0F;
            this.minMasteringLuminance = -1.0F;
            this.channelCount = 1;
            this.audioBitDepth = -1;
            this.sampleRate = 8000;
            this.codecDelayNs = 0L;
            this.seekPreRollNs = 0L;
            this.flagDefault = true;
            this.language = "eng";
        }

        public void initializeOutput(ExtractorOutput output, int trackId) throws ParserException {
            int maxInputSize = -1;
            int pcmEncoding = -1;
            List<byte[]> initializationData = null;
            String var7 = this.codecId;
            byte var8 = -1;
            switch (var7.hashCode()) {
                case -2095576542:
                    if (var7.equals("V_MPEG4/ISO/AP")) {
                        var8 = 5;
                    }
                    break;
                case -2095575984:
                    if (var7.equals("V_MPEG4/ISO/SP")) {
                        var8 = 3;
                    }
                    break;
                case -1985379776:
                    if (var7.equals("A_MS/ACM")) {
                        var8 = 22;
                    }
                    break;
                case -1784763192:
                    if (var7.equals("A_TRUEHD")) {
                        var8 = 17;
                    }
                    break;
                case -1730367663:
                    if (var7.equals("A_VORBIS")) {
                        var8 = 10;
                    }
                    break;
                case -1482641358:
                    if (var7.equals("A_MPEG/L2")) {
                        var8 = 13;
                    }
                    break;
                case -1482641357:
                    if (var7.equals("A_MPEG/L3")) {
                        var8 = 14;
                    }
                    break;
                case -1373388978:
                    if (var7.equals("V_MS/VFW/FOURCC")) {
                        var8 = 8;
                    }
                    break;
                case -933872740:
                    if (var7.equals("S_DVBSUB")) {
                        var8 = 28;
                    }
                    break;
                case -538363189:
                    if (var7.equals("V_MPEG4/ISO/ASP")) {
                        var8 = 4;
                    }
                    break;
                case -538363109:
                    if (var7.equals("V_MPEG4/ISO/AVC")) {
                        var8 = 6;
                    }
                    break;
                case -425012669:
                    if (var7.equals("S_VOBSUB")) {
                        var8 = 26;
                    }
                    break;
                case -356037306:
                    if (var7.equals("A_DTS/LOSSLESS")) {
                        var8 = 20;
                    }
                    break;
                case 62923557:
                    if (var7.equals("A_AAC")) {
                        var8 = 12;
                    }
                    break;
                case 62923603:
                    if (var7.equals("A_AC3")) {
                        var8 = 15;
                    }
                    break;
                case 62927045:
                    if (var7.equals("A_DTS")) {
                        var8 = 18;
                    }
                    break;
                case 82338133:
                    if (var7.equals("V_VP8")) {
                        var8 = 0;
                    }
                    break;
                case 82338134:
                    if (var7.equals("V_VP9")) {
                        var8 = 1;
                    }
                    break;
                case 99146302:
                    if (var7.equals("S_HDMV/PGS")) {
                        var8 = 27;
                    }
                    break;
                case 444813526:
                    if (var7.equals("V_THEORA")) {
                        var8 = 9;
                    }
                    break;
                case 542569478:
                    if (var7.equals("A_DTS/EXPRESS")) {
                        var8 = 19;
                    }
                    break;
                case 725957860:
                    if (var7.equals("A_PCM/INT/LIT")) {
                        var8 = 23;
                    }
                    break;
                case 738597099:
                    if (var7.equals("S_TEXT/ASS")) {
                        var8 = 25;
                    }
                    break;
                case 855502857:
                    if (var7.equals("V_MPEGH/ISO/HEVC")) {
                        var8 = 7;
                    }
                    break;
                case 1422270023:
                    if (var7.equals("S_TEXT/UTF8")) {
                        var8 = 24;
                    }
                    break;
                case 1809237540:
                    if (var7.equals("V_MPEG2")) {
                        var8 = 2;
                    }
                    break;
                case 1950749482:
                    if (var7.equals("A_EAC3")) {
                        var8 = 16;
                    }
                    break;
                case 1950789798:
                    if (var7.equals("A_FLAC")) {
                        var8 = 21;
                    }
                    break;
                case 1951062397:
                    if (var7.equals("A_OPUS")) {
                        var8 = 11;
                    }
            }

            String mimeType;
            switch (var8) {
                case 0:
                    mimeType = "video/x-vnd.on2.vp8";
                    break;
                case 1:
                    mimeType = "video/x-vnd.on2.vp9";
                    break;
                case 2:
                    mimeType = "video/mpeg2";
                    break;
                case 3:
                case 4:
                case 5:
                    mimeType = "video/mp4v-es";
                    initializationData = this.codecPrivate == null ? null : Collections.singletonList(this.codecPrivate);
                    break;
                case 6:
                    mimeType = "video/avc";
                    AvcConfig avcConfig = AvcConfig.parse(new ParsableByteArray(this.codecPrivate));
                    initializationData = avcConfig.initializationData;
                    this.nalUnitLengthFieldLength = avcConfig.nalUnitLengthFieldLength;
                    break;
                case 7:
                    mimeType = "video/hevc";
                    HevcConfig hevcConfig = HevcConfig.parse(new ParsableByteArray(this.codecPrivate));
                    initializationData = hevcConfig.initializationData;
                    this.nalUnitLengthFieldLength = hevcConfig.nalUnitLengthFieldLength;
                    break;
                case 8:
                    Pair<String, List<byte[]>> pair = parseFourCcPrivate(new ParsableByteArray(this.codecPrivate));
                    mimeType = pair.first;
                    initializationData = pair.second;
                    break;
                case 9:
                    mimeType = "video/x-unknown";
                    break;
                case 10:
                    mimeType = "audio/vorbis";
                    maxInputSize = 8192;
                    initializationData = parseVorbisCodecPrivate(this.codecPrivate);
                    break;
                case 11:
                    mimeType = "audio/opus";
                    maxInputSize = 5760;
                    initializationData = new ArrayList<>(3);
                    initializationData.add(this.codecPrivate);
                    initializationData.add(ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(this.codecDelayNs).array());
                    initializationData.add(ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(this.seekPreRollNs).array());
                    break;
                case 12:
                    mimeType = "audio/mp4a-latm";
                    initializationData = Collections.singletonList(this.codecPrivate);
                    break;
                case 13:
                    mimeType = "audio/mpeg-L2";
                    maxInputSize = 4096;
                    break;
                case 14:
                    mimeType = "audio/mpeg";
                    maxInputSize = 4096;
                    break;
                case 15:
                    mimeType = "audio/ac3";
                    break;
                case 16:
                    mimeType = "audio/eac3";
                    break;
                case 17:
                    mimeType = "audio/true-hd";
                    this.trueHdSampleRechunker = new TrueHdSampleRechunker();
                    break;
                case 18:
                case 19:
                    mimeType = "audio/vnd.dts";
                    break;
                case 20:
                    mimeType = "audio/vnd.dts.hd";
                    break;
                case 21:
                    mimeType = "audio/flac";
                    initializationData = Collections.singletonList(this.codecPrivate);
                    break;
                case 22:
                    mimeType = "audio/raw";
                    if (parseMsAcmCodecPrivate(new ParsableByteArray(this.codecPrivate))) {
                        pcmEncoding = Util.getPcmEncoding(this.audioBitDepth);
                        if (pcmEncoding == 0) {
                            pcmEncoding = -1;
                            mimeType = "audio/x-unknown";
                            Log.w("MatroskaExtractor", "Unsupported PCM bit depth: " + this.audioBitDepth + ". Setting mimeType to " + mimeType);
                        }
                    } else {
                        mimeType = "audio/x-unknown";
                        Log.w("MatroskaExtractor", "Non-PCM MS/ACM is unsupported. Setting mimeType to " + mimeType);
                    }
                    break;
                case 23:
                    mimeType = "audio/raw";
                    pcmEncoding = Util.getPcmEncoding(this.audioBitDepth);
                    if (pcmEncoding == 0) {
                        pcmEncoding = -1;
                        mimeType = "audio/x-unknown";
                        Log.w("MatroskaExtractor", "Unsupported PCM bit depth: " + this.audioBitDepth + ". Setting mimeType to " + mimeType);
                    }
                    break;
                case 24:
                    mimeType = "application/x-subrip";
                    break;
                case 25:
                    mimeType = "text/x-ssa";
                    break;
                case 26:
                    mimeType = "application/vobsub";
                    initializationData = Collections.singletonList(this.codecPrivate);
                    break;
                case 27:
                    mimeType = "application/pgs";
                    break;
                case 28:
                    mimeType = "application/dvbsubs";
                    initializationData = Collections.singletonList(new byte[]{this.codecPrivate[0], this.codecPrivate[1], this.codecPrivate[2], this.codecPrivate[3]});
                    break;
                default:
                    throw new ParserException("Unrecognized codec identifier.");
            }

            int selectionFlags = (this.flagDefault ? 1 : 0) | (this.flagForced ? 2 : 0);
            byte type;
            Format format;
            if (MimeTypes.isAudio(mimeType)) {
                type = 1;
                format = Format.createAudioSampleFormat(Integer.toString(trackId), mimeType, null, -1, maxInputSize, this.channelCount, this.sampleRate, pcmEncoding, initializationData, this.drmInitData, selectionFlags, this.language);
            } else if (MimeTypes.isVideo(mimeType)) {
                type = 2;
                if (this.displayUnit == 0) {
                    this.displayWidth = this.displayWidth == -1 ? this.width : this.displayWidth;
                    this.displayHeight = this.displayHeight == -1 ? this.height : this.displayHeight;
                }

                float pixelWidthHeightRatio = -1.0F;
                if (this.displayWidth != -1 && this.displayHeight != -1) {
                    pixelWidthHeightRatio = (float) (this.height * this.displayWidth) / (float) (this.width * this.displayHeight);
                }

                ColorInfo colorInfo = null;
                if (this.hasColorInfo) {
                    byte[] hdrStaticInfo = this.getHdrStaticInfo();
                    colorInfo = new ColorInfo(this.colorSpace, this.colorRange, this.colorTransfer, hdrStaticInfo);
                }

                int rotationDegrees = -1;
                if ("htc_video_rotA-000".equals(this.name)) {
                    rotationDegrees = 0;
                } else if ("htc_video_rotA-090".equals(this.name)) {
                    rotationDegrees = 90;
                } else if ("htc_video_rotA-180".equals(this.name)) {
                    rotationDegrees = 180;
                } else if ("htc_video_rotA-270".equals(this.name)) {
                    rotationDegrees = 270;
                }

                format = Format.createVideoSampleFormat(Integer.toString(trackId), mimeType, null, -1, maxInputSize, this.width, this.height, -1.0F, initializationData, rotationDegrees, pixelWidthHeightRatio, this.projectionData, this.stereoMode, colorInfo, this.drmInitData);
            } else if ("application/x-subrip".equals(mimeType)) {
                type = 3;
                format = Format.createTextSampleFormat(Integer.toString(trackId), mimeType, selectionFlags, this.language, this.drmInitData);
            } else if ("text/x-ssa".equals(mimeType)) {
                type = 3;
                initializationData = new ArrayList<>(2);
                initializationData.add(MatroskaExtractor.SSA_DIALOGUE_FORMAT);
                initializationData.add(this.codecPrivate);
                format = Format.createTextSampleFormat(Integer.toString(trackId), mimeType, null, -1, selectionFlags, this.language, -1, this.drmInitData, Long.MAX_VALUE, initializationData);
            } else {
                if (!"application/vobsub".equals(mimeType) && !"application/pgs".equals(mimeType) && !"application/dvbsubs".equals(mimeType)) {
                    throw new ParserException("Unexpected MIME type.");
                }
                type = 3;
                format = Format.createImageSampleFormat(Integer.toString(trackId), mimeType, null, -1, selectionFlags, initializationData, this.language, this.drmInitData);
            }

            this.output = output.track(this.number, type);
            this.output.format(format);
        }

        public void outputPendingSampleMetadata() {
            if (this.trueHdSampleRechunker != null) {
                this.trueHdSampleRechunker.outputPendingSampleMetadata(this);
            }

        }

        public void reset() {
            if (this.trueHdSampleRechunker != null) {
                this.trueHdSampleRechunker.reset();
            }

        }

        private byte[] getHdrStaticInfo() {
            if (this.primaryRChromaticityX != -1.0F && this.primaryRChromaticityY != -1.0F && this.primaryGChromaticityX != -1.0F && this.primaryGChromaticityY != -1.0F && this.primaryBChromaticityX != -1.0F && this.primaryBChromaticityY != -1.0F && this.whitePointChromaticityX != -1.0F && this.whitePointChromaticityY != -1.0F && this.maxMasteringLuminance != -1.0F && this.minMasteringLuminance != -1.0F) {
                byte[] hdrStaticInfoData = new byte[25];
                ByteBuffer hdrStaticInfo = ByteBuffer.wrap(hdrStaticInfoData);
                hdrStaticInfo.put((byte) 0);
                hdrStaticInfo.putShort((short) ((int) (this.primaryRChromaticityX * 50000.0F + 0.5F)));
                hdrStaticInfo.putShort((short) ((int) (this.primaryRChromaticityY * 50000.0F + 0.5F)));
                hdrStaticInfo.putShort((short) ((int) (this.primaryGChromaticityX * 50000.0F + 0.5F)));
                hdrStaticInfo.putShort((short) ((int) (this.primaryGChromaticityY * 50000.0F + 0.5F)));
                hdrStaticInfo.putShort((short) ((int) (this.primaryBChromaticityX * 50000.0F + 0.5F)));
                hdrStaticInfo.putShort((short) ((int) (this.primaryBChromaticityY * 50000.0F + 0.5F)));
                hdrStaticInfo.putShort((short) ((int) (this.whitePointChromaticityX * 50000.0F + 0.5F)));
                hdrStaticInfo.putShort((short) ((int) (this.whitePointChromaticityY * 50000.0F + 0.5F)));
                hdrStaticInfo.putShort((short) ((int) (this.maxMasteringLuminance + 0.5F)));
                hdrStaticInfo.putShort((short) ((int) (this.minMasteringLuminance + 0.5F)));
                hdrStaticInfo.putShort((short) this.maxContentLuminance);
                hdrStaticInfo.putShort((short) this.maxFrameAverageLuminance);
                return hdrStaticInfoData;
            } else {
                return null;
            }
        }

        private static Pair<String, List<byte[]>> parseFourCcPrivate(ParsableByteArray buffer) throws ParserException {
            try {
                buffer.skipBytes(16);
                long compression = buffer.readLittleEndianUnsignedInt();
                if (compression == 1482049860L) {
                    return new Pair("video/3gpp", null);
                }

                if (compression == 826496599L) {
                    int startOffset = buffer.getPosition() + 20;
                    byte[] bufferData = buffer.data;

                    for (int offset = startOffset; offset < bufferData.length - 4; ++offset) {
                        if (bufferData[offset] == 0 && bufferData[offset + 1] == 0 && bufferData[offset + 2] == 1 && bufferData[offset + 3] == 15) {
                            byte[] initializationData = Arrays.copyOfRange(bufferData, offset, bufferData.length);
                            return new Pair("video/wvc1", Collections.singletonList(initializationData));
                        }
                    }

                    throw new ParserException("Failed to find FourCC VC1 initialization data");
                }
            } catch (ArrayIndexOutOfBoundsException var7) {
                throw new ParserException("Error parsing FourCC private data");
            }

            Log.w("MatroskaExtractor", "Unknown FourCC. Setting mimeType to video/x-unknown");
            return new Pair("video/x-unknown", null);
        }

        private static List<byte[]> parseVorbisCodecPrivate(byte[] codecPrivate) throws ParserException {
            try {
                if (codecPrivate[0] != 2) {
                    throw new ParserException("Error parsing vorbis codec private");
                } else {
                    int offset = 1;

                    int vorbisInfoLength;
                    for (vorbisInfoLength = 0; codecPrivate[offset] == -1; ++offset) {
                        vorbisInfoLength += 255;
                    }

                    vorbisInfoLength += codecPrivate[offset++];

                    int vorbisSkipLength;
                    for (vorbisSkipLength = 0; codecPrivate[offset] == -1; ++offset) {
                        vorbisSkipLength += 255;
                    }

                    vorbisSkipLength += codecPrivate[offset++];
                    if (codecPrivate[offset] != 1) {
                        throw new ParserException("Error parsing vorbis codec private");
                    } else {
                        byte[] vorbisInfo = new byte[vorbisInfoLength];
                        System.arraycopy(codecPrivate, offset, vorbisInfo, 0, vorbisInfoLength);
                        offset += vorbisInfoLength;
                        if (codecPrivate[offset] != 3) {
                            throw new ParserException("Error parsing vorbis codec private");
                        } else {
                            offset += vorbisSkipLength;
                            if (codecPrivate[offset] != 5) {
                                throw new ParserException("Error parsing vorbis codec private");
                            } else {
                                byte[] vorbisBooks = new byte[codecPrivate.length - offset];
                                System.arraycopy(codecPrivate, offset, vorbisBooks, 0, codecPrivate.length - offset);
                                List<byte[]> initializationData = new ArrayList(2);
                                initializationData.add(vorbisInfo);
                                initializationData.add(vorbisBooks);
                                return initializationData;
                            }
                        }
                    }
                }
            } catch (ArrayIndexOutOfBoundsException var7) {
                throw new ParserException("Error parsing vorbis codec private");
            }
        }

        private static boolean parseMsAcmCodecPrivate(ParsableByteArray buffer) throws ParserException {
            try {
                int formatTag = buffer.readLittleEndianUnsignedShort();
                if (formatTag == 1) {
                    return true;
                } else if (formatTag != 65534) {
                    return false;
                } else {
                    buffer.setPosition(24);
                    return buffer.readLong() == MatroskaExtractor.WAVE_SUBFORMAT_PCM.getMostSignificantBits() && buffer.readLong() == MatroskaExtractor.WAVE_SUBFORMAT_PCM.getLeastSignificantBits();
                }
            } catch (ArrayIndexOutOfBoundsException var2) {
                throw new ParserException("Error parsing MS/ACM codec private");
            }
        }
    }

    private static final class TrueHdSampleRechunker {
        private final byte[] syncframePrefix = new byte[10];
        private boolean foundSyncframe;
        private int sampleCount;
        private int chunkSize;
        private long timeUs;
        private int blockFlags;

        public TrueHdSampleRechunker() {
        }

        public void reset() {
            this.foundSyncframe = false;
        }

        public void startSample(ExtractorInput input, int blockFlags, int size) throws IOException, InterruptedException {
            if (!this.foundSyncframe) {
                input.peekFully(this.syncframePrefix, 0, 10);
                input.resetPeekPosition();
                if (Ac3Util.parseTrueHdSyncframeAudioSampleCount(this.syncframePrefix) == 0) {
                    return;
                }

                this.foundSyncframe = true;
                this.sampleCount = 0;
            }

            if (this.sampleCount == 0) {
                this.blockFlags = blockFlags;
                this.chunkSize = 0;
            }

            this.chunkSize += size;
        }

        public void sampleMetadata(Track track, long timeUs) {
            if (this.foundSyncframe) {
                if (this.sampleCount++ == 0) {
                    this.timeUs = timeUs;
                }

                if (this.sampleCount >= 16) {
                    track.output.sampleMetadata(this.timeUs, this.blockFlags, this.chunkSize, 0, track.cryptoData);
                    this.sampleCount = 0;
                }
            }
        }

        public void outputPendingSampleMetadata(Track track) {
            if (this.foundSyncframe && this.sampleCount > 0) {
                track.output.sampleMetadata(this.timeUs, this.blockFlags, this.chunkSize, 0, track.cryptoData);
                this.sampleCount = 0;
            }

        }
    }

    private final class InnerEbmlReaderOutput implements EbmlReaderOutput {
        private InnerEbmlReaderOutput() {
        }

        public int getElementType(int id) {
            switch (id) {
                case 131:
                case 136:
                case 155:
                case 159:
                case 176:
                case 179:
                case 186:
                case 215:
                case 231:
                case 241:
                case 251:
                case 16980:
                case 17029:
                case 17143:
                case 18401:
                case 18408:
                case 20529:
                case 20530:
                case 21420:
                case 21432:
                case 21680:
                case 21682:
                case 21690:
                case 21930:
                case 21945:
                case 21946:
                case 21947:
                case 21948:
                case 21949:
                case 22186:
                case 22203:
                case 25188:
                case 2352003:
                case 2807729:
                    return 2;
                case 134:
                case 17026:
                case 21358:
                case 2274716:
                    return 3;
                case 160:
                case 174:
                case 183:
                case 187:
                case 224:
                case 225:
                case 18407:
                case 19899:
                case 20532:
                case 20533:
                case 21936:
                case 21968:
                case 25152:
                case 28032:
                case 30320:
                case 290298740:
                case 357149030:
                case 374648427:
                case 408125543:
                case 440786851:
                case 475249515:
                case 524531317:
                    return 1;
                case 161:
                case 163:
                case 16981:
                case 18402:
                case 21419:
                case 25506:
                case 30322:
                    return 4;
                case 181:
                case 17545:
                case 21969:
                case 21970:
                case 21971:
                case 21972:
                case 21973:
                case 21974:
                case 21975:
                case 21976:
                case 21977:
                case 21978:
                    return 5;
                default:
                    return 0;
            }
        }

        public boolean isLevel1Element(int id) {
            return id == 357149030 || id == 524531317 || id == 475249515 || id == 374648427;
        }

        public void startMasterElement(int id, long contentPosition, long contentSize) throws ParserException {
            MatroskaExtractor.this.startMasterElement(id, contentPosition, contentSize);
        }

        public void endMasterElement(int id) throws ParserException {
            MatroskaExtractor.this.endMasterElement(id);
        }

        public void integerElement(int id, long value) throws ParserException {
            MatroskaExtractor.this.integerElement(id, value);
        }

        public void floatElement(int id, double value) throws ParserException {
            MatroskaExtractor.this.floatElement(id, value);
        }

        public void stringElement(int id, String value) throws ParserException {
            MatroskaExtractor.this.stringElement(id, value);
        }

        public void binaryElement(int id, int contentsSize, ExtractorInput input) throws IOException, InterruptedException {
            MatroskaExtractor.this.binaryElement(id, contentsSize, input);
        }
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface Flags {}
}
