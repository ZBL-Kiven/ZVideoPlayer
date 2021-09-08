//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream;

import android.net.Uri;
import android.util.Base64;

import androidx.annotation.Nullable;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.util.Util;

import java.io.IOException;
import java.net.URLDecoder;

public final class DataSchemeDataSource extends BaseDataSource {
    public static final String SCHEME_DATA = "data";
    @Nullable
    private DataSpec dataSpec;
    private int bytesRead;
    @Nullable
    private byte[] data;

    public DataSchemeDataSource() {
        super(false);
    }

    public long open(DataSpec dataSpec) throws IOException {
        this.transferInitializing(dataSpec);
        this.dataSpec = dataSpec;
        Uri uri = dataSpec.uri;
        String scheme = uri.getScheme();
        if (!"data".equals(scheme)) {
            throw new ParserException("Unsupported scheme: " + scheme);
        } else {
            String[] uriParts = Util.split(uri.getSchemeSpecificPart(), ",");
            if (uriParts.length != 2) {
                throw new ParserException("Unexpected URI format: " + uri);
            } else {
                String dataString = uriParts[1];
                if (uriParts[0].contains(";base64")) {
                    try {
                        this.data = Base64.decode(dataString, 0);
                    } catch (IllegalArgumentException var7) {
                        throw new ParserException("Error while parsing Base64 encoded string: " + dataString, var7);
                    }
                } else {
                    this.data = Util.getUtf8Bytes(URLDecoder.decode(dataString, "US-ASCII"));
                }

                this.transferStarted(dataSpec);
                return (long)this.data.length;
            }
        }
    }

    public int read(byte[] buffer, int offset, int readLength) {
        if (readLength == 0) {
            return 0;
        } else {
            int remainingBytes = this.data.length - this.bytesRead;
            if (remainingBytes == 0) {
                return -1;
            } else {
                readLength = Math.min(readLength, remainingBytes);
                System.arraycopy(this.data, this.bytesRead, buffer, offset, readLength);
                this.bytesRead += readLength;
                this.bytesTransferred(readLength);
                return readLength;
            }
        }
    }

    @Nullable
    public Uri getUri() {
        return this.dataSpec != null ? this.dataSpec.uri : null;
    }

    public void close() throws IOException {
        if (this.data != null) {
            this.data = null;
            this.transferEnded();
        }

        this.dataSpec = null;
    }
}
