//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.text.webvtt;

import android.text.Layout.Alignment;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan.Standard;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;

import androidx.annotation.NonNull;

import com.zj.playerLib.text.webvtt.WebvttCue.Builder;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WebvttCueParser {
    public static final Pattern CUE_HEADER_PATTERN = Pattern.compile("^(\\S+)\\s+-->\\s+(\\S+)(.*)?$");
    private static final Pattern CUE_SETTING_PATTERN = Pattern.compile("(\\S+?):(\\S+)");
    private static final char CHAR_LESS_THAN = '<';
    private static final char CHAR_GREATER_THAN = '>';
    private static final char CHAR_SLASH = '/';
    private static final char CHAR_AMPERSAND = '&';
    private static final char CHAR_SEMI_COLON = ';';
    private static final char CHAR_SPACE = ' ';
    private static final String ENTITY_LESS_THAN = "lt";
    private static final String ENTITY_GREATER_THAN = "gt";
    private static final String ENTITY_AMPERSAND = "amp";
    private static final String ENTITY_NON_BREAK_SPACE = "nbsp";
    private static final String TAG_BOLD = "b";
    private static final String TAG_ITALIC = "i";
    private static final String TAG_UNDERLINE = "u";
    private static final String TAG_CLASS = "c";
    private static final String TAG_VOICE = "v";
    private static final String TAG_LANG = "lang";
    private static final int STYLE_BOLD = 1;
    private static final int STYLE_ITALIC = 2;
    private static final String TAG = "WebvttCueParser";
    private final StringBuilder textBuilder = new StringBuilder();

    public WebvttCueParser() {
    }

    public boolean parseCue(ParsableByteArray webvttData, Builder builder, List<WebvttCssStyle> styles) {
        String firstLine = webvttData.readLine();
        if (firstLine == null) {
            return false;
        } else {
            Matcher cueHeaderMatcher = CUE_HEADER_PATTERN.matcher(firstLine);
            if (cueHeaderMatcher.matches()) {
                return parseCue((String)null, cueHeaderMatcher, webvttData, builder, this.textBuilder, styles);
            } else {
                String secondLine = webvttData.readLine();
                if (secondLine == null) {
                    return false;
                } else {
                    cueHeaderMatcher = CUE_HEADER_PATTERN.matcher(secondLine);
                    return cueHeaderMatcher.matches() ? parseCue(firstLine.trim(), cueHeaderMatcher, webvttData, builder, this.textBuilder, styles) : false;
                }
            }
        }
    }

    static void parseCueSettingsList(String cueSettingsList, Builder builder) {
        Matcher cueSettingMatcher = CUE_SETTING_PATTERN.matcher(cueSettingsList);

        while(cueSettingMatcher.find()) {
            String name = cueSettingMatcher.group(1);
            String value = cueSettingMatcher.group(2);

            try {
                if ("line".equals(name)) {
                    parseLineAttribute(value, builder);
                } else if ("align".equals(name)) {
                    builder.setTextAlignment(parseTextAlignment(value));
                } else if ("position".equals(name)) {
                    parsePositionAttribute(value, builder);
                } else if ("size".equals(name)) {
                    builder.setWidth(WebvttParserUtil.parsePercentage(value));
                } else {
                    Log.w("WebvttCueParser", "Unknown cue setting " + name + ":" + value);
                }
            } catch (NumberFormatException var6) {
                Log.w("WebvttCueParser", "Skipping bad cue setting: " + cueSettingMatcher.group());
            }
        }

    }

    static void parseCueText(String id, String markup, Builder builder, List<WebvttCssStyle> styles) {
        SpannableStringBuilder spannedText = new SpannableStringBuilder();
        ArrayDeque<StartTag> startTagStack = new ArrayDeque();
        List<StyleMatch> scratchStyleMatches = new ArrayList();
        int pos = 0;

        while(true) {
            while(true) {
                while(pos < markup.length()) {
                    char curr = markup.charAt(pos);
                    switch(curr) {
                    case '&':
                        int semiColonEndIndex = markup.indexOf(59, pos + 1);
                        int spaceEndIndex = markup.indexOf(32, pos + 1);
                        int entityEndIndex = semiColonEndIndex == -1 ? spaceEndIndex : (spaceEndIndex == -1 ? semiColonEndIndex : Math.min(semiColonEndIndex, spaceEndIndex));
                        if (entityEndIndex != -1) {
                            applyEntity(markup.substring(pos + 1, entityEndIndex), spannedText);
                            if (entityEndIndex == spaceEndIndex) {
                                spannedText.append(" ");
                            }

                            pos = entityEndIndex + 1;
                        } else {
                            spannedText.append(curr);
                            ++pos;
                        }
                        break;
                    case '<':
                        if (pos + 1 >= markup.length()) {
                            ++pos;
                        } else {
                            int ltPos = pos;
                            boolean isClosingTag = markup.charAt(pos + 1) == '/';
                            pos = findEndOfTag(markup, pos + 1);
                            boolean isVoidTag = markup.charAt(pos - 2) == '/';
                            String fullTagExpression = markup.substring(ltPos + (isClosingTag ? 2 : 1), isVoidTag ? pos - 2 : pos - 1);
                            String tagName = getTagName(fullTagExpression);
                            if (tagName != null && isSupportedTag(tagName)) {
                                if (isClosingTag) {
                                    while(!startTagStack.isEmpty()) {
                                        StartTag startTag = (StartTag)startTagStack.pop();
                                        applySpansForTag(id, startTag, spannedText, styles, scratchStyleMatches);
                                        if (startTag.name.equals(tagName)) {
                                            break;
                                        }
                                    }
                                } else if (!isVoidTag) {
                                    startTagStack.push(StartTag.buildStartTag(fullTagExpression, spannedText.length()));
                                }
                            }
                        }
                        break;
                    default:
                        spannedText.append(curr);
                        ++pos;
                    }
                }

                while(!startTagStack.isEmpty()) {
                    applySpansForTag(id, (StartTag)startTagStack.pop(), spannedText, styles, scratchStyleMatches);
                }

                applySpansForTag(id, StartTag.buildWholeCueVirtualTag(), spannedText, styles, scratchStyleMatches);
                builder.setText(spannedText);
                return;
            }
        }
    }

    private static boolean parseCue(String id, Matcher cueHeaderMatcher, ParsableByteArray webvttData, Builder builder, StringBuilder textBuilder, List<WebvttCssStyle> styles) {
        try {
            builder.setStartTime(WebvttParserUtil.parseTimestampUs(cueHeaderMatcher.group(1))).setEndTime(WebvttParserUtil.parseTimestampUs(cueHeaderMatcher.group(2)));
        } catch (NumberFormatException var7) {
            Log.w("WebvttCueParser", "Skipping cue with bad header: " + cueHeaderMatcher.group());
            return false;
        }

        parseCueSettingsList(cueHeaderMatcher.group(3), builder);
        textBuilder.setLength(0);

        String line;
        for(; !TextUtils.isEmpty(line = webvttData.readLine()); textBuilder.append(line.trim())) {
            if (textBuilder.length() > 0) {
                textBuilder.append("\n");
            }
        }

        parseCueText(id, textBuilder.toString(), builder, styles);
        return true;
    }

    private static void parseLineAttribute(String s, Builder builder) throws NumberFormatException {
        int commaIndex = s.indexOf(44);
        if (commaIndex != -1) {
            builder.setLineAnchor(parsePositionAnchor(s.substring(commaIndex + 1)));
            s = s.substring(0, commaIndex);
        } else {
            builder.setLineAnchor(-2147483648);
        }

        if (s.endsWith("%")) {
            builder.setLine(WebvttParserUtil.parsePercentage(s)).setLineType(0);
        } else {
            int lineNumber = Integer.parseInt(s);
            if (lineNumber < 0) {
                --lineNumber;
            }

            builder.setLine((float)lineNumber).setLineType(1);
        }

    }

    private static void parsePositionAttribute(String s, Builder builder) throws NumberFormatException {
        int commaIndex = s.indexOf(44);
        if (commaIndex != -1) {
            builder.setPositionAnchor(parsePositionAnchor(s.substring(commaIndex + 1)));
            s = s.substring(0, commaIndex);
        } else {
            builder.setPositionAnchor(-2147483648);
        }

        builder.setPosition(WebvttParserUtil.parsePercentage(s));
    }

    private static int parsePositionAnchor(String s) {
        byte var2 = -1;
        switch(s.hashCode()) {
        case -1364013995:
            if (s.equals("center")) {
                var2 = 1;
            }
            break;
        case -1074341483:
            if (s.equals("middle")) {
                var2 = 2;
            }
            break;
        case 100571:
            if (s.equals("end")) {
                var2 = 3;
            }
            break;
        case 109757538:
            if (s.equals("start")) {
                var2 = 0;
            }
        }

        switch(var2) {
        case 0:
            return 0;
        case 1:
        case 2:
            return 1;
        case 3:
            return 2;
        default:
            Log.w("WebvttCueParser", "Invalid anchor value: " + s);
            return -2147483648;
        }
    }

    private static Alignment parseTextAlignment(String s) {
        byte var2 = -1;
        switch(s.hashCode()) {
        case -1364013995:
            if (s.equals("center")) {
                var2 = 2;
            }
            break;
        case -1074341483:
            if (s.equals("middle")) {
                var2 = 3;
            }
            break;
        case 100571:
            if (s.equals("end")) {
                var2 = 4;
            }
            break;
        case 3317767:
            if (s.equals("left")) {
                var2 = 1;
            }
            break;
        case 108511772:
            if (s.equals("right")) {
                var2 = 5;
            }
            break;
        case 109757538:
            if (s.equals("start")) {
                var2 = 0;
            }
        }

        switch(var2) {
        case 0:
        case 1:
            return Alignment.ALIGN_NORMAL;
        case 2:
        case 3:
            return Alignment.ALIGN_CENTER;
        case 4:
        case 5:
            return Alignment.ALIGN_OPPOSITE;
        default:
            Log.w("WebvttCueParser", "Invalid alignment value: " + s);
            return null;
        }
    }

    private static int findEndOfTag(String markup, int startPos) {
        int index = markup.indexOf(62, startPos);
        return index == -1 ? markup.length() : index + 1;
    }

    private static void applyEntity(String entity, SpannableStringBuilder spannedText) {
        byte var3 = -1;
        switch(entity.hashCode()) {
        case 3309:
            if (entity.equals("gt")) {
                var3 = 1;
            }
            break;
        case 3464:
            if (entity.equals("lt")) {
                var3 = 0;
            }
            break;
        case 96708:
            if (entity.equals("amp")) {
                var3 = 3;
            }
            break;
        case 3374865:
            if (entity.equals("nbsp")) {
                var3 = 2;
            }
        }

        switch(var3) {
        case 0:
            spannedText.append('<');
            break;
        case 1:
            spannedText.append('>');
            break;
        case 2:
            spannedText.append(' ');
            break;
        case 3:
            spannedText.append('&');
            break;
        default:
            Log.w("WebvttCueParser", "ignoring unsupported entity: '&" + entity + ";'");
        }

    }

    private static boolean isSupportedTag(String tagName) {
        byte var2 = -1;
        switch(tagName.hashCode()) {
        case 98:
            if (tagName.equals("b")) {
                var2 = 0;
            }
            break;
        case 99:
            if (tagName.equals("c")) {
                var2 = 1;
            }
            break;
        case 105:
            if (tagName.equals("i")) {
                var2 = 2;
            }
            break;
        case 117:
            if (tagName.equals("u")) {
                var2 = 4;
            }
            break;
        case 118:
            if (tagName.equals("v")) {
                var2 = 5;
            }
            break;
        case 3314158:
            if (tagName.equals("lang")) {
                var2 = 3;
            }
        }

        switch(var2) {
        case 0:
        case 1:
        case 2:
        case 3:
        case 4:
        case 5:
            return true;
        default:
            return false;
        }
    }

    private static void applySpansForTag(String cueId, StartTag startTag, SpannableStringBuilder text, List<WebvttCssStyle> styles, List<StyleMatch> scratchStyleMatches) {
        int start = startTag.position;
        int end = text.length();
        String var7 = startTag.name;
        byte var8 = -1;
        switch(var7.hashCode()) {
        case 0:
            if (var7.equals("")) {
                var8 = 6;
            }
            break;
        case 98:
            if (var7.equals("b")) {
                var8 = 0;
            }
            break;
        case 99:
            if (var7.equals("c")) {
                var8 = 3;
            }
            break;
        case 105:
            if (var7.equals("i")) {
                var8 = 1;
            }
            break;
        case 117:
            if (var7.equals("u")) {
                var8 = 2;
            }
            break;
        case 118:
            if (var7.equals("v")) {
                var8 = 5;
            }
            break;
        case 3314158:
            if (var7.equals("lang")) {
                var8 = 4;
            }
        }

        switch(var8) {
        case 0:
            text.setSpan(new StyleSpan(1), start, end, 33);
            break;
        case 1:
            text.setSpan(new StyleSpan(2), start, end, 33);
            break;
        case 2:
            text.setSpan(new UnderlineSpan(), start, end, 33);
        case 3:
        case 4:
        case 5:
        case 6:
            break;
        default:
            return;
        }

        scratchStyleMatches.clear();
        getApplicableStyles(styles, cueId, startTag, scratchStyleMatches);
        int styleMatchesCount = scratchStyleMatches.size();

        for(int i = 0; i < styleMatchesCount; ++i) {
            applyStyleToText(text, ((StyleMatch)scratchStyleMatches.get(i)).style, start, end);
        }

    }

    private static void applyStyleToText(SpannableStringBuilder spannedText, WebvttCssStyle style, int start, int end) {
        if (style != null) {
            if (style.getStyle() != -1) {
                spannedText.setSpan(new StyleSpan(style.getStyle()), start, end, 33);
            }

            if (style.isLinethrough()) {
                spannedText.setSpan(new StrikethroughSpan(), start, end, 33);
            }

            if (style.isUnderline()) {
                spannedText.setSpan(new UnderlineSpan(), start, end, 33);
            }

            if (style.hasFontColor()) {
                spannedText.setSpan(new ForegroundColorSpan(style.getFontColor()), start, end, 33);
            }

            if (style.hasBackgroundColor()) {
                spannedText.setSpan(new BackgroundColorSpan(style.getBackgroundColor()), start, end, 33);
            }

            if (style.getFontFamily() != null) {
                spannedText.setSpan(new TypefaceSpan(style.getFontFamily()), start, end, 33);
            }

            if (style.getTextAlign() != null) {
                spannedText.setSpan(new Standard(style.getTextAlign()), start, end, 33);
            }

            switch(style.getFontSizeUnit()) {
            case -1:
            case 0:
            default:
                break;
            case 1:
                spannedText.setSpan(new AbsoluteSizeSpan((int)style.getFontSize(), true), start, end, 33);
                break;
            case 2:
                spannedText.setSpan(new RelativeSizeSpan(style.getFontSize()), start, end, 33);
                break;
            case 3:
                spannedText.setSpan(new RelativeSizeSpan(style.getFontSize() / 100.0F), start, end, 33);
            }

        }
    }

    private static String getTagName(String tagExpression) {
        tagExpression = tagExpression.trim();
        return tagExpression.isEmpty() ? null : Util.splitAtFirst(tagExpression, "[ \\.]")[0];
    }

    private static void getApplicableStyles(List<WebvttCssStyle> declaredStyles, String id, StartTag tag, List<StyleMatch> output) {
        int styleCount = declaredStyles.size();

        for(int i = 0; i < styleCount; ++i) {
            WebvttCssStyle style = (WebvttCssStyle)declaredStyles.get(i);
            int score = style.getSpecificityScore(id, tag.name, tag.classes, tag.voice);
            if (score > 0) {
                output.add(new StyleMatch(score, style));
            }
        }

        Collections.sort(output);
    }

    private static final class StartTag {
        private static final String[] NO_CLASSES = new String[0];
        public final String name;
        public final int position;
        public final String voice;
        public final String[] classes;

        private StartTag(String name, int position, String voice, String[] classes) {
            this.position = position;
            this.name = name;
            this.voice = voice;
            this.classes = classes;
        }

        public static StartTag buildStartTag(String fullTagExpression, int position) {
            fullTagExpression = fullTagExpression.trim();
            if (fullTagExpression.isEmpty()) {
                return null;
            } else {
                int voiceStartIndex = fullTagExpression.indexOf(" ");
                String voice;
                if (voiceStartIndex == -1) {
                    voice = "";
                } else {
                    voice = fullTagExpression.substring(voiceStartIndex).trim();
                    fullTagExpression = fullTagExpression.substring(0, voiceStartIndex);
                }

                String[] nameAndClasses = Util.split(fullTagExpression, "\\.");
                String name = nameAndClasses[0];
                String[] classes;
                if (nameAndClasses.length > 1) {
                    classes = (String[])Arrays.copyOfRange(nameAndClasses, 1, nameAndClasses.length);
                } else {
                    classes = NO_CLASSES;
                }

                return new StartTag(name, position, voice, classes);
            }
        }

        public static StartTag buildWholeCueVirtualTag() {
            return new StartTag("", 0, "", new String[0]);
        }
    }

    private static final class StyleMatch implements Comparable<StyleMatch> {
        public final int score;
        public final WebvttCssStyle style;

        public StyleMatch(int score, WebvttCssStyle style) {
            this.score = score;
            this.style = style;
        }

        public int compareTo(@NonNull WebvttCueParser.StyleMatch another) {
            return this.score - another.score;
        }
    }
}
