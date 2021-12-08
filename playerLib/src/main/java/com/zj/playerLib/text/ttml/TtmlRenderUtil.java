package com.zj.playerLib.text.ttml;

import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.text.style.AlignmentSpan.Standard;
import java.util.Map;

final class TtmlRenderUtil {
    public static TtmlStyle resolveStyle(TtmlStyle style, String[] styleIds, Map<String, TtmlStyle> globalStyles) {
        if (style == null && styleIds == null) {
            return null;
        } else if (style == null && styleIds.length == 1) {
            return globalStyles.get(styleIds[0]);
        } else {
            int var5;
            if (style == null && styleIds.length > 1) {
                TtmlStyle chainedStyle = new TtmlStyle();
                String[] var9 = styleIds;
                var5 = styleIds.length;

                for(int var10 = 0; var10 < var5; ++var10) {
                    String id = var9[var10];
                    chainedStyle.chain(globalStyles.get(id));
                }

                return chainedStyle;
            } else if (style != null && styleIds != null && styleIds.length == 1) {
                return style.chain(globalStyles.get(styleIds[0]));
            } else if (style != null && styleIds != null && styleIds.length > 1) {
                String[] var3 = styleIds;
                int var4 = styleIds.length;

                for(var5 = 0; var5 < var4; ++var5) {
                    String id = var3[var5];
                    style.chain(globalStyles.get(id));
                }

                return style;
            } else {
                return style;
            }
        }
    }

    public static void applyStylesToSpan(SpannableStringBuilder builder, int start, int end, TtmlStyle style) {
        if (style.getStyle() != -1) {
            builder.setSpan(new StyleSpan(style.getStyle()), start, end, 33);
        }

        if (style.isLinethrough()) {
            builder.setSpan(new StrikethroughSpan(), start, end, 33);
        }

        if (style.isUnderline()) {
            builder.setSpan(new UnderlineSpan(), start, end, 33);
        }

        if (style.hasFontColor()) {
            builder.setSpan(new ForegroundColorSpan(style.getFontColor()), start, end, 33);
        }

        if (style.hasBackgroundColor()) {
            builder.setSpan(new BackgroundColorSpan(style.getBackgroundColor()), start, end, 33);
        }

        if (style.getFontFamily() != null) {
            builder.setSpan(new TypefaceSpan(style.getFontFamily()), start, end, 33);
        }

        if (style.getTextAlign() != null) {
            builder.setSpan(new Standard(style.getTextAlign()), start, end, 33);
        }

        switch(style.getFontSizeUnit()) {
        case -1:
        case 0:
        default:
            break;
        case 1:
            builder.setSpan(new AbsoluteSizeSpan((int)style.getFontSize(), true), start, end, 33);
            break;
        case 2:
            builder.setSpan(new RelativeSizeSpan(style.getFontSize()), start, end, 33);
            break;
        case 3:
            builder.setSpan(new RelativeSizeSpan(style.getFontSize() / 100.0F), start, end, 33);
        }

    }

    static void endParagraph(SpannableStringBuilder builder) {
        int position;
        for(position = builder.length() - 1; position >= 0 && builder.charAt(position) == ' '; --position) {
        }

        if (position >= 0 && builder.charAt(position) != '\n') {
            builder.append('\n');
        }

    }

    static String applyTextElementSpacePolicy(String in) {
        String out = in.replaceAll("\r\n", "\n");
        out = out.replaceAll(" *\n *", "\n");
        out = out.replaceAll("\n", " ");
        out = out.replaceAll("[ \t\\x0B\f\r]+", " ");
        return out;
    }

    private TtmlRenderUtil() {
    }
}
