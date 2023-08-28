package com.zj.player.ut;

import android.content.Context;

import androidx.annotation.Nullable;

import com.zj.player.base.BaseRender;

import java.util.List;

/**
 * @author ZJJ on 2020.6.16
 */
public interface PlayerEventController<R extends BaseRender> {

    void onLoading(String path, boolean isRegulate);

    void onPrepare(String path, long videoSize, boolean isRegulate);

    void onPlay(String path, boolean isRegulate);

    void onPause(String path, boolean isRegulate);

    void onStop(boolean notifyStop, String path, boolean isRegulate);

    void completing(String path, boolean isRegulate);

    void onCompleted(String path, boolean isRegulate);

    void onSeekingLoading(String path, boolean isRegulate);

    void onSeekChanged(int seek, long buffered, boolean fromUser, long played, long videoSize);

    @Nullable
    R getPlayerView();

    long getProgressInterval();

    @Nullable
    Context getContext();

    boolean keepScreenOnWhenPlaying();

    void onError(Exception e);

    void onFirstFrameRender();

    void onPlayerInfo(int volume, float speed);

    void onPlayQualityChanged(PlayQualityLevel qualityLevel, @Nullable List<PlayQualityLevel> supportedQualities);

}
