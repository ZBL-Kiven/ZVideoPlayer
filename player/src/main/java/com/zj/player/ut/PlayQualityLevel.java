package com.zj.player.ut;

import com.zj.player.R;

public enum PlayQualityLevel {
    AUTO(R.id.z_player_quality_view_tv_auto, R.string.z_player_str_quality_auto), SMALL(R.id.z_player_quality_view_tv_240, R.string.z_player_str_quality_small), MEDIUM(R.id.z_player_quality_view_tv_360, R.string.z_player_str_quality_medium), LARGE(R.id.z_player_quality_view_tv_480, R.string.z_player_str_quality_large), M720(R.id.z_player_quality_view_tv_720, R.string.z_player_str_quality_720), H1080(R.id.z_player_quality_view_tv_1080, R.string.z_player_str_quality_1080), BR(R.id.z_player_quality_view_tv_br, R.string.z_player_str_quality_br);

    public int menuId;
    public int textId;

    PlayQualityLevel(int menuId, int textId) {
        this.menuId = menuId;
        this.textId = textId;
    }
}
