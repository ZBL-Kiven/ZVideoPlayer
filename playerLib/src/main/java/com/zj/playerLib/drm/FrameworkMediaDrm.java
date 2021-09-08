package com.zj.playerLib.drm;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.DeniedByServerException;
import android.media.MediaCryptoException;
import android.media.MediaDrmException;
import android.media.NotProvisionedException;
import android.media.UnsupportedSchemeException;
import android.os.Handler;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.zj.playerLib.C;
import com.zj.playerLib.drm.DrmInitData.SchemeData;
import com.zj.playerLib.extractor.mp4.PsshAtomUtil;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@TargetApi(23)
public final class FrameworkMediaDrm implements MediaDrm<FrameworkMediaCrypto> {
    private static final String CENC_SCHEME_MIME_TYPE = "cenc";
    private final UUID uuid;
    private final android.media.MediaDrm mediaDrm;

    public static FrameworkMediaDrm newInstance(UUID uuid) throws UnsupportedDrmException {
        try {
            return new FrameworkMediaDrm(uuid);
        } catch (UnsupportedSchemeException var2) {
            throw new UnsupportedDrmException(1, var2);
        } catch (Exception var3) {
            throw new UnsupportedDrmException(2, var3);
        }
    }

    private FrameworkMediaDrm(UUID uuid) throws UnsupportedSchemeException {
        Assertions.checkNotNull(uuid);
        Assertions.checkArgument(!C.COMMON_PSSH_UUID.equals(uuid), "Use C.CLEARKEY_UUID instead");
        this.uuid = uuid;
        this.mediaDrm = new android.media.MediaDrm(adjustUuid(uuid));
        if (C.WIDEVINE_UUID.equals(uuid) && needsForceWidevineL3Workaround()) {
            forceWidevineL3(this.mediaDrm);
        }

    }

    public void setOnEventListener(OnEventListener<? super FrameworkMediaCrypto> listener) {
        this.mediaDrm.setOnEventListener(listener == null ? null : (mediaDrm, sessionId, event, extra, data) -> {
            listener.onEvent(this, sessionId, event, extra, data);
        });
    }

    public void setOnKeyStatusChangeListener(OnKeyStatusChangeListener<? super FrameworkMediaCrypto> listener) {
        if (Util.SDK_INT < 23) {
            throw new UnsupportedOperationException();
        } else {
            this.mediaDrm.setOnKeyStatusChangeListener(listener == null ? null : (mediaDrm, sessionId, keyInfo, hasNewUsableKey) -> {
                List<KeyStatus> exoKeyInfo = new ArrayList();
                Iterator var7 = keyInfo.iterator();

                while (var7.hasNext()) {
                    android.media.MediaDrm.KeyStatus keyStatus = (android.media.MediaDrm.KeyStatus) var7.next();
                    exoKeyInfo.add(new KeyStatus(keyStatus.getStatusCode(), keyStatus.getKeyId()));
                }

                listener.onKeyStatusChange(this, sessionId, exoKeyInfo, hasNewUsableKey);
            }, (Handler) null);
        }
    }

    public byte[] openSession() throws MediaDrmException {
        return this.mediaDrm.openSession();
    }

    public void closeSession(byte[] sessionId) {
        this.mediaDrm.closeSession(sessionId);
    }

    public KeyRequest getKeyRequest(byte[] scope, @Nullable List<SchemeData> schemeDatas, int keyType, @Nullable HashMap<String, String> optionalParameters) throws NotProvisionedException {
        SchemeData schemeData = null;
        byte[] initData = null;
        String mimeType = null;
        if (schemeDatas != null) {
            schemeData = getSchemeData(this.uuid, schemeDatas);
            initData = adjustRequestInitData(this.uuid, schemeData.data);
            mimeType = adjustRequestMimeType(this.uuid, schemeData.mimeType);
        }

        android.media.MediaDrm.KeyRequest request = this.mediaDrm.getKeyRequest(scope, initData, mimeType, keyType, optionalParameters);
        byte[] requestData = adjustRequestData(this.uuid, request.getData());
        String licenseServerUrl = request.getDefaultUrl();
        if (TextUtils.isEmpty(licenseServerUrl) && schemeData != null && !TextUtils.isEmpty(schemeData.licenseServerUrl)) {
            licenseServerUrl = schemeData.licenseServerUrl;
        }

        return new KeyRequest(requestData, licenseServerUrl);
    }

    public byte[] provideKeyResponse(byte[] scope, byte[] response) throws NotProvisionedException, DeniedByServerException {
        if (C.CLEARKEY_UUID.equals(this.uuid)) {
            response = ClearKeyUtil.adjustResponseData(response);
        }

        return this.mediaDrm.provideKeyResponse(scope, response);
    }

    public ProvisionRequest getProvisionRequest() {
        android.media.MediaDrm.ProvisionRequest request = this.mediaDrm.getProvisionRequest();
        return new ProvisionRequest(request.getData(), request.getDefaultUrl());
    }

    public void provideProvisionResponse(byte[] response) throws DeniedByServerException {
        this.mediaDrm.provideProvisionResponse(response);
    }

    public Map<String, String> queryKeyStatus(byte[] sessionId) {
        return this.mediaDrm.queryKeyStatus(sessionId);
    }

    public void release() {
        this.mediaDrm.release();
    }

    public void restoreKeys(byte[] sessionId, byte[] keySetId) {
        this.mediaDrm.restoreKeys(sessionId, keySetId);
    }

    public String getPropertyString(String propertyName) {
        return this.mediaDrm.getPropertyString(propertyName);
    }

    public byte[] getPropertyByteArray(String propertyName) {
        return this.mediaDrm.getPropertyByteArray(propertyName);
    }

    public void setPropertyString(String propertyName, String value) {
        this.mediaDrm.setPropertyString(propertyName, value);
    }

    public void setPropertyByteArray(String propertyName, byte[] value) {
        this.mediaDrm.setPropertyByteArray(propertyName, value);
    }

    public FrameworkMediaCrypto createMediaCrypto(byte[] initData) throws MediaCryptoException {
        return new FrameworkMediaCrypto(new android.media.MediaCrypto(adjustUuid(this.uuid), initData), false);
    }

    private static SchemeData getSchemeData(UUID uuid, List<SchemeData> schemeDatas) {
        if (!C.WIDEVINE_UUID.equals(uuid)) {
            return (SchemeData) schemeDatas.get(0);
        } else {
            if (Util.SDK_INT >= 28 && schemeDatas.size() > 1) {
                SchemeData firstSchemeData = (SchemeData) schemeDatas.get(0);
                int concatenatedDataLength = 0;
                boolean canConcatenateData = true;

                for (int i = 0; i < schemeDatas.size(); ++i) {
                    SchemeData schemeData = (SchemeData) schemeDatas.get(i);
                    if (schemeData.requiresSecureDecryption != firstSchemeData.requiresSecureDecryption || !Util.areEqual(schemeData.mimeType, firstSchemeData.mimeType) || !Util.areEqual(schemeData.licenseServerUrl, firstSchemeData.licenseServerUrl) || !PsshAtomUtil.isPsshAtom(schemeData.data)) {
                        canConcatenateData = false;
                        break;
                    }

                    concatenatedDataLength += schemeData.data.length;
                }

                if (canConcatenateData) {
                    byte[] concatenatedData = new byte[concatenatedDataLength];
                    int concatenatedDataPosition = 0;

                    for (int i = 0; i < schemeDatas.size(); ++i) {
                        SchemeData schemeData = (SchemeData) schemeDatas.get(i);
                        int schemeDataLength = schemeData.data.length;
                        System.arraycopy(schemeData.data, 0, concatenatedData, concatenatedDataPosition, schemeDataLength);
                        concatenatedDataPosition += schemeDataLength;
                    }

                    return firstSchemeData.copyWithData(concatenatedData);
                }
            }

            for (int i = 0; i < schemeDatas.size(); ++i) {
                SchemeData schemeData = (SchemeData) schemeDatas.get(i);
                int version = PsshAtomUtil.parseVersion(schemeData.data);
                if (Util.SDK_INT < 23 && version == 0) {
                    return schemeData;
                }

                if (Util.SDK_INT >= 23 && version == 1) {
                    return schemeData;
                }
            }

            return (SchemeData) schemeDatas.get(0);
        }
    }

    private static UUID adjustUuid(UUID uuid) {
        return Util.SDK_INT < 27 && C.CLEARKEY_UUID.equals(uuid) ? C.COMMON_PSSH_UUID : uuid;
    }

    private static byte[] adjustRequestInitData(UUID uuid, byte[] initData) {
        if (Util.SDK_INT < 21 && C.WIDEVINE_UUID.equals(uuid) || C.PLAYREADY_UUID.equals(uuid) && "Amazon".equals(Util.MANUFACTURER) && ("AFTB".equals(Util.MODEL) || "AFTS".equals(Util.MODEL) || "AFTM".equals(Util.MODEL))) {
            byte[] psshData = PsshAtomUtil.parseSchemeSpecificData(initData, uuid);
            if (psshData != null) {
                return psshData;
            }
        }

        return initData;
    }

    private static String adjustRequestMimeType(UUID uuid, String mimeType) {
        return Util.SDK_INT >= 26 || !C.CLEARKEY_UUID.equals(uuid) || !"video/mp4".equals(mimeType) && !"audio/mp4".equals(mimeType) ? mimeType : "cenc";
    }

    private static byte[] adjustRequestData(UUID uuid, byte[] requestData) {
        return C.CLEARKEY_UUID.equals(uuid) ? ClearKeyUtil.adjustRequestData(requestData) : requestData;
    }

    @SuppressLint({"WrongConstant"})
    private static void forceWidevineL3(android.media.MediaDrm mediaDrm) {
        mediaDrm.setPropertyString("securityLevel", "L3");
    }

    private static boolean needsForceWidevineL3Workaround() {
        return "ASUS_Z00AD".equals(Util.MODEL);
    }
}
