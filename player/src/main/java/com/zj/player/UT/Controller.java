package com.zj.player.UT;


import android.content.Context;
import android.view.ViewGroup;

import com.zj.player.ZController;
import com.zj.player.base.InflateInfo;

import org.jetbrains.annotations.Nullable;

/**
 * @author ZJJ on 2020.6.16
 */
public interface Controller {

    void controllerBinder(ZController controller);

    Context getContext();

    InflateInfo getControllerInfo();

    void onLoading(String path, boolean isRegulate);

    void onPrepare(String path, long videoSize, boolean isRegulate);

    void onPlay(String path, boolean isRegulate);

    void onPause(String path, boolean isRegulate);

    void onStop(String path, boolean isRegulate);

    void completing(String path, boolean isRegulate);

    void onCompleted(String path, boolean isRegulate);

    void onSeekChanged(int seek, int buffered, boolean fromUser, long videoSize);

    void onSeekingLoading(String path);

    void onError(@Nullable Exception e);
}
