package com.zj.playerLib.text.webvtt;

import android.text.Layout.Alignment;

import com.zj.playerLib.util.Util;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class WebvttCssStyle {
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
    private String targetId;
    private String targetTag;
    private List<String> targetClasses;
    private String targetVoice;
    private String fontFamily;
    private int fontColor;
    private boolean hasFontColor;
    private int backgroundColor;
    private boolean hasBackgroundColor;
    private int linethrough;
    private int underline;
    private int bold;
    private int italic;
    private int fontSizeUnit;
    private float fontSize;
    private Alignment textAlign;

    public WebvttCssStyle() {
        this.reset();
    }

    public void reset() {
        this.targetId = "";
        this.targetTag = "";
        this.targetClasses = Collections.emptyList();
        this.targetVoice = "";
        this.fontFamily = null;
        this.hasFontColor = false;
        this.hasBackgroundColor = false;
        this.linethrough = -1;
        this.underline = -1;
        this.bold = -1;
        this.italic = -1;
        this.fontSizeUnit = -1;
        this.textAlign = null;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public void setTargetTagName(String targetTag) {
        this.targetTag = targetTag;
    }

    public void setTargetClasses(String[] targetClasses) {
        this.targetClasses = Arrays.asList(targetClasses);
    }

    public void setTargetVoice(String targetVoice) {
        this.targetVoice = targetVoice;
    }

    public int getSpecificityScore(String id, String tag, String[] classes, String voice) {
        if (this.targetId.isEmpty() && this.targetTag.isEmpty() && this.targetClasses.isEmpty() && this.targetVoice.isEmpty()) {
            return tag.isEmpty() ? 1 : 0;
        } else {
            int score = updateScoreForMatch(0, this.targetId, id, 1073741824);
            score = updateScoreForMatch(score, this.targetTag, tag, 2);
            score = updateScoreForMatch(score, this.targetVoice, voice, 4);
            if (score != -1 && Arrays.asList(classes).containsAll(this.targetClasses)) {
                score += this.targetClasses.size() * 4;
                return score;
            } else {
                return 0;
            }
        }
    }

    public int getStyle() {
        return this.bold == -1 && this.italic == -1 ? -1 : (this.bold == 1 ? 1 : 0) | (this.italic == 1 ? 2 : 0);
    }

    public boolean isLinethrough() {
        return this.linethrough == 1;
    }

    public WebvttCssStyle setLinethrough(boolean linethrough) {
        this.linethrough = linethrough ? 1 : 0;
        return this;
    }

    public boolean isUnderline() {
        return this.underline == 1;
    }

    public WebvttCssStyle setUnderline(boolean underline) {
        this.underline = underline ? 1 : 0;
        return this;
    }

    public WebvttCssStyle setBold(boolean bold) {
        this.bold = bold ? 1 : 0;
        return this;
    }

    public WebvttCssStyle setItalic(boolean italic) {
        this.italic = italic ? 1 : 0;
        return this;
    }

    public String getFontFamily() {
        return this.fontFamily;
    }

    public WebvttCssStyle setFontFamily(String fontFamily) {
        this.fontFamily = Util.toLowerInvariant(fontFamily);
        return this;
    }

    public int getFontColor() {
        if (!this.hasFontColor) {
            throw new IllegalStateException("Font color not defined");
        } else {
            return this.fontColor;
        }
    }

    public WebvttCssStyle setFontColor(int color) {
        this.fontColor = color;
        this.hasFontColor = true;
        return this;
    }

    public boolean hasFontColor() {
        return this.hasFontColor;
    }

    public int getBackgroundColor() {
        if (!this.hasBackgroundColor) {
            throw new IllegalStateException("Background color not defined.");
        } else {
            return this.backgroundColor;
        }
    }

    public WebvttCssStyle setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
        this.hasBackgroundColor = true;
        return this;
    }

    public boolean hasBackgroundColor() {
        return this.hasBackgroundColor;
    }

    public Alignment getTextAlign() {
        return this.textAlign;
    }

    public WebvttCssStyle setTextAlign(Alignment textAlign) {
        this.textAlign = textAlign;
        return this;
    }

    public WebvttCssStyle setFontSize(float fontSize) {
        this.fontSize = fontSize;
        return this;
    }

    public WebvttCssStyle setFontSizeUnit(short unit) {
        this.fontSizeUnit = unit;
        return this;
    }

    public int getFontSizeUnit() {
        return this.fontSizeUnit;
    }

    public float getFontSize() {
        return this.fontSize;
    }

    public void cascadeFrom(WebvttCssStyle style) {
        if (style.hasFontColor) {
            this.setFontColor(style.fontColor);
        }

        if (style.bold != -1) {
            this.bold = style.bold;
        }

        if (style.italic != -1) {
            this.italic = style.italic;
        }

        if (style.fontFamily != null) {
            this.fontFamily = style.fontFamily;
        }

        if (this.linethrough == -1) {
            this.linethrough = style.linethrough;
        }

        if (this.underline == -1) {
            this.underline = style.underline;
        }

        if (this.textAlign == null) {
            this.textAlign = style.textAlign;
        }

        if (this.fontSizeUnit == -1) {
            this.fontSizeUnit = style.fontSizeUnit;
            this.fontSize = style.fontSize;
        }

        if (style.hasBackgroundColor) {
            this.setBackgroundColor(style.backgroundColor);
        }

    }

    private static int updateScoreForMatch(int currentScore, String target, String actual, int score) {
        if (!target.isEmpty() && currentScore != -1) {
            return target.equals(actual) ? currentScore + score : -1;
        } else {
            return currentScore;
        }
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
