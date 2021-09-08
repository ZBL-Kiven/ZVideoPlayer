//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.wav;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.audio.WavUtil;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;

import java.io.IOException;

final class WavHeaderReader {
    private static final String TAG = "WavHeaderReader";

    public static WavHeader peek(ExtractorInput input) throws IOException, InterruptedException {
        Assertions.checkNotNull(input);
        ParsableByteArray scratch = new ParsableByteArray(16);
        ChunkHeader chunkHeader = ChunkHeader.peek(input, scratch);
        if (chunkHeader.id != WavUtil.RIFF_FOURCC) {
            return null;
        } else {
            input.peekFully(scratch.data, 0, 4);
            scratch.setPosition(0);
            int riffFormat = scratch.readInt();
            if (riffFormat != WavUtil.WAVE_FOURCC) {
                Log.e("WavHeaderReader", "Unsupported RIFF format: " + riffFormat);
                return null;
            } else {
                for(chunkHeader = ChunkHeader.peek(input, scratch); chunkHeader.id != WavUtil.FMT_FOURCC; chunkHeader = ChunkHeader.peek(input, scratch)) {
                    input.advancePeekPosition((int)chunkHeader.size);
                }

                Assertions.checkState(chunkHeader.size >= 16L);
                input.peekFully(scratch.data, 0, 16);
                scratch.setPosition(0);
                int type = scratch.readLittleEndianUnsignedShort();
                int numChannels = scratch.readLittleEndianUnsignedShort();
                int sampleRateHz = scratch.readLittleEndianUnsignedIntToInt();
                int averageBytesPerSecond = scratch.readLittleEndianUnsignedIntToInt();
                int blockAlignment = scratch.readLittleEndianUnsignedShort();
                int bitsPerSample = scratch.readLittleEndianUnsignedShort();
                int expectedBlockAlignment = numChannels * bitsPerSample / 8;
                if (blockAlignment != expectedBlockAlignment) {
                    throw new ParserException("Expected block alignment: " + expectedBlockAlignment + "; got: " + blockAlignment);
                } else {
                    int encoding = WavUtil.getEncodingForType(type, bitsPerSample);
                    if (encoding == 0) {
                        Log.e("WavHeaderReader", "Unsupported WAV format: " + bitsPerSample + " bit/sample, type " + type);
                        return null;
                    } else {
                        input.advancePeekPosition((int)chunkHeader.size - 16);
                        return new WavHeader(numChannels, sampleRateHz, averageBytesPerSecond, blockAlignment, bitsPerSample, encoding);
                    }
                }
            }
        }
    }

    public static void skipToData(ExtractorInput input, WavHeader wavHeader) throws IOException, InterruptedException {
        Assertions.checkNotNull(input);
        Assertions.checkNotNull(wavHeader);
        input.resetPeekPosition();
        ParsableByteArray scratch = new ParsableByteArray(8);

        ChunkHeader chunkHeader;
        for(chunkHeader = ChunkHeader.peek(input, scratch); chunkHeader.id != Util.getIntegerCodeForString("data"); chunkHeader = ChunkHeader.peek(input, scratch)) {
            Log.w("WavHeaderReader", "Ignoring unknown WAV chunk: " + chunkHeader.id);
            long bytesToSkip = 8L + chunkHeader.size;
            if (chunkHeader.id == Util.getIntegerCodeForString("RIFF")) {
                bytesToSkip = 12L;
            }

            if (bytesToSkip > 2147483647L) {
                throw new ParserException("Chunk is too large (~2GB+) to skip; id: " + chunkHeader.id);
            }

            input.skipFully((int)bytesToSkip);
        }

        input.skipFully(8);
        wavHeader.setDataBounds(input.getPosition(), chunkHeader.size);
    }

    private WavHeaderReader() {
    }

    private static final class ChunkHeader {
        public static final int SIZE_IN_BYTES = 8;
        public final int id;
        public final long size;

        private ChunkHeader(int id, long size) {
            this.id = id;
            this.size = size;
        }

        public static ChunkHeader peek(ExtractorInput input, ParsableByteArray scratch) throws IOException, InterruptedException {
            input.peekFully(scratch.data, 0, 8);
            scratch.setPosition(0);
            int id = scratch.readInt();
            long size = scratch.readLittleEndianUnsignedInt();
            return new ChunkHeader(id, size);
        }
    }
}
