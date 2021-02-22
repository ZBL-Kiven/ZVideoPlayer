package com.zj.player.ut;


import android.content.Context;

import com.zj.player.z.ZController;
import com.zj.player.base.InflateInfo;

import org.jetbrains.annotations.Nullable;

/**
 * @author ZJJ on 2020.6.16
 */
public interface Controller {

    void onControllerBind(ZController<?, ?> controller);

    Context getContext();

    boolean keepScreenOnWhenPlaying();

    InflateInfo getControllerInfo();

    void onLoading(String path, boolean isRegulate);

    void onPrepare(String path, long videoSize, boolean isRegulate);

    void onPlay(String path, boolean isRegulate);

    void onPause(String path, boolean isRegulate);

    void onStop(String path, boolean isRegulate);

    void onDestroy(String path, boolean isRegulate);

    void completing(String path, boolean isRegulate);

    void onCompleted(String path, boolean isRegulate);

    void onSeekChanged(int seek, int buffered, boolean fromUser, long videoSize);

    void onSeekingLoading(String path);

    void onError(@Nullable Exception e);

    void updateCurPlayerInfo(int volume, float speed);
}
