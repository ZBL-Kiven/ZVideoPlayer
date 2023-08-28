package com.zj.playerLib.upstream;

public interface Allocator {
    Allocation allocate();

    void release(Allocation var1);

    void release(Allocation[] var1);

    void trim();

    int getTotalBytesAllocated();

    int getIndividualAllocationLength();
}
