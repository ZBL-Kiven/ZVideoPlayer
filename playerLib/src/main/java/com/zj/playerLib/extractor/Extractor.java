package com.zj.playerLib.extractor;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface Extractor {
    int RESULT_CONTINUE = 0;
    int RESULT_SEEK = 1;
    int RESULT_END_OF_INPUT = -1;

    boolean sniff(ExtractorInput var1) throws IOException, InterruptedException;

    void init(ExtractorOutput var1);

    int read(ExtractorInput var1, PositionHolder var2) throws IOException, InterruptedException;

    void seek(long var1, long var3);

    void release();

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @interface ReadResult {
    }
}
