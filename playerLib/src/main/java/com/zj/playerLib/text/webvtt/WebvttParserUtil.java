package com.zj.playerLib.text.webvtt;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WebvttParserUtil {
    private static final Pattern COMMENT = Pattern.compile("^NOTE(( |\t).*)?$");
    private static final String WEBVTT_HEADER = "WEBVTT";

    private WebvttParserUtil() {
    }

    public static void validateWebvttHeaderLine(ParsableByteArray input) throws ParserException {
        int startPosition = input.getPosition();
        if (!isWebvttHeaderLine(input)) {
            input.setPosition(startPosition);
            throw new ParserException("Expected WEBVTT. Got " + input.readLine());
        }
    }

    public static boolean isWebvttHeaderLine(ParsableByteArray input) {
        String line = input.readLine();
        return line != null && line.startsWith("WEBVTT");
    }

    public static long parseTimestampUs(String timestamp) throws NumberFormatException {
        long value = 0L;
        String[] parts = Util.splitAtFirst(timestamp, "\\.");
        String[] subparts = Util.split(parts[0], ":");
        String[] var5 = subparts;
        int var6 = subparts.length;

        for(int var7 = 0; var7 < var6; ++var7) {
            String subpart = var5[var7];
            value = value * 60L + Long.parseLong(subpart);
        }

        value *= 1000L;
        if (parts.length == 2) {
            value += Long.parseLong(parts[1]);
        }

        return value * 1000L;
    }

    public static float parsePercentage(String s) throws NumberFormatException {
        if (!s.endsWith("%")) {
            throw new NumberFormatException("Percentages must end with %");
        } else {
            return Float.parseFloat(s.substring(0, s.length() - 1)) / 100.0F;
        }
    }

    public static Matcher findNextCueHeader(ParsableByteArray input) {
        label23:
        while(true) {
            String line;
            if ((line = input.readLine()) != null) {
                if (COMMENT.matcher(line).matches()) {
                    while(true) {
                        if ((line = input.readLine()) == null || line.isEmpty()) {
                            continue label23;
                        }
                    }
                }

                Matcher cueHeaderMatcher = WebvttCueParser.CUE_HEADER_PATTERN.matcher(line);
                if (!cueHeaderMatcher.matches()) {
                    continue;
                }

                return cueHeaderMatcher;
            }

            return null;
        }
    }
}
