//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream;

public interface Allocator {
    Allocation allocate();

    void release(Allocation var1);

    void release(Allocation[] var1);

    void trim();

    int getTotalBytesAllocated();

    int getIndividualAllocationLength();
}
