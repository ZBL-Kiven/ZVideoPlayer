//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.offline;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.zj.playerLib.upstream.cache.CacheUtil;
import com.zj.playerLib.util.Util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class ProgressiveDownloadAction extends DownloadAction {
    private static final String TYPE = "progressive";
    private static final int VERSION = 0;
    public static final Deserializer DESERIALIZER = new Deserializer("progressive", 0) {
        public ProgressiveDownloadAction readFromStream(int version, DataInputStream input) throws IOException {
            Uri uri = Uri.parse(input.readUTF());
            boolean isRemoveAction = input.readBoolean();
            int dataLength = input.readInt();
            byte[] data = new byte[dataLength];
            input.readFully(data);
            String customCacheKey = input.readBoolean() ? input.readUTF() : null;
            return new ProgressiveDownloadAction(uri, isRemoveAction, data, customCacheKey);
        }
    };
    @Nullable
    private final String customCacheKey;

    public static ProgressiveDownloadAction createDownloadAction(Uri uri, @Nullable byte[] data, @Nullable String customCacheKey) {
        return new ProgressiveDownloadAction(uri, false, data, customCacheKey);
    }

    public static ProgressiveDownloadAction createRemoveAction(Uri uri, @Nullable byte[] data, @Nullable String customCacheKey) {
        return new ProgressiveDownloadAction(uri, true, data, customCacheKey);
    }

    /** @deprecated */
    @Deprecated
    public ProgressiveDownloadAction(Uri uri, boolean isRemoveAction, @Nullable byte[] data, @Nullable String customCacheKey) {
        super("progressive", 0, uri, isRemoveAction, data);
        this.customCacheKey = customCacheKey;
    }

    public ProgressiveDownloader createDownloader(DownloaderConstructorHelper constructorHelper) {
        return new ProgressiveDownloader(this.uri, this.customCacheKey, constructorHelper);
    }

    protected void writeToStream(DataOutputStream output) throws IOException {
        output.writeUTF(this.uri.toString());
        output.writeBoolean(this.isRemoveAction);
        output.writeInt(this.data.length);
        output.write(this.data);
        boolean customCacheKeySet = this.customCacheKey != null;
        output.writeBoolean(customCacheKeySet);
        if (customCacheKeySet) {
            output.writeUTF(this.customCacheKey);
        }

    }

    public boolean isSameMedia(DownloadAction other) {
        return other instanceof ProgressiveDownloadAction && this.getCacheKey().equals(((ProgressiveDownloadAction)other).getCacheKey());
    }

    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        } else if (!super.equals(o)) {
            return false;
        } else {
            ProgressiveDownloadAction that = (ProgressiveDownloadAction)o;
            return Util.areEqual(this.customCacheKey, that.customCacheKey);
        }
    }

    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (this.customCacheKey != null ? this.customCacheKey.hashCode() : 0);
        return result;
    }

    private String getCacheKey() {
        return this.customCacheKey != null ? this.customCacheKey : CacheUtil.generateKey(this.uri);
    }
}
