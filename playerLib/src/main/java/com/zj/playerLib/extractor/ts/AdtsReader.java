package com.zj.playerLib.extractor.ts;

import android.util.Pair;
import com.zj.playerLib.Format;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.DummyTrackOutput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.util.CodecSpecificDataUtil;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.ParsableBitArray;
import com.zj.playerLib.util.ParsableByteArray;
import java.util.Arrays;
import java.util.Collections;

public final class AdtsReader implements ElementaryStreamReader {
    private static final String TAG = "AdtsReader";
    private static final int STATE_FINDING_SAMPLE = 0;
    private static final int STATE_CHECKING_ADTS_HEADER = 1;
    private static final int STATE_READING_ID3_HEADER = 2;
    private static final int STATE_READING_ADTS_HEADER = 3;
    private static final int STATE_READING_SAMPLE = 4;
    private static final int HEADER_SIZE = 5;
    private static final int CRC_SIZE = 2;
    private static final int MATCH_STATE_VALUE_SHIFT = 8;
    private static final int MATCH_STATE_START = 256;
    private static final int MATCH_STATE_FF = 512;
    private static final int MATCH_STATE_I = 768;
    private static final int MATCH_STATE_ID = 1024;
    private static final int ID3_HEADER_SIZE = 10;
    private static final int ID3_SIZE_OFFSET = 6;
    private static final byte[] ID3_IDENTIFIER = new byte[]{73, 68, 51};
    private static final int VERSION_UNSET = -1;
    private final boolean exposeId3;
    private final ParsableBitArray adtsScratch;
    private final ParsableByteArray id3HeaderBuffer;
    private final String language;
    private String formatId;
    private TrackOutput output;
    private TrackOutput id3Output;
    private int state;
    private int bytesRead;
    private int matchState;
    private boolean hasCrc;
    private boolean foundFirstFrame;
    private int firstFrameVersion;
    private int firstFrameSampleRateIndex;
    private int currentFrameVersion;
    private boolean hasOutputFormat;
    private long sampleDurationUs;
    private int sampleSize;
    private long timeUs;
    private TrackOutput currentOutput;
    private long currentSampleDuration;

    public AdtsReader(boolean exposeId3) {
        this(exposeId3, null);
    }

    public AdtsReader(boolean exposeId3, String language) {
        this.adtsScratch = new ParsableBitArray(new byte[7]);
        this.id3HeaderBuffer = new ParsableByteArray(Arrays.copyOf(ID3_IDENTIFIER, 10));
        this.setFindingSampleState();
        this.firstFrameVersion = -1;
        this.firstFrameSampleRateIndex = -1;
        this.sampleDurationUs = -Long.MAX_VALUE;
        this.exposeId3 = exposeId3;
        this.language = language;
    }

    public static boolean isAdtsSyncWord(int candidateSyncWord) {
        return (candidateSyncWord & '\ufff6') == 65520;
    }

    public void seek() {
        this.resetSync();
    }

    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
        idGenerator.generateNewId();
        this.formatId = idGenerator.getFormatId();
        this.output = extractorOutput.track(idGenerator.getTrackId(), 1);
        if (this.exposeId3) {
            idGenerator.generateNewId();
            this.id3Output = extractorOutput.track(idGenerator.getTrackId(), 4);
            this.id3Output.format(Format.createSampleFormat(idGenerator.getFormatId(), "application/id3", null, -1, null));
        } else {
            this.id3Output = new DummyTrackOutput();
        }

    }

    public void packetStarted(long pesTimeUs, int flags) {
        this.timeUs = pesTimeUs;
    }

    public void consume(ParsableByteArray data) throws ParserException {
        while(data.bytesLeft() > 0) {
            switch(this.state) {
            case 0:
                this.findNextSample(data);
                break;
            case 1:
                this.checkAdtsHeader(data);
                break;
            case 2:
                if (this.continueRead(data, this.id3HeaderBuffer.data, 10)) {
                    this.parseId3Header();
                }
                break;
            case 3:
                int targetLength = this.hasCrc ? 7 : 5;
                if (this.continueRead(data, this.adtsScratch.data, targetLength)) {
                    this.parseAdtsHeader();
                }
                break;
            case 4:
                this.readSample(data);
                break;
            default:
                throw new IllegalStateException();
            }
        }

    }

    public void packetFinished() {
    }

    public long getSampleDurationUs() {
        return this.sampleDurationUs;
    }

    private void resetSync() {
        this.foundFirstFrame = false;
        this.setFindingSampleState();
    }

    private boolean continueRead(ParsableByteArray source, byte[] target, int targetLength) {
        int bytesToRead = Math.min(source.bytesLeft(), targetLength - this.bytesRead);
        source.readBytes(target, this.bytesRead, bytesToRead);
        this.bytesRead += bytesToRead;
        return this.bytesRead == targetLength;
    }

    private void setFindingSampleState() {
        this.state = 0;
        this.bytesRead = 0;
        this.matchState = 256;
    }

    private void setReadingId3HeaderState() {
        this.state = 2;
        this.bytesRead = ID3_IDENTIFIER.length;
        this.sampleSize = 0;
        this.id3HeaderBuffer.setPosition(0);
    }

    private void setReadingSampleState(TrackOutput outputToUse, long currentSampleDuration, int priorReadBytes, int sampleSize) {
        this.state = 4;
        this.bytesRead = priorReadBytes;
        this.currentOutput = outputToUse;
        this.currentSampleDuration = currentSampleDuration;
        this.sampleSize = sampleSize;
    }

    private void setReadingAdtsHeaderState() {
        this.state = 3;
        this.bytesRead = 0;
    }

    private void setCheckingAdtsHeaderState() {
        this.state = 1;
        this.bytesRead = 0;
    }

    private void findNextSample(ParsableByteArray pesBuffer) {
        byte[] adtsData = pesBuffer.data;
        int position = pesBuffer.getPosition();
        int endOffset = pesBuffer.limit();

        while(position < endOffset) {
            int data = adtsData[position++] & 255;
            if (this.matchState == 512 && this.isAdtsSyncBytes((byte)-1, (byte)data) && (this.foundFirstFrame || this.checkSyncPositionValid(pesBuffer, position - 2))) {
                this.currentFrameVersion = (data & 8) >> 3;
                this.hasCrc = (data & 1) == 0;
                if (!this.foundFirstFrame) {
                    this.setCheckingAdtsHeaderState();
                } else {
                    this.setReadingAdtsHeaderState();
                }

                pesBuffer.setPosition(position);
                return;
            }

            switch(this.matchState | data) {
            case 329:
                this.matchState = 768;
                break;
            case 511:
                this.matchState = 512;
                break;
            case 836:
                this.matchState = 1024;
                break;
            case 1075:
                this.setReadingId3HeaderState();
                pesBuffer.setPosition(position);
                return;
            default:
                if (this.matchState != 256) {
                    this.matchState = 256;
                    --position;
                }
            }
        }

        pesBuffer.setPosition(position);
    }

    private void checkAdtsHeader(ParsableByteArray buffer) {
        if (buffer.bytesLeft() != 0) {
            this.adtsScratch.data[0] = buffer.data[buffer.getPosition()];
            this.adtsScratch.setPosition(2);
            int currentFrameSampleRateIndex = this.adtsScratch.readBits(4);
            if (this.firstFrameSampleRateIndex != -1 && currentFrameSampleRateIndex != this.firstFrameSampleRateIndex) {
                this.resetSync();
            } else {
                if (!this.foundFirstFrame) {
                    this.foundFirstFrame = true;
                    this.firstFrameVersion = this.currentFrameVersion;
                    this.firstFrameSampleRateIndex = currentFrameSampleRateIndex;
                }

                this.setReadingAdtsHeaderState();
            }
        }
    }

    private boolean checkSyncPositionValid(ParsableByteArray pesBuffer, int syncPositionCandidate) {
        pesBuffer.setPosition(syncPositionCandidate + 1);
        if (!this.tryRead(pesBuffer, this.adtsScratch.data, 1)) {
            return false;
        } else {
            this.adtsScratch.setPosition(4);
            int currentFrameVersion = this.adtsScratch.readBits(1);
            if (this.firstFrameVersion != -1 && currentFrameVersion != this.firstFrameVersion) {
                return false;
            } else {
                int frameSize;
                if (this.firstFrameSampleRateIndex != -1) {
                    if (!this.tryRead(pesBuffer, this.adtsScratch.data, 1)) {
                        return true;
                    }

                    this.adtsScratch.setPosition(2);
                    frameSize = this.adtsScratch.readBits(4);
                    if (frameSize != this.firstFrameSampleRateIndex) {
                        return false;
                    }

                    pesBuffer.setPosition(syncPositionCandidate + 2);
                }

                if (!this.tryRead(pesBuffer, this.adtsScratch.data, 4)) {
                    return true;
                } else {
                    this.adtsScratch.setPosition(14);
                    frameSize = this.adtsScratch.readBits(13);
                    if (frameSize <= 6) {
                        return false;
                    } else {
                        int nextSyncPosition = syncPositionCandidate + frameSize;
                        if (nextSyncPosition + 1 >= pesBuffer.limit()) {
                            return true;
                        } else {
                            return this.isAdtsSyncBytes(pesBuffer.data[nextSyncPosition], pesBuffer.data[nextSyncPosition + 1]) && (this.firstFrameVersion == -1 || (pesBuffer.data[nextSyncPosition + 1] & 8) >> 3 == currentFrameVersion);
                        }
                    }
                }
            }
        }
    }

    private boolean isAdtsSyncBytes(byte firstByte, byte secondByte) {
        int syncWord = (firstByte & 255) << 8 | secondByte & 255;
        return isAdtsSyncWord(syncWord);
    }

    private boolean tryRead(ParsableByteArray source, byte[] target, int targetLength) {
        if (source.bytesLeft() < targetLength) {
            return false;
        } else {
            source.readBytes(target, 0, targetLength);
            return true;
        }
    }

    private void parseId3Header() {
        this.id3Output.sampleData(this.id3HeaderBuffer, 10);
        this.id3HeaderBuffer.setPosition(6);
        this.setReadingSampleState(this.id3Output, 0L, 10, this.id3HeaderBuffer.readSynchSafeInt() + 10);
    }

    private void parseAdtsHeader() throws ParserException {
        this.adtsScratch.setPosition(0);
        int sampleSize;
        if (!this.hasOutputFormat) {
            sampleSize = this.adtsScratch.readBits(2) + 1;
            if (sampleSize != 2) {
                Log.w("AdtsReader", "Detected audio object type: " + sampleSize + ", but assuming AAC LC.");
                sampleSize = 2;
            }

            this.adtsScratch.skipBits(5);
            int channelConfig = this.adtsScratch.readBits(3);
            byte[] audioSpecificConfig = CodecSpecificDataUtil.buildAacAudioSpecificConfig(sampleSize, this.firstFrameSampleRateIndex, channelConfig);
            Pair<Integer, Integer> audioParams = CodecSpecificDataUtil.parseAacAudioSpecificConfig(audioSpecificConfig);
            Format format = Format.createAudioSampleFormat(this.formatId, "audio/mp4a-latm", null, -1, -1, audioParams.second, audioParams.first, Collections.singletonList(audioSpecificConfig), null, 0, this.language);
            this.sampleDurationUs = 1024000000L / (long)format.sampleRate;
            this.output.format(format);
            this.hasOutputFormat = true;
        } else {
            this.adtsScratch.skipBits(10);
        }

        this.adtsScratch.skipBits(4);
        sampleSize = this.adtsScratch.readBits(13) - 2 - 5;
        if (this.hasCrc) {
            sampleSize -= 2;
        }

        this.setReadingSampleState(this.output, this.sampleDurationUs, 0, sampleSize);
    }

    private void readSample(ParsableByteArray data) {
        int bytesToRead = Math.min(data.bytesLeft(), this.sampleSize - this.bytesRead);
        this.currentOutput.sampleData(data, bytesToRead);
        this.bytesRead += bytesToRead;
        if (this.bytesRead == this.sampleSize) {
            this.currentOutput.sampleMetadata(this.timeUs, 1, this.sampleSize, 0, null);
            this.timeUs += this.currentSampleDuration;
            this.setFindingSampleState();
        }

    }
}
