package com.zj.player.ut;

import android.content.Context;

import androidx.annotation.Nullable;

import com.zj.player.ZRender;

import java.util.Map;

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

    void onFirstFrameRender();

    void onPlayerInfo(float volume, float speed);
}
