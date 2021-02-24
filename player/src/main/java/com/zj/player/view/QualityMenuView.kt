package com.zj.player.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.zj.player.R
import com.zj.player.ut.PlayQualityLevel

@SuppressLint("ViewConstructor")
internal class QualityMenuView @JvmOverloads constructor(context: Context, attr: AttributeSet? = null, def: Int = 0) : LinearLayout(context, attr, def), View.OnClickListener {

    init {
        View.inflate(context, R.layout.z_player_quality_pop_view, this)
        PlayQualityLevel.values().forEach {
            findViewById<View>(it.menuId).setOnClickListener(this)
        }
        visibility = View.GONE
    }

    private var cur: PlayQualityLevel? = null
    private var menus: List<PlayQualityLevel>? = null
    private var onSelected: ((PlayQualityLevel) -> Unit)? = null

    fun setSupportedMenusAndShow(cur: PlayQualityLevel, menus: List<PlayQualityLevel>, onSelected: (PlayQualityLevel) -> Unit) {
        this.cur = cur
        this.menus = menus
        this.onSelected = onSelected
        PlayQualityLevel.values().forEach {
            findViewById<View>(it.menuId).visibility = if (it != cur && menus.contains(it)) View.VISIBLE else View.GONE
        }
        visibility = View.VISIBLE
    }

    override fun onClick(v: View?) {
        val level = PlayQualityLevel.values().first { it.menuId == v?.id }
        onSelected?.invoke(level)
        visibility = View.GONE
    }
}