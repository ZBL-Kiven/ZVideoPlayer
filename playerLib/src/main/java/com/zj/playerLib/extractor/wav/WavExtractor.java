//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.wav;

import com.zj.playerLib.Format;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.ExtractorsFactory;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.util.Assertions;
import java.io.IOException;
import java.util.List;

public final class WavExtractor implements Extractor {
    public static final ExtractorsFactory FACTORY = () -> {
        return new Extractor[]{new WavExtractor()};
    };
    private static final int MAX_INPUT_SIZE = 32768;
    private ExtractorOutput extractorOutput;
    private TrackOutput trackOutput;
    private WavHeader wavHeader;
    private int bytesPerFrame;
    private int pendingBytes;

    public WavExtractor() {
    }

    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        return WavHeaderReader.peek(input) != null;
    }

    public void init(ExtractorOutput output) {
        this.extractorOutput = output;
        this.trackOutput = output.track(0, 1);
        this.wavHeader = null;
        output.endTracks();
    }

    public void seek(long position, long timeUs) {
        this.pendingBytes = 0;
    }

    public void release() {
    }

    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        if (this.wavHeader == null) {
            this.wavHeader = WavHeaderReader.peek(input);
            if (this.wavHeader == null) {
                throw new ParserException("Unsupported or unrecognized wav header.");
            }

            Format format = Format.createAudioSampleFormat((String)null, "audio/raw", (String)null, this.wavHeader.getBitrate(), 32768, this.wavHeader.getNumChannels(), this.wavHeader.getSampleRateHz(), this.wavHeader.getEncoding(), (List)null, (DrmInitData)null, 0, (String)null);
            this.trackOutput.format(format);
            this.bytesPerFrame = this.wavHeader.getBytesPerFrame();
        }

        if (!this.wavHeader.hasDataBounds()) {
            WavHeaderReader.skipToData(input, this.wavHeader);
            this.extractorOutput.seekMap(this.wavHeader);
        }

        long dataLimit = this.wavHeader.getDataLimit();
        Assertions.checkState(dataLimit != -1L);
        long bytesLeft = dataLimit - input.getPosition();
        if (bytesLeft <= 0L) {
            return -1;
        } else {
            int maxBytesToRead = (int)Math.min((long)('耀' - this.pendingBytes), bytesLeft);
            int bytesAppended = this.trackOutput.sampleData(input, maxBytesToRead, true);
            if (bytesAppended != -1) {
                this.pendingBytes += bytesAppended;
            }

            int pendingFrames = this.pendingBytes / this.bytesPerFrame;
            if (pendingFrames > 0) {
                long timeUs = this.wavHeader.getTimeUs(input.getPosition() - (long)this.pendingBytes);
                int size = pendingFrames * this.bytesPerFrame;
                this.pendingBytes -= size;
                this.trackOutput.sampleMetadata(timeUs, 1, size, this.pendingBytes, (CryptoData)null);
            }

            return bytesAppended == -1 ? -1 : 0;
        }
    }
}
