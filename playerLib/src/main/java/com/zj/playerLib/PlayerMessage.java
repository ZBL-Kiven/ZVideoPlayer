//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib;

import android.os.Handler;
import androidx.annotation.Nullable;
import com.zj.playerLib.util.Assertions;

public final class PlayerMessage {
    private final Target target;
    private final Sender sender;
    private final Timeline timeline;
    private int type;
    @Nullable
    private Object payload;
    private Handler handler;
    private int windowIndex;
    private long positionMs;
    private boolean deleteAfterDelivery;
    private boolean isSent;
    private boolean isDelivered;
    private boolean isProcessed;
    private boolean isCanceled;

    public PlayerMessage(Sender sender, Target target, Timeline timeline, int defaultWindowIndex, Handler defaultHandler) {
        this.sender = sender;
        this.target = target;
        this.timeline = timeline;
        this.handler = defaultHandler;
        this.windowIndex = defaultWindowIndex;
        this.positionMs = -9223372036854775807L;
        this.deleteAfterDelivery = true;
    }

    public Timeline getTimeline() {
        return this.timeline;
    }

    public Target getTarget() {
        return this.target;
    }

    public PlayerMessage setType(int messageType) {
        Assertions.checkState(!this.isSent);
        this.type = messageType;
        return this;
    }

    public int getType() {
        return this.type;
    }

    public PlayerMessage setPayload(@Nullable Object payload) {
        Assertions.checkState(!this.isSent);
        this.payload = payload;
        return this;
    }

    @Nullable
    public Object getPayload() {
        return this.payload;
    }

    public PlayerMessage setHandler(Handler handler) {
        Assertions.checkState(!this.isSent);
        this.handler = handler;
        return this;
    }

    public Handler getHandler() {
        return this.handler;
    }

    public long getPositionMs() {
        return this.positionMs;
    }

    public PlayerMessage setPosition(long positionMs) {
        Assertions.checkState(!this.isSent);
        this.positionMs = positionMs;
        return this;
    }

    public PlayerMessage setPosition(int windowIndex, long positionMs) {
        Assertions.checkState(!this.isSent);
        Assertions.checkArgument(positionMs != -9223372036854775807L);
        if (windowIndex >= 0 && (this.timeline.isEmpty() || windowIndex < this.timeline.getWindowCount())) {
            this.windowIndex = windowIndex;
            this.positionMs = positionMs;
            return this;
        } else {
            throw new IllegalSeekPositionException(this.timeline, windowIndex, positionMs);
        }
    }

    public int getWindowIndex() {
        return this.windowIndex;
    }

    public PlayerMessage setDeleteAfterDelivery(boolean deleteAfterDelivery) {
        Assertions.checkState(!this.isSent);
        this.deleteAfterDelivery = deleteAfterDelivery;
        return this;
    }

    public boolean getDeleteAfterDelivery() {
        return this.deleteAfterDelivery;
    }

    public PlayerMessage send() {
        Assertions.checkState(!this.isSent);
        if (this.positionMs == -9223372036854775807L) {
            Assertions.checkArgument(this.deleteAfterDelivery);
        }

        this.isSent = true;
        this.sender.sendMessage(this);
        return this;
    }

    public synchronized PlayerMessage cancel() {
        Assertions.checkState(this.isSent);
        this.isCanceled = true;
        this.markAsProcessed(false);
        return this;
    }

    public synchronized boolean isCanceled() {
        return this.isCanceled;
    }

    public synchronized boolean blockUntilDelivered() throws InterruptedException {
        Assertions.checkState(this.isSent);
        Assertions.checkState(this.handler.getLooper().getThread() != Thread.currentThread());

        while(!this.isProcessed) {
            this.wait();
        }

        return this.isDelivered;
    }

    public synchronized void markAsProcessed(boolean isDelivered) {
        this.isDelivered |= isDelivered;
        this.isProcessed = true;
        this.notifyAll();
    }

    public interface Sender {
        void sendMessage(PlayerMessage var1);
    }

    public interface Target {
        void handleMessage(int var1, @Nullable Object var2) throws PlaybackException;
    }
}
