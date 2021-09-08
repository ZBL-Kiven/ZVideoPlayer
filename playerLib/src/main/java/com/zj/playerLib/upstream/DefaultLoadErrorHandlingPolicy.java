//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.upstream.HttpDataSource.InvalidResponseCodeException;
import java.io.IOException;

public class DefaultLoadErrorHandlingPolicy implements LoadErrorHandlingPolicy {
    public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT = 3;
    public static final int DEFAULT_MIN_LOADABLE_RETRY_COUNT_PROGRESSIVE_LIVE = 6;
    public static final long DEFAULT_TRACK_BLACKLIST_MS = 60000L;
    private static final int DEFAULT_BEHAVIOR_MIN_LOADABLE_RETRY_COUNT = -1;
    private final int minimumLoadableRetryCount;

    public DefaultLoadErrorHandlingPolicy() {
        this(-1);
    }

    public DefaultLoadErrorHandlingPolicy(int minimumLoadableRetryCount) {
        this.minimumLoadableRetryCount = minimumLoadableRetryCount;
    }

    public long getBlacklistDurationMsFor(int dataType, long loadDurationMs, IOException exception, int errorCount) {
        if (!(exception instanceof InvalidResponseCodeException)) {
            return -9223372036854775807L;
        } else {
            int responseCode = ((InvalidResponseCodeException)exception).responseCode;
            return responseCode != 404 && responseCode != 410 ? -9223372036854775807L : 60000L;
        }
    }

    public long getRetryDelayMsFor(int dataType, long loadDurationMs, IOException exception, int errorCount) {
        return exception instanceof ParserException ? -9223372036854775807L : (long)Math.min((errorCount - 1) * 1000, 5000);
    }

    public int getMinimumLoadableRetryCount(int dataType) {
        if (this.minimumLoadableRetryCount == -1) {
            return dataType == 7 ? 6 : 3;
        } else {
            return this.minimumLoadableRetryCount;
        }
    }
}
