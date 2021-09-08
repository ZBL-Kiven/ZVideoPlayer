//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.ts;

import com.zj.playerLib.Format;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.zj.playerLib.text.cea.CeaUtil;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.ParsableByteArray;

import java.util.List;

final class UserDataReader {
    private static final int USER_DATA_START_CODE = 434;
    private final List<Format> closedCaptionFormats;
    private final TrackOutput[] outputs;

    public UserDataReader(List<Format> closedCaptionFormats) {
        this.closedCaptionFormats = closedCaptionFormats;
        this.outputs = new TrackOutput[closedCaptionFormats.size()];
    }

    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
        for(int i = 0; i < this.outputs.length; ++i) {
            idGenerator.generateNewId();
            TrackOutput output = extractorOutput.track(idGenerator.getTrackId(), 3);
            Format channelFormat = (Format)this.closedCaptionFormats.get(i);
            String channelMimeType = channelFormat.sampleMimeType;
            Assertions.checkArgument("application/cea-608".equals(channelMimeType) || "application/cea-708".equals(channelMimeType), "Invalid closed caption mime type provided: " + channelMimeType);
            output.format(Format.createTextSampleFormat(idGenerator.getFormatId(), channelMimeType, (String)null, -1, channelFormat.selectionFlags, channelFormat.language, channelFormat.accessibilityChannel, (DrmInitData)null, 9223372036854775807L, channelFormat.initializationData));
            this.outputs[i] = output;
        }

    }

    public void consume(long pesTimeUs, ParsableByteArray userDataPayload) {
        if (userDataPayload.bytesLeft() >= 9) {
            int userDataStartCode = userDataPayload.readInt();
            int userDataIdentifier = userDataPayload.readInt();
            int userDataTypeCode = userDataPayload.readUnsignedByte();
            if (userDataStartCode == 434 && userDataIdentifier == CeaUtil.USER_DATA_IDENTIFIER_GA94 && userDataTypeCode == 3) {
                CeaUtil.consumeCcData(pesTimeUs, userDataPayload, this.outputs);
            }

        }
    }
}
