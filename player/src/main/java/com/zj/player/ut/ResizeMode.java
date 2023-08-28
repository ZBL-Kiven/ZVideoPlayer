package com.zj.player.ut;

import androidx.annotation.IntDef;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static com.zj.player.ut.Constance.RESIZE_MODE_FILL;
import static com.zj.player.ut.Constance.RESIZE_MODE_FIT;
import static com.zj.player.ut.Constance.RESIZE_MODE_FIXED_HEIGHT;
import static com.zj.player.ut.Constance.RESIZE_MODE_FIXED_WIDTH;
import static com.zj.player.ut.Constance.RESIZE_MODE_ZOOM;


@Documented
@Retention(RetentionPolicy.SOURCE)
@IntDef({RESIZE_MODE_FIT, RESIZE_MODE_FIXED_WIDTH, RESIZE_MODE_FIXED_HEIGHT, RESIZE_MODE_FILL, RESIZE_MODE_ZOOM})
public @interface ResizeMode {}