package com.zj.playerLib.drm;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.zj.playerLib.C;
import com.zj.playerLib.drm.DefaultDrmSession.ProvisioningManager;
import com.zj.playerLib.drm.DrmInitData.SchemeData;
import com.zj.playerLib.drm.DrmSession.DrmSessionException;
import com.zj.playerLib.drm.MediaDrm.OnEventListener;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.EventDispatcher;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@TargetApi(18)
public class DefaultDrmSessionManager<T extends MediaCrypto> implements DrmSessionManager<T>, ProvisioningManager<T> {
    private final UUID uuid;
    private final MediaDrm<T> mediaDrm;
    private final MediaDrmCallback callback;
    private final HashMap<String, String> optionalKeyRequestParameters;
    private final EventDispatcher<DefaultDrmSessionEventListener> eventDispatcher;
    private final boolean multiSession;
    private final int initialDrmRequestRetryCount;
    private final List<DefaultDrmSession<T>> sessions;
    private final List<DefaultDrmSession<T>> provisioningSessions;
    private Looper playbackLooper;
    private int mode;
    private byte[] offlineLicenseKeySetId;
    volatile MediaDrmHandler mediaDrmHandler;


    public static DefaultDrmSessionManager<FrameworkMediaCrypto> newWidevineInstance(MediaDrmCallback callback, HashMap<String, String> optionalKeyRequestParameters) throws UnsupportedDrmException {
        return newFrameworkInstance(C.WIDEVINE_UUID, callback, optionalKeyRequestParameters);
    }


    public static DefaultDrmSessionManager<FrameworkMediaCrypto> newPlayReadyInstance(MediaDrmCallback callback, String customData) throws UnsupportedDrmException {
        HashMap<String, String> optionalKeyRequestParameters;
        if (!TextUtils.isEmpty(customData)) {
            optionalKeyRequestParameters = new HashMap<>();
            optionalKeyRequestParameters.put("PRCustomData", customData);
        } else {
            optionalKeyRequestParameters = null;
        }

        return newFrameworkInstance(C.PLAYREADY_UUID, callback, optionalKeyRequestParameters);
    }


    public static DefaultDrmSessionManager<FrameworkMediaCrypto> newFrameworkInstance(UUID uuid, MediaDrmCallback callback, HashMap<String, String> optionalKeyRequestParameters) throws UnsupportedDrmException {
        return new DefaultDrmSessionManager<>(uuid, FrameworkMediaDrm.newInstance(uuid), callback, optionalKeyRequestParameters, false, 3);
    }

    public DefaultDrmSessionManager(UUID uuid, MediaDrm<T> mediaDrm, MediaDrmCallback callback, HashMap<String, String> optionalKeyRequestParameters) {
        this(uuid, mediaDrm, callback, optionalKeyRequestParameters, false, 3);
    }

    public DefaultDrmSessionManager(UUID uuid, MediaDrm<T> mediaDrm, MediaDrmCallback callback, HashMap<String, String> optionalKeyRequestParameters, boolean multiSession, int initialDrmRequestRetryCount) {
        Assertions.checkNotNull(uuid);
        Assertions.checkNotNull(mediaDrm);
        Assertions.checkArgument(!C.COMMON_PSSH_UUID.equals(uuid), "Use C.CLEAR_KEY_UUID instead");
        this.uuid = uuid;
        this.mediaDrm = mediaDrm;
        this.callback = callback;
        this.optionalKeyRequestParameters = optionalKeyRequestParameters;
        this.eventDispatcher = new EventDispatcher<>();
        this.multiSession = multiSession;
        this.initialDrmRequestRetryCount = initialDrmRequestRetryCount;
        this.mode = 0;
        this.sessions = new ArrayList<>();
        this.provisioningSessions = new ArrayList<>();
        if (multiSession && C.WIDEVINE_UUID.equals(uuid)) {
            mediaDrm.setPropertyString("sessionSharing", "enable");
        }

        mediaDrm.setOnEventListener(new MediaDrmEventListener());
    }

    public final void addListener(Handler handler, DefaultDrmSessionEventListener eventListener) {
        this.eventDispatcher.addListener(handler, eventListener);
    }

    public final void removeListener(DefaultDrmSessionEventListener eventListener) {
        this.eventDispatcher.removeListener(eventListener);
    }

    public final String getPropertyString(String key) {
        return this.mediaDrm.getPropertyString(key);
    }

    public final void setPropertyString(String key, String value) {
        this.mediaDrm.setPropertyString(key, value);
    }

    public final byte[] getPropertyByteArray(String key) {
        return this.mediaDrm.getPropertyByteArray(key);
    }

    public final void setPropertyByteArray(String key, byte[] value) {
        this.mediaDrm.setPropertyByteArray(key, value);
    }

    public void setMode(int mode, byte[] offlineLicenseKeySetId) {
        Assertions.checkState(this.sessions.isEmpty());
        if (mode == 1 || mode == 3) {
            Assertions.checkNotNull(offlineLicenseKeySetId);
        }

        this.mode = mode;
        this.offlineLicenseKeySetId = offlineLicenseKeySetId;
    }

    public boolean canAcquireSession(@NonNull DrmInitData drmInitData) {
        if (this.offlineLicenseKeySetId != null) {
            return true;
        } else {
            List<SchemeData> schemeData = getSchemeData(drmInitData, this.uuid, true);
            if (schemeData.isEmpty()) {
                if (drmInitData.schemeDataCount != 1 || !drmInitData.get(0).matches(C.COMMON_PSSH_UUID)) {
                    return false;
                }

                Log.w("DefaultDrmSessionMgr", "DrmInitData only contains common PSSH SchemeData. Assuming support for: " + this.uuid);
            }

            String schemeType = drmInitData.schemeType;
            if (schemeType != null && !"cenc".equals(schemeType)) {
                if (!"cbc1".equals(schemeType) && !"cbcs".equals(schemeType) && !"cens".equals(schemeType)) {
                    return true;
                } else {
                    return Util.SDK_INT >= 25;
                }
            } else {
                return true;
            }
        }
    }

    public DrmSession<T> acquireSession(Looper playbackLooper, DrmInitData drmInitData) {
        Assertions.checkState(this.playbackLooper == null || this.playbackLooper == playbackLooper);
        if (this.sessions.isEmpty()) {
            this.playbackLooper = playbackLooper;
            if (this.mediaDrmHandler == null) {
                this.mediaDrmHandler = new MediaDrmHandler(playbackLooper);
            }
        }

        List<SchemeData> schemeData = null;
        if (this.offlineLicenseKeySetId == null) {
            schemeData = getSchemeData(drmInitData, this.uuid, false);
            if (schemeData.isEmpty()) {
                MissingSchemeDataException error = new MissingSchemeDataException(this.uuid);
                this.eventDispatcher.dispatch((listener) -> listener.onDrmSessionManagerError(error));
                return new ErrorStateDrmSession<>(new DrmSessionException(error));
            }
        }
        DefaultDrmSession<T> session;
        if (!this.multiSession) {
            session = this.sessions.isEmpty() ? null : this.sessions.get(0);
        } else {
            session = null;
            for (DefaultDrmSession<T> tDefaultDrmSession : this.sessions) {
                if (Util.areEqual(tDefaultDrmSession.schemeDatas, schemeData)) {
                    session = tDefaultDrmSession;
                    break;
                }
            }
        }

        if (session == null) {
            session = new DefaultDrmSession<>(this.uuid, this.mediaDrm, this, schemeData, this.mode, this.offlineLicenseKeySetId, this.optionalKeyRequestParameters, this.callback, playbackLooper, this.eventDispatcher, this.initialDrmRequestRetryCount);
            this.sessions.add(session);
        }

        session.acquire();
        return session;
    }

    public void releaseSession(DrmSession<T> session) {
        if (!(session instanceof ErrorStateDrmSession)) {
            DefaultDrmSession<T> drmSession = (DefaultDrmSession<T>) session;
            if (drmSession.release()) {
                this.sessions.remove(drmSession);
                if (this.provisioningSessions.size() > 1 && this.provisioningSessions.get(0) == drmSession) {
                    this.provisioningSessions.get(1).provision();
                }

                this.provisioningSessions.remove(drmSession);
            }

        }
    }

    public void provisionRequired(DefaultDrmSession<T> session) {
        this.provisioningSessions.add(session);
        if (this.provisioningSessions.size() == 1) {
            session.provision();
        }

    }

    public void onProvisionCompleted() {
        for (DefaultDrmSession<T> provisioningSession : this.provisioningSessions) {
            provisioningSession.onProvisionCompleted();
        }
        this.provisioningSessions.clear();
    }

    public void onProvisionError(Exception error) {
        for (DefaultDrmSession<T> provisioningSession : this.provisioningSessions) {
            provisioningSession.onProvisionError(error);
        }
        this.provisioningSessions.clear();
    }

    private static List<SchemeData> getSchemeData(DrmInitData drmInitData, UUID uuid, boolean allowMissingData) {
        List<SchemeData> matchingSchemeData = new ArrayList<>(drmInitData.schemeDataCount);
        for (int i = 0; i < drmInitData.schemeDataCount; ++i) {
            SchemeData schemeData = drmInitData.get(i);
            boolean uuidMatches = schemeData.matches(uuid) || C.CLEARKEY_UUID.equals(uuid) && schemeData.matches(C.COMMON_PSSH_UUID);
            if (uuidMatches && (schemeData.data != null || allowMissingData)) {
                matchingSchemeData.add(schemeData);
            }
        }

        return matchingSchemeData;
    }

    private class MediaDrmEventListener implements OnEventListener<T> {
        private MediaDrmEventListener() {
        }

        public void onEvent(MediaDrm<? extends T> md, byte[] sessionId, int event, int extra, byte[] data) {
            if (DefaultDrmSessionManager.this.mode == 0) {
                DefaultDrmSessionManager.this.mediaDrmHandler.obtainMessage(event, sessionId).sendToTarget();
            }

        }
    }

    @SuppressLint({"HandlerLeak"})
    private class MediaDrmHandler extends Handler {
        public MediaDrmHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            byte[] sessionId = (byte[]) msg.obj;
            Iterator<DefaultDrmSession<T>> var3 = DefaultDrmSessionManager.this.sessions.iterator();
            DefaultDrmSession<T> session;
            do {
                if (!var3.hasNext()) {
                    return;
                }
                session = var3.next();
            } while (!session.hasSessionId(sessionId));

            session.onMediaDrmEvent(msg.what);
        }
    }

    public static final class MissingSchemeDataException extends Exception {
        private MissingSchemeDataException(UUID uuid) {
            super("Media does not support uuid: " + uuid);
        }
    }

    /**
     * @deprecated
     */
    @Deprecated
    public interface EventListener extends DefaultDrmSessionEventListener {}
}
