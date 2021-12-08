package com.zj.playerLib.drm;

import com.zj.playerLib.drm.MediaDrm.KeyRequest;
import com.zj.playerLib.drm.MediaDrm.ProvisionRequest;
import com.zj.playerLib.util.Assertions;
import java.io.IOException;
import java.util.UUID;

public final class LocalMediaDrmCallback implements MediaDrmCallback {
    private final byte[] keyResponse;

    public LocalMediaDrmCallback(byte[] keyResponse) {
        this.keyResponse = Assertions.checkNotNull(keyResponse);
    }

    public byte[] executeProvisionRequest(UUID uuid, ProvisionRequest request) throws IOException {
        throw new UnsupportedOperationException();
    }

    public byte[] executeKeyRequest(UUID uuid, KeyRequest request) throws Exception {
        return this.keyResponse;
    }
}
