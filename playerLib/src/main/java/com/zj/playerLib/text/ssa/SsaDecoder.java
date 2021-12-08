package com.zj.playerLib.text.ssa;

import android.text.TextUtils;

import com.zj.playerLib.text.Cue;
import com.zj.playerLib.text.SimpleSubtitleDecoder;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.LongArray;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SsaDecoder extends SimpleSubtitleDecoder {
    private static final String TAG = "SsaDecoder";
    private static final Pattern SSA_TIMECODE_PATTERN = Pattern.compile("(?:(\\d+):)?(\\d+):(\\d+)(?::|\\.)(\\d+)");
    private static final String FORMAT_LINE_PREFIX = "Format: ";
    private static final String DIALOGUE_LINE_PREFIX = "Dialogue: ";
    private final boolean haveInitializationData;
    private int formatKeyCount;
    private int formatStartIndex;
    private int formatEndIndex;
    private int formatTextIndex;

    public SsaDecoder() {
        this(null);
    }

    public SsaDecoder(List<byte[]> initializationData) {
        super("SsaDecoder");
        if (initializationData != null && !initializationData.isEmpty()) {
            this.haveInitializationData = true;
            String formatLine = Util.fromUtf8Bytes(initializationData.get(0));
            Assertions.checkArgument(formatLine.startsWith("Format: "));
            this.parseFormatLine(formatLine);
            this.parseHeader(new ParsableByteArray(initializationData.get(1)));
        } else {
            this.haveInitializationData = false;
        }

    }

    protected SsaSubtitle decode(byte[] bytes, int length, boolean reset) {
        ArrayList<Cue> cues = new ArrayList();
        LongArray cueTimesUs = new LongArray();
        ParsableByteArray data = new ParsableByteArray(bytes, length);
        if (!this.haveInitializationData) {
            this.parseHeader(data);
        }

        this.parseEventBody(data, cues, cueTimesUs);
        Cue[] cuesArray = new Cue[cues.size()];
        cues.toArray(cuesArray);
        long[] cueTimesUsArray = cueTimesUs.toArray();
        return new SsaSubtitle(cuesArray, cueTimesUsArray);
    }

    private void parseHeader(ParsableByteArray data) {
        while (true) {
            String currentLine;
            if ((currentLine = data.readLine()) != null) {
                if (!currentLine.startsWith("[Events]")) {
                    continue;
                }

                return;
            }

            return;
        }
    }

    private void parseEventBody(ParsableByteArray data, List<Cue> cues, LongArray cueTimesUs) {
        String currentLine;
        while ((currentLine = data.readLine()) != null) {
            if (!this.haveInitializationData && currentLine.startsWith("Format: ")) {
                this.parseFormatLine(currentLine);
            } else if (currentLine.startsWith("Dialogue: ")) {
                this.parseDialogueLine(currentLine, cues, cueTimesUs);
            }
        }

    }

    private void parseFormatLine(String formatLine) {
        String[] values = TextUtils.split(formatLine.substring("Format: ".length()), ",");
        this.formatKeyCount = values.length;
        this.formatStartIndex = -1;
        this.formatEndIndex = -1;
        this.formatTextIndex = -1;

        for (int i = 0; i < this.formatKeyCount; ++i) {
            String key = Util.toLowerInvariant(values[i].trim());
            byte var6 = -1;
            switch (key.hashCode()) {
                case 100571:
                    if (key.equals("end")) {
                        var6 = 1;
                    }
                    break;
                case 3556653:
                    if (key.equals("text")) {
                        var6 = 2;
                    }
                    break;
                case 109757538:
                    if (key.equals("start")) {
                        var6 = 0;
                    }
            }

            switch (var6) {
                case 0:
                    this.formatStartIndex = i;
                    break;
                case 1:
                    this.formatEndIndex = i;
                    break;
                case 2:
                    this.formatTextIndex = i;
            }
        }

        if (this.formatStartIndex == -1 || this.formatEndIndex == -1 || this.formatTextIndex == -1) {
            this.formatKeyCount = 0;
        }

    }

    private void parseDialogueLine(String dialogueLine, List<Cue> cues, LongArray cueTimesUs) {
        if (this.formatKeyCount == 0) {
            Log.w("SsaDecoder", "Skipping dialogue line before complete format: " + dialogueLine);
        } else {
            String[] lineValues = dialogueLine.substring("Dialogue: ".length()).split(",", this.formatKeyCount);
            if (lineValues.length != this.formatKeyCount) {
                Log.w("SsaDecoder", "Skipping dialogue line with fewer columns than format: " + dialogueLine);
            } else {
                long startTimeUs = parseTimecodeUs(lineValues[this.formatStartIndex]);
                if (startTimeUs == -Long.MAX_VALUE) {
                    Log.w("SsaDecoder", "Skipping invalid timing: " + dialogueLine);
                } else {
                    long endTimeUs = -Long.MAX_VALUE;
                    String endTimeString = lineValues[this.formatEndIndex];
                    if (!endTimeString.trim().isEmpty()) {
                        endTimeUs = parseTimecodeUs(endTimeString);
                        if (endTimeUs == -Long.MAX_VALUE) {
                            Log.w("SsaDecoder", "Skipping invalid timing: " + dialogueLine);
                            return;
                        }
                    }

                    String text = lineValues[this.formatTextIndex].replaceAll("\\{.*?\\}", "").replaceAll("\\\\N", "\n").replaceAll("\\\\n", "\n");
                    cues.add(new Cue(text));
                    cueTimesUs.add(startTimeUs);
                    if (endTimeUs != -Long.MAX_VALUE) {
                        cues.add(null);
                        cueTimesUs.add(endTimeUs);
                    }

                }
            }
        }
    }

    public static long parseTimecodeUs(String timeString) {
        Matcher matcher = SSA_TIMECODE_PATTERN.matcher(timeString);
        if (!matcher.matches()) {
            return -Long.MAX_VALUE;
        } else {
            long timestampUs = Long.parseLong(matcher.group(1)) * 60L * 60L * 1000000L;
            timestampUs += Long.parseLong(matcher.group(2)) * 60L * 1000000L;
            timestampUs += Long.parseLong(matcher.group(3)) * 1000000L;
            timestampUs += Long.parseLong(matcher.group(4)) * 10000L;
            return timestampUs;
        }
    }
}
