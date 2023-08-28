package com.zj.videotest.feed.bean;

import android.content.res.Resources;

import com.zj.videotest.feed.data.DataType;
import com.zj.videotest.feed.data.FeedDataIn;
import com.zj.views.ut.DPUtils;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class VideoSource implements FeedDataIn {

    public boolean picClicked = false;

    public boolean clapped = false;

    public int shareCount = 0;

    public VideoSource(String imgPath, DataType dataType, String videoPath) {
        imgPreviewRemoteStorageUrl = imgPath;
        videoRemoteStorageUrl = videoPath;
        type = dataType;
    }

    public String sourceId = UUID.randomUUID().toString();

    /**
     * 视频标题
     */
    public String videoTitle = "it`s a simple desc for mock impl";

    /***
     *
     * 视频远程仓库地址
     *
     * */
    public String videoRemoteStorageUrl;

    /**
     * 预览图远程仓库地址
     */
    public String imgPreviewRemoteStorageUrl;

    /**
     * data 类型 ，video & img
     */
    public DataType type;

    /**
     * 名字
     */
    public String authorName = "user-01";

    /**
     * 头像
     */
    public String authorAvatar = "https://encrypted-tbn0.gstatic.com/images?q=tbn%3AANd9GcRc96kcLicYy25CFi7P_ocMargwSC_vjRxIMg&usqp=CAU";


    @NotNull
    @Override
    public String getAvatarPath() {
        return authorAvatar;
    }

    @NotNull
    @Override
    public String getNickname() {
        return authorName;
    }

    @NotNull
    @Override
    public String getDesc() {
        return videoTitle;
    }

    @NotNull
    @Override
    public String getImagePath() {
        return imgPreviewRemoteStorageUrl;
    }

    @NotNull
    @Override
    public String getVideoPath() {
        return videoRemoteStorageUrl;
    }

    @NotNull
    @Override
    public String getSourceId() {
        return sourceId;
    }

    @Override
    public int getClapsCount() {
        return 9986;
    }

    @Override
    public int getViewWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    @Override
    public int getViewHeight() {
        return DPUtils.dp2px(252f);
    }

    @NotNull
    @Override
    public DataType getType() {
        return type;
    }
}

