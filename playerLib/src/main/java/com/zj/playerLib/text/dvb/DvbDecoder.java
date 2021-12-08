package com.zj.playerLib.text.dvb;

import com.zj.playerLib.text.SimpleSubtitleDecoder;
import com.zj.playerLib.util.ParsableByteArray;

import java.util.List;

public final class DvbDecoder extends SimpleSubtitleDecoder {
    private final DvbParser parser;

    public DvbDecoder(List<byte[]> initializationData) {
        super("DvbDecoder");
        ParsableByteArray data = new ParsableByteArray(initializationData.get(0));
        int subtitleCompositionPage = data.readUnsignedShort();
        int subtitleAncillaryPage = data.readUnsignedShort();
        this.parser = new DvbParser(subtitleCompositionPage, subtitleAncillaryPage);
    }

    protected DvbSubtitle decode(byte[] data, int length, boolean reset) {
        if (reset) {
            this.parser.reset();
        }

        return new DvbSubtitle(this.parser.decode(data, length));
    }
}
