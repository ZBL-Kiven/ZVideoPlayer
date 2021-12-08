package com.zj.playerLib.upstream.cache;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SimpleCacheSpan extends CacheSpan {
    private static final String SUFFIX_V1 = ".player.cached";
    private static final Pattern CACHE_FILE_PATTERN_V1 = Pattern.compile("^(.+)\\.(\\d+)\\.(\\d+)\\.player\\.cached$", Pattern.DOTALL);

    public static File getCacheFile(File cacheDir, int id, long position, long lastAccessTimestamp) {
        return new File(cacheDir, id + "." + position + "." + lastAccessTimestamp + SUFFIX_V1);
    }

    public static SimpleCacheSpan createLookup(String key, long position) {
        return new SimpleCacheSpan(key, position, -1L, -Long.MAX_VALUE, null);
    }

    public static SimpleCacheSpan createOpenHole(String key, long position) {
        return new SimpleCacheSpan(key, position, -1L, -Long.MAX_VALUE, null);
    }

    public static SimpleCacheSpan createClosedHole(String key, long position, long length) {
        return new SimpleCacheSpan(key, position, length, -Long.MAX_VALUE, null);
    }

    @Nullable
    public static SimpleCacheSpan createCacheEntry(File file, CachedContentIndex index) {
        String name = file.getName();
        if (!name.endsWith(SUFFIX_V1)) {
            file = upgradeFile(file, index);
            if (file == null) {
                return null;
            }

            name = file.getName();
        }

        Matcher matcher = CACHE_FILE_PATTERN_V1.matcher(name);
        if (!matcher.matches()) {
            return null;
        } else {
            long length = file.length();
            int id = Integer.parseInt(matcher.group(1));
            String key = index.getKeyForId(id);
            return key == null ? null : new SimpleCacheSpan(key, Long.parseLong(matcher.group(2)), length, Long.parseLong(matcher.group(3)), file);
        }
    }

    @Nullable
    private static File upgradeFile(File file, CachedContentIndex index) {
//        String filename = file.getName();
        //        Matcher matcher = CACHE_FILE_PATTERN_V2.matcher(filename);
        //        String key;
        //        if (matcher.matches()) {
        //            key = Util.unescapeFileName(matcher.group(1));
        //            if (key == null) {
        //                return null;
        //            }
        //        } else {
        //            matcher = CACHE_FILE_PATTERN_V1.matcher(filename);
        //            if (!matcher.matches()) {
        //                return null;
        //            }
        //
        //            key = matcher.group(1);
        //        }

        //        File newCacheFile = getCacheFile(file.getParentFile(), index.assignIdForKey(key), Long.parseLong(matcher.group(2)), Long.parseLong(matcher.group(3)));
        //        return !file.renameTo(newCacheFile) ? null : newCacheFile;
        return null;
    }

    private SimpleCacheSpan(String key, long position, long length, long lastAccessTimestamp, @Nullable File file) {
        super(key, position, length, lastAccessTimestamp, file);
    }

    public SimpleCacheSpan copyWithUpdatedLastAccessTime(int id) {
        Assertions.checkState(this.isCached);
        long now = System.currentTimeMillis();
        File newCacheFile = getCacheFile(this.file.getParentFile(), id, this.position, now);
        return new SimpleCacheSpan(this.key, this.position, this.length, now, newCacheFile);
    }
}
