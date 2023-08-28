package com.zj.player.ut

import com.zj.player.logs.ZPlayerLogs

/**
 * @author ZJJ on 2020.6.16
 */
object Constance {

    internal var CORE_LOG_ABLE = false

    const val SURFACE_TYPE_NONE = 0
    const val SURFACE_TYPE_SURFACE_VIEW = 1
    const val SURFACE_TYPE_TEXTURE_VIEW = 2

    /**
     * Either the width or height is decreased to obtain the desired aspect ratio.
     */
    const val RESIZE_MODE_FIT = 0

    /**
     * The width is fixed and the height is increased or decreased to obtain the desired aspect ratio.
     */
    const val RESIZE_MODE_FIXED_WIDTH = 1

    /**
     * The height is fixed and the width is increased or decreased to obtain the desired aspect ratio.
     */
    const val RESIZE_MODE_FIXED_HEIGHT = 2

    /**
     * The specified aspect ratio is ignored.
     */
    const val RESIZE_MODE_FILL = 3

    /**
     * Either the width or height is increased to obtain the desired aspect ratio.
     */
    const val RESIZE_MODE_ZOOM = 4
    const val ANIMATE_DURATION = 200L

    /**
     * ------- set the global default controller visibility --------
     *
     * Its priority is lower than XML Properties. When XML does not set custom properties,
     * it works normally and is attached to any BaseVideo Controller as default values.
     * */
    internal const val defaultControllerVisibility = 1
    internal const val playIconEnable = 2
    internal const val muteIconEnable = 2
    internal const val qualityEnable = 1
    internal const val speedIconEnable = 2
    internal const val fullScreenEnAble = 2
    internal const val lockRotationEnable = 1
    internal const val secondarySeekBarEnable = 1
    internal const val isDefaultMaxScreen = false
    internal const val isAllowReversePortrait = false
    internal const val fullMaxScreenEnable = true

    /**
     * Setting the following properties only supports the name reflect method, because I am lazy.
     * @param value see [VideoDefaultPropertiesMode]
     * */
    @JvmStatic
    @Suppress("unused")
    fun setDefaultProperties(name: String, value: VideoDefaultPropertiesMode): Boolean {
        return try {
            val field = Constance::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.set(this, value.code)
            true
        } catch (e: Exception) {
            ZPlayerLogs.onError(e)
            false
        }
    }

    /**
     * [DISPLAY_VISIBLE] means it will be instanced when create , and it is VISIBLE to user in default.
     * [DISPLAY_GONE] means it will be instanced when create , and it is GONE to user in default.
     * [GONE] it`ll never been instanced.
     * [TRUE] ,[FALSE] only can set the BOOLEAN fields.
     * */
    enum class VideoDefaultPropertiesMode(internal val code: Any) {
        DISPLAY_VISIBLE(2), DISPLAY_GONE(1), GONE(0), TRUE(true), FALSE(false)
    }
}