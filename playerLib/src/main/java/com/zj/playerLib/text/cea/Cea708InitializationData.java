package com.zj.playerLib.text.cea;

import java.util.Collections;
import java.util.List;

public final class Cea708InitializationData {
    public final boolean isWideAspectRatio;

    private Cea708InitializationData(List<byte[]> initializationData) {
        this.isWideAspectRatio = ((byte[])initializationData.get(0))[0] != 0;
    }

    public static Cea708InitializationData fromData(List<byte[]> initializationData) {
        return new Cea708InitializationData(initializationData);
    }

    public static List<byte[]> buildData(boolean isWideAspectRatio) {
        return Collections.singletonList(new byte[]{(byte)(isWideAspectRatio ? 1 : 0)});
    }
}
