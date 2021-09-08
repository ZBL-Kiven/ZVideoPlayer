//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.zj.playerLib.upstream.Loader.Loadable;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public final class ParsingLoadable<T> implements Loadable {
    public final DataSpec dataSpec;
    public final int type;
    private final StatsDataSource dataSource;
    private final Parser<? extends T> parser;
    @Nullable
    private volatile T result;

    public static <T> T load(DataSource dataSource, Parser<? extends T> parser, Uri uri, int type) throws IOException {
        ParsingLoadable<T> loadable = new ParsingLoadable(dataSource, uri, type, parser);
        loadable.load();
        return Assertions.checkNotNull(loadable.getResult());
    }

    public ParsingLoadable(DataSource dataSource, Uri uri, int type, Parser<? extends T> parser) {
        this(dataSource, new DataSpec(uri, 3), type, parser);
    }

    public ParsingLoadable(DataSource dataSource, DataSpec dataSpec, int type, Parser<? extends T> parser) {
        this.dataSource = new StatsDataSource(dataSource);
        this.dataSpec = dataSpec;
        this.type = type;
        this.parser = parser;
    }

    @Nullable
    public final T getResult() {
        return this.result;
    }

    public long bytesLoaded() {
        return this.dataSource.getBytesRead();
    }

    public Uri getUri() {
        return this.dataSource.getLastOpenedUri();
    }

    public Map<String, List<String>> getResponseHeaders() {
        return this.dataSource.getLastResponseHeaders();
    }

    public final void cancelLoad() {
    }

    public final void load() throws IOException {
        this.dataSource.resetBytesRead();
        DataSourceInputStream inputStream = new DataSourceInputStream(this.dataSource, this.dataSpec);

        try {
            inputStream.open();
            Uri dataSourceUri = (Uri)Assertions.checkNotNull(this.dataSource.getUri());
            this.result = this.parser.parse(dataSourceUri, inputStream);
        } finally {
            Util.closeQuietly(inputStream);
        }

    }

    public interface Parser<T> {
        T parse(Uri var1, InputStream var2) throws IOException;
    }
}
