//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source.chunk;

import java.util.NoSuchElementException;

public abstract class BaseMediaChunkIterator implements MediaChunkIterator {
    private final long fromIndex;
    private final long toIndex;
    private long currentIndex;

    public BaseMediaChunkIterator(long fromIndex, long toIndex) {
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
        this.currentIndex = fromIndex - 1L;
    }

    public boolean isEnded() {
        return this.currentIndex > this.toIndex;
    }

    public boolean next() {
        ++this.currentIndex;
        return !this.isEnded();
    }

    protected void checkInBounds() {
        if (this.currentIndex < this.fromIndex || this.currentIndex > this.toIndex) {
            throw new NoSuchElementException();
        }
    }

    protected long getCurrentIndex() {
        return this.currentIndex;
    }
}
