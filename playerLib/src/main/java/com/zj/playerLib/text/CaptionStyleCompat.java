//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.text;

import android.annotation.TargetApi;
import android.graphics.Typeface;
import android.view.accessibility.CaptioningManager.CaptionStyle;
import com.zj.playerLib.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class CaptionStyleCompat {
    public static final int EDGE_TYPE_NONE = 0;
    public static final int EDGE_TYPE_OUTLINE = 1;
    public static final int EDGE_TYPE_DROP_SHADOW = 2;
    public static final int EDGE_TYPE_RAISED = 3;
    public static final int EDGE_TYPE_DEPRESSED = 4;
    public static final int USE_TRACK_COLOR_SETTINGS = 1;
    public static final CaptionStyleCompat DEFAULT = new CaptionStyleCompat(-1, -16777216, 0, 0, -1, (Typeface)null);
    public final int foregroundColor;
    public final int backgroundColor;
    public final int windowColor;
    public final int edgeType;
    public final int edgeColor;
    public final Typeface typeface;

    @TargetApi(19)
    public static CaptionStyleCompat createFromCaptionStyle(CaptionStyle captionStyle) {
        return Util.SDK_INT >= 21 ? createFromCaptionStyleV21(captionStyle) : createFromCaptionStyleV19(captionStyle);
    }

    public CaptionStyleCompat(int foregroundColor, int backgroundColor, int windowColor, int edgeType, int edgeColor, Typeface typeface) {
        this.foregroundColor = foregroundColor;
        this.backgroundColor = backgroundColor;
        this.windowColor = windowColor;
        this.edgeType = edgeType;
        this.edgeColor = edgeColor;
        this.typeface = typeface;
    }

    @TargetApi(19)
    private static CaptionStyleCompat createFromCaptionStyleV19(CaptionStyle captionStyle) {
        return new CaptionStyleCompat(captionStyle.foregroundColor, captionStyle.backgroundColor, 0, captionStyle.edgeType, captionStyle.edgeColor, captionStyle.getTypeface());
    }

    @TargetApi(21)
    private static CaptionStyleCompat createFromCaptionStyleV21(CaptionStyle captionStyle) {
        return new CaptionStyleCompat(captionStyle.hasForegroundColor() ? captionStyle.foregroundColor : DEFAULT.foregroundColor, captionStyle.hasBackgroundColor() ? captionStyle.backgroundColor : DEFAULT.backgroundColor, captionStyle.hasWindowColor() ? captionStyle.windowColor : DEFAULT.windowColor, captionStyle.hasEdgeType() ? captionStyle.edgeType : DEFAULT.edgeType, captionStyle.hasEdgeColor() ? captionStyle.edgeColor : DEFAULT.edgeColor, captionStyle.getTypeface());
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface EdgeType {
    }
}
