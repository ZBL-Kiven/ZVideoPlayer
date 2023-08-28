package com.zj.playerLib.util;

import androidx.annotation.Nullable;

import java.util.Arrays;

public final class TimedValueQueue {
    private long[] timestamps;
    private Object[] values;
    private int first;
    private int size;

    public TimedValueQueue() {
        this(10);
    }

    public TimedValueQueue(int initialBufferSize) {
        this.timestamps = new long[initialBufferSize];
        this.values = newArray(initialBufferSize);
    }

    public synchronized void add(long timestamp, Object value) {
        this.clearBufferOnTimeDiscontinuity(timestamp);
        this.doubleCapacityIfFull();
        this.addUnchecked(timestamp, value);
    }

    public synchronized void clear() {
        this.first = 0;
        this.size = 0;
        Arrays.fill(this.values, null);
    }

    public synchronized int size() {
        return this.size;
    }

    @Nullable
    public synchronized Object pollFloor(long timestamp) {
        return this.poll(timestamp, true);
    }

    @Nullable
    public synchronized Object poll(long timestamp) {
        return this.poll(timestamp, false);
    }

    @Nullable
    private Object poll(long timestamp, boolean onlyOlder) {
        Object value = null;

        for (long previousTimeDiff = Long.MAX_VALUE; this.size > 0; --this.size) {
            long timeDiff = timestamp - this.timestamps[this.first];
            if (timeDiff < 0L && (onlyOlder || -timeDiff >= previousTimeDiff)) {
                break;
            }

            previousTimeDiff = timeDiff;
            value = this.values[this.first];
            this.values[this.first] = null;
            this.first = (this.first + 1) % this.values.length;
        }

        return value;
    }

    private void clearBufferOnTimeDiscontinuity(long timestamp) {
        if (this.size > 0) {
            int last = (this.first + this.size - 1) % this.values.length;
            if (timestamp <= this.timestamps[last]) {
                this.clear();
            }
        }

    }

    private void doubleCapacityIfFull() {
        int capacity = this.values.length;
        if (this.size >= capacity) {
            int newCapacity = capacity * 2;
            long[] newTimestamps = new long[newCapacity];
            Object[] newObjectalues = newArray(newCapacity);
            int length = capacity - this.first;
            System.arraycopy(this.timestamps, this.first, newTimestamps, 0, length);
            System.arraycopy(this.values, this.first, newObjectalues, 0, length);
            if (this.first > 0) {
                System.arraycopy(this.timestamps, 0, newTimestamps, length, this.first);
                System.arraycopy(this.values, 0, newObjectalues, length, this.first);
            }

            this.timestamps = newTimestamps;
            this.values = newObjectalues;
            this.first = 0;
        }
    }

    private void addUnchecked(long timestamp, Object value) {
        int next = (this.first + this.size) % this.values.length;
        this.timestamps[next] = timestamp;
        this.values[next] = value;
        ++this.size;
    }

    private static Object[] newArray(int length) {
        return new Object[length];
    }
}
