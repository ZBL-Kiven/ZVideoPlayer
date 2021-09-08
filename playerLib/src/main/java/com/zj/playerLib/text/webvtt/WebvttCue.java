//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.text.webvtt;

import android.text.SpannableStringBuilder;
import android.text.Layout.Alignment;
import com.zj.playerLib.text.Cue;
import com.zj.playerLib.util.Log;

public final class WebvttCue extends Cue {
    public final long startTime;
    public final long endTime;

    public WebvttCue(CharSequence text) {
        this(0L, 0L, text);
    }

    public WebvttCue(long startTime, long endTime, CharSequence text) {
        this(startTime, endTime, text, (Alignment)null, 1.4E-45F, -2147483648, -2147483648, 1.4E-45F, -2147483648, 1.4E-45F);
    }

    public WebvttCue(long startTime, long endTime, CharSequence text, Alignment textAlignment, float line, int lineType, int lineAnchor, float position, int positionAnchor, float width) {
        super(text, textAlignment, line, lineType, lineAnchor, position, positionAnchor, width);
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public boolean isNormalCue() {
        return this.line == 1.4E-45F && this.position == 1.4E-45F;
    }

    public static class Builder {
        private static final String TAG = "WebvttCueBuilder";
        private long startTime;
        private long endTime;
        private SpannableStringBuilder text;
        private Alignment textAlignment;
        private float line;
        private int lineType;
        private int lineAnchor;
        private float position;
        private int positionAnchor;
        private float width;

        public Builder() {
            this.reset();
        }

        public void reset() {
            this.startTime = 0L;
            this.endTime = 0L;
            this.text = null;
            this.textAlignment = null;
            this.line = 1.4E-45F;
            this.lineType = -2147483648;
            this.lineAnchor = -2147483648;
            this.position = 1.4E-45F;
            this.positionAnchor = -2147483648;
            this.width = 1.4E-45F;
        }

        public WebvttCue build() {
            if (this.position != 1.4E-45F && this.positionAnchor == -2147483648) {
                this.derivePositionAnchorFromAlignment();
            }

            return new WebvttCue(this.startTime, this.endTime, this.text, this.textAlignment, this.line, this.lineType, this.lineAnchor, this.position, this.positionAnchor, this.width);
        }

        public Builder setStartTime(long time) {
            this.startTime = time;
            return this;
        }

        public Builder setEndTime(long time) {
            this.endTime = time;
            return this;
        }

        public Builder setText(SpannableStringBuilder aText) {
            this.text = aText;
            return this;
        }

        public Builder setTextAlignment(Alignment textAlignment) {
            this.textAlignment = textAlignment;
            return this;
        }

        public Builder setLine(float line) {
            this.line = line;
            return this;
        }

        public Builder setLineType(int lineType) {
            this.lineType = lineType;
            return this;
        }

        public Builder setLineAnchor(int lineAnchor) {
            this.lineAnchor = lineAnchor;
            return this;
        }

        public Builder setPosition(float position) {
            this.position = position;
            return this;
        }

        public Builder setPositionAnchor(int positionAnchor) {
            this.positionAnchor = positionAnchor;
            return this;
        }

        public Builder setWidth(float width) {
            this.width = width;
            return this;
        }

        private Builder derivePositionAnchorFromAlignment() {
            if (this.textAlignment == null) {
                this.positionAnchor = -2147483648;
            } else {
                switch(this.textAlignment) {
                case ALIGN_NORMAL:
                    this.positionAnchor = 0;
                    break;
                case ALIGN_CENTER:
                    this.positionAnchor = 1;
                    break;
                case ALIGN_OPPOSITE:
                    this.positionAnchor = 2;
                    break;
                default:
                    Log.w("WebvttCueBuilder", "Unrecognized alignment: " + this.textAlignment);
                    this.positionAnchor = 0;
                }
            }

            return this;
        }
    }
}
