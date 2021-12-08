package com.zj.playerLib.upstream;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.zj.playerLib.util.Assertions;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

public final class DataSpec {
    public static final int FLAG_ALLOW_GZIP = 1;
    public static final int FLAG_ALLOW_CACHING_UNKNOWN_LENGTH = 2;
    public static final int HTTP_METHOD_GET = 1;
    public static final int HTTP_METHOD_POST = 2;
    public static final int HTTP_METHOD_HEAD = 3;
    public final Uri uri;
    public final int httpMethod;
    @Nullable
    public final byte[] httpBody;
    /** @deprecated */
    @Deprecated
    @Nullable
    public final byte[] postBody;
    public final long absoluteStreamPosition;
    public final long position;
    public final long length;
    @Nullable
    public final String key;
    public final int flags;

    public DataSpec(Uri uri) {
        this(uri, 0);
    }

    public DataSpec(Uri uri, int flags) {
        this(uri, 0L, -1L, null, flags);
    }

    public DataSpec(Uri uri, long absoluteStreamPosition, long length, @Nullable String key) {
        this(uri, absoluteStreamPosition, absoluteStreamPosition, length, key, 0);
    }

    public DataSpec(Uri uri, long absoluteStreamPosition, long length, @Nullable String key, int flags) {
        this(uri, absoluteStreamPosition, absoluteStreamPosition, length, key, flags);
    }

    public DataSpec(Uri uri, long absoluteStreamPosition, long position, long length, @Nullable String key, int flags) {
        this(uri, null, absoluteStreamPosition, position, length, key, flags);
    }

    public DataSpec(Uri uri, @Nullable byte[] postBody, long absoluteStreamPosition, long position, long length, @Nullable String key, int flags) {
        this(uri, postBody != null ? 2 : 1, postBody, absoluteStreamPosition, position, length, key, flags);
    }

    public DataSpec(Uri uri, int httpMethod, @Nullable byte[] httpBody, long absoluteStreamPosition, long position, long length, @Nullable String key, int flags) {
        Assertions.checkArgument(absoluteStreamPosition >= 0L);
        Assertions.checkArgument(position >= 0L);
        Assertions.checkArgument(length > 0L || length == -1L);
        this.uri = uri;
        this.httpMethod = httpMethod;
        this.httpBody = httpBody != null && httpBody.length != 0 ? httpBody : null;
        this.postBody = this.httpBody;
        this.absoluteStreamPosition = absoluteStreamPosition;
        this.position = position;
        this.length = length;
        this.key = key;
        this.flags = flags;
    }

    public boolean isFlagSet(int flag) {
        return (this.flags & flag) == flag;
    }

    public String toString() {
        return "DataSpec[" + this.getHttpMethodString() + " " + this.uri + ", " + Arrays.toString(this.httpBody) + ", " + this.absoluteStreamPosition + ", " + this.position + ", " + this.length + ", " + this.key + ", " + this.flags + "]";
    }

    public final String getHttpMethodString() {
        return getStringForHttpMethod(this.httpMethod);
    }

    public static String getStringForHttpMethod(int httpMethod) {
        switch(httpMethod) {
        case 1:
            return "GET";
        case 2:
            return "POST";
        case 3:
            return "HEAD";
        default:
            throw new AssertionError(httpMethod);
        }
    }

    public DataSpec subrange(long offset) {
        return this.subrange(offset, this.length == -1L ? -1L : this.length - offset);
    }

    public DataSpec subrange(long offset, long length) {
        return offset == 0L && this.length == length ? this : new DataSpec(this.uri, this.httpMethod, this.httpBody, this.absoluteStreamPosition + offset, this.position + offset, length, this.key, this.flags);
    }

    public DataSpec withUri(Uri uri) {
        return new DataSpec(uri, this.httpMethod, this.httpBody, this.absoluteStreamPosition, this.position, this.length, this.key, this.flags);
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface HttpMethod {
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface Flags {
    }
}
