//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib;

import com.zj.playerLib.PlayerMessage.Target;
import com.zj.playerLib.source.SampleStream;
import com.zj.playerLib.util.MediaClock;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface Renderer extends Target {
    int STATE_DISABLED = 0;
    int STATE_ENABLED = 1;
    int STATE_STARTED = 2;

    int getTrackType();

    RendererCapabilities getCapabilities();

    void setIndex(int var1);

    MediaClock getMediaClock();

    int getState();

    void enable(RendererConfiguration var1, Format[] var2, SampleStream var3, long var4, boolean var6, long var7) throws PlaybackException;

    void start() throws PlaybackException;

    void replaceStream(Format[] var1, SampleStream var2, long var3) throws PlaybackException;

    SampleStream getStream();

    boolean hasReadStreamToEnd();

    void setCurrentStreamFinal();

    boolean isCurrentStreamFinal();

    void maybeThrowStreamError() throws IOException;

    void resetPosition(long var1) throws PlaybackException;

    default void setOperatingRate(float operatingRate) throws PlaybackException {
    }

    void render(long var1, long var3) throws PlaybackException;

    boolean isReady();

    boolean isEnded();

    void stop() throws PlaybackException;

    void disable();

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
    }
}
