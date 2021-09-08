//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib;

import com.zj.playerLib.util.Assertions;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class PlaybackException extends Exception {
    public static final int TYPE_SOURCE = 0;
    public static final int TYPE_RENDERER = 1;
    public static final int TYPE_UNEXPECTED = 2;
    public final int type;
    public final int rendererIndex;
    private final Throwable cause;

    public static PlaybackException createForSource(IOException cause) {
        return new PlaybackException(0, cause, -1);
    }

    public static PlaybackException createForRenderer(Exception cause, int rendererIndex) {
        return new PlaybackException(1, cause, rendererIndex);
    }

    static PlaybackException createForUnexpected(RuntimeException cause) {
        return new PlaybackException(2, cause, -1);
    }

    private PlaybackException(int type, Throwable cause, int rendererIndex) {
        super(cause);
        this.type = type;
        this.cause = cause;
        this.rendererIndex = rendererIndex;
    }

    public IOException getSourceException() {
        Assertions.checkState(this.type == 0);
        return (IOException)this.cause;
    }

    public Exception getRendererException() {
        Assertions.checkState(this.type == 1);
        return (Exception)this.cause;
    }

    public RuntimeException getUnexpectedException() {
        Assertions.checkState(this.type == 2);
        return (RuntimeException)this.cause;
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {
    }
}
