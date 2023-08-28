package com.zj.playerLib.upstream.cache;

import android.net.Uri;

import androidx.annotation.Nullable;

final class ContentMetadataInternal {
    private static final String PREFIX = "player_";
    private static final String METADATA_NAME_REDIRECTED_URI = PREFIX + "reDir";
    private static final String METADATA_NAME_CONTENT_LENGTH = PREFIX + "len";

    public static long getContentLength(ContentMetadata contentMetadata) {
        return contentMetadata.get(METADATA_NAME_CONTENT_LENGTH, -1L);
    }

    public static void setContentLength(ContentMetadataMutations mutations, long length) {
        mutations.set(METADATA_NAME_CONTENT_LENGTH, length);
    }

    public static void removeContentLength(ContentMetadataMutations mutations) {
        mutations.remove(METADATA_NAME_CONTENT_LENGTH);
    }

    @Nullable
    public static Uri getRedirectedUri(ContentMetadata contentMetadata) {
        String redirectedUri = contentMetadata.get(METADATA_NAME_REDIRECTED_URI, (String) null);
        return redirectedUri == null ? null : Uri.parse(redirectedUri);
    }

    public static void setRedirectedUri(ContentMetadataMutations mutations, Uri uri) {
        mutations.set(METADATA_NAME_REDIRECTED_URI, uri.toString());
    }

    public static void removeRedirectedUri(ContentMetadataMutations mutations) {
        mutations.remove(METADATA_NAME_REDIRECTED_URI);
    }

    private ContentMetadataInternal() {
    }
}
