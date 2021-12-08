package com.zj.playerLib.text.ttml;

import android.text.Layout.Alignment;

import com.zj.playerLib.util.Assertions;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

final class TtmlStyle {
    public static final int UNSPECIFIED = -1;
    public static final int STYLE_NORMAL = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_BOLD_ITALIC = 3;
    public static final int FONT_SIZE_UNIT_PIXEL = 1;
    public static final int FONT_SIZE_UNIT_EM = 2;
    public static final int FONT_SIZE_UNIT_PERCENT = 3;
    private static final int OFF = 0;
    private static final int ON = 1;
    private String fontFamily;
    private int fontColor;
    private boolean hasFontColor;
    private int backgroundColor;
    private boolean hasBackgroundColor;
    private int linethrough = -1;
    private int underline = -1;
    private int bold = -1;
    private int italic = -1;
    private int fontSizeUnit = -1;
    private float fontSize;
    private String id;
    private TtmlStyle inheritableStyle;
    private Alignment textAlign;

    public TtmlStyle() {
    }

    public int getStyle() {
        return this.bold == -1 && this.italic == -1 ? -1 : (this.bold == 1 ? 1 : 0) | (this.italic == 1 ? 2 : 0);
    }

    public boolean isLinethrough() {
        return this.linethrough == 1;
    }

    public TtmlStyle setLinethrough(boolean linethrough) {
        Assertions.checkState(this.inheritableStyle == null);
        this.linethrough = linethrough ? 1 : 0;
        return this;
    }

    public boolean isUnderline() {
        return this.underline == 1;
    }

    public TtmlStyle setUnderline(boolean underline) {
        Assertions.checkState(this.inheritableStyle == null);
        this.underline = underline ? 1 : 0;
        return this;
    }

    public TtmlStyle setBold(boolean bold) {
        Assertions.checkState(this.inheritableStyle == null);
        this.bold = bold ? 1 : 0;
        return this;
    }

    public TtmlStyle setItalic(boolean italic) {
        Assertions.checkState(this.inheritableStyle == null);
        this.italic = italic ? 1 : 0;
        return this;
    }

    public String getFontFamily() {
        return this.fontFamily;
    }

    public TtmlStyle setFontFamily(String fontFamily) {
        Assertions.checkState(this.inheritableStyle == null);
        this.fontFamily = fontFamily;
        return this;
    }

    public int getFontColor() {
        if (!this.hasFontColor) {
            throw new IllegalStateException("Font color has not been defined.");
        } else {
            return this.fontColor;
        }
    }

    public TtmlStyle setFontColor(int fontColor) {
        Assertions.checkState(this.inheritableStyle == null);
        this.fontColor = fontColor;
        this.hasFontColor = true;
        return this;
    }

    public boolean hasFontColor() {
        return this.hasFontColor;
    }

    public int getBackgroundColor() {
        if (!this.hasBackgroundColor) {
            throw new IllegalStateException("Background color has not been defined.");
        } else {
            return this.backgroundColor;
        }
    }

    public TtmlStyle setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
        this.hasBackgroundColor = true;
        return this;
    }

    public boolean hasBackgroundColor() {
        return this.hasBackgroundColor;
    }

    public TtmlStyle inherit(TtmlStyle ancestor) {
        return this.inherit(ancestor, false);
    }

    public TtmlStyle chain(TtmlStyle ancestor) {
        return this.inherit(ancestor, true);
    }

    private TtmlStyle inherit(TtmlStyle ancestor, boolean chaining) {
        if (ancestor != null) {
            if (!this.hasFontColor && ancestor.hasFontColor) {
                this.setFontColor(ancestor.fontColor);
            }

            if (this.bold == -1) {
                this.bold = ancestor.bold;
            }

            if (this.italic == -1) {
                this.italic = ancestor.italic;
            }

            if (this.fontFamily == null) {
                this.fontFamily = ancestor.fontFamily;
            }

            if (this.linethrough == -1) {
                this.linethrough = ancestor.linethrough;
            }

            if (this.underline == -1) {
                this.underline = ancestor.underline;
            }

            if (this.textAlign == null) {
                this.textAlign = ancestor.textAlign;
            }

            if (this.fontSizeUnit == -1) {
                this.fontSizeUnit = ancestor.fontSizeUnit;
                this.fontSize = ancestor.fontSize;
            }

            if (chaining && !this.hasBackgroundColor && ancestor.hasBackgroundColor) {
                this.setBackgroundColor(ancestor.backgroundColor);
            }
        }

        return this;
    }

    public TtmlStyle setId(String id) {
        this.id = id;
        return this;
    }

    public String getId() {
        return this.id;
    }

    public Alignment getTextAlign() {
        return this.textAlign;
    }

    public TtmlStyle setTextAlign(Alignment textAlign) {
        this.textAlign = textAlign;
        return this;
    }

    public TtmlStyle setFontSize(float fontSize) {
        this.fontSize = fontSize;
        return this;
    }

    public TtmlStyle setFontSizeUnit(int fontSizeUnit) {
        this.fontSizeUnit = fontSizeUnit;
        return this;
    }

    public int getFontSizeUnit() {
        return this.fontSizeUnit;
    }

    public float getFontSize() {
        return this.fontSize;
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface FontSizeUnit {
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface StyleFlags {
    }
}
