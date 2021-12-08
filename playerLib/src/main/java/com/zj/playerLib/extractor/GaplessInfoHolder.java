package com.zj.playerLib.extractor;

import com.zj.playerLib.metadata.Metadata;
import com.zj.playerLib.metadata.Metadata.Entry;
import com.zj.playerLib.metadata.id3.CommentFrame;
import com.zj.playerLib.metadata.id3.InternalFrame;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GaplessInfoHolder {
    private static final String GAPLESS_DOMAIN = "com.apple.iTunes";
    private static final String GAPLESS_DESCRIPTION = "iTunSMPB";
    private static final Pattern GAPLESS_COMMENT_PATTERN = Pattern.compile("^ [0-9a-fA-F]{8} ([0-9a-fA-F]{8}) ([0-9a-fA-F]{8})");
    public int encoderDelay = -1;
    public int encoderPadding = -1;

    public GaplessInfoHolder() {
    }

    public boolean setFromXingHeaderValue(int value) {
        int encoderDelay = value >> 12;
        int encoderPadding = value & 4095;
        if (encoderDelay <= 0 && encoderPadding <= 0) {
            return false;
        } else {
            this.encoderDelay = encoderDelay;
            this.encoderPadding = encoderPadding;
            return true;
        }
    }

    public boolean setFromMetadata(Metadata metadata) {
        for(int i = 0; i < metadata.length(); ++i) {
            Entry entry = metadata.get(i);
            if (entry instanceof CommentFrame) {
                CommentFrame commentFrame = (CommentFrame)entry;
                if ("iTunSMPB".equals(commentFrame.description) && this.setFromComment(commentFrame.text)) {
                    return true;
                }
            } else if (entry instanceof InternalFrame) {
                InternalFrame internalFrame = (InternalFrame)entry;
                if ("com.apple.iTunes".equals(internalFrame.domain) && "iTunSMPB".equals(internalFrame.description) && this.setFromComment(internalFrame.text)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean setFromComment(String data) {
        Matcher matcher = GAPLESS_COMMENT_PATTERN.matcher(data);
        if (matcher.find()) {
            try {
                int encoderDelay = Integer.parseInt(matcher.group(1), 16);
                int encoderPadding = Integer.parseInt(matcher.group(2), 16);
                if (encoderDelay > 0 || encoderPadding > 0) {
                    this.encoderDelay = encoderDelay;
                    this.encoderPadding = encoderPadding;
                    return true;
                }
            } catch (NumberFormatException var5) {
            }
        }

        return false;
    }

    public boolean hasGaplessInfo() {
        return this.encoderDelay != -1 && this.encoderPadding != -1;
    }
}
