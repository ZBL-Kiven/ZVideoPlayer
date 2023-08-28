package com.zj.playerLib.text.webvtt;

import android.text.TextUtils;
import com.zj.playerLib.util.ColorParser;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CssParser {
    private static final String PROPERTY_BGCOLOR = "background-color";
    private static final String PROPERTY_FONT_FAMILY = "font-family";
    private static final String PROPERTY_FONT_WEIGHT = "font-weight";
    private static final String PROPERTY_TEXT_DECORATION = "text-decoration";
    private static final String VALUE_BOLD = "bold";
    private static final String VALUE_UNDERLINE = "underline";
    private static final String BLOCK_START = "{";
    private static final String BLOCK_END = "}";
    private static final String PROPERTY_FONT_STYLE = "font-style";
    private static final String VALUE_ITALIC = "italic";
    private static final Pattern VOICE_NAME_PATTERN = Pattern.compile("\\[voice=\"([^\"]*)\"\\]");
    private final ParsableByteArray styleInput = new ParsableByteArray();
    private final StringBuilder stringBuilder = new StringBuilder();

    public CssParser() {
    }

    public WebvttCssStyle parseBlock(ParsableByteArray input) {
        this.stringBuilder.setLength(0);
        int initialInputPosition = input.getPosition();
        skipStyleBlock(input);
        this.styleInput.reset(input.data, input.getPosition());
        this.styleInput.setPosition(initialInputPosition);
        String selector = parseSelector(this.styleInput, this.stringBuilder);
        if (selector != null && "{".equals(parseNextToken(this.styleInput, this.stringBuilder))) {
            WebvttCssStyle style = new WebvttCssStyle();
            this.applySelectorToStyle(style, selector);
            String token = null;
            boolean blockEndFound = false;

            while(!blockEndFound) {
                int position = this.styleInput.getPosition();
                token = parseNextToken(this.styleInput, this.stringBuilder);
                blockEndFound = token == null || "}".equals(token);
                if (!blockEndFound) {
                    this.styleInput.setPosition(position);
                    parseStyleDeclaration(this.styleInput, style, this.stringBuilder);
                }
            }

            return "}".equals(token) ? style : null;
        } else {
            return null;
        }
    }

    private static String parseSelector(ParsableByteArray input, StringBuilder stringBuilder) {
        skipWhitespaceAndComments(input);
        if (input.bytesLeft() < 5) {
            return null;
        } else {
            String cueSelector = input.readString(5);
            if (!"::cue".equals(cueSelector)) {
                return null;
            } else {
                int position = input.getPosition();
                String token = parseNextToken(input, stringBuilder);
                if (token == null) {
                    return null;
                } else if ("{".equals(token)) {
                    input.setPosition(position);
                    return "";
                } else {
                    String target = null;
                    if ("(".equals(token)) {
                        target = readCueTarget(input);
                    }

                    token = parseNextToken(input, stringBuilder);
                    return ")".equals(token) && token != null ? target : null;
                }
            }
        }
    }

    private static String readCueTarget(ParsableByteArray input) {
        int position = input.getPosition();
        int limit = input.limit();

        char c;
        for(boolean cueTargetEndFound = false; position < limit && !cueTargetEndFound; cueTargetEndFound = c == ')') {
            c = (char)input.data[position++];
        }

        --position;
        return input.readString(position - input.getPosition()).trim();
    }

    private static void parseStyleDeclaration(ParsableByteArray input, WebvttCssStyle style, StringBuilder stringBuilder) {
        skipWhitespaceAndComments(input);
        String property = parseIdentifier(input, stringBuilder);
        if (!"".equals(property)) {
            if (":".equals(parseNextToken(input, stringBuilder))) {
                skipWhitespaceAndComments(input);
                String value = parsePropertyValue(input, stringBuilder);
                if (value != null && !"".equals(value)) {
                    int position = input.getPosition();
                    String token = parseNextToken(input, stringBuilder);
                    if (!";".equals(token)) {
                        if (!"}".equals(token)) {
                            return;
                        }

                        input.setPosition(position);
                    }

                    if ("color".equals(property)) {
                        style.setFontColor(ColorParser.parseCssColor(value));
                    } else if ("background-color".equals(property)) {
                        style.setBackgroundColor(ColorParser.parseCssColor(value));
                    } else if ("text-decoration".equals(property)) {
                        if ("underline".equals(value)) {
                            style.setUnderline(true);
                        }
                    } else if ("font-family".equals(property)) {
                        style.setFontFamily(value);
                    } else if ("font-weight".equals(property)) {
                        if ("bold".equals(value)) {
                            style.setBold(true);
                        }
                    } else if ("font-style".equals(property) && "italic".equals(value)) {
                        style.setItalic(true);
                    }

                }
            }
        }
    }

    static void skipWhitespaceAndComments(ParsableByteArray input) {
        for(boolean skipping = true; input.bytesLeft() > 0 && skipping; skipping = maybeSkipWhitespace(input) || maybeSkipComment(input)) {
        }

    }

    static String parseNextToken(ParsableByteArray input, StringBuilder stringBuilder) {
        skipWhitespaceAndComments(input);
        if (input.bytesLeft() == 0) {
            return null;
        } else {
            String identifier = parseIdentifier(input, stringBuilder);
            return !"".equals(identifier) ? identifier : "" + (char)input.readUnsignedByte();
        }
    }

    private static boolean maybeSkipWhitespace(ParsableByteArray input) {
        switch(peekCharAtPosition(input, input.getPosition())) {
        case '\t':
        case '\n':
        case '\f':
        case '\r':
        case ' ':
            input.skipBytes(1);
            return true;
        default:
            return false;
        }
    }

    static void skipStyleBlock(ParsableByteArray input) {
        String line;
        do {
            line = input.readLine();
        } while(!TextUtils.isEmpty(line));

    }

    private static char peekCharAtPosition(ParsableByteArray input, int position) {
        return (char)input.data[position];
    }

    private static String parsePropertyValue(ParsableByteArray input, StringBuilder stringBuilder) {
        StringBuilder expressionBuilder = new StringBuilder();
        boolean expressionEndFound = false;

        while(true) {
            while(!expressionEndFound) {
                int position = input.getPosition();
                String token = parseNextToken(input, stringBuilder);
                if (token == null) {
                    return null;
                }

                if (!"}".equals(token) && !";".equals(token)) {
                    expressionBuilder.append(token);
                } else {
                    input.setPosition(position);
                    expressionEndFound = true;
                }
            }

            return expressionBuilder.toString();
        }
    }

    private static boolean maybeSkipComment(ParsableByteArray input) {
        int position = input.getPosition();
        int limit = input.limit();
        byte[] data = input.data;
        if (position + 2 <= limit && data[position++] == 47 && data[position++] == 42) {
            while(position + 1 < limit) {
                char skippedChar = (char)data[position++];
                if (skippedChar == '*' && (char)data[position] == '/') {
                    ++position;
                    limit = position;
                }
            }

            input.skipBytes(limit - input.getPosition());
            return true;
        } else {
            return false;
        }
    }

    private static String parseIdentifier(ParsableByteArray input, StringBuilder stringBuilder) {
        stringBuilder.setLength(0);
        int position = input.getPosition();
        int limit = input.limit();
        boolean identifierEndFound = false;

        while(position < limit && !identifierEndFound) {
            char c = (char)input.data[position];
            if ((c < 'A' || c > 'Z') && (c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '#' && c != '-' && c != '.' && c != '_') {
                identifierEndFound = true;
            } else {
                ++position;
                stringBuilder.append(c);
            }
        }

        input.skipBytes(position - input.getPosition());
        return stringBuilder.toString();
    }

    private void applySelectorToStyle(WebvttCssStyle style, String selector) {
        if (!"".equals(selector)) {
            int voiceStartIndex = selector.indexOf(91);
            if (voiceStartIndex != -1) {
                Matcher matcher = VOICE_NAME_PATTERN.matcher(selector.substring(voiceStartIndex));
                if (matcher.matches()) {
                    style.setTargetVoice(matcher.group(1));
                }

                selector = selector.substring(0, voiceStartIndex);
            }

            String[] classDivision = Util.split(selector, "\\.");
            String tagAndIdDivision = classDivision[0];
            int idPrefixIndex = tagAndIdDivision.indexOf(35);
            if (idPrefixIndex != -1) {
                style.setTargetTagName(tagAndIdDivision.substring(0, idPrefixIndex));
                style.setTargetId(tagAndIdDivision.substring(idPrefixIndex + 1));
            } else {
                style.setTargetTagName(tagAndIdDivision);
            }

            if (classDivision.length > 1) {
                style.setTargetClasses(Arrays.copyOfRange(classDivision, 1, classDivision.length));
            }

        }
    }
}
