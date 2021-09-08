//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.text.ttml;

final class TtmlRegion {
    public final String id;
    public final float position;
    public final float line;
    public final int lineType;
    public final int lineAnchor;
    public final float width;
    public final int textSizeType;
    public final float textSize;

    public TtmlRegion(String id) {
        this(id, 1.4E-45F, 1.4E-45F, -2147483648, -2147483648, 1.4E-45F, -2147483648, 1.4E-45F);
    }

    public TtmlRegion(String id, float position, float line, int lineType, int lineAnchor, float width, int textSizeType, float textSize) {
        this.id = id;
        this.position = position;
        this.line = line;
        this.lineType = lineType;
        this.lineAnchor = lineAnchor;
        this.width = width;
        this.textSizeType = textSizeType;
        this.textSize = textSize;
    }
}
