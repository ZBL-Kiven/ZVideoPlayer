package com.zj.playerLib.util;

import android.net.Uri;
import android.net.Uri.Builder;
import android.text.TextUtils;

import java.util.Iterator;

public final class UriUtil {
    private static final int INDEX_COUNT = 4;
    private static final int SCHEME_COLON = 0;
    private static final int PATH = 1;
    private static final int QUERY = 2;
    private static final int FRAGMENT = 3;

    private UriUtil() {
    }

    public static Uri resolveToUri(String baseUri, String referenceUri) {
        return Uri.parse(resolve(baseUri, referenceUri));
    }

    public static String resolve(String baseUri, String referenceUri) {
        StringBuilder uri = new StringBuilder();
        baseUri = baseUri == null ? "" : baseUri;
        referenceUri = referenceUri == null ? "" : referenceUri;
        int[] refIndices = getUriIndices(referenceUri);
        if (refIndices[0] != -1) {
            uri.append(referenceUri);
            removeDotSegments(uri, refIndices[1], refIndices[2]);
            return uri.toString();
        } else {
            int[] baseIndices = getUriIndices(baseUri);
            if (refIndices[3] == 0) {
                return uri.append(baseUri, 0, baseIndices[3]).append(referenceUri).toString();
            } else if (refIndices[2] == 0) {
                return uri.append(baseUri, 0, baseIndices[2]).append(referenceUri).toString();
            } else {
                int lastSlashIndex;
                if (refIndices[1] != 0) {
                    lastSlashIndex = baseIndices[0] + 1;
                    uri.append(baseUri, 0, lastSlashIndex).append(referenceUri);
                    return removeDotSegments(uri, lastSlashIndex + refIndices[1], lastSlashIndex + refIndices[2]);
                } else if (referenceUri.charAt(refIndices[1]) == '/') {
                    uri.append(baseUri, 0, baseIndices[1]).append(referenceUri);
                    return removeDotSegments(uri, baseIndices[1], baseIndices[1] + refIndices[2]);
                } else if (baseIndices[0] + 2 < baseIndices[1] && baseIndices[1] == baseIndices[2]) {
                    uri.append(baseUri, 0, baseIndices[1]).append('/').append(referenceUri);
                    return removeDotSegments(uri, baseIndices[1], baseIndices[1] + refIndices[2] + 1);
                } else {
                    lastSlashIndex = baseUri.lastIndexOf(47, baseIndices[2] - 1);
                    int baseLimit = lastSlashIndex == -1 ? baseIndices[1] : lastSlashIndex + 1;
                    uri.append(baseUri, 0, baseLimit).append(referenceUri);
                    return removeDotSegments(uri, baseIndices[1], baseLimit + refIndices[2]);
                }
            }
        }
    }

    public static Uri removeQueryParameter(Uri uri, String queryParameterName) {
        Builder builder = uri.buildUpon();
        builder.clearQuery();
        Iterator var3 = uri.getQueryParameterNames().iterator();

        while(true) {
            String key;
            do {
                if (!var3.hasNext()) {
                    return builder.build();
                }

                key = (String)var3.next();
            } while(key.equals(queryParameterName));

            Iterator var5 = uri.getQueryParameters(key).iterator();

            while(var5.hasNext()) {
                String value = (String)var5.next();
                builder.appendQueryParameter(key, value);
            }
        }
    }

    private static String removeDotSegments(StringBuilder uri, int offset, int limit) {
        if (offset >= limit) {
            return uri.toString();
        } else {
            if (uri.charAt(offset) == '/') {
                ++offset;
            }

            int segmentStart = offset;
            int i = offset;

            while(true) {
                while(true) {
                    int nextSegmentStart;
                    while(true) {
                        if (i > limit) {
                            return uri.toString();
                        }

                        if (i == limit) {
                            nextSegmentStart = i;
                            break;
                        }

                        if (uri.charAt(i) == '/') {
                            nextSegmentStart = i + 1;
                            break;
                        }

                        ++i;
                    }

                    if (i == segmentStart + 1 && uri.charAt(segmentStart) == '.') {
                        uri.delete(segmentStart, nextSegmentStart);
                        limit -= nextSegmentStart - segmentStart;
                        i = segmentStart;
                    } else if (i == segmentStart + 2 && uri.charAt(segmentStart) == '.' && uri.charAt(segmentStart + 1) == '.') {
                        int prevSegmentStart = uri.lastIndexOf("/", segmentStart - 2) + 1;
                        int removeFrom = prevSegmentStart > offset ? prevSegmentStart : offset;
                        uri.delete(removeFrom, nextSegmentStart);
                        limit -= nextSegmentStart - removeFrom;
                        segmentStart = prevSegmentStart;
                        i = prevSegmentStart;
                    } else {
                        ++i;
                        segmentStart = i;
                    }
                }
            }
        }
    }

    private static int[] getUriIndices(String uriString) {
        int[] indices = new int[4];
        if (TextUtils.isEmpty(uriString)) {
            indices[0] = -1;
            return indices;
        } else {
            int length = uriString.length();
            int fragmentIndex = uriString.indexOf(35);
            if (fragmentIndex == -1) {
                fragmentIndex = length;
            }

            int queryIndex = uriString.indexOf(63);
            if (queryIndex == -1 || queryIndex > fragmentIndex) {
                queryIndex = fragmentIndex;
            }

            int schemeIndexLimit = uriString.indexOf(47);
            if (schemeIndexLimit == -1 || schemeIndexLimit > queryIndex) {
                schemeIndexLimit = queryIndex;
            }

            int schemeIndex = uriString.indexOf(58);
            if (schemeIndex > schemeIndexLimit) {
                schemeIndex = -1;
            }

            boolean hasAuthority = schemeIndex + 2 < queryIndex && uriString.charAt(schemeIndex + 1) == '/' && uriString.charAt(schemeIndex + 2) == '/';
            int pathIndex;
            if (hasAuthority) {
                pathIndex = uriString.indexOf(47, schemeIndex + 3);
                if (pathIndex == -1 || pathIndex > queryIndex) {
                    pathIndex = queryIndex;
                }
            } else {
                pathIndex = schemeIndex + 1;
            }

            indices[0] = schemeIndex;
            indices[1] = pathIndex;
            indices[2] = queryIndex;
            indices[3] = fragmentIndex;
            return indices;
        }
    }
}
