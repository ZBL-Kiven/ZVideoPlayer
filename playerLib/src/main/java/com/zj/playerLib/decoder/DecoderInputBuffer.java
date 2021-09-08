//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.decoder;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

public class DecoderInputBuffer extends Buffer {
    public static final int BUFFER_REPLACEMENT_MODE_DISABLED = 0;
    public static final int BUFFER_REPLACEMENT_MODE_NORMAL = 1;
    public static final int BUFFER_REPLACEMENT_MODE_DIRECT = 2;
    public final CryptoInfo cryptoInfo = new CryptoInfo();
    public ByteBuffer data;
    public long timeUs;
    private final int bufferReplacementMode;

    public static DecoderInputBuffer newFlagsOnlyInstance() {
        return new DecoderInputBuffer(0);
    }

    public DecoderInputBuffer(int bufferReplacementMode) {
        this.bufferReplacementMode = bufferReplacementMode;
    }

    public void ensureSpaceForWrite(int length) {
        if (this.data == null) {
            this.data = this.createReplacementByteBuffer(length);
        } else {
            int capacity = this.data.capacity();
            int position = this.data.position();
            int requiredCapacity = position + length;
            if (capacity < requiredCapacity) {
                ByteBuffer newData = this.createReplacementByteBuffer(requiredCapacity);
                if (position > 0) {
                    this.data.position(0);
                    this.data.limit(position);
                    newData.put(this.data);
                }

                this.data = newData;
            }
        }
    }

    public final boolean isFlagsOnly() {
        return this.data == null && this.bufferReplacementMode == 0;
    }

    public final boolean isEncrypted() {
        return this.getFlag(1073741824);
    }

    public final void flip() {
        this.data.flip();
    }

    public void clear() {
        super.clear();
        if (this.data != null) {
            this.data.clear();
        }

    }

    private ByteBuffer createReplacementByteBuffer(int requiredCapacity) {
        if (this.bufferReplacementMode == 1) {
            return ByteBuffer.allocate(requiredCapacity);
        } else if (this.bufferReplacementMode == 2) {
            return ByteBuffer.allocateDirect(requiredCapacity);
        } else {
            int currentCapacity = this.data == null ? 0 : this.data.capacity();
            throw new IllegalStateException("Buffer too small (" + currentCapacity + " < " + requiredCapacity + ")");
        }
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface BufferReplacementMode {
    }
}
