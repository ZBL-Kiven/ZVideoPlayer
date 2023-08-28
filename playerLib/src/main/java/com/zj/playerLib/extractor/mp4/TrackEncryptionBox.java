package com.zj.playerLib.extractor.mp4;

import androidx.annotation.Nullable;

import com.zj.playerLib.extractor.TrackOutput.CryptoData;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;

public final class TrackEncryptionBox {
    private static final String TAG = "TrackEncryptionBox";
    public final boolean isEncrypted;
    @Nullable
    public final String schemeType;
    public final CryptoData cryptoData;
    public final int perSampleIvSize;
    public final byte[] defaultInitializationVector;

    public TrackEncryptionBox(boolean isEncrypted, @Nullable String schemeType, int perSampleIvSize, byte[] keyId, int defaultEncryptedBlocks, int defaultClearBlocks, @Nullable byte[] defaultInitializationVector) {
        Assertions.checkArgument(perSampleIvSize == 0 ^ defaultInitializationVector == null);
        this.isEncrypted = isEncrypted;
        this.schemeType = schemeType;
        this.perSampleIvSize = perSampleIvSize;
        this.defaultInitializationVector = defaultInitializationVector;
        this.cryptoData = new CryptoData(schemeToCryptoMode(schemeType), keyId, defaultEncryptedBlocks, defaultClearBlocks);
    }

    private static int schemeToCryptoMode(@Nullable String schemeType) {
        if (schemeType == null) {
            return 1;
        } else {
            byte var2 = -1;
            switch(schemeType.hashCode()) {
            case 3046605:
                if (schemeType.equals("cbc1")) {
                    var2 = 2;
                }
                break;
            case 3046671:
                if (schemeType.equals("cbcs")) {
                    var2 = 3;
                }
                break;
            case 3049879:
                if (schemeType.equals("cenc")) {
                    var2 = 0;
                }
                break;
            case 3049895:
                if (schemeType.equals("cens")) {
                    var2 = 1;
                }
            }

            switch(var2) {
            case 0:
            case 1:
                return 1;
            case 2:
            case 3:
                return 2;
            default:
                Log.w("TrackEncryptionBox", "Unsupported protection scheme type '" + schemeType + "'. Assuming AES-CTR crypto mode.");
                return 1;
            }
        }
    }
}
