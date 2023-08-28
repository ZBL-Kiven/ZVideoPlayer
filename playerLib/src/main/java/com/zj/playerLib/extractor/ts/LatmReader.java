package com.zj.playerLib.extractor.ts;

import android.util.Pair;
import androidx.annotation.Nullable;
import com.zj.playerLib.Format;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.util.CodecSpecificDataUtil;
import com.zj.playerLib.util.ParsableBitArray;
import com.zj.playerLib.util.ParsableByteArray;
import java.util.Collections;

public final class LatmReader implements ElementaryStreamReader {
    private static final int STATE_FINDING_SYNC_1 = 0;
    private static final int STATE_FINDING_SYNC_2 = 1;
    private static final int STATE_READING_HEADER = 2;
    private static final int STATE_READING_SAMPLE = 3;
    private static final int INITIAL_BUFFER_SIZE = 1024;
    private static final int SYNC_BYTE_FIRST = 86;
    private static final int SYNC_BYTE_SECOND = 224;
    private final String language;
    private final ParsableByteArray sampleDataBuffer;
    private final ParsableBitArray sampleBitArray;
    private TrackOutput output;
    private Format format;
    private String formatId;
    private int state;
    private int bytesRead;
    private int sampleSize;
    private int secondHeaderByte;
    private long timeUs;
    private boolean streamMuxRead;
    private int audioMuxVersionA;
    private int numSubframes;
    private int frameLengthType;
    private boolean otherDataPresent;
    private long otherDataLenBits;
    private int sampleRateHz;
    private long sampleDurationUs;
    private int channelCount;

    public LatmReader(@Nullable String language) {
        this.language = language;
        this.sampleDataBuffer = new ParsableByteArray(1024);
        this.sampleBitArray = new ParsableBitArray(this.sampleDataBuffer.data);
    }

    public void seek() {
        this.state = 0;
        this.streamMuxRead = false;
    }

    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
        idGenerator.generateNewId();
        this.output = extractorOutput.track(idGenerator.getTrackId(), 1);
        this.formatId = idGenerator.getFormatId();
    }

    public void packetStarted(long pesTimeUs, int flags) {
        this.timeUs = pesTimeUs;
    }

    public void consume(ParsableByteArray data) throws ParserException {
        while(data.bytesLeft() > 0) {
            switch(this.state) {
            case 0:
                if (data.readUnsignedByte() == 86) {
                    this.state = 1;
                }
                break;
            case 1:
                int secondByte = data.readUnsignedByte();
                if ((secondByte & 224) == 224) {
                    this.secondHeaderByte = secondByte;
                    this.state = 2;
                } else if (secondByte != 86) {
                    this.state = 0;
                }
                break;
            case 2:
                this.sampleSize = (this.secondHeaderByte & -225) << 8 | data.readUnsignedByte();
                if (this.sampleSize > this.sampleDataBuffer.data.length) {
                    this.resetBufferForSize(this.sampleSize);
                }

                this.bytesRead = 0;
                this.state = 3;
                break;
            case 3:
                int bytesToRead = Math.min(data.bytesLeft(), this.sampleSize - this.bytesRead);
                data.readBytes(this.sampleBitArray.data, this.bytesRead, bytesToRead);
                this.bytesRead += bytesToRead;
                if (this.bytesRead == this.sampleSize) {
                    this.sampleBitArray.setPosition(0);
                    this.parseAudioMuxElement(this.sampleBitArray);
                    this.state = 0;
                }
                break;
            default:
                throw new IllegalStateException();
            }
        }

    }

    public void packetFinished() {
    }

    private void parseAudioMuxElement(ParsableBitArray data) throws ParserException {
        boolean useSameStreamMux = data.readBit();
        if (!useSameStreamMux) {
            this.streamMuxRead = true;
            this.parseStreamMuxConfig(data);
        } else if (!this.streamMuxRead) {
            return;
        }

        if (this.audioMuxVersionA == 0) {
            if (this.numSubframes != 0) {
                throw new ParserException();
            } else {
                int muxSlotLengthBytes = this.parsePayloadLengthInfo(data);
                this.parsePayloadMux(data, muxSlotLengthBytes);
                if (this.otherDataPresent) {
                    data.skipBits((int)this.otherDataLenBits);
                }

            }
        } else {
            throw new ParserException();
        }
    }

    private void parseStreamMuxConfig(ParsableBitArray data) throws ParserException {
        int audioMuxVersion = data.readBits(1);
        this.audioMuxVersionA = audioMuxVersion == 1 ? data.readBits(1) : 0;
        if (this.audioMuxVersionA != 0) {
            throw new ParserException();
        } else {
            if (audioMuxVersion == 1) {
                latmGetValue(data);
            }

            if (!data.readBit()) {
                throw new ParserException();
            } else {
                this.numSubframes = data.readBits(6);
                int numProgram = data.readBits(4);
                int numLayer = data.readBits(3);
                if (numProgram == 0 && numLayer == 0) {
                    int startPosition;
                    int readBits;
                    if (audioMuxVersion == 0) {
                        startPosition = data.getPosition();
                        readBits = this.parseAudioSpecificConfig(data);
                        data.setPosition(startPosition);
                        byte[] initData = new byte[(readBits + 7) / 8];
                        data.readBits(initData, 0, readBits);
                        Format format = Format.createAudioSampleFormat(this.formatId, "audio/mp4a-latm", null, -1, -1, this.channelCount, this.sampleRateHz, Collections.singletonList(initData), null, 0, this.language);
                        if (!format.equals(this.format)) {
                            this.format = format;
                            this.sampleDurationUs = 1024000000L / (long)format.sampleRate;
                            this.output.format(format);
                        }
                    } else {
                        startPosition = (int)latmGetValue(data);
                        readBits = this.parseAudioSpecificConfig(data);
                        data.skipBits(startPosition - readBits);
                    }

                    this.parseFrameLength(data);
                    this.otherDataPresent = data.readBit();
                    this.otherDataLenBits = 0L;
                    boolean otherDataLenEsc;
                    if (this.otherDataPresent) {
                        if (audioMuxVersion == 1) {
                            this.otherDataLenBits = latmGetValue(data);
                        } else {
                            do {
                                otherDataLenEsc = data.readBit();
                                this.otherDataLenBits = (this.otherDataLenBits << 8) + (long)data.readBits(8);
                            } while(otherDataLenEsc);
                        }
                    }

                    otherDataLenEsc = data.readBit();
                    if (otherDataLenEsc) {
                        data.skipBits(8);
                    }

                } else {
                    throw new ParserException();
                }
            }
        }
    }

    private void parseFrameLength(ParsableBitArray data) {
        this.frameLengthType = data.readBits(3);
        switch(this.frameLengthType) {
        case 0:
            data.skipBits(8);
            break;
        case 1:
            data.skipBits(9);
            break;
        case 2:
        default:
            throw new IllegalStateException();
        case 3:
        case 4:
        case 5:
            data.skipBits(6);
            break;
        case 6:
        case 7:
            data.skipBits(1);
        }

    }

    private int parseAudioSpecificConfig(ParsableBitArray data) throws ParserException {
        int bitsLeft = data.bitsLeft();
        Pair<Integer, Integer> config = CodecSpecificDataUtil.parseAacAudioSpecificConfig(data, true);
        this.sampleRateHz = config.first;
        this.channelCount = config.second;
        return bitsLeft - data.bitsLeft();
    }

    private int parsePayloadLengthInfo(ParsableBitArray data) throws ParserException {
        int muxSlotLengthBytes = 0;
        if (this.frameLengthType != 0) {
            throw new ParserException();
        } else {
            int tmp;
            do {
                tmp = data.readBits(8);
                muxSlotLengthBytes += tmp;
            } while(tmp == 255);

            return muxSlotLengthBytes;
        }
    }

    private void parsePayloadMux(ParsableBitArray data, int muxLengthBytes) {
        int bitPosition = data.getPosition();
        if ((bitPosition & 7) == 0) {
            this.sampleDataBuffer.setPosition(bitPosition >> 3);
        } else {
            data.readBits(this.sampleDataBuffer.data, 0, muxLengthBytes * 8);
            this.sampleDataBuffer.setPosition(0);
        }

        this.output.sampleData(this.sampleDataBuffer, muxLengthBytes);
        this.output.sampleMetadata(this.timeUs, 1, muxLengthBytes, 0, null);
        this.timeUs += this.sampleDurationUs;
    }

    private void resetBufferForSize(int newSize) {
        this.sampleDataBuffer.reset(newSize);
        this.sampleBitArray.reset(this.sampleDataBuffer.data);
    }

    private static long latmGetValue(ParsableBitArray data) {
        int bytesForValue = data.readBits(2);
        return data.readBits((bytesForValue + 1) * 8);
    }
}
