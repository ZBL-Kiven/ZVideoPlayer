//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.text.subrip;

import android.text.Html;
import android.text.Layout.Alignment;
import android.text.Spanned;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.zj.playerLib.text.Cue;
import com.zj.playerLib.text.SimpleSubtitleDecoder;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.LongArray;
import com.zj.playerLib.util.ParsableByteArray;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SubripDecoder extends SimpleSubtitleDecoder {
    static final float START_FRACTION = 0.08F;
    static final float END_FRACTION = 0.92F;
    static final float MID_FRACTION = 0.5F;
    private static final String TAG = "SubripDecoder";
    private static final String SUBRIP_TIMECODE = "(?:(\\d+):)?(\\d+):(\\d+),(\\d+)";
    private static final Pattern SUBRIP_TIMING_LINE = Pattern.compile("\\s*((?:(\\d+):)?(\\d+):(\\d+),(\\d+))\\s*-->\\s*((?:(\\d+):)?(\\d+):(\\d+),(\\d+))?\\s*");
    private static final Pattern SUBRIP_TAG_PATTERN = Pattern.compile("\\{\\\\.*?\\}");
    private static final String SUBRIP_ALIGNMENT_TAG = "\\{\\\\an[1-9]\\}";
    private static final String ALIGN_BOTTOM_LEFT = "{\\an1}";
    private static final String ALIGN_BOTTOM_MID = "{\\an2}";
    private static final String ALIGN_BOTTOM_RIGHT = "{\\an3}";
    private static final String ALIGN_MID_LEFT = "{\\an4}";
    private static final String ALIGN_MID_MID = "{\\an5}";
    private static final String ALIGN_MID_RIGHT = "{\\an6}";
    private static final String ALIGN_TOP_LEFT = "{\\an7}";
    private static final String ALIGN_TOP_MID = "{\\an8}";
    private static final String ALIGN_TOP_RIGHT = "{\\an9}";
    private final StringBuilder textBuilder = new StringBuilder();
    private final ArrayList<String> tags = new ArrayList();

    public SubripDecoder() {
        super("SubripDecoder");
    }

    protected SubripSubtitle decode(byte[] bytes, int length, boolean reset) {
        ArrayList<Cue> cues = new ArrayList();
        LongArray cueTimesUs = new LongArray();
        ParsableByteArray subripData = new ParsableByteArray(bytes, length);

        String currentLine;
        while((currentLine = subripData.readLine()) != null) {
            if (currentLine.length() != 0) {
                try {
                    Integer.parseInt(currentLine);
                } catch (NumberFormatException var14) {
                    Log.w("SubripDecoder", "Skipping invalid index: " + currentLine);
                    continue;
                }

                boolean haveEndTimecode = false;
                currentLine = subripData.readLine();
                if (currentLine == null) {
                    Log.w("SubripDecoder", "Unexpected end");
                    break;
                }

                Matcher matcher = SUBRIP_TIMING_LINE.matcher(currentLine);
                if (!matcher.matches()) {
                    Log.w("SubripDecoder", "Skipping invalid timing: " + currentLine);
                } else {
                    cueTimesUs.add(parseTimecode(matcher, 1));
                    if (!TextUtils.isEmpty(matcher.group(6))) {
                        haveEndTimecode = true;
                        cueTimesUs.add(parseTimecode(matcher, 6));
                    }

                    this.textBuilder.setLength(0);
                    this.tags.clear();

                    for(; !TextUtils.isEmpty(currentLine = subripData.readLine()); this.textBuilder.append(this.processLine(currentLine, this.tags))) {
                        if (this.textBuilder.length() > 0) {
                            this.textBuilder.append("<br>");
                        }
                    }

                    Spanned text = Html.fromHtml(this.textBuilder.toString());
                    String alignmentTag = null;

                    for(int i = 0; i < this.tags.size(); ++i) {
                        String tag = (String)this.tags.get(i);
                        if (tag.matches("\\{\\\\an[1-9]\\}")) {
                            alignmentTag = tag;
                            break;
                        }
                    }

                    cues.add(this.buildCue(text, alignmentTag));
                    if (haveEndTimecode) {
                        cues.add(null);
                    }
                }
            }
        }

        Cue[] cuesArray = new Cue[cues.size()];
        cues.toArray(cuesArray);
        long[] cueTimesUsArray = cueTimesUs.toArray();
        return new SubripSubtitle(cuesArray, cueTimesUsArray);
    }

    private String processLine(String line, ArrayList<String> tags) {
        line = line.trim();
        int removedCharacterCount = 0;
        StringBuilder processedLine = new StringBuilder(line);

        int tagLength;
        for(Matcher matcher = SUBRIP_TAG_PATTERN.matcher(line); matcher.find(); removedCharacterCount += tagLength) {
            String tag = matcher.group();
            tags.add(tag);
            int start = matcher.start() - removedCharacterCount;
            tagLength = tag.length();
            processedLine.replace(start, start + tagLength, "");
        }

        return processedLine.toString();
    }

    private Cue buildCue(Spanned text, @Nullable String alignmentTag) {
        if (alignmentTag == null) {
            return new Cue(text);
        } else {
            byte var5 = -1;
            switch(alignmentTag.hashCode()) {
            case -685620710:
                if (alignmentTag.equals("{\\an1}")) {
                    var5 = 0;
                }
                break;
            case -685620679:
                if (alignmentTag.equals("{\\an2}")) {
                    var5 = 6;
                }
                break;
            case -685620648:
                if (alignmentTag.equals("{\\an3}")) {
                    var5 = 3;
                }
                break;
            case -685620617:
                if (alignmentTag.equals("{\\an4}")) {
                    var5 = 1;
                }
                break;
            case -685620586:
                if (alignmentTag.equals("{\\an5}")) {
                    var5 = 7;
                }
                break;
            case -685620555:
                if (alignmentTag.equals("{\\an6}")) {
                    var5 = 4;
                }
                break;
            case -685620524:
                if (alignmentTag.equals("{\\an7}")) {
                    var5 = 2;
                }
                break;
            case -685620493:
                if (alignmentTag.equals("{\\an8}")) {
                    var5 = 8;
                }
                break;
            case -685620462:
                if (alignmentTag.equals("{\\an9}")) {
                    var5 = 5;
                }
            }

            byte positionAnchor;
            switch(var5) {
            case 0:
            case 1:
            case 2:
                positionAnchor = 0;
                break;
            case 3:
            case 4:
            case 5:
                positionAnchor = 2;
                break;
            case 6:
            case 7:
            case 8:
            default:
                positionAnchor = 1;
            }

            byte var6 = -1;
            switch(alignmentTag.hashCode()) {
            case -685620710:
                if (alignmentTag.equals("{\\an1}")) {
                    var6 = 0;
                }
                break;
            case -685620679:
                if (alignmentTag.equals("{\\an2}")) {
                    var6 = 1;
                }
                break;
            case -685620648:
                if (alignmentTag.equals("{\\an3}")) {
                    var6 = 2;
                }
                break;
            case -685620617:
                if (alignmentTag.equals("{\\an4}")) {
                    var6 = 6;
                }
                break;
            case -685620586:
                if (alignmentTag.equals("{\\an5}")) {
                    var6 = 7;
                }
                break;
            case -685620555:
                if (alignmentTag.equals("{\\an6}")) {
                    var6 = 8;
                }
                break;
            case -685620524:
                if (alignmentTag.equals("{\\an7}")) {
                    var6 = 3;
                }
                break;
            case -685620493:
                if (alignmentTag.equals("{\\an8}")) {
                    var6 = 4;
                }
                break;
            case -685620462:
                if (alignmentTag.equals("{\\an9}")) {
                    var6 = 5;
                }
            }

            byte lineAnchor;
            switch(var6) {
            case 0:
            case 1:
            case 2:
                lineAnchor = 2;
                break;
            case 3:
            case 4:
            case 5:
                lineAnchor = 0;
                break;
            case 6:
            case 7:
            case 8:
            default:
                lineAnchor = 1;
            }

            return new Cue(text, (Alignment)null, getFractionalPositionForAnchorType(lineAnchor), 0, lineAnchor, getFractionalPositionForAnchorType(positionAnchor), positionAnchor, 1.4E-45F);
        }
    }

    private static long parseTimecode(Matcher matcher, int groupOffset) {
        long timestampMs = Long.parseLong(matcher.group(groupOffset + 1)) * 60L * 60L * 1000L;
        timestampMs += Long.parseLong(matcher.group(groupOffset + 2)) * 60L * 1000L;
        timestampMs += Long.parseLong(matcher.group(groupOffset + 3)) * 1000L;
        timestampMs += Long.parseLong(matcher.group(groupOffset + 4));
        return timestampMs * 1000L;
    }

    static float getFractionalPositionForAnchorType(int anchorType) {
        switch(anchorType) {
        case 0:
            return 0.08F;
        case 1:
            return 0.5F;
        case 2:
        default:
            return 0.92F;
        }
    }
}
