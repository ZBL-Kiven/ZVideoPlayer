package com.zj.playerLib.util;

import static android.os.SystemClock.elapsedRealtime;

public final class ConditionVariable {
    private boolean isOpen;

    public ConditionVariable() {
    }

    public synchronized boolean open() {
        if (this.isOpen) {
            return false;
        } else {
            this.isOpen = true;
            this.notifyAll();
            return true;
        }
    }

    public synchronized boolean close() {
        boolean wasOpen = this.isOpen;
        this.isOpen = false;
        return wasOpen;
    }

    public synchronized void block() throws InterruptedException {
        while(!this.isOpen) {
            this.wait();
        }

    }

    public synchronized boolean block(long timeout) throws InterruptedException {
        long now = elapsedRealtime();

        for(long end = now + timeout; !this.isOpen && now < end; now = elapsedRealtime()) {
            this.wait(end - now);
        }

        return this.isOpen;
    }
}
