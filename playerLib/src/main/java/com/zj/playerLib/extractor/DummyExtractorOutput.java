//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor;

public final class DummyExtractorOutput implements ExtractorOutput {
    public DummyExtractorOutput() {
    }

    public TrackOutput track(int id, int type) {
        return new DummyTrackOutput();
    }

    public void endTracks() {
    }

    public void seekMap(SeekMap seekMap) {
    }
}
