package com.zj.playerLib;

import com.zj.playerLib.util.Assertions;

public final class PlaybackException extends Exception {
    public static final int TYPE_SOURCE = 0;
    public static final int TYPE_RENDERER = 1;
    public static final int TYPE_UNEXPECTED = 2;
    public final int type;
    public final int rendererIndex;
    private final Throwable cause;

    public static PlaybackException createForSource(Throwable cause) {
        return new PlaybackException(TYPE_SOURCE, cause, -1);
    }

    public static PlaybackException createForRenderer(Exception cause, int rendererIndex) {
        return new PlaybackException(TYPE_RENDERER, cause, rendererIndex);
    }

    static PlaybackException createForUnexpected(RuntimeException cause) {
        return new PlaybackException(TYPE_UNEXPECTED, cause, -1);
    }

    private PlaybackException(int type, Throwable cause, int rendererIndex) {
        super(cause);
        this.type = type;
        this.cause = cause;
        this.rendererIndex = rendererIndex;
    }

    public Throwable getSourceException() {
        Assertions.checkState(this.type == TYPE_SOURCE);
        return this.cause;
    }

    public Exception getRendererException() {
        Assertions.checkState(this.type == TYPE_RENDERER);
        return (Exception) this.cause;
    }

    public RuntimeException getUnexpectedException() {
        Assertions.checkState(this.type == TYPE_UNEXPECTED);
        return (RuntimeException) this.cause;
    }
}
