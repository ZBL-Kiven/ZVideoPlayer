package com.zj.playerLib.extractor;

import androidx.annotation.Nullable;
import com.zj.playerLib.metadata.Metadata;
import com.zj.playerLib.metadata.id3.Id3Decoder;
import com.zj.playerLib.metadata.id3.Id3Decoder.FramePredicate;
import com.zj.playerLib.util.ParsableByteArray;
import java.io.EOFException;
import java.io.IOException;

public final class Id3Peeker {
    private final ParsableByteArray scratch = new ParsableByteArray(10);

    public Id3Peeker() {
    }

    @Nullable
    public Metadata peekId3Data(ExtractorInput input, @Nullable FramePredicate id3FramePredicate) throws IOException, InterruptedException {
        int peekedId3Bytes = 0;
        Metadata metadata = null;

        while(true) {
            try {
                input.peekFully(this.scratch.data, 0, 10);
            } catch (EOFException var8) {
                break;
            }

            this.scratch.setPosition(0);
            if (this.scratch.readUnsignedInt24() != Id3Decoder.ID3_TAG) {
                break;
            }

            this.scratch.skipBytes(3);
            int framesLength = this.scratch.readSynchSafeInt();
            int tagLength = 10 + framesLength;
            if (metadata == null) {
                byte[] id3Data = new byte[tagLength];
                System.arraycopy(this.scratch.data, 0, id3Data, 0, 10);
                input.peekFully(id3Data, 10, framesLength);
                metadata = (new Id3Decoder(id3FramePredicate)).decode(id3Data, tagLength);
            } else {
                input.advancePeekPosition(framesLength);
            }

            peekedId3Bytes += tagLength;
        }

        input.resetPeekPosition();
        input.advancePeekPosition(peekedId3Bytes);
        return metadata;
    }
}
