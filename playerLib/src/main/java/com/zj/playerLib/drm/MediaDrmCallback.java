package com.zj.playerLib.drm;

import com.zj.playerLib.drm.MediaDrm.KeyRequest;
import com.zj.playerLib.drm.MediaDrm.ProvisionRequest;
import java.util.UUID;

public interface MediaDrmCallback {
    byte[] executeProvisionRequest(UUID var1, ProvisionRequest var2) throws Exception;

    byte[] executeKeyRequest(UUID var1, KeyRequest var2) throws Exception;
}
