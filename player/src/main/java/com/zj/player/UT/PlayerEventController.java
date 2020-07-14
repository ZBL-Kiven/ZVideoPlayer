package com.zj.player.UT;

import android.content.Context;

import androidx.annotation.Nullable;

import com.zj.player.ZRender;

/**
 * @author ZJJ on 2020.6.16
 */
public interface PlayerEventController {

    void onLoading(String path, boolean isRegulate);

    void onPrepare(String path, long videoSize, boolean isRegulate);

    void onPlay(String path, boolean isRegulate);

    void onPause(String path, boolean isRegulate);

    void onStop(String path, boolean isRegulate);

    void completing(String path, boolean isRegulate);

    void onCompleted(String path, boolean isRegulate);

    void onSeekingLoading(String path, boolean isRegulate);

    void onSeekChanged(int playingPosition, int buffered, boolean fromUser, long videoSize);

    @Nullable
    ZRender getPlayerView();

    long getProgressInterval();

    @Nullable
    Context getContext();

    void onError(Exception e);

    void onLog(String s, String curPath, String accessKey, String modeName);

    void onFirstFrameRender();

    void onPlayerInfo(float volume, float speed);
}
