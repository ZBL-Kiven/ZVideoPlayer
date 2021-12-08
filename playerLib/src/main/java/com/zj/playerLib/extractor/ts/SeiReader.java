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

final class SeiReader {
    private final List<Format> closedCaptionFormats;
    private final TrackOutput[] outputs;

    public SeiReader(List<Format> closedCaptionFormats) {
        this.closedCaptionFormats = closedCaptionFormats;
        this.outputs = new TrackOutput[closedCaptionFormats.size()];
    }

    public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
        for(int i = 0; i < this.outputs.length; ++i) {
            idGenerator.generateNewId();
            TrackOutput output = extractorOutput.track(idGenerator.getTrackId(), 3);
            Format channelFormat = this.closedCaptionFormats.get(i);
            String channelMimeType = channelFormat.sampleMimeType;
            Assertions.checkArgument("application/cea-608".equals(channelMimeType) || "application/cea-708".equals(channelMimeType), "Invalid closed caption mime type provided: " + channelMimeType);
            String formatId = channelFormat.id != null ? channelFormat.id : idGenerator.getFormatId();
            output.format(Format.createTextSampleFormat(formatId, channelMimeType, null, -1, channelFormat.selectionFlags, channelFormat.language, channelFormat.accessibilityChannel, null, Long.MAX_VALUE, channelFormat.initializationData));
            this.outputs[i] = output;
        }

    }

    public void consume(long pesTimeUs, ParsableByteArray seiBuffer) {
        CeaUtil.consume(pesTimeUs, seiBuffer, this.outputs);
    }
}
