package com.zj.playerLib.text;

final class SimpleSubtitleOutputBuffer extends SubtitleOutputBuffer {
    private final SimpleSubtitleDecoder owner;

    public SimpleSubtitleOutputBuffer(SimpleSubtitleDecoder owner) {
        this.owner = owner;
    }

    public final void release() {
        this.owner.releaseOutputBuffer(this);
    }
}
