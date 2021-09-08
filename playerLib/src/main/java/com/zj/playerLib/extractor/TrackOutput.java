//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor;

import androidx.annotation.Nullable;

import com.zj.playerLib.Format;
import com.zj.playerLib.util.ParsableByteArray;

import java.io.IOException;
import java.util.Arrays;

public interface TrackOutput {
    void format(Format var1);

    int sampleData(ExtractorInput var1, int var2, boolean var3) throws IOException, InterruptedException;

    void sampleData(ParsableByteArray var1, int var2);

    void sampleMetadata(long var1, int var3, int var4, int var5, @Nullable TrackOutput.CryptoData var6);

    public static final class CryptoData {
        public final int cryptoMode;
        public final byte[] encryptionKey;
        public final int encryptedBlocks;
        public final int clearBlocks;

        public CryptoData(int cryptoMode, byte[] encryptionKey, int encryptedBlocks, int clearBlocks) {
            this.cryptoMode = cryptoMode;
            this.encryptionKey = encryptionKey;
            this.encryptedBlocks = encryptedBlocks;
            this.clearBlocks = clearBlocks;
        }

        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            } else if (obj != null && this.getClass() == obj.getClass()) {
                CryptoData other = (CryptoData)obj;
                return this.cryptoMode == other.cryptoMode && this.encryptedBlocks == other.encryptedBlocks && this.clearBlocks == other.clearBlocks && Arrays.equals(this.encryptionKey, other.encryptionKey);
            } else {
                return false;
            }
        }

        public int hashCode() {
            int result = this.cryptoMode;
            result = 31 * result + Arrays.hashCode(this.encryptionKey);
            result = 31 * result + this.encryptedBlocks;
            result = 31 * result + this.clearBlocks;
            return result;
        }
    }
}
