//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source.chunk;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.zj.playerLib.Format;
import com.zj.playerLib.upstream.DataSource;
import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.upstream.Loader.Loadable;
import com.zj.playerLib.upstream.StatsDataSource;
import com.zj.playerLib.util.Assertions;

import java.util.List;
import java.util.Map;

public abstract class Chunk implements Loadable {
    public final DataSpec dataSpec;
    public final int type;
    public final Format trackFormat;
    public final int trackSelectionReason;
    @Nullable
    public final Object trackSelectionData;
    public final long startTimeUs;
    public final long endTimeUs;
    protected final StatsDataSource dataSource;

    public Chunk(DataSource dataSource, DataSpec dataSpec, int type, Format trackFormat, int trackSelectionReason, @Nullable Object trackSelectionData, long startTimeUs, long endTimeUs) {
        this.dataSource = new StatsDataSource(dataSource);
        this.dataSpec = (DataSpec)Assertions.checkNotNull(dataSpec);
        this.type = type;
        this.trackFormat = trackFormat;
        this.trackSelectionReason = trackSelectionReason;
        this.trackSelectionData = trackSelectionData;
        this.startTimeUs = startTimeUs;
        this.endTimeUs = endTimeUs;
    }

    public final long getDurationUs() {
        return this.endTimeUs - this.startTimeUs;
    }

    public final long bytesLoaded() {
        return this.dataSource.getBytesRead();
    }

    public final Uri getUri() {
        return this.dataSource.getLastOpenedUri();
    }

    public final Map<String, List<String>> getResponseHeaders() {
        return this.dataSource.getLastResponseHeaders();
    }
}
