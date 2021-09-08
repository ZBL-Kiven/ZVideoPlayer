//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.util;

import java.util.Arrays;

public final class LongArray {
    private static final int DEFAULT_INITIAL_CAPACITY = 32;
    private int size;
    private long[] values;

    public LongArray() {
        this(32);
    }

    public LongArray(int initialCapacity) {
        this.values = new long[initialCapacity];
    }

    public void add(long value) {
        if (this.size == this.values.length) {
            this.values = Arrays.copyOf(this.values, this.size * 2);
        }

        this.values[this.size++] = value;
    }

    public long get(int index) {
        if (index >= 0 && index < this.size) {
            return this.values[index];
        } else {
            throw new IndexOutOfBoundsException("Invalid index " + index + ", size is " + this.size);
        }
    }

    public int size() {
        return this.size;
    }

    public long[] toArray() {
        return Arrays.copyOf(this.values, this.size);
    }
}
