//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.text.ttml;

import android.text.Layout.Alignment;

import com.zj.playerLib.text.SimpleSubtitleDecoder;
import com.zj.playerLib.text.SubtitleDecoderException;
import com.zj.playerLib.util.ColorParser;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.Util;
import com.zj.playerLib.util.XmlPullParserUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TtmlDecoder extends SimpleSubtitleDecoder {
    private static final String TAG = "TtmlDecoder";
    private static final String TTP = "http://www.w3.org/ns/ttml#parameter";
    private static final String ATTR_BEGIN = "begin";
    private static final String ATTR_DURATION = "dur";
    private static final String ATTR_END = "end";
    private static final String ATTR_STYLE = "style";
    private static final String ATTR_REGION = "region";
    private static final String ATTR_IMAGE = "backgroundImage";
    private static final Pattern CLOCK_TIME = Pattern.compile("^([0-9][0-9]+):([0-9][0-9]):([0-9][0-9])(?:(\\.[0-9]+)|:([0-9][0-9])(?:\\.([0-9]+))?)?$");
    private static final Pattern OFFSET_TIME = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)(h|m|s|ms|f|t)$");
    private static final Pattern FONT_SIZE = Pattern.compile("^(([0-9]*.)?[0-9]+)(px|em|%)$");
    private static final Pattern PERCENTAGE_COORDINATES = Pattern.compile("^(\\d+\\.?\\d*?)% (\\d+\\.?\\d*?)%$");
    private static final Pattern PIXEL_COORDINATES = Pattern.compile("^(\\d+\\.?\\d*?)px (\\d+\\.?\\d*?)px$");
    private static final Pattern CELL_RESOLUTION = Pattern.compile("^(\\d+) (\\d+)$");
    private static final int DEFAULT_FRAME_RATE = 30;
    private static final FrameAndTickRate DEFAULT_FRAME_AND_TICK_RATE = new FrameAndTickRate(30.0F, 1, 1);
    private static final CellResolution DEFAULT_CELL_RESOLUTION = new CellResolution(32, 15);
    private final XmlPullParserFactory xmlParserFactory;

    public TtmlDecoder() {
        super("TtmlDecoder");

        try {
            this.xmlParserFactory = XmlPullParserFactory.newInstance();
            this.xmlParserFactory.setNamespaceAware(true);
        } catch (XmlPullParserException var2) {
            throw new RuntimeException("Couldn't create XmlPullParserFactory instance", var2);
        }
    }

    protected TtmlSubtitle decode(byte[] bytes, int length, boolean reset) throws SubtitleDecoderException {
        try {
            XmlPullParser xmlParser = this.xmlParserFactory.newPullParser();
            Map<String, TtmlStyle> globalStyles = new HashMap();
            Map<String, TtmlRegion> regionMap = new HashMap();
            Map<String, String> imageMap = new HashMap();
            regionMap.put("", new TtmlRegion((String)null));
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes, 0, length);
            xmlParser.setInput(inputStream, (String)null);
            TtmlSubtitle ttmlSubtitle = null;
            ArrayDeque<TtmlNode> nodeStack = new ArrayDeque();
            int unsupportedNodeDepth = 0;
            int eventType = xmlParser.getEventType();
            FrameAndTickRate frameAndTickRate = DEFAULT_FRAME_AND_TICK_RATE;
            CellResolution cellResolution = DEFAULT_CELL_RESOLUTION;

            for(TtsExtent ttsExtent = null; eventType != 1; eventType = xmlParser.getEventType()) {
                TtmlNode parent = (TtmlNode)nodeStack.peek();
                if (unsupportedNodeDepth == 0) {
                    String name = xmlParser.getName();
                    if (eventType == 2) {
                        if ("tt".equals(name)) {
                            frameAndTickRate = this.parseFrameAndTickRates(xmlParser);
                            cellResolution = this.parseCellResolution(xmlParser, DEFAULT_CELL_RESOLUTION);
                            ttsExtent = this.parseTtsExtent(xmlParser);
                        }

                        if (!isSupportedTag(name)) {
                            Log.i("TtmlDecoder", "Ignoring unsupported tag: " + xmlParser.getName());
                            ++unsupportedNodeDepth;
                        } else if ("head".equals(name)) {
                            this.parseHeader(xmlParser, globalStyles, cellResolution, ttsExtent, regionMap, imageMap);
                        } else {
                            try {
                                TtmlNode node = this.parseNode(xmlParser, parent, regionMap, frameAndTickRate);
                                nodeStack.push(node);
                                if (parent != null) {
                                    parent.addChild(node);
                                }
                            } catch (SubtitleDecoderException var19) {
                                Log.w("TtmlDecoder", "Suppressing parser error", var19);
                                ++unsupportedNodeDepth;
                            }
                        }
                    } else if (eventType == 4) {
                        parent.addChild(TtmlNode.buildTextNode(xmlParser.getText()));
                    } else if (eventType == 3) {
                        if (xmlParser.getName().equals("tt")) {
                            ttmlSubtitle = new TtmlSubtitle((TtmlNode)nodeStack.peek(), globalStyles, regionMap, imageMap);
                        }

                        nodeStack.pop();
                    }
                } else if (eventType == 2) {
                    ++unsupportedNodeDepth;
                } else if (eventType == 3) {
                    --unsupportedNodeDepth;
                }

                xmlParser.next();
            }

            return ttmlSubtitle;
        } catch (XmlPullParserException var20) {
            throw new SubtitleDecoderException("Unable to decode source", var20);
        } catch (IOException var21) {
            throw new IllegalStateException("Unexpected error when reading input.", var21);
        }
    }

    private FrameAndTickRate parseFrameAndTickRates(XmlPullParser xmlParser) throws SubtitleDecoderException {
        int frameRate = 30;
        String frameRateString = xmlParser.getAttributeValue("http://www.w3.org/ns/ttml#parameter", "frameRate");
        if (frameRateString != null) {
            frameRate = Integer.parseInt(frameRateString);
        }

        float frameRateMultiplier = 1.0F;
        String frameRateMultiplierString = xmlParser.getAttributeValue("http://www.w3.org/ns/ttml#parameter", "frameRateMultiplier");
        if (frameRateMultiplierString != null) {
            String[] parts = Util.split(frameRateMultiplierString, " ");
            if (parts.length != 2) {
                throw new SubtitleDecoderException("frameRateMultiplier doesn't have 2 parts");
            }

            float numerator = (float)Integer.parseInt(parts[0]);
            float denominator = (float)Integer.parseInt(parts[1]);
            frameRateMultiplier = numerator / denominator;
        }

        int subFrameRate = DEFAULT_FRAME_AND_TICK_RATE.subFrameRate;
        String subFrameRateString = xmlParser.getAttributeValue("http://www.w3.org/ns/ttml#parameter", "subFrameRate");
        if (subFrameRateString != null) {
            subFrameRate = Integer.parseInt(subFrameRateString);
        }

        int tickRate = DEFAULT_FRAME_AND_TICK_RATE.tickRate;
        String tickRateString = xmlParser.getAttributeValue("http://www.w3.org/ns/ttml#parameter", "tickRate");
        if (tickRateString != null) {
            tickRate = Integer.parseInt(tickRateString);
        }

        return new FrameAndTickRate((float)frameRate * frameRateMultiplier, subFrameRate, tickRate);
    }

    private CellResolution parseCellResolution(XmlPullParser xmlParser, CellResolution defaultValue) throws SubtitleDecoderException {
        String cellResolution = xmlParser.getAttributeValue("http://www.w3.org/ns/ttml#parameter", "cellResolution");
        if (cellResolution == null) {
            return defaultValue;
        } else {
            Matcher cellResolutionMatcher = CELL_RESOLUTION.matcher(cellResolution);
            if (!cellResolutionMatcher.matches()) {
                Log.w("TtmlDecoder", "Ignoring malformed cell resolution: " + cellResolution);
                return defaultValue;
            } else {
                try {
                    int columns = Integer.parseInt(cellResolutionMatcher.group(1));
                    int rows = Integer.parseInt(cellResolutionMatcher.group(2));
                    if (columns != 0 && rows != 0) {
                        return new CellResolution(columns, rows);
                    } else {
                        throw new SubtitleDecoderException("Invalid cell resolution " + columns + " " + rows);
                    }
                } catch (NumberFormatException var7) {
                    Log.w("TtmlDecoder", "Ignoring malformed cell resolution: " + cellResolution);
                    return defaultValue;
                }
            }
        }
    }

    private TtsExtent parseTtsExtent(XmlPullParser xmlParser) {
        String ttsExtent = XmlPullParserUtil.getAttributeValue(xmlParser, "extent");
        if (ttsExtent == null) {
            return null;
        } else {
            Matcher extentMatcher = PIXEL_COORDINATES.matcher(ttsExtent);
            if (!extentMatcher.matches()) {
                Log.w("TtmlDecoder", "Ignoring non-pixel tts extent: " + ttsExtent);
                return null;
            } else {
                try {
                    int width = Integer.parseInt(extentMatcher.group(1));
                    int height = Integer.parseInt(extentMatcher.group(2));
                    return new TtsExtent(width, height);
                } catch (NumberFormatException var6) {
                    Log.w("TtmlDecoder", "Ignoring malformed tts extent: " + ttsExtent);
                    return null;
                }
            }
        }
    }

    private Map<String, TtmlStyle> parseHeader(XmlPullParser xmlParser, Map<String, TtmlStyle> globalStyles, CellResolution cellResolution, TtsExtent ttsExtent, Map<String, TtmlRegion> globalRegions, Map<String, String> imageMap) throws IOException, XmlPullParserException {
        do {
            xmlParser.next();
            if (XmlPullParserUtil.isStartTag(xmlParser, "style")) {
                String parentStyleId = XmlPullParserUtil.getAttributeValue(xmlParser, "style");
                TtmlStyle style = this.parseStyleAttributes(xmlParser, new TtmlStyle());
                if (parentStyleId != null) {
                    String[] var9 = this.parseStyleIds(parentStyleId);
                    int var10 = var9.length;

                    for(int var11 = 0; var11 < var10; ++var11) {
                        String id = var9[var11];
                        style.chain((TtmlStyle)globalStyles.get(id));
                    }
                }

                if (style.getId() != null) {
                    globalStyles.put(style.getId(), style);
                }
            } else if (XmlPullParserUtil.isStartTag(xmlParser, "region")) {
                TtmlRegion ttmlRegion = this.parseRegionAttributes(xmlParser, cellResolution, ttsExtent);
                if (ttmlRegion != null) {
                    globalRegions.put(ttmlRegion.id, ttmlRegion);
                }
            } else if (XmlPullParserUtil.isStartTag(xmlParser, "metadata")) {
                this.parseMetadata(xmlParser, imageMap);
            }
        } while(!XmlPullParserUtil.isEndTag(xmlParser, "head"));

        return globalStyles;
    }

    private void parseMetadata(XmlPullParser xmlParser, Map<String, String> imageMap) throws IOException, XmlPullParserException {
        do {
            xmlParser.next();
            if (XmlPullParserUtil.isStartTag(xmlParser, "image")) {
                String id = XmlPullParserUtil.getAttributeValue(xmlParser, "id");
                if (id != null) {
                    String encodedBitmapData = xmlParser.nextText();
                    imageMap.put(id, encodedBitmapData);
                }
            }
        } while(!XmlPullParserUtil.isEndTag(xmlParser, "metadata"));

    }

    private TtmlRegion parseRegionAttributes(XmlPullParser xmlParser, CellResolution cellResolution, TtsExtent ttsExtent) {
        String regionId = XmlPullParserUtil.getAttributeValue(xmlParser, "id");
        if (regionId == null) {
            return null;
        } else {
            String regionOrigin = XmlPullParserUtil.getAttributeValue(xmlParser, "origin");
            if (regionOrigin != null) {
                Matcher originPercentageMatcher = PERCENTAGE_COORDINATES.matcher(regionOrigin);
                Matcher originPixelMatcher = PIXEL_COORDINATES.matcher(regionOrigin);
                float position;
                float line;
                if (originPercentageMatcher.matches()) {
                    try {
                        position = Float.parseFloat(originPercentageMatcher.group(1)) / 100.0F;
                        line = Float.parseFloat(originPercentageMatcher.group(2)) / 100.0F;
                    } catch (NumberFormatException var18) {
                        Log.w("TtmlDecoder", "Ignoring region with malformed origin: " + regionOrigin);
                        return null;
                    }
                } else {
                    if (!originPixelMatcher.matches()) {
                        Log.w("TtmlDecoder", "Ignoring region with unsupported origin: " + regionOrigin);
                        return null;
                    }

                    if (ttsExtent == null) {
                        Log.w("TtmlDecoder", "Ignoring region with missing tts:extent: " + regionOrigin);
                        return null;
                    }

                    try {
                        int width = Integer.parseInt(originPixelMatcher.group(1));
                        int height = Integer.parseInt(originPixelMatcher.group(2));
                        position = (float)width / (float)ttsExtent.width;
                        line = (float)height / (float)ttsExtent.height;
                    } catch (NumberFormatException var17) {
                        Log.w("TtmlDecoder", "Ignoring region with malformed origin: " + regionOrigin);
                        return null;
                    }
                }

                String regionExtent = XmlPullParserUtil.getAttributeValue(xmlParser, "extent");
                if (regionExtent != null) {
                    Matcher extentPercentageMatcher = PERCENTAGE_COORDINATES.matcher(regionExtent);
                    Matcher extentPixelMatcher = PIXEL_COORDINATES.matcher(regionExtent);
                    float width;
                    float height;
                    if (extentPercentageMatcher.matches()) {
                        try {
                            width = Float.parseFloat(extentPercentageMatcher.group(1)) / 100.0F;
                            height = Float.parseFloat(extentPercentageMatcher.group(2)) / 100.0F;
                        } catch (NumberFormatException var16) {
                            Log.w("TtmlDecoder", "Ignoring region with malformed extent: " + regionOrigin);
                            return null;
                        }
                    } else {
                        if (!extentPixelMatcher.matches()) {
                            Log.w("TtmlDecoder", "Ignoring region with unsupported extent: " + regionOrigin);
                            return null;
                        }

                        if (ttsExtent == null) {
                            Log.w("TtmlDecoder", "Ignoring region with missing tts:extent: " + regionOrigin);
                            return null;
                        }

                        try {
                            int extentWidth = Integer.parseInt(extentPixelMatcher.group(1));
                            int extentHeight = Integer.parseInt(extentPixelMatcher.group(2));
                            width = (float)extentWidth / (float)ttsExtent.width;
                            height = (float)extentHeight / (float)ttsExtent.height;
                        } catch (NumberFormatException var15) {
                            Log.w("TtmlDecoder", "Ignoring region with malformed extent: " + regionOrigin);
                            return null;
                        }
                    }

                    int lineAnchor = 0;
                    String displayAlign = XmlPullParserUtil.getAttributeValue(xmlParser, "displayAlign");
                    if (displayAlign != null) {
                        String var25 = Util.toLowerInvariant(displayAlign);
                        byte var27 = -1;
                        switch(var25.hashCode()) {
                        case -1364013995:
                            if (var25.equals("center")) {
                                var27 = 0;
                            }
                            break;
                        case 92734940:
                            if (var25.equals("after")) {
                                var27 = 1;
                            }
                        }

                        switch(var27) {
                        case 0:
                            lineAnchor = 1;
                            line += height / 2.0F;
                            break;
                        case 1:
                            lineAnchor = 2;
                            line += height;
                        }
                    }

                    float regionTextHeight = 1.0F / (float)cellResolution.rows;
                    return new TtmlRegion(regionId, position, line, 0, lineAnchor, width, 1, regionTextHeight);
                } else {
                    Log.w("TtmlDecoder", "Ignoring region without an extent");
                    return null;
                }
            } else {
                Log.w("TtmlDecoder", "Ignoring region without an origin");
                return null;
            }
        }
    }

    private String[] parseStyleIds(String parentStyleIds) {
        parentStyleIds = parentStyleIds.trim();
        return parentStyleIds.isEmpty() ? new String[0] : Util.split(parentStyleIds, "\\s+");
    }

    private TtmlStyle parseStyleAttributes(XmlPullParser parser, TtmlStyle style) {
        int attributeCount = parser.getAttributeCount();

        for(int i = 0; i < attributeCount; ++i) {
            String attributeValue = parser.getAttributeValue(i);
            String var6 = parser.getAttributeName(i);
            byte var7 = -1;
            switch(var6.hashCode()) {
            case -1550943582:
                if (var6.equals("fontStyle")) {
                    var7 = 6;
                }
                break;
            case -1224696685:
                if (var6.equals("fontFamily")) {
                    var7 = 3;
                }
                break;
            case -1065511464:
                if (var6.equals("textAlign")) {
                    var7 = 7;
                }
                break;
            case -879295043:
                if (var6.equals("textDecoration")) {
                    var7 = 8;
                }
                break;
            case -734428249:
                if (var6.equals("fontWeight")) {
                    var7 = 5;
                }
                break;
            case 3355:
                if (var6.equals("id")) {
                    var7 = 0;
                }
                break;
            case 94842723:
                if (var6.equals("color")) {
                    var7 = 2;
                }
                break;
            case 365601008:
                if (var6.equals("fontSize")) {
                    var7 = 4;
                }
                break;
            case 1287124693:
                if (var6.equals("backgroundColor")) {
                    var7 = 1;
                }
            }

            String var8;
            byte var9;
            switch(var7) {
            case 0:
                if ("style".equals(parser.getName())) {
                    style = this.createIfNull(style).setId(attributeValue);
                }
                break;
            case 1:
                style = this.createIfNull(style);

                try {
                    style.setBackgroundColor(ColorParser.parseTtmlColor(attributeValue));
                } catch (IllegalArgumentException var12) {
                    Log.w("TtmlDecoder", "Failed parsing background value: " + attributeValue);
                }
                break;
            case 2:
                style = this.createIfNull(style);

                try {
                    style.setFontColor(ColorParser.parseTtmlColor(attributeValue));
                } catch (IllegalArgumentException var11) {
                    Log.w("TtmlDecoder", "Failed parsing color value: " + attributeValue);
                }
                break;
            case 3:
                style = this.createIfNull(style).setFontFamily(attributeValue);
                break;
            case 4:
                try {
                    style = this.createIfNull(style);
                    parseFontSize(attributeValue, style);
                } catch (SubtitleDecoderException var10) {
                    Log.w("TtmlDecoder", "Failed parsing fontSize value: " + attributeValue);
                }
                break;
            case 5:
                style = this.createIfNull(style).setBold("bold".equalsIgnoreCase(attributeValue));
                break;
            case 6:
                style = this.createIfNull(style).setItalic("italic".equalsIgnoreCase(attributeValue));
                break;
            case 7:
                var8 = Util.toLowerInvariant(attributeValue);
                var9 = -1;
                switch(var8.hashCode()) {
                case -1364013995:
                    if (var8.equals("center")) {
                        var9 = 4;
                    }
                    break;
                case 100571:
                    if (var8.equals("end")) {
                        var9 = 3;
                    }
                    break;
                case 3317767:
                    if (var8.equals("left")) {
                        var9 = 0;
                    }
                    break;
                case 108511772:
                    if (var8.equals("right")) {
                        var9 = 2;
                    }
                    break;
                case 109757538:
                    if (var8.equals("start")) {
                        var9 = 1;
                    }
                }

                switch(var9) {
                case 0:
                    style = this.createIfNull(style).setTextAlign(Alignment.ALIGN_NORMAL);
                    continue;
                case 1:
                    style = this.createIfNull(style).setTextAlign(Alignment.ALIGN_NORMAL);
                    continue;
                case 2:
                    style = this.createIfNull(style).setTextAlign(Alignment.ALIGN_OPPOSITE);
                    continue;
                case 3:
                    style = this.createIfNull(style).setTextAlign(Alignment.ALIGN_OPPOSITE);
                    continue;
                case 4:
                    style = this.createIfNull(style).setTextAlign(Alignment.ALIGN_CENTER);
                default:
                    continue;
                }
            case 8:
                var8 = Util.toLowerInvariant(attributeValue);
                var9 = -1;
                switch(var8.hashCode()) {
                case -1461280213:
                    if (var8.equals("nounderline")) {
                        var9 = 3;
                    }
                    break;
                case -1026963764:
                    if (var8.equals("underline")) {
                        var9 = 2;
                    }
                    break;
                case 913457136:
                    if (var8.equals("nolinethrough")) {
                        var9 = 1;
                    }
                    break;
                case 1679736913:
                    if (var8.equals("linethrough")) {
                        var9 = 0;
                    }
                }

                switch(var9) {
                case 0:
                    style = this.createIfNull(style).setLinethrough(true);
                    break;
                case 1:
                    style = this.createIfNull(style).setLinethrough(false);
                    break;
                case 2:
                    style = this.createIfNull(style).setUnderline(true);
                    break;
                case 3:
                    style = this.createIfNull(style).setUnderline(false);
                }
            }
        }

        return style;
    }

    private TtmlStyle createIfNull(TtmlStyle style) {
        return style == null ? new TtmlStyle() : style;
    }

    private TtmlNode parseNode(XmlPullParser parser, TtmlNode parent, Map<String, TtmlRegion> regionMap, FrameAndTickRate frameAndTickRate) throws SubtitleDecoderException {
        long duration = -9223372036854775807L;
        long startTime = -9223372036854775807L;
        long endTime = -9223372036854775807L;
        String regionId = "";
        String imageId = null;
        String[] styleIds = null;
        int attributeCount = parser.getAttributeCount();
        TtmlStyle style = this.parseStyleAttributes(parser, (TtmlStyle)null);

        for(int i = 0; i < attributeCount; ++i) {
            String attr = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            byte var20 = -1;
            switch(attr.hashCode()) {
            case -934795532:
                if (attr.equals("region")) {
                    var20 = 4;
                }
                break;
            case 99841:
                if (attr.equals("dur")) {
                    var20 = 2;
                }
                break;
            case 100571:
                if (attr.equals("end")) {
                    var20 = 1;
                }
                break;
            case 93616297:
                if (attr.equals("begin")) {
                    var20 = 0;
                }
                break;
            case 109780401:
                if (attr.equals("style")) {
                    var20 = 3;
                }
                break;
            case 1292595405:
                if (attr.equals("backgroundImage")) {
                    var20 = 5;
                }
            }

            switch(var20) {
            case 0:
                startTime = parseTimeExpression(value, frameAndTickRate);
                break;
            case 1:
                endTime = parseTimeExpression(value, frameAndTickRate);
                break;
            case 2:
                duration = parseTimeExpression(value, frameAndTickRate);
                break;
            case 3:
                String[] ids = this.parseStyleIds(value);
                if (ids.length > 0) {
                    styleIds = ids;
                }
                break;
            case 4:
                if (regionMap.containsKey(value)) {
                    regionId = value;
                }
                break;
            case 5:
                if (value.startsWith("#")) {
                    imageId = value.substring(1);
                }
            }
        }

        if (parent != null && parent.startTimeUs != -9223372036854775807L) {
            if (startTime != -9223372036854775807L) {
                startTime += parent.startTimeUs;
            }

            if (endTime != -9223372036854775807L) {
                endTime += parent.startTimeUs;
            }
        }

        if (endTime == -9223372036854775807L) {
            if (duration != -9223372036854775807L) {
                endTime = startTime + duration;
            } else if (parent != null && parent.endTimeUs != -9223372036854775807L) {
                endTime = parent.endTimeUs;
            }
        }

        return TtmlNode.buildNode(parser.getName(), startTime, endTime, style, styleIds, regionId, imageId);
    }

    private static boolean isSupportedTag(String tag) {
        return tag.equals("tt") || tag.equals("head") || tag.equals("body") || tag.equals("div") || tag.equals("p") || tag.equals("span") || tag.equals("br") || tag.equals("style") || tag.equals("styling") || tag.equals("layout") || tag.equals("region") || tag.equals("metadata") || tag.equals("image") || tag.equals("data") || tag.equals("information");
    }

    private static void parseFontSize(String expression, TtmlStyle out) throws SubtitleDecoderException {
        String[] expressions = Util.split(expression, "\\s+");
        Matcher matcher;
        if (expressions.length == 1) {
            matcher = FONT_SIZE.matcher(expression);
        } else {
            if (expressions.length != 2) {
                throw new SubtitleDecoderException("Invalid number of entries for fontSize: " + expressions.length + ".");
            }

            matcher = FONT_SIZE.matcher(expressions[1]);
            Log.w("TtmlDecoder", "Multiple values in fontSize attribute. Picking the second value for vertical font size and ignoring the first.");
        }

        if (matcher.matches()) {
            String unit = matcher.group(3);
            byte var6 = -1;
            switch(unit.hashCode()) {
            case 37:
                if (unit.equals("%")) {
                    var6 = 2;
                }
                break;
            case 3240:
                if (unit.equals("em")) {
                    var6 = 1;
                }
                break;
            case 3592:
                if (unit.equals("px")) {
                    var6 = 0;
                }
            }

            switch(var6) {
            case 0:
                out.setFontSizeUnit(1);
                break;
            case 1:
                out.setFontSizeUnit(2);
                break;
            case 2:
                out.setFontSizeUnit(3);
                break;
            default:
                throw new SubtitleDecoderException("Invalid unit for fontSize: '" + unit + "'.");
            }

            out.setFontSize(Float.valueOf(matcher.group(1)));
        } else {
            throw new SubtitleDecoderException("Invalid expression for fontSize: '" + expression + "'.");
        }
    }

    private static long parseTimeExpression(String time, FrameAndTickRate frameAndTickRate) throws SubtitleDecoderException {
        Matcher matcher = CLOCK_TIME.matcher(time);
        String timeValue;
        double offsetSeconds;
        String unit;
        if (matcher.matches()) {
            timeValue = matcher.group(1);
            offsetSeconds = (double)(Long.parseLong(timeValue) * 3600L);
            unit = matcher.group(2);
            offsetSeconds += (double)(Long.parseLong(unit) * 60L);
            String seconds = matcher.group(3);
            offsetSeconds += (double)Long.parseLong(seconds);
            String fraction = matcher.group(4);
            offsetSeconds += fraction != null ? Double.parseDouble(fraction) : 0.0D;
            String frames = matcher.group(5);
            offsetSeconds += frames != null ? (double)((float)Long.parseLong(frames) / frameAndTickRate.effectiveFrameRate) : 0.0D;
            String subframes = matcher.group(6);
            offsetSeconds += subframes != null ? (double)Long.parseLong(subframes) / (double)frameAndTickRate.subFrameRate / (double)frameAndTickRate.effectiveFrameRate : 0.0D;
            return (long)(offsetSeconds * 1000000.0D);
        } else {
            matcher = OFFSET_TIME.matcher(time);
            if (matcher.matches()) {
                timeValue = matcher.group(1);
                offsetSeconds = Double.parseDouble(timeValue);
                unit = matcher.group(2);
                byte var8 = -1;
                switch(unit.hashCode()) {
                case 102:
                    if (unit.equals("f")) {
                        var8 = 4;
                    }
                    break;
                case 104:
                    if (unit.equals("h")) {
                        var8 = 0;
                    }
                    break;
                case 109:
                    if (unit.equals("m")) {
                        var8 = 1;
                    }
                    break;
                case 115:
                    if (unit.equals("s")) {
                        var8 = 2;
                    }
                    break;
                case 116:
                    if (unit.equals("t")) {
                        var8 = 5;
                    }
                    break;
                case 3494:
                    if (unit.equals("ms")) {
                        var8 = 3;
                    }
                }

                switch(var8) {
                case 0:
                    offsetSeconds *= 3600.0D;
                    break;
                case 1:
                    offsetSeconds *= 60.0D;
                case 2:
                default:
                    break;
                case 3:
                    offsetSeconds /= 1000.0D;
                    break;
                case 4:
                    offsetSeconds /= (double)frameAndTickRate.effectiveFrameRate;
                    break;
                case 5:
                    offsetSeconds /= (double)frameAndTickRate.tickRate;
                }

                return (long)(offsetSeconds * 1000000.0D);
            } else {
                throw new SubtitleDecoderException("Malformed time expression: " + time);
            }
        }
    }

    private static final class TtsExtent {
        final int width;
        final int height;

        TtsExtent(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final class CellResolution {
        final int columns;
        final int rows;

        CellResolution(int columns, int rows) {
            this.columns = columns;
            this.rows = rows;
        }
    }

    private static final class FrameAndTickRate {
        final float effectiveFrameRate;
        final int subFrameRate;
        final int tickRate;

        FrameAndTickRate(float effectiveFrameRate, int subFrameRate, int tickRate) {
            this.effectiveFrameRate = effectiveFrameRate;
            this.subFrameRate = subFrameRate;
            this.tickRate = tickRate;
        }
    }
}
