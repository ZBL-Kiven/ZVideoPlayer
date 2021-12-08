package com.zj.playerLib.text.cea;

import android.text.Layout.Alignment;

import androidx.annotation.NonNull;

import com.zj.playerLib.text.Cue;

final class Cea708Cue extends Cue implements Comparable<Cea708Cue> {
    public static final int PRIORITY_UNSET = -1;
    public final int priority;

    public Cea708Cue(CharSequence text, Alignment textAlignment, float line, int lineType, int lineAnchor, float position, int positionAnchor, float size, boolean windowColorSet, int windowColor, int priority) {
        super(text, textAlignment, line, lineType, lineAnchor, position, positionAnchor, size, windowColorSet, windowColor);
        this.priority = priority;
    }

    public int compareTo(@NonNull Cea708Cue other) {
        if (other.priority < this.priority) {
            return -1;
        } else {
            return other.priority > this.priority ? 1 : 0;
        }
    }
}
