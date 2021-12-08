package com.zj.playerLib.text;

import android.graphics.Bitmap;
import android.text.Layout.Alignment;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class Cue {
    public static final float DIMEN_UNSET = 1.4E-45F;
    public static final int TYPE_UNSET = -2147483648;
    public static final int ANCHOR_TYPE_START = 0;
    public static final int ANCHOR_TYPE_MIDDLE = 1;
    public static final int ANCHOR_TYPE_END = 2;
    public static final int LINE_TYPE_FRACTION = 0;
    public static final int LINE_TYPE_NUMBER = 1;
    public static final int TEXT_SIZE_TYPE_FRACTIONAL = 0;
    public static final int TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING = 1;
    public static final int TEXT_SIZE_TYPE_ABSOLUTE = 2;
    public final CharSequence text;
    public final Alignment textAlignment;
    public final Bitmap bitmap;
    public final float line;
    public final int lineType;
    public final int lineAnchor;
    public final float position;
    public final int positionAnchor;
    public final float size;
    public final float bitmapHeight;
    public final boolean windowColorSet;
    public final int windowColor;
    public final int textSizeType;
    public final float textSize;

    public Cue(Bitmap bitmap, float horizontalPosition, int horizontalPositionAnchor, float verticalPosition, int verticalPositionAnchor, float width, float height) {
        this(null, null, bitmap, verticalPosition, 0, verticalPositionAnchor, horizontalPosition, horizontalPositionAnchor, -2147483648, 1.4E-45F, width, height, false, -16777216);
    }

    public Cue(CharSequence text) {
        this(text, null, 1.4E-45F, -2147483648, -2147483648, 1.4E-45F, -2147483648, 1.4E-45F);
    }

    public Cue(CharSequence text, Alignment textAlignment, float line, int lineType, int lineAnchor, float position, int positionAnchor, float size) {
        this(text, textAlignment, line, lineType, lineAnchor, position, positionAnchor, size, false, -16777216);
    }

    public Cue(CharSequence text, Alignment textAlignment, float line, int lineType, int lineAnchor, float position, int positionAnchor, float size, int textSizeType, float textSize) {
        this(text, textAlignment, null, line, lineType, lineAnchor, position, positionAnchor, textSizeType, textSize, size, 1.4E-45F, false, -16777216);
    }

    public Cue(CharSequence text, Alignment textAlignment, float line, int lineType, int lineAnchor, float position, int positionAnchor, float size, boolean windowColorSet, int windowColor) {
        this(text, textAlignment, null, line, lineType, lineAnchor, position, positionAnchor, -2147483648, 1.4E-45F, size, 1.4E-45F, windowColorSet, windowColor);
    }

    private Cue(CharSequence text, Alignment textAlignment, Bitmap bitmap, float line, int lineType, int lineAnchor, float position, int positionAnchor, int textSizeType, float textSize, float size, float bitmapHeight, boolean windowColorSet, int windowColor) {
        this.text = text;
        this.textAlignment = textAlignment;
        this.bitmap = bitmap;
        this.line = line;
        this.lineType = lineType;
        this.lineAnchor = lineAnchor;
        this.position = position;
        this.positionAnchor = positionAnchor;
        this.size = size;
        this.bitmapHeight = bitmapHeight;
        this.windowColorSet = windowColorSet;
        this.windowColor = windowColor;
        this.textSizeType = textSizeType;
        this.textSize = textSize;
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface TextSizeType {
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface LineType {
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface AnchorType {
    }
}
