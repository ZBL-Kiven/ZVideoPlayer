package com.zj.playerLib.offline;

import android.net.Uri;

import com.zj.playerLib.upstream.ParsingLoadable.Parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class FilteringManifestParser<T extends FilterableManifest<T>> implements Parser<T> {
    private final Parser<T> parser;
    private final List<StreamKey> streamKeys;

    public FilteringManifestParser(Parser<T> parser, List<StreamKey> streamKeys) {
        this.parser = parser;
        this.streamKeys = streamKeys;
    }

    public T parse(Uri uri, InputStream inputStream) throws IOException {
        T manifest = this.parser.parse(uri, inputStream);
        return this.streamKeys != null && !this.streamKeys.isEmpty() ? manifest.copy(this.streamKeys) : manifest;
    }
}
