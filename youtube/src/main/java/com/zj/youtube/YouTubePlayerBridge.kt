package com.zj.youtube

import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.annotation.RestrictTo
import android.text.TextUtils
import android.webkit.JavascriptInterface
import com.zj.youtube.constance.PlayerConstants
import com.zj.youtube.proctol.YouTubePlayerListener
import com.zj.youtube.utils.Utils


/**
 * Bridge used for Javascript-Java communication.
 */
@Suppress("unused", "SpellCheckingInspection")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
open class YouTubePlayerBridge(private val youTubePlayerOwner: YouTubePlayerListener) {

    companion object {
        // these constants correspond to the values in the Javascript player
        internal const val STATE_UNSTARTED = "UNSTARTED"
        internal const val STATE_ENDED = "ENDED"
        internal const val STATE_PLAYING = "PLAYING"
        internal const val STATE_PAUSED = "PAUSED"
        internal const val STATE_BUFFERING = "BUFFERING"
        internal const val STATE_CUED = "CUED"

        private const val QUALITY_SMALL = "small"
        private const val QUALITY_MEDIUM = "medium"
        private const val QUALITY_LARGE = "large"
        private const val QUALITY_HD720 = "hd720"
        private const val QUALITY_HD1080 = "hd1080"
        private const val QUALITY_HIGH_RES = "highres"
        private const val QUALITY_DEFAULT = "default"

        private const val RATE_0_25 = "0.25"
        private const val RATE_0_5 = "0.5"
        private const val RATE_1 = "1"
        private const val RATE_1_5 = "1.5"
        private const val RATE_2 = "2"

        private const val ERROR_INVALID_PARAMETER_IN_REQUEST = "2"
        private const val ERROR_HTML_5_PLAYER = "5"
        private const val ERROR_VIDEO_NOT_FOUND = "100"
        private const val ERROR_VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER1 = "101"
        private const val ERROR_VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER2 = "150"
    }

    private val mainThreadHandler: Handler = Handler(Looper.getMainLooper()) {
        if (it.what == 51463) {
            youTubePlayerOwner.onStateChange(it.obj as PlayerConstants.PlayerState)
        }
        return@Handler false
    }

    @JavascriptInterface
    fun sendYouTubeIFrameAPIReady() = mainThreadHandler.post { youTubePlayerOwner.onYouTubeIFrameAPIReady() }

    @JavascriptInterface
    fun sendReady(totalDuration: String) {
        val duration: Long
        try {
            duration = totalDuration.toFloat().toLong()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            return
        }
        mainThreadHandler.post {
            youTubePlayerOwner.onReady(duration)
        }
    }

    @JavascriptInterface
    fun sendPlayerInfo(volume: String, isMute: Boolean, speed: String) {
        val curSpeed: Float
        val curVolome: Int
        try {
            curSpeed = speed.toFloat()
            curVolome = volume.toInt()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            return
        }
        mainThreadHandler.post {
            youTubePlayerOwner.onCurrentPlayerInfo(curVolome, isMute, curSpeed)
        }
    }

    @JavascriptInterface
    fun sendStateChange(state: String) {
        val playerState = parsePlayerState(state)
        mainThreadHandler.removeMessages(51463)
        mainThreadHandler.sendMessage(Message.obtain().apply {
            what = 51463
            obj = playerState
        })
    }

    @JavascriptInterface
    fun onIFrameContent(s: String) {
        Utils.log(s)
    }

    @JavascriptInterface
    fun sendPlaybackQualityChange(quality: String, qualityList: Array<String>?) {
        val lst = if (qualityList != null) hashSetOf(*qualityList) else null
        val playbackQuality = parsePlaybackQuality(quality)
        mainThreadHandler.post {
            youTubePlayerOwner.onPlaybackQualityChange(playbackQuality, lst?.map { parsePlaybackQuality(it) })
        }
    }

    @JavascriptInterface
    fun sendPlaybackRateChange(rate: String) {
        val playbackRate = parsePlaybackRate(rate)
        mainThreadHandler.post {
            youTubePlayerOwner.onPlaybackRateChange(playbackRate)
        }
    }

    @JavascriptInterface
    fun sendError(error: String) {
        val playerError = parsePlayerError(error)
        mainThreadHandler.post {
            youTubePlayerOwner.onError(playerError)
        }
    }

    @JavascriptInterface
    fun sendApiChange() {
        mainThreadHandler.post {
            youTubePlayerOwner.onApiChange()
        }
    }

    @JavascriptInterface
    fun sendVideoCurrentTime(seconds: String) {
        val currentTimeSeconds: Long
        try {
            currentTimeSeconds = seconds.toFloat().toLong()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            return
        }
        mainThreadHandler.post {
            youTubePlayerOwner.onCurrentSecond(currentTimeSeconds)
        }
    }

    @JavascriptInterface
    fun sendVideoDuration(seconds: String) {
        val videoDuration: Long
        try {
            val finalSeconds = if (TextUtils.isEmpty(seconds)) "0" else seconds
            videoDuration = finalSeconds.toFloat().toLong()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            return
        }
        mainThreadHandler.post {
            youTubePlayerOwner.onVideoDuration(videoDuration)
        }
    }

    @JavascriptInterface
    fun sendVideoLoadedFraction(fraction: String) {
        val loadedFraction: Float
        try {
            loadedFraction = fraction.toFloat()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            return
        }

        mainThreadHandler.post {
            youTubePlayerOwner.onVideoLoadedFraction(loadedFraction)
        }
    }

    @JavascriptInterface
    fun sendVideoId(videoUrl: String) {
        mainThreadHandler.post {
            youTubePlayerOwner.onVideoUrl(videoUrl)
        }
    }

    private fun parsePlayerState(state: String): PlayerConstants.PlayerState {
        return when {
            state.equals(STATE_UNSTARTED, ignoreCase = true) -> PlayerConstants.PlayerState.STOP.setFrom(STATE_UNSTARTED)
            state.equals(STATE_ENDED, ignoreCase = true) -> PlayerConstants.PlayerState.ENDED
            state.equals(STATE_PLAYING, ignoreCase = true) -> PlayerConstants.PlayerState.PLAYING
            state.equals(STATE_PAUSED, ignoreCase = true) -> PlayerConstants.PlayerState.PAUSED
            state.equals(STATE_BUFFERING, ignoreCase = true) -> PlayerConstants.PlayerState.BUFFERING
            state.equals(STATE_CUED, ignoreCase = true) -> PlayerConstants.PlayerState.PREPARED
            else -> PlayerConstants.PlayerState.ERROR.setFrom("no state parsed !!")
        }
    }


    private fun parsePlaybackQuality(quality: String): PlayerConstants.PlaybackQuality {
        return when {
            quality.equals(QUALITY_SMALL, ignoreCase = true) -> PlayerConstants.PlaybackQuality.SMALL
            quality.equals(QUALITY_MEDIUM, ignoreCase = true) -> PlayerConstants.PlaybackQuality.MEDIUM
            quality.equals(QUALITY_LARGE, ignoreCase = true) -> PlayerConstants.PlaybackQuality.LARGE
            quality.equals(QUALITY_HD720, ignoreCase = true) -> PlayerConstants.PlaybackQuality.HD720
            quality.equals(QUALITY_HD1080, ignoreCase = true) -> PlayerConstants.PlaybackQuality.HD1080
            quality.equals(QUALITY_HIGH_RES, ignoreCase = true) -> PlayerConstants.PlaybackQuality.HIGH_RES
            quality.equals(QUALITY_DEFAULT, ignoreCase = true) -> PlayerConstants.PlaybackQuality.DEFAULT
            else -> PlayerConstants.PlaybackQuality.UNKNOWN
        }
    }

    private fun parsePlaybackRate(rate: String): PlayerConstants.PlaybackRate {
        return when {
            rate.equals(RATE_0_25, ignoreCase = true) -> PlayerConstants.PlaybackRate.RATE_0_25
            rate.equals(RATE_0_5, ignoreCase = true) -> PlayerConstants.PlaybackRate.RATE_0_5
            rate.equals(RATE_1, ignoreCase = true) -> PlayerConstants.PlaybackRate.RATE_1
            rate.equals(RATE_1_5, ignoreCase = true) -> PlayerConstants.PlaybackRate.RATE_1_5
            rate.equals(RATE_2, ignoreCase = true) -> PlayerConstants.PlaybackRate.RATE_2
            else -> PlayerConstants.PlaybackRate.UNKNOWN
        }
    }

    private fun parsePlayerError(error: String): PlayerConstants.PlayerError {
        return when {
            error.equals(ERROR_INVALID_PARAMETER_IN_REQUEST, ignoreCase = true) -> PlayerConstants.PlayerError.INVALID_PARAMETER_IN_REQUEST
            error.equals(ERROR_HTML_5_PLAYER, ignoreCase = true) -> PlayerConstants.PlayerError.HTML_5_PLAYER
            error.equals(ERROR_VIDEO_NOT_FOUND, ignoreCase = true) -> PlayerConstants.PlayerError.VIDEO_NOT_FOUND
            error.equals(ERROR_VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER1, ignoreCase = true) -> PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER
            error.equals(ERROR_VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER2, ignoreCase = true) -> PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER
            else -> PlayerConstants.PlayerError.UNKNOWN
        }
    }
}
