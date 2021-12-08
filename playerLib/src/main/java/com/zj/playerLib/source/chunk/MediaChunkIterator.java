package com.zj.playerLib.source.chunk;

import com.zj.playerLib.upstream.DataSpec;
import java.util.NoSuchElementException;

public interface MediaChunkIterator {
    MediaChunkIterator EMPTY = new MediaChunkIterator() {
        public boolean isEnded() {
            return true;
        }

        public boolean next() {
            return false;
        }

        public DataSpec getDataSpec() {
            throw new NoSuchElementException();
        }

        public long getChunkStartTimeUs() {
            throw new NoSuchElementException();
        }

        public long getChunkEndTimeUs() {
            throw new NoSuchElementException();
        }
    };

    boolean isEnded();

    boolean next();

    DataSpec getDataSpec();

    long getChunkStartTimeUs();

    long getChunkEndTimeUs();
}
