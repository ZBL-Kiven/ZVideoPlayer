package com.zj.playerLib.upstream.crypto;

import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesFlushingCipher {
    private final Cipher cipher;
    private final int blockSize;
    private final byte[] zerosBlock;
    private final byte[] flushedBlock;
    private int pendingXorBytes;

    public AesFlushingCipher(int mode, byte[] secretKey, long nonce, long offset) {
        try {
            this.cipher = Cipher.getInstance("AES/CTR/NoPadding");
            this.blockSize = this.cipher.getBlockSize();
            this.zerosBlock = new byte[this.blockSize];
            this.flushedBlock = new byte[this.blockSize];
            long counter = offset / (long)this.blockSize;
            int startPadding = (int)(offset % (long)this.blockSize);
            this.cipher.init(mode, new SecretKeySpec(secretKey, Util.splitAtFirst(this.cipher.getAlgorithm(), "/")[0]), new IvParameterSpec(this.getInitializationVector(nonce, counter)));
            if (startPadding != 0) {
                this.updateInPlace(new byte[startPadding], 0, startPadding);
            }

        } catch (NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException var10) {
            throw new RuntimeException(var10);
        }
    }

    public void updateInPlace(byte[] data, int offset, int length) {
        this.update(data, offset, length, data, offset);
    }

    public void update(byte[] in, int inOffset, int length, byte[] out, int outOffset) {
        while(true) {
            if (this.pendingXorBytes > 0) {
                out[outOffset] = (byte)(in[inOffset] ^ this.flushedBlock[this.blockSize - this.pendingXorBytes]);
                ++outOffset;
                ++inOffset;
                --this.pendingXorBytes;
                --length;
                if (length != 0) {
                    continue;
                }

                return;
            }

            int written = this.nonFlushingUpdate(in, inOffset, length, out, outOffset);
            if (length == written) {
                return;
            }

            int bytesToFlush = length - written;
            Assertions.checkState(bytesToFlush < this.blockSize);
            outOffset += written;
            this.pendingXorBytes = this.blockSize - bytesToFlush;
            written = this.nonFlushingUpdate(this.zerosBlock, 0, this.pendingXorBytes, this.flushedBlock, 0);
            Assertions.checkState(written == this.blockSize);

            for(int i = 0; i < bytesToFlush; ++i) {
                out[outOffset++] = this.flushedBlock[i];
            }

            return;
        }
    }

    private int nonFlushingUpdate(byte[] in, int inOffset, int length, byte[] out, int outOffset) {
        try {
            return this.cipher.update(in, inOffset, length, out, outOffset);
        } catch (ShortBufferException var7) {
            throw new RuntimeException(var7);
        }
    }

    private byte[] getInitializationVector(long nonce, long counter) {
        return ByteBuffer.allocate(16).putLong(nonce).putLong(counter).array();
    }
}
