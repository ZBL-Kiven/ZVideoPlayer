package com.zj.videotest.videos

object PlayedPathRecorder {

    const val VIDEO_DETACHED = "videoDetached"
    const val VIDEO_ATTACHED = "videoAttached"

    private val recordList = arrayListOf<String>()
    private val passedList = arrayListOf<String>()

    fun record(path: String) {
        recordList.add(path)
    }

    fun isExits(path: String): Boolean {
        return recordList.contains(path)
    }

    fun clear() {
        recordList.clear()
    }

}