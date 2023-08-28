package com.zj.playerLib.upstream;

import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;
import java.util.Arrays;

public final class DefaultAllocator implements Allocator {
    private static final int AVAILABLE_EXTRA_CAPACITY = 100;
    private final boolean trimOnReset;
    private final int individualAllocationSize;
    private final byte[] initialAllocationBlock;
    private final Allocation[] singleAllocationReleaseHolder;
    private int targetBufferSize;
    private int allocatedCount;
    private int availableCount;
    private Allocation[] availableAllocations;

    public DefaultAllocator(boolean trimOnReset, int individualAllocationSize) {
        this(trimOnReset, individualAllocationSize, 0);
    }

    public DefaultAllocator(boolean trimOnReset, int individualAllocationSize, int initialAllocationCount) {
        Assertions.checkArgument(individualAllocationSize > 0);
        Assertions.checkArgument(initialAllocationCount >= 0);
        this.trimOnReset = trimOnReset;
        this.individualAllocationSize = individualAllocationSize;
        this.availableCount = initialAllocationCount;
        this.availableAllocations = new Allocation[initialAllocationCount + 100];
        if (initialAllocationCount > 0) {
            this.initialAllocationBlock = new byte[initialAllocationCount * individualAllocationSize];

            for(int i = 0; i < initialAllocationCount; ++i) {
                int allocationOffset = i * individualAllocationSize;
                this.availableAllocations[i] = new Allocation(this.initialAllocationBlock, allocationOffset);
            }
        } else {
            this.initialAllocationBlock = null;
        }

        this.singleAllocationReleaseHolder = new Allocation[1];
    }

    public synchronized void reset() {
        if (this.trimOnReset) {
            this.setTargetBufferSize(0);
        }

    }

    public synchronized void setTargetBufferSize(int targetBufferSize) {
        boolean targetBufferSizeReduced = targetBufferSize < this.targetBufferSize;
        this.targetBufferSize = targetBufferSize;
        if (targetBufferSizeReduced) {
            this.trim();
        }

    }

    public synchronized Allocation allocate() {
        ++this.allocatedCount;
        Allocation allocation;
        if (this.availableCount > 0) {
            allocation = this.availableAllocations[--this.availableCount];
            this.availableAllocations[this.availableCount] = null;
        } else {
            allocation = new Allocation(new byte[this.individualAllocationSize], 0);
        }

        return allocation;
    }

    public synchronized void release(Allocation allocation) {
        this.singleAllocationReleaseHolder[0] = allocation;
        this.release(this.singleAllocationReleaseHolder);
    }

    public synchronized void release(Allocation[] allocations) {
        if (this.availableCount + allocations.length >= this.availableAllocations.length) {
            this.availableAllocations = Arrays.copyOf(this.availableAllocations, Math.max(this.availableAllocations.length * 2, this.availableCount + allocations.length));
        }

        Allocation[] var2 = allocations;
        int var3 = allocations.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            Allocation allocation = var2[var4];
            this.availableAllocations[this.availableCount++] = allocation;
        }

        this.allocatedCount -= allocations.length;
        this.notifyAll();
    }

    public synchronized void trim() {
        int targetAllocationCount = Util.ceilDivide(this.targetBufferSize, this.individualAllocationSize);
        int targetAvailableCount = Math.max(0, targetAllocationCount - this.allocatedCount);
        if (targetAvailableCount < this.availableCount) {
            if (this.initialAllocationBlock != null) {
                int lowIndex = 0;
                int highIndex = this.availableCount - 1;

                while(lowIndex <= highIndex) {
                    Allocation lowAllocation = this.availableAllocations[lowIndex];
                    if (lowAllocation.data == this.initialAllocationBlock) {
                        ++lowIndex;
                    } else {
                        Allocation highAllocation = this.availableAllocations[highIndex];
                        if (highAllocation.data != this.initialAllocationBlock) {
                            --highIndex;
                        } else {
                            this.availableAllocations[lowIndex++] = highAllocation;
                            this.availableAllocations[highIndex--] = lowAllocation;
                        }
                    }
                }

                targetAvailableCount = Math.max(targetAvailableCount, lowIndex);
                if (targetAvailableCount >= this.availableCount) {
                    return;
                }
            }

            Arrays.fill(this.availableAllocations, targetAvailableCount, this.availableCount, null);
            this.availableCount = targetAvailableCount;
        }
    }

    public synchronized int getTotalBytesAllocated() {
        return this.allocatedCount * this.individualAllocationSize;
    }

    public int getIndividualAllocationLength() {
        return this.individualAllocationSize;
    }
}
