package com.zj.playerLib.upstream.crypto;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.zj.playerLib.upstream.DataSource;
import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.upstream.TransferListener;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class AesCipherDataSource implements DataSource {
    private final DataSource upstream;
    private final byte[] secretKey;
    @Nullable
    private AesFlushingCipher cipher;

    public AesCipherDataSource(byte[] secretKey, DataSource upstream) {
        this.upstream = upstream;
        this.secretKey = secretKey;
    }

    public void addTransferListener(TransferListener transferListener) {
        this.upstream.addTransferListener(transferListener);
    }

    public long open(DataSpec dataSpec) throws IOException {
        long dataLength = this.upstream.open(dataSpec);
        long nonce = CryptoUtil.getFNV64Hash(dataSpec.key);
        this.cipher = new AesFlushingCipher(2, this.secretKey, nonce, dataSpec.absoluteStreamPosition);
        return dataLength;
    }

    public int read(byte[] data, int offset, int readLength) throws IOException {
        if (readLength == 0) {
            return 0;
        } else {
            int read = this.upstream.read(data, offset, readLength);
            if (read == -1) {
                return -1;
            } else {
                this.cipher.updateInPlace(data, offset, read);
                return read;
            }
        }
    }

    @Nullable
    public Uri getUri() {
        return this.upstream.getUri();
    }

    public Map<String, List<String>> getResponseHeaders() {
        return this.upstream.getResponseHeaders();
    }

    public void close() throws IOException {
        this.cipher = null;
        this.upstream.close();
    }
}
