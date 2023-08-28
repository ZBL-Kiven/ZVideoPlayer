package com.zj.playerLib.extractor;

import androidx.annotation.Nullable;

import com.zj.playerLib.Format;
import com.zj.playerLib.util.ParsableByteArray;

import java.io.EOFException;
import java.io.IOException;

public final class DummyTrackOutput implements TrackOutput {
    public DummyTrackOutput() {
    }

    public void format(Format format) {
    }

    public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        int bytesSkipped = input.skip(length);
        if (bytesSkipped == -1) {
            if (allowEndOfInput) {
                return -1;
            } else {
                throw new EOFException();
            }
        } else {
            return bytesSkipped;
        }
    }

    public void sampleData(ParsableByteArray data, int length) {
        data.skipBytes(length);
    }

    public void sampleMetadata(long timeUs, int flags, int size, int offset, @Nullable CryptoData cryptoData) {
    }
}
