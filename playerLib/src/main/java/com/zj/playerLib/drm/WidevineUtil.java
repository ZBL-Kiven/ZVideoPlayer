package com.zj.playerLib.drm;

import android.util.Pair;

import java.util.Map;

public final class WidevineUtil {
    public static final String PROPERTY_LICENSE_DURATION_REMAINING = "LicenseDurationRemaining";
    public static final String PROPERTY_PLAYBACK_DURATION_REMAINING = "PlaybackDurationRemaining";

    private WidevineUtil() {
    }

    public static Pair<Long, Long> getLicenseDurationRemainingSec(DrmSession<?> drmSession) {
        Map<String, String> keyStatus = drmSession.queryKeyStatus();
        return keyStatus == null ? null : new Pair(getDurationRemainingSec(keyStatus, "LicenseDurationRemaining"), getDurationRemainingSec(keyStatus, "PlaybackDurationRemaining"));
    }

    private static long getDurationRemainingSec(Map<String, String> keyStatus, String property) {
        if (keyStatus != null) {
            try {
                String value = keyStatus.get(property);
                if (value != null) {
                    return Long.parseLong(value);
                }
            } catch (NumberFormatException var3) {
            }
        }

        return -Long.MAX_VALUE;
    }
}
