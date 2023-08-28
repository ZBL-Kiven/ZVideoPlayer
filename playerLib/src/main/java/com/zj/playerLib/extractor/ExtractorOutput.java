package com.zj.playerLib.extractor;

public interface ExtractorOutput {
    TrackOutput track(int var1, int var2);

    void endTracks();

    void seekMap(SeekMap var1);
}
