package com.zj.youtube.constance

class PlayerConstants {

    companion object {
        const val youtubeHost = "https://www.youtube.com"
        const val youtubeIdHost = "https://youtu.be/"
    }

    enum class PlayerState(val level: Int) {

        PLAYING(8), PREPARED(7), LOADING(6), BUFFERING(5), ENDED(3), PAUSED(2), STOP(1), UNKNOWN(0);

        var from: String = ""; private set

        fun setFrom(s: String): PlayerState {
            this.from = s;return this
        }
    }

    enum class PlaybackQuality(val value: String) {
        UNKNOWN("default"), SMALL("small"), MEDIUM("medium"), LARGE("large"), HD720("hd720"), HD1080("hd1080"), HIGH_RES("highres"), DEFAULT("default")
    }

    enum class PlayerError {
        UNKNOWN, INVALID_PARAMETER_IN_REQUEST, HTML_5_PLAYER, VIDEO_NOT_FOUND, VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER
    }

    enum class PlaybackRate(val rate: Float) {
        UNKNOWN(0f), RATE_0_25(0.25f), RATE_0_5(0.5f), RATE_1(1f), RATE_1_5(1.5f), RATE_2(2f)
    }
}