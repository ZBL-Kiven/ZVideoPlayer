package com.zj.playerLib.upstream.cache;

import com.zj.playerLib.upstream.DataSpec;

public interface CacheKeyFactory {
    String buildCacheKey(DataSpec var1);
}
