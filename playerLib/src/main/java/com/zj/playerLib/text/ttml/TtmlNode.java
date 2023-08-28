package com.zj.playerLib.text.ttml;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.SpannableStringBuilder;
import android.text.Layout.Alignment;
import android.util.Base64;
import android.util.Pair;
import androidx.annotation.Nullable;
import com.zj.playerLib.text.Cue;
import com.zj.playerLib.util.Assertions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

final class TtmlNode {
    public static final String TAG_TT = "tt";
    public static final String TAG_HEAD = "head";
    public static final String TAG_BODY = "body";
    public static final String TAG_DIV = "div";
    public static final String TAG_P = "p";
    public static final String TAG_SPAN = "span";
    public static final String TAG_BR = "br";
    public static final String TAG_STYLE = "style";
    public static final String TAG_STYLING = "styling";
    public static final String TAG_LAYOUT = "layout";
    public static final String TAG_REGION = "region";
    public static final String TAG_METADATA = "metadata";
    public static final String TAG_IMAGE = "image";
    public static final String TAG_DATA = "data";
    public static final String TAG_INFORMATION = "information";
    public static final String ANONYMOUS_REGION_ID = "";
    public static final String ATTR_ID = "id";
    public static final String ATTR_TTS_ORIGIN = "origin";
    public static final String ATTR_TTS_EXTENT = "extent";
    public static final String ATTR_TTS_DISPLAY_ALIGN = "displayAlign";
    public static final String ATTR_TTS_BACKGROUND_COLOR = "backgroundColor";
    public static final String ATTR_TTS_FONT_STYLE = "fontStyle";
    public static final String ATTR_TTS_FONT_SIZE = "fontSize";
    public static final String ATTR_TTS_FONT_FAMILY = "fontFamily";
    public static final String ATTR_TTS_FONT_WEIGHT = "fontWeight";
    public static final String ATTR_TTS_COLOR = "color";
    public static final String ATTR_TTS_TEXT_DECORATION = "textDecoration";
    public static final String ATTR_TTS_TEXT_ALIGN = "textAlign";
    public static final String LINETHROUGH = "linethrough";
    public static final String NO_LINETHROUGH = "nolinethrough";
    public static final String UNDERLINE = "underline";
    public static final String NO_UNDERLINE = "nounderline";
    public static final String ITALIC = "italic";
    public static final String BOLD = "bold";
    public static final String LEFT = "left";
    public static final String CENTER = "center";
    public static final String RIGHT = "right";
    public static final String START = "start";
    public static final String END = "end";
    @Nullable
    public final String tag;
    @Nullable
    public final String text;
    public final boolean isTextNode;
    public final long startTimeUs;
    public final long endTimeUs;
    @Nullable
    public final TtmlStyle style;
    @Nullable
    private final String[] styleIds;
    public final String regionId;
    @Nullable
    public final String imageId;
    private final HashMap<String, Integer> nodeStartsByRegion;
    private final HashMap<String, Integer> nodeEndsByRegion;
    private List<TtmlNode> children;

    public static TtmlNode buildTextNode(String text) {
        return new TtmlNode(null, TtmlRenderUtil.applyTextElementSpacePolicy(text), -Long.MAX_VALUE, -Long.MAX_VALUE, null, null, "", null);
    }

    public static TtmlNode buildNode(@Nullable String tag, long startTimeUs, long endTimeUs, @Nullable TtmlStyle style, @Nullable String[] styleIds, String regionId, @Nullable String imageId) {
        return new TtmlNode(tag, null, startTimeUs, endTimeUs, style, styleIds, regionId, imageId);
    }

    private TtmlNode(@Nullable String tag, @Nullable String text, long startTimeUs, long endTimeUs, @Nullable TtmlStyle style, @Nullable String[] styleIds, String regionId, @Nullable String imageId) {
        this.tag = tag;
        this.text = text;
        this.imageId = imageId;
        this.style = style;
        this.styleIds = styleIds;
        this.isTextNode = text != null;
        this.startTimeUs = startTimeUs;
        this.endTimeUs = endTimeUs;
        this.regionId = Assertions.checkNotNull(regionId);
        this.nodeStartsByRegion = new HashMap();
        this.nodeEndsByRegion = new HashMap();
    }

    public boolean isActive(long timeUs) {
        return this.startTimeUs == -Long.MAX_VALUE && this.endTimeUs == -Long.MAX_VALUE || this.startTimeUs <= timeUs && this.endTimeUs == -Long.MAX_VALUE || this.startTimeUs == -Long.MAX_VALUE && timeUs < this.endTimeUs || this.startTimeUs <= timeUs && timeUs < this.endTimeUs;
    }

    public void addChild(TtmlNode child) {
        if (this.children == null) {
            this.children = new ArrayList();
        }

        this.children.add(child);
    }

    public TtmlNode getChild(int index) {
        if (this.children == null) {
            throw new IndexOutOfBoundsException();
        } else {
            return this.children.get(index);
        }
    }

    public int getChildCount() {
        return this.children == null ? 0 : this.children.size();
    }

    public long[] getEventTimesUs() {
        TreeSet<Long> eventTimeSet = new TreeSet();
        this.getEventTimes(eventTimeSet, false);
        long[] eventTimes = new long[eventTimeSet.size()];
        int i = 0;

        long eventTimeUs;
        for(Iterator var4 = eventTimeSet.iterator(); var4.hasNext(); eventTimes[i++] = eventTimeUs) {
            eventTimeUs = (Long)var4.next();
        }

        return eventTimes;
    }

    private void getEventTimes(TreeSet<Long> out, boolean descendsPNode) {
        boolean isPNode = "p".equals(this.tag);
        boolean isDivNode = "div".equals(this.tag);
        if (descendsPNode || isPNode || isDivNode && this.imageId != null) {
            if (this.startTimeUs != -Long.MAX_VALUE) {
                out.add(this.startTimeUs);
            }

            if (this.endTimeUs != -Long.MAX_VALUE) {
                out.add(this.endTimeUs);
            }
        }

        if (this.children != null) {
            for(int i = 0; i < this.children.size(); ++i) {
                this.children.get(i).getEventTimes(out, descendsPNode || isPNode);
            }

        }
    }

    public String[] getStyleIds() {
        return this.styleIds;
    }

    public List<Cue> getCues(long timeUs, Map<String, TtmlStyle> globalStyles, Map<String, TtmlRegion> regionMap, Map<String, String> imageMap) {
        List<Pair<String, String>> regionImageOutputs = new ArrayList();
        this.traverseForImage(timeUs, this.regionId, regionImageOutputs);
        TreeMap<String, SpannableStringBuilder> regionTextOutputs = new TreeMap();
        this.traverseForText(timeUs, false, this.regionId, regionTextOutputs);
        this.traverseForStyle(timeUs, globalStyles, regionTextOutputs);
        List<Cue> cues = new ArrayList();
        Iterator var9 = regionImageOutputs.iterator();

        while(var9.hasNext()) {
            Pair<String, String> regionImagePair = (Pair)var9.next();
            String encodedBitmapData = imageMap.get(regionImagePair.second);
            if (encodedBitmapData != null) {
                byte[] bitmapData = Base64.decode(encodedBitmapData, 0);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.length);
                TtmlRegion region = regionMap.get(regionImagePair.first);
                cues.add(new Cue(bitmap, region.position, 1, region.line, region.lineAnchor, region.width, 1.4E-45F));
            }
        }

        var9 = regionTextOutputs.entrySet().iterator();

        while(var9.hasNext()) {
            Entry<String, SpannableStringBuilder> entry = (Entry)var9.next();
            TtmlRegion region = regionMap.get(entry.getKey());
            cues.add(new Cue(this.cleanUpText(entry.getValue()), null, region.line, region.lineType, region.lineAnchor, region.position, -2147483648, region.width, region.textSizeType, region.textSize));
        }

        return cues;
    }

    private void traverseForImage(long timeUs, String inheritedRegion, List<Pair<String, String>> regionImageList) {
        String resolvedRegionId = "".equals(this.regionId) ? inheritedRegion : this.regionId;
        if (this.isActive(timeUs) && "div".equals(this.tag) && this.imageId != null) {
            regionImageList.add(new Pair(resolvedRegionId, this.imageId));
        } else {
            for(int i = 0; i < this.getChildCount(); ++i) {
                this.getChild(i).traverseForImage(timeUs, resolvedRegionId, regionImageList);
            }

        }
    }

    private void traverseForText(long timeUs, boolean descendsPNode, String inheritedRegion, Map<String, SpannableStringBuilder> regionOutputs) {
        this.nodeStartsByRegion.clear();
        this.nodeEndsByRegion.clear();
        if (!"metadata".equals(this.tag)) {
            String resolvedRegionId = "".equals(this.regionId) ? inheritedRegion : this.regionId;
            if (this.isTextNode && descendsPNode) {
                getRegionOutput(resolvedRegionId, regionOutputs).append(this.text);
            } else if ("br".equals(this.tag) && descendsPNode) {
                getRegionOutput(resolvedRegionId, regionOutputs).append('\n');
            } else if (this.isActive(timeUs)) {
                Iterator var7 = regionOutputs.entrySet().iterator();

                while(var7.hasNext()) {
                    Entry<String, SpannableStringBuilder> entry = (Entry)var7.next();
                    this.nodeStartsByRegion.put(entry.getKey(), entry.getValue().length());
                }

                boolean isPNode = "p".equals(this.tag);

                for(int i = 0; i < this.getChildCount(); ++i) {
                    this.getChild(i).traverseForText(timeUs, descendsPNode || isPNode, resolvedRegionId, regionOutputs);
                }

                if (isPNode) {
                    TtmlRenderUtil.endParagraph(getRegionOutput(resolvedRegionId, regionOutputs));
                }

                Iterator var12 = regionOutputs.entrySet().iterator();

                while(var12.hasNext()) {
                    Entry<String, SpannableStringBuilder> entry = (Entry)var12.next();
                    this.nodeEndsByRegion.put(entry.getKey(), entry.getValue().length());
                }
            }

        }
    }

    private static SpannableStringBuilder getRegionOutput(String resolvedRegionId, Map<String, SpannableStringBuilder> regionOutputs) {
        if (!regionOutputs.containsKey(resolvedRegionId)) {
            regionOutputs.put(resolvedRegionId, new SpannableStringBuilder());
        }

        return regionOutputs.get(resolvedRegionId);
    }

    private void traverseForStyle(long timeUs, Map<String, TtmlStyle> globalStyles, Map<String, SpannableStringBuilder> regionOutputs) {
        if (this.isActive(timeUs)) {
            Iterator var5 = this.nodeEndsByRegion.entrySet().iterator();

            while(var5.hasNext()) {
                Entry<String, Integer> entry = (Entry)var5.next();
                String regionId = entry.getKey();
                int start = this.nodeStartsByRegion.containsKey(regionId) ? this.nodeStartsByRegion.get(regionId) : 0;
                int end = entry.getValue();
                if (start != end) {
                    SpannableStringBuilder regionOutput = regionOutputs.get(regionId);
                    this.applyStyleToOutput(globalStyles, regionOutput, start, end);
                }
            }

            for(int i = 0; i < this.getChildCount(); ++i) {
                this.getChild(i).traverseForStyle(timeUs, globalStyles, regionOutputs);
            }

        }
    }

    private void applyStyleToOutput(Map<String, TtmlStyle> globalStyles, SpannableStringBuilder regionOutput, int start, int end) {
        TtmlStyle resolvedStyle = TtmlRenderUtil.resolveStyle(this.style, this.styleIds, globalStyles);
        if (resolvedStyle != null) {
            TtmlRenderUtil.applyStylesToSpan(regionOutput, start, end, resolvedStyle);
        }

    }

    private SpannableStringBuilder cleanUpText(SpannableStringBuilder builder) {
        int builderLength = builder.length();

        int i;
        for(i = 0; i < builderLength; ++i) {
            if (builder.charAt(i) == ' ') {
                int j;
                for(j = i + 1; j < builder.length() && builder.charAt(j) == ' '; ++j) {
                }

                int spacesToDelete = j - (i + 1);
                if (spacesToDelete > 0) {
                    builder.delete(i, i + spacesToDelete);
                    builderLength -= spacesToDelete;
                }
            }
        }

        if (builderLength > 0 && builder.charAt(0) == ' ') {
            builder.delete(0, 1);
            --builderLength;
        }

        for(i = 0; i < builderLength - 1; ++i) {
            if (builder.charAt(i) == '\n' && builder.charAt(i + 1) == ' ') {
                builder.delete(i + 1, i + 2);
                --builderLength;
            }
        }

        if (builderLength > 0 && builder.charAt(builderLength - 1) == ' ') {
            builder.delete(builderLength - 1, builderLength);
            --builderLength;
        }

        for(i = 0; i < builderLength - 1; ++i) {
            if (builder.charAt(i) == ' ' && builder.charAt(i + 1) == '\n') {
                builder.delete(i, i + 1);
                --builderLength;
            }
        }

        if (builderLength > 0 && builder.charAt(builderLength - 1) == '\n') {
            builder.delete(builderLength - 1, builderLength);
        }

        return builder;
    }
}
