package com.zj.videotest.ytb

import com.zj.webkit.proctol.WebJavaScriptIn
import com.zj.youtube.YouTubePlayerBridge
import com.zj.youtube.proctol.YouTubePlayerListener

class YtbWebBridge(youTubePlayerOwner: YouTubePlayerListener) : YouTubePlayerBridge(youTubePlayerOwner), WebJavaScriptIn {
    override val name = "YouTubePlayerBridge"
}