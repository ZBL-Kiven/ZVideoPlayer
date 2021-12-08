package com.zj.playerLib.extractor.ogg;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.ExtractorsFactory;
import com.zj.playerLib.extractor.PositionHolder;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.util.ParsableByteArray;

import java.io.IOException;

public class OggExtractor implements Extractor {
    public static final ExtractorsFactory FACTORY = () -> {
        return new Extractor[]{new OggExtractor()};
    };
    private static final int MAX_VERIFICATION_BYTES = 8;
    private ExtractorOutput output;
    private StreamReader streamReader;
    private boolean streamReaderInitialized;

    public OggExtractor() {
    }

    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        try {
            return this.sniffInternal(input);
        } catch (ParserException var3) {
            return false;
        }
    }

    public void init(ExtractorOutput output) {
        this.output = output;
    }

    public void seek(long position, long timeUs) {
        if (this.streamReader != null) {
            this.streamReader.seek(position, timeUs);
        }

    }

    public void release() {
    }

    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        if (this.streamReader == null) {
            if (!this.sniffInternal(input)) {
                throw new ParserException("Failed to determine bitstream type");
            }

            input.resetPeekPosition();
        }

        if (!this.streamReaderInitialized) {
            TrackOutput trackOutput = this.output.track(0, 1);
            this.output.endTracks();
            this.streamReader.init(this.output, trackOutput);
            this.streamReaderInitialized = true;
        }

        return this.streamReader.read(input, seekPosition);
    }

    private boolean sniffInternal(ExtractorInput input) throws IOException, InterruptedException {
        OggPageHeader header = new OggPageHeader();
        if (header.populate(input, true) && (header.type & 2) == 2) {
            int length = Math.min(header.bodySize, 8);
            ParsableByteArray scratch = new ParsableByteArray(length);
            input.peekFully(scratch.data, 0, length);
            if (FlacReader.verifyBitstreamType(resetPosition(scratch))) {
                this.streamReader = new FlacReader();
            } else if (VorbisReader.verifyBitstreamType(resetPosition(scratch))) {
                this.streamReader = new VorbisReader();
            } else {
                if (!OpusReader.verifyBitstreamType(resetPosition(scratch))) {
                    return false;
                }

                this.streamReader = new OpusReader();
            }

            return true;
        } else {
            return false;
        }
    }

    private static ParsableByteArray resetPosition(ParsableByteArray scratch) {
        scratch.setPosition(0);
        return scratch;
    }
}
