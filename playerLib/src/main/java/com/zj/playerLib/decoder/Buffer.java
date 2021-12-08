package com.zj.playerLib.decoder;

public abstract class Buffer {
    private int flags;

    public Buffer() {
    }

    public void clear() {
        this.flags = 0;
    }

    public final boolean isDecodeOnly() {
        return this.getFlag(-2147483648);
    }

    public final boolean isEndOfStream() {
        return this.getFlag(4);
    }

    public final boolean isKeyFrame() {
        return this.getFlag(1);
    }

    public final void setFlags(int flags) {
        this.flags = flags;
    }

    public final void addFlag(int flag) {
        this.flags |= flag;
    }

    public final void clearFlag(int flag) {
        this.flags &= ~flag;
    }

    protected final boolean getFlag(int flag) {
        return (this.flags & flag) == flag;
    }
}
