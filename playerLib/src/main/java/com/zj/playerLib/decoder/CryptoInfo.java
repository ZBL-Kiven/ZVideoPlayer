package com.zj.playerLib.decoder;

import android.annotation.TargetApi;
import android.media.MediaCodec.CryptoInfo.Pattern;
import com.zj.playerLib.util.Util;

public final class CryptoInfo {
    public byte[] iv;
    public byte[] key;
    public int mode;
    public int[] numBytesOfClearData;
    public int[] numBytesOfEncryptedData;
    public int numSubSamples;
    public int encryptedBlocks;
    public int clearBlocks;
    private final android.media.MediaCodec.CryptoInfo frameworkCryptoInfo;
    private final PatternHolderV24 patternHolder;

    public CryptoInfo() {
        this.frameworkCryptoInfo = Util.SDK_INT >= 16 ? this.newFrameworkCryptoInfoV16() : null;
        this.patternHolder = Util.SDK_INT >= 24 ? new PatternHolderV24(this.frameworkCryptoInfo) : null;
    }

    public void set(int numSubSamples, int[] numBytesOfClearData, int[] numBytesOfEncryptedData, byte[] key, byte[] iv, int mode, int encryptedBlocks, int clearBlocks) {
        this.numSubSamples = numSubSamples;
        this.numBytesOfClearData = numBytesOfClearData;
        this.numBytesOfEncryptedData = numBytesOfEncryptedData;
        this.key = key;
        this.iv = iv;
        this.mode = mode;
        this.encryptedBlocks = encryptedBlocks;
        this.clearBlocks = clearBlocks;
        if (Util.SDK_INT >= 16) {
            this.updateFrameworkCryptoInfoV16();
        }

    }

    @TargetApi(16)
    public android.media.MediaCodec.CryptoInfo getFrameworkCryptoInfoV16() {
        return this.frameworkCryptoInfo;
    }

    @TargetApi(16)
    private android.media.MediaCodec.CryptoInfo newFrameworkCryptoInfoV16() {
        return new android.media.MediaCodec.CryptoInfo();
    }

    @TargetApi(16)
    private void updateFrameworkCryptoInfoV16() {
        this.frameworkCryptoInfo.numSubSamples = this.numSubSamples;
        this.frameworkCryptoInfo.numBytesOfClearData = this.numBytesOfClearData;
        this.frameworkCryptoInfo.numBytesOfEncryptedData = this.numBytesOfEncryptedData;
        this.frameworkCryptoInfo.key = this.key;
        this.frameworkCryptoInfo.iv = this.iv;
        this.frameworkCryptoInfo.mode = this.mode;
        if (Util.SDK_INT >= 24) {
            this.patternHolder.set(this.encryptedBlocks, this.clearBlocks);
        }

    }

    @TargetApi(24)
    private static final class PatternHolderV24 {
        private final android.media.MediaCodec.CryptoInfo frameworkCryptoInfo;
        private final Pattern pattern;

        private PatternHolderV24(android.media.MediaCodec.CryptoInfo frameworkCryptoInfo) {
            this.frameworkCryptoInfo = frameworkCryptoInfo;
            this.pattern = new Pattern(0, 0);
        }

        private void set(int encryptedBlocks, int clearBlocks) {
            this.pattern.set(encryptedBlocks, clearBlocks);
            this.frameworkCryptoInfo.setPattern(this.pattern);
        }
    }
}
