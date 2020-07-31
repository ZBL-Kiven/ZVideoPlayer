package com.zj.player.ut;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static com.zj.player.ut.Constance.SURFACE_TYPE_NONE;
import static com.zj.player.ut.Constance.SURFACE_TYPE_SURFACE_VIEW;
import static com.zj.player.ut.Constance.SURFACE_TYPE_TEXTURE_VIEW;

@Retention(RetentionPolicy.SOURCE)
@IntDef({SURFACE_TYPE_NONE, SURFACE_TYPE_SURFACE_VIEW, SURFACE_TYPE_TEXTURE_VIEW})
public @interface SurfaceType {}