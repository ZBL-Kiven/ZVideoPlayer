package com.zj.videotest.feed.bean;

import com.zj.videotest.feed.data.DataType;
import com.zj.videotest.feed.data.FeedDataIn;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VideoSource implements FeedDataIn {

    private String sourceId;

    /**
     * 视频标题
     */
    private String videoTitle;

    /***
     *
     * 视频远程仓库地址
     *
     * */
    private String videoRemoteStorageUrl;

    /**
     * 预览图远程仓库地址
     */
    private String imgPreviewRemoteStorageUrl;

    /**
     * 下载地址
     */
    private String downloadVideoUrl;

    /**
     * 时长
     */
    private Double duration;


    /**
     * bitRate
     **/
    private Long bitRate;

    /**
     * width
     */
    private Integer width;

    /**
     * height
     */
    private Integer height;

    /**
     * size
     */
    private Long size;

    /**
     * 创建日期
     */
    private String createTime;

    /**
     * data 类型 ，video & img
     */
    private String type;


    /*************************上传者信息***********************/

    /**
     * user open id
     */
    private String userOpenId;

    /**
     * code 号
     **/
    private String authorClapCode;

    /**
     * 名字
     */
    private String authorName;

    /**
     * 头像
     */
    private String authorAvatar;


    /************************************互动信息***************************************/
    /**
     * 点赞数量
     */
    private Long clapNum;

    /**
     * 收藏数量
     */
    private Long favoriteNum;

    /**
     * 转发数量
     */
    private Long shareNum;


    /**
     * 评论数量
     */
    private Long commentsNum;

    /**
     * 播放数量
     */
    private Long viewNum;

    /**
     * 完播数量
     */
    private Long fullViewNum;


    /****************************价值相关字段*********************************/
    /**
     * 获得金币总数
     */
    private Long coinValue;

    /**
     * 是否达到上限制
     */
    private Boolean isMaxCoins;


    /***************************视频动态**********************************************/
    private List<MomentUser> clapMoment;

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
        return clapNum.intValue();
    }

    @Override
    public int getViewWidth() {
        return width;
    }

    @Override
    public int getViewHeight() {
        return height;
    }

    @NotNull
    @Override
    public DataType getType() {
        if (type.equalsIgnoreCase("video")) {
            return DataType.VIDEO;
        } else if (type.equalsIgnoreCase("img")) {
            return DataType.IMG;
        }
        return DataType.IMG;
    }
}

