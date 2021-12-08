package com.zj.playerLib.drm;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.NotProvisionedException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.zj.playerLib.C;
import com.zj.playerLib.drm.DrmInitData.SchemeData;
import com.zj.playerLib.drm.MediaDrm.KeyRequest;
import com.zj.playerLib.drm.MediaDrm.ProvisionRequest;
import com.zj.playerLib.util.EventDispatcher;
import com.zj.playerLib.util.Log;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@TargetApi(18)
class DefaultDrmSession<T extends MediaCrypto> implements DrmSession<T> {
    private static final String TAG = "DefaultDrmSession";
    private static final int MSG_PROVISION = 0;
    private static final int MSG_KEYS = 1;
    private static final int MAX_LICENSE_DURATION_TO_RENEW = 60;
    @Nullable
    public final List<SchemeData> schemeDatas;
    private final MediaDrm<T> mediaDrm;
    private final ProvisioningManager<T> provisioningManager;
    private final int mode;
    private final HashMap<String, String> optionalKeyRequestParameters;
    private final EventDispatcher<DefaultDrmSessionEventListener> eventDispatcher;
    private final int initialDrmRequestRetryCount;
    final MediaDrmCallback callback;
    final UUID uuid;
    final PostResponseHandler postResponseHandler;
    private int state;
    private int openCount;
    private HandlerThread requestHandlerThread;
    private PostRequestHandler postRequestHandler;
    private T mediaCrypto;
    private DrmSessionException lastException;
    private byte[] sessionId;
    @Nullable
    private byte[] offlineLicenseKeySetId;
    private KeyRequest currentKeyRequest;
    private ProvisionRequest currentProvisionRequest;

    public DefaultDrmSession(UUID uuid, MediaDrm<T> mediaDrm, ProvisioningManager<T> provisioningManager, List<SchemeData> schemeDatas, int mode, @Nullable byte[] offlineLicenseKeySetId, HashMap<String, String> optionalKeyRequestParameters, MediaDrmCallback callback, Looper playbackLooper, EventDispatcher<DefaultDrmSessionEventListener> eventDispatcher, int initialDrmRequestRetryCount) {
        this.uuid = uuid;
        this.provisioningManager = provisioningManager;
        this.mediaDrm = mediaDrm;
        this.mode = mode;
        this.offlineLicenseKeySetId = offlineLicenseKeySetId;
        this.schemeDatas = offlineLicenseKeySetId == null ? Collections.unmodifiableList(schemeDatas) : null;
        this.optionalKeyRequestParameters = optionalKeyRequestParameters;
        this.callback = callback;
        this.initialDrmRequestRetryCount = initialDrmRequestRetryCount;
        this.eventDispatcher = eventDispatcher;
        this.state = 2;
        this.postResponseHandler = new PostResponseHandler(playbackLooper);
        this.requestHandlerThread = new HandlerThread("DrmRequestHandler");
        this.requestHandlerThread.start();
        this.postRequestHandler = new PostRequestHandler(this.requestHandlerThread.getLooper());
    }

    public void acquire() {
        if (++this.openCount == 1) {
            if (this.state == 1) {
                return;
            }

            if (this.openInternal(true)) {
                this.doLicense(true);
            }
        }

    }

    public boolean release() {
        if (--this.openCount == 0) {
            this.state = 0;
            this.postResponseHandler.removeCallbacksAndMessages(null);
            this.postRequestHandler.removeCallbacksAndMessages(null);
            this.postRequestHandler = null;
            this.requestHandlerThread.quit();
            this.requestHandlerThread = null;
            this.mediaCrypto = null;
            this.lastException = null;
            this.currentKeyRequest = null;
            this.currentProvisionRequest = null;
            if (this.sessionId != null) {
                this.mediaDrm.closeSession(this.sessionId);
                this.sessionId = null;
                this.eventDispatcher.dispatch(DefaultDrmSessionEventListener::onDrmSessionReleased);
            }

            return true;
        } else {
            return false;
        }
    }

    public boolean hasSessionId(byte[] sessionId) {
        return Arrays.equals(this.sessionId, sessionId);
    }

    public void onMediaDrmEvent(int what) {
        if (this.isOpen()) {
            switch (what) {
                case 1:
                    this.state = 3;
                    this.provisioningManager.provisionRequired(this);
                    break;
                case 2:
                    this.doLicense(false);
                    break;
                case 3:
                    this.onKeysExpired();
            }

        }
    }

    public void provision() {
        this.currentProvisionRequest = this.mediaDrm.getProvisionRequest();
        this.postRequestHandler.post(0, this.currentProvisionRequest, true);
    }

    public void onProvisionCompleted() {
        if (this.openInternal(false)) {
            this.doLicense(true);
        }

    }

    public void onProvisionError(Exception error) {
        this.onError(error);
    }

    public final int getState() {
        return this.state;
    }

    public final DrmSessionException getError() {
        return this.state == 1 ? this.lastException : null;
    }

    public final T getMediaCrypto() {
        return this.mediaCrypto;
    }

    public Map<String, String> queryKeyStatus() {
        return this.sessionId == null ? null : this.mediaDrm.queryKeyStatus(this.sessionId);
    }

    public byte[] getOfflineLicenseKeySetId() {
        return this.offlineLicenseKeySetId;
    }

    private boolean openInternal(boolean allowProvisioning) {
        if (this.isOpen()) {
            return true;
        } else {
            try {
                this.sessionId = this.mediaDrm.openSession();
                this.eventDispatcher.dispatch(DefaultDrmSessionEventListener::onDrmSessionAcquired);
                this.mediaCrypto = this.mediaDrm.createMediaCrypto(this.sessionId);
                this.state = 3;
                return true;
            } catch (NotProvisionedException var3) {
                if (allowProvisioning) {
                    this.provisioningManager.provisionRequired(this);
                } else {
                    this.onError(var3);
                }
            } catch (Exception var4) {
                this.onError(var4);
            }

            return false;
        }
    }

    private void onProvisionResponse(Object request, Object response) {
        if (request == this.currentProvisionRequest && (this.state == 2 || this.isOpen())) {
            this.currentProvisionRequest = null;
            if (response instanceof Exception) {
                this.provisioningManager.onProvisionError((Exception) response);
            } else {
                try {
                    this.mediaDrm.provideProvisionResponse((byte[]) response);
                } catch (Exception var4) {
                    this.provisioningManager.onProvisionError(var4);
                    return;
                }

                this.provisioningManager.onProvisionCompleted();
            }
        }
    }

    private void doLicense(boolean allowRetry) {
        switch (this.mode) {
            case 0:
            case 1:
                if (this.offlineLicenseKeySetId == null) {
                    this.postKeyRequest(1, allowRetry);
                } else if (this.state == 4 || this.restoreKeys()) {
                    long licenseDurationRemainingSec = this.getLicenseDurationRemainingSec();
                    if (this.mode == 0 && licenseDurationRemainingSec <= 60L) {
                        Log.d("DefaultDrmSession", "Offline license has expired or will expire soon. Remaining seconds: " + licenseDurationRemainingSec);
                        this.postKeyRequest(2, allowRetry);
                    } else if (licenseDurationRemainingSec <= 0L) {
                        this.onError(new KeysExpiredException());
                    } else {
                        this.state = 4;
                        this.eventDispatcher.dispatch(DefaultDrmSessionEventListener::onDrmKeysRestored);
                    }
                }
                break;
            case 2:
                if (this.offlineLicenseKeySetId == null) {
                    this.postKeyRequest(2, allowRetry);
                } else if (this.restoreKeys()) {
                    this.postKeyRequest(2, allowRetry);
                }
                break;
            case 3:
                if (this.restoreKeys()) {
                    this.postKeyRequest(3, allowRetry);
                }
        }

    }

    private boolean restoreKeys() {
        try {
            this.mediaDrm.restoreKeys(this.sessionId, this.offlineLicenseKeySetId);
            return true;
        } catch (Exception var2) {
            Log.e("DefaultDrmSession", "Error trying to restore Widevine keys.", var2);
            this.onError(var2);
            return false;
        }
    }

    private long getLicenseDurationRemainingSec() {
        if (!C.WIDEVINE_UUID.equals(this.uuid)) {
            return Long.MAX_VALUE;
        } else {
            Pair<Long, Long> pair = WidevineUtil.getLicenseDurationRemainingSec(this);
            if (pair == null) return Long.MAX_VALUE;
            return Math.min(pair.first, pair.second);
        }
    }

    private void postKeyRequest(int type, boolean allowRetry) {
        byte[] scope = type == 3 ? this.offlineLicenseKeySetId : this.sessionId;

        try {
            this.currentKeyRequest = this.mediaDrm.getKeyRequest(scope, this.schemeDatas, type, this.optionalKeyRequestParameters);
            this.postRequestHandler.post(1, this.currentKeyRequest, allowRetry);
        } catch (Exception var5) {
            this.onKeysError(var5);
        }

    }

    private void onKeyResponse(Object request, Object response) {
        if (request == this.currentKeyRequest && this.isOpen()) {
            this.currentKeyRequest = null;
            if (response instanceof Exception) {
                this.onKeysError((Exception) response);
            } else {
                try {
                    byte[] responseData = (byte[]) response;
                    if (this.mode == 3) {
                        this.mediaDrm.provideKeyResponse(this.offlineLicenseKeySetId, responseData);
                        this.eventDispatcher.dispatch(DefaultDrmSessionEventListener::onDrmKeysRestored);
                    } else {
                        byte[] keySetId = this.mediaDrm.provideKeyResponse(this.sessionId, responseData);
                        if ((this.mode == 2 || this.mode == 0 && this.offlineLicenseKeySetId != null) && keySetId != null && keySetId.length != 0) {
                            this.offlineLicenseKeySetId = keySetId;
                        }

                        this.state = 4;
                        this.eventDispatcher.dispatch(DefaultDrmSessionEventListener::onDrmKeysLoaded);
                    }
                } catch (Exception var5) {
                    this.onKeysError(var5);
                }

            }
        }
    }

    private void onKeysExpired() {
        if (this.state == 4) {
            this.state = 3;
            this.onError(new KeysExpiredException());
        }

    }

    private void onKeysError(Exception e) {
        if (e instanceof NotProvisionedException) {
            this.provisioningManager.provisionRequired(this);
        } else {
            this.onError(e);
        }

    }

    private void onError(Exception e) {
        this.lastException = new DrmSessionException(e);
        this.eventDispatcher.dispatch((listener) -> listener.onDrmSessionManagerError(e));
        if (this.state != 4) {
            this.state = 1;
        }

    }

    private boolean isOpen() {
        return this.state == 3 || this.state == 4;
    }

    @SuppressLint({"HandlerLeak"})
    private class PostRequestHandler extends Handler {
        public PostRequestHandler(Looper backgroundLooper) {
            super(backgroundLooper);
        }

        void post(int what, Object request, boolean allowRetry) {
            int allowRetryInt = allowRetry ? 1 : 0;
            int errorCount = 0;
            this.obtainMessage(what, allowRetryInt, errorCount, request).sendToTarget();
        }

        public void handleMessage(Message msg) {
            Object request = msg.obj;

            Object response;
            try {
                switch (msg.what) {
                    case 0:
                        response = DefaultDrmSession.this.callback.executeProvisionRequest(DefaultDrmSession.this.uuid, (ProvisionRequest) request);
                        break;
                    case 1:
                        response = DefaultDrmSession.this.callback.executeKeyRequest(DefaultDrmSession.this.uuid, (KeyRequest) request);
                        break;
                    default:
                        throw new RuntimeException();
                }
            } catch (Exception var5) {
                if (this.maybeRetryRequest(msg)) {
                    return;
                }

                response = var5;
            }

            DefaultDrmSession.this.postResponseHandler.obtainMessage(msg.what, Pair.create(request, response)).sendToTarget();
        }

        private boolean maybeRetryRequest(Message originalMsg) {
            boolean allowRetry = originalMsg.arg1 == 1;
            if (!allowRetry) {
                return false;
            } else {
                int errorCount = originalMsg.arg2 + 1;
                if (errorCount > DefaultDrmSession.this.initialDrmRequestRetryCount) {
                    return false;
                } else {
                    Message retryMsg = Message.obtain(originalMsg);
                    retryMsg.arg2 = errorCount;
                    this.sendMessageDelayed(retryMsg, this.getRetryDelayMillis(errorCount));
                    return true;
                }
            }
        }

        private long getRetryDelayMillis(int errorCount) {
            return Math.min((errorCount - 1) * 1000, 5000);
        }
    }

    @SuppressLint({"HandlerLeak"})
    private class PostResponseHandler extends Handler {
        public PostResponseHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            Pair<?, ?> requestAndResponse = (Pair<?, ?>) msg.obj;
            Object request = requestAndResponse.first;
            Object response = requestAndResponse.second;
            switch (msg.what) {
                case 0:
                    DefaultDrmSession.this.onProvisionResponse(request, response);
                    break;
                case 1:
                    DefaultDrmSession.this.onKeyResponse(request, response);
            }

        }
    }

    public interface ProvisioningManager<T extends MediaCrypto> {
        void provisionRequired(DefaultDrmSession<T> var1);

        void onProvisionError(Exception var1);

        void onProvisionCompleted();
    }
}
