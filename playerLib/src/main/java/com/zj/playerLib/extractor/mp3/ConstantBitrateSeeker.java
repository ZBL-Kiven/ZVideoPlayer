//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.mp3;

import com.zj.playerLib.extractor.ConstantBitrateSeekMap;
import com.zj.playerLib.extractor.MpegAudioHeader;
import com.zj.playerLib.extractor.mp3.Mp3Extractor.Seeker;

final class ConstantBitrateSeeker extends ConstantBitrateSeekMap implements Seeker {
    public ConstantBitrateSeeker(long inputLength, long firstFramePosition, MpegAudioHeader mpegAudioHeader) {
        super(inputLength, firstFramePosition, mpegAudioHeader.bitrate, mpegAudioHeader.frameSize);
    }

    public long getTimeUs(long position) {
        return this.getTimeUsAtPosition(position);
    }

    public long getDataEndPosition() {
        return -1L;
    }
}
