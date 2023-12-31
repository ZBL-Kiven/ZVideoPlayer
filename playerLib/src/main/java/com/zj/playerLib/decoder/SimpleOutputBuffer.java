package com.zj.playerLib.decoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SimpleOutputBuffer extends OutputBuffer {
    private final SimpleDecoder<?, SimpleOutputBuffer, ?> owner;
    public ByteBuffer data;

    public SimpleOutputBuffer(SimpleDecoder<?, SimpleOutputBuffer, ?> owner) {
        this.owner = owner;
    }

    public ByteBuffer init(long timeUs, int size) {
        this.timeUs = timeUs;
        if (this.data == null || this.data.capacity() < size) {
            this.data = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        }

        this.data.position(0);
        this.data.limit(size);
        return this.data;
    }

    public void clear() {
        super.clear();
        if (this.data != null) {
            this.data.clear();
        }

    }

    public void release() {
        this.owner.releaseOutputBuffer(this);
    }
}
