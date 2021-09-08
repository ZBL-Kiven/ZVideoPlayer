//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.text;

import com.zj.playerLib.decoder.Decoder;

public interface SubtitleDecoder extends Decoder<SubtitleInputBuffer, SubtitleOutputBuffer, SubtitleDecoderException> {
    void setPositionUs(long var1);
}
