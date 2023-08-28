package com.zj.playerLib.util;

import android.os.Handler;

import java.util.concurrent.CopyOnWriteArrayList;

public final class EventDispatcher<T> {
    private final CopyOnWriteArrayList<HandlerAndListener<T>> listeners = new CopyOnWriteArrayList<>();

    public EventDispatcher() {
    }

    public void addListener(Handler handler, T eventListener) {
        Assertions.checkArgument(handler != null && eventListener != null);
        this.removeListener(eventListener);
        this.listeners.add(new HandlerAndListener<>(handler, eventListener));
    }

    public void removeListener(T eventListener) {
        for (HandlerAndListener<T> listener : this.listeners) {
            if (listener.listener == eventListener) {
                listener.release();
                this.listeners.remove(listener);
            }
        }

    }

    public void dispatch(Event<T> event) {
        for (HandlerAndListener<T> listener : this.listeners) {
            listener.dispatch(event);
        }
    }

    private static final class HandlerAndListener<T> {
        private final Handler handler;
        private final T listener;
        private boolean released;

        public HandlerAndListener(Handler handler, T eventListener) {
            this.handler = handler;
            this.listener = eventListener;
        }

        public void release() {
            this.released = true;
        }

        public void dispatch(Event<T> event) {
            this.handler.post(() -> {
                if (!this.released) {
                    event.sendTo(this.listener);
                }

            });
        }
    }

    public interface Event<T> {
        void sendTo(T var1);
    }
}
