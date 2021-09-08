//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.flv;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.util.ParsableByteArray;

abstract class TagPayloadReader {
    protected final TrackOutput output;

    protected TagPayloadReader(TrackOutput output) {
        this.output = output;
    }

    public abstract void seek();

    public final void consume(ParsableByteArray data, long timeUs) throws ParserException {
        if (this.parseHeader(data)) {
            this.parsePayload(data, timeUs);
        }

    }

    protected abstract boolean parseHeader(ParsableByteArray var1) throws ParserException;

    protected abstract void parsePayload(ParsableByteArray var1, long var2) throws ParserException;

    public static final class UnsupportedFormatException extends ParserException {
        public UnsupportedFormatException(String msg) {
            super(msg);
        }
    }
}
