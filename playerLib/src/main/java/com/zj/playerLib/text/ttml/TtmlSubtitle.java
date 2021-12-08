package com.zj.playerLib.text.ttml;

import com.zj.playerLib.text.Cue;
import com.zj.playerLib.text.Subtitle;
import com.zj.playerLib.util.Util;

import java.util.Collections;
import java.util.List;
import java.util.Map;

final class TtmlSubtitle implements Subtitle {
    private final TtmlNode root;
    private final long[] eventTimesUs;
    private final Map<String, TtmlStyle> globalStyles;
    private final Map<String, TtmlRegion> regionMap;
    private final Map<String, String> imageMap;

    public TtmlSubtitle(TtmlNode root, Map<String, TtmlStyle> globalStyles, Map<String, TtmlRegion> regionMap, Map<String, String> imageMap) {
        this.root = root;
        this.regionMap = regionMap;
        this.imageMap = imageMap;
        this.globalStyles = globalStyles != null ? Collections.unmodifiableMap(globalStyles) : Collections.emptyMap();
        this.eventTimesUs = root.getEventTimesUs();
    }

    public int getNextEventTimeIndex(long timeUs) {
        int index = Util.binarySearchCeil(this.eventTimesUs, timeUs, false, false);
        return index < this.eventTimesUs.length ? index : -1;
    }

    public int getEventTimeCount() {
        return this.eventTimesUs.length;
    }

    public long getEventTime(int index) {
        return this.eventTimesUs[index];
    }

    TtmlNode getRoot() {
        return this.root;
    }

    public List<Cue> getCues(long timeUs) {
        return this.root.getCues(timeUs, this.globalStyles, this.regionMap, this.imageMap);
    }

    Map<String, TtmlStyle> getGlobalStyles() {
        return this.globalStyles;
    }
}
