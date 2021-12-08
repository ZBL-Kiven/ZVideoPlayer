package com.zj.playerLib.upstream.crypto;

import com.zj.playerLib.upstream.DataSink;
import com.zj.playerLib.upstream.DataSpec;

import java.io.IOException;

public final class AesCipherDataSink implements DataSink {
    private final DataSink wrappedDataSink;
    private final byte[] secretKey;
    private final byte[] scratch;
    private AesFlushingCipher cipher;

    public AesCipherDataSink(byte[] secretKey, DataSink wrappedDataSink) {
        this(secretKey, wrappedDataSink, null);
    }

    public AesCipherDataSink(byte[] secretKey, DataSink wrappedDataSink, byte[] scratch) {
        this.wrappedDataSink = wrappedDataSink;
        this.secretKey = secretKey;
        this.scratch = scratch;
    }

    public void open(DataSpec dataSpec) throws IOException {
        this.wrappedDataSink.open(dataSpec);
        long nonce = CryptoUtil.getFNV64Hash(dataSpec.key);
        this.cipher = new AesFlushingCipher(1, this.secretKey, nonce, dataSpec.absoluteStreamPosition);
    }

    public void write(byte[] data, int offset, int length) throws IOException {
        int bytesToProcess;
        if (this.scratch == null) {
            this.cipher.updateInPlace(data, offset, length);
            this.wrappedDataSink.write(data, offset, length);
        } else {
            for(int bytesProcessed = 0; bytesProcessed < length; bytesProcessed += bytesToProcess) {
                bytesToProcess = Math.min(length - bytesProcessed, this.scratch.length);
                this.cipher.update(data, offset + bytesProcessed, bytesToProcess, this.scratch, 0);
                this.wrappedDataSink.write(this.scratch, 0, bytesToProcess);
            }
        }

    }

    public void close() throws IOException {
        this.cipher = null;
        this.wrappedDataSink.close();
    }
}
