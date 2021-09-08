//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream;

import android.content.Context;
import android.os.Handler;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Clock;
import com.zj.playerLib.util.EventDispatcher;
import com.zj.playerLib.util.SlidingPercentile;
import com.zj.playerLib.util.Util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class DefaultBandwidthMeter implements BandwidthMeter, TransferListener {
    public static final Map<String, int[]> DEFAULT_INITIAL_BITRATE_COUNTRY_GROUPS = createInitialBitrateCountryGroupAssignment();
    public static final long[] DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI = new long[]{5700000L, 3400000L, 1900000L, 1000000L, 400000L};
    public static final long[] DEFAULT_INITIAL_BITRATE_ESTIMATES_2G = new long[]{169000L, 129000L, 114000L, 102000L, 87000L};
    public static final long[] DEFAULT_INITIAL_BITRATE_ESTIMATES_3G = new long[]{2100000L, 1300000L, 950000L, 700000L, 400000L};
    public static final long[] DEFAULT_INITIAL_BITRATE_ESTIMATES_4G = new long[]{6900000L, 4300000L, 2700000L, 1600000L, 450000L};
    public static final long DEFAULT_INITIAL_BITRATE_ESTIMATE = 1000000L;
    public static final int DEFAULT_SLIDING_WINDOW_MAX_WEIGHT = 2000;
    private static final int ELAPSED_MILLIS_FOR_ESTIMATE = 2000;
    private static final int BYTES_TRANSFERRED_FOR_ESTIMATE = 524288;
    private final EventDispatcher<EventListener> eventDispatcher;
    private final SlidingPercentile slidingPercentile;
    private final Clock clock;
    private int streamCount;
    private long sampleStartTimeMs;
    private long sampleBytesTransferred;
    private long totalElapsedTimeMs;
    private long totalBytesTransferred;
    private long bitrateEstimate;

    public DefaultBandwidthMeter() {
        this(1000000L, 2000, Clock.DEFAULT);
    }

    /** @deprecated */
    @Deprecated
    public DefaultBandwidthMeter(Handler eventHandler, EventListener eventListener) {
        this(1000000L, 2000, Clock.DEFAULT);
        if (eventHandler != null && eventListener != null) {
            this.addEventListener(eventHandler, eventListener);
        }

    }

    /** @deprecated */
    @Deprecated
    public DefaultBandwidthMeter(Handler eventHandler, EventListener eventListener, int maxWeight) {
        this(1000000L, maxWeight, Clock.DEFAULT);
        if (eventHandler != null && eventListener != null) {
            this.addEventListener(eventHandler, eventListener);
        }

    }

    private DefaultBandwidthMeter(long initialBitrateEstimate, int maxWeight, Clock clock) {
        this.eventDispatcher = new EventDispatcher();
        this.slidingPercentile = new SlidingPercentile(maxWeight);
        this.clock = clock;
        this.bitrateEstimate = initialBitrateEstimate;
    }

    public synchronized long getBitrateEstimate() {
        return this.bitrateEstimate;
    }

    @Nullable
    public TransferListener getTransferListener() {
        return this;
    }

    public void addEventListener(Handler eventHandler, EventListener eventListener) {
        this.eventDispatcher.addListener(eventHandler, eventListener);
    }

    public void removeEventListener(EventListener eventListener) {
        this.eventDispatcher.removeListener(eventListener);
    }

    public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {
    }

    public synchronized void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork) {
        if (isNetwork) {
            if (this.streamCount == 0) {
                this.sampleStartTimeMs = this.clock.elapsedRealtime();
            }

            ++this.streamCount;
        }
    }

    public synchronized void onBytesTransferred(DataSource source, DataSpec dataSpec, boolean isNetwork, int bytes) {
        if (isNetwork) {
            this.sampleBytesTransferred += (long)bytes;
        }
    }

    public synchronized void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {
        if (isNetwork) {
            Assertions.checkState(this.streamCount > 0);
            long nowMs = this.clock.elapsedRealtime();
            int sampleElapsedTimeMs = (int)(nowMs - this.sampleStartTimeMs);
            this.totalElapsedTimeMs += (long)sampleElapsedTimeMs;
            this.totalBytesTransferred += this.sampleBytesTransferred;
            if (sampleElapsedTimeMs > 0) {
                float bitsPerSecond = (float)(this.sampleBytesTransferred * 8000L / (long)sampleElapsedTimeMs);
                this.slidingPercentile.addSample((int)Math.sqrt((double)this.sampleBytesTransferred), bitsPerSecond);
                if (this.totalElapsedTimeMs >= 2000L || this.totalBytesTransferred >= 524288L) {
                    this.bitrateEstimate = (long)this.slidingPercentile.getPercentile(0.5F);
                }
            }

            this.notifyBandwidthSample(sampleElapsedTimeMs, this.sampleBytesTransferred, this.bitrateEstimate);
            if (--this.streamCount > 0) {
                this.sampleStartTimeMs = nowMs;
            }

            this.sampleBytesTransferred = 0L;
        }
    }

    private void notifyBandwidthSample(int elapsedMs, long bytes, long bitrate) {
        this.eventDispatcher.dispatch((listener) -> {
            listener.onBandwidthSample(elapsedMs, bytes, bitrate);
        });
    }

    private static Map<String, int[]> createInitialBitrateCountryGroupAssignment() {
        HashMap<String, int[]> countryGroupAssignment = new HashMap();
        countryGroupAssignment.put("AD", new int[]{1, 0, 0, 0});
        countryGroupAssignment.put("AE", new int[]{1, 3, 4, 4});
        countryGroupAssignment.put("AF", new int[]{4, 4, 3, 2});
        countryGroupAssignment.put("AG", new int[]{3, 2, 1, 2});
        countryGroupAssignment.put("AI", new int[]{1, 0, 0, 2});
        countryGroupAssignment.put("AL", new int[]{1, 1, 1, 1});
        countryGroupAssignment.put("AM", new int[]{2, 2, 4, 3});
        countryGroupAssignment.put("AO", new int[]{2, 4, 2, 0});
        countryGroupAssignment.put("AR", new int[]{2, 3, 2, 3});
        countryGroupAssignment.put("AS", new int[]{3, 4, 4, 1});
        countryGroupAssignment.put("AT", new int[]{0, 1, 0, 0});
        countryGroupAssignment.put("AU", new int[]{0, 3, 0, 0});
        countryGroupAssignment.put("AW", new int[]{1, 1, 0, 4});
        countryGroupAssignment.put("AX", new int[]{0, 1, 0, 0});
        countryGroupAssignment.put("AZ", new int[]{3, 3, 2, 2});
        countryGroupAssignment.put("BA", new int[]{1, 1, 1, 2});
        countryGroupAssignment.put("BB", new int[]{0, 1, 0, 0});
        countryGroupAssignment.put("BD", new int[]{2, 1, 3, 2});
        countryGroupAssignment.put("BE", new int[]{0, 0, 0, 0});
        countryGroupAssignment.put("BF", new int[]{4, 4, 4, 1});
        countryGroupAssignment.put("BG", new int[]{0, 0, 0, 1});
        countryGroupAssignment.put("BH", new int[]{2, 1, 3, 4});
        countryGroupAssignment.put("BI", new int[]{4, 3, 4, 4});
        countryGroupAssignment.put("BJ", new int[]{4, 3, 4, 3});
        countryGroupAssignment.put("BL", new int[]{1, 0, 1, 2});
        countryGroupAssignment.put("BM", new int[]{1, 0, 0, 0});
        countryGroupAssignment.put("BN", new int[]{4, 3, 3, 3});
        countryGroupAssignment.put("BO", new int[]{2, 2, 1, 2});
        countryGroupAssignment.put("BQ", new int[]{1, 1, 2, 4});
        countryGroupAssignment.put("BR", new int[]{2, 3, 2, 2});
        countryGroupAssignment.put("BS", new int[]{1, 1, 0, 2});
        countryGroupAssignment.put("BT", new int[]{3, 0, 2, 1});
        countryGroupAssignment.put("BW", new int[]{4, 4, 2, 3});
        countryGroupAssignment.put("BY", new int[]{1, 1, 1, 1});
        countryGroupAssignment.put("BZ", new int[]{2, 3, 3, 1});
        countryGroupAssignment.put("CA", new int[]{0, 2, 2, 3});
        countryGroupAssignment.put("CD", new int[]{4, 4, 2, 1});
        countryGroupAssignment.put("CF", new int[]{4, 4, 3, 3});
        countryGroupAssignment.put("CG", new int[]{4, 4, 4, 4});
        countryGroupAssignment.put("CH", new int[]{0, 0, 0, 0});
        countryGroupAssignment.put("CI", new int[]{4, 4, 4, 4});
        countryGroupAssignment.put("CK", new int[]{2, 4, 2, 0});
        countryGroupAssignment.put("CL", new int[]{2, 2, 2, 3});
        countryGroupAssignment.put("CM", new int[]{3, 4, 3, 1});
        countryGroupAssignment.put("CN", new int[]{2, 0, 1, 2});
        countryGroupAssignment.put("CO", new int[]{2, 3, 2, 1});
        countryGroupAssignment.put("CR", new int[]{2, 2, 4, 4});
        countryGroupAssignment.put("CU", new int[]{4, 4, 4, 1});
        countryGroupAssignment.put("CV", new int[]{2, 2, 2, 4});
        countryGroupAssignment.put("CW", new int[]{1, 1, 0, 0});
        countryGroupAssignment.put("CX", new int[]{1, 2, 2, 2});
        countryGroupAssignment.put("CY", new int[]{1, 1, 0, 0});
        countryGroupAssignment.put("CZ", new int[]{0, 1, 0, 0});
        countryGroupAssignment.put("DE", new int[]{0, 2, 2, 2});
        countryGroupAssignment.put("DJ", new int[]{3, 4, 4, 0});
        countryGroupAssignment.put("DK", new int[]{0, 0, 0, 0});
        countryGroupAssignment.put("DM", new int[]{2, 0, 3, 4});
        countryGroupAssignment.put("DO", new int[]{3, 3, 4, 4});
        countryGroupAssignment.put("DZ", new int[]{3, 3, 4, 4});
        countryGroupAssignment.put("EC", new int[]{2, 3, 3, 1});
        countryGroupAssignment.put("EE", new int[]{0, 0, 0, 0});
        countryGroupAssignment.put("EG", new int[]{3, 3, 1, 1});
        countryGroupAssignment.put("EH", new int[]{2, 0, 2, 3});
        countryGroupAssignment.put("ER", new int[]{4, 2, 2, 2});
        countryGroupAssignment.put("ES", new int[]{0, 0, 1, 1});
        countryGroupAssignment.put("ET", new int[]{4, 4, 4, 0});
        countryGroupAssignment.put("FI", new int[]{0, 0, 1, 0});
        countryGroupAssignment.put("FJ", new int[]{3, 2, 3, 3});
        countryGroupAssignment.put("FK", new int[]{3, 4, 2, 1});
        countryGroupAssignment.put("FM", new int[]{4, 2, 4, 0});
        countryGroupAssignment.put("FO", new int[]{0, 0, 0, 1});
        countryGroupAssignment.put("FR", new int[]{1, 0, 2, 1});
        countryGroupAssignment.put("GA", new int[]{3, 3, 2, 1});
        countryGroupAssignment.put("GB", new int[]{0, 1, 3, 2});
        countryGroupAssignment.put("GD", new int[]{2, 0, 3, 0});
        countryGroupAssignment.put("GE", new int[]{1, 1, 0, 3});
        countryGroupAssignment.put("GF", new int[]{1, 2, 4, 4});
        countryGroupAssignment.put("GG", new int[]{0, 1, 0, 0});
        countryGroupAssignment.put("GH", new int[]{3, 2, 2, 2});
        countryGroupAssignment.put("GI", new int[]{0, 0, 0, 1});
        countryGroupAssignment.put("GL", new int[]{2, 4, 1, 4});
        countryGroupAssignment.put("GM", new int[]{4, 3, 3, 0});
        countryGroupAssignment.put("GN", new int[]{4, 4, 3, 4});
        countryGroupAssignment.put("GP", new int[]{2, 2, 1, 3});
        countryGroupAssignment.put("GQ", new int[]{4, 4, 3, 1});
        countryGroupAssignment.put("GR", new int[]{1, 1, 0, 1});
        countryGroupAssignment.put("GT", new int[]{3, 2, 3, 4});
        countryGroupAssignment.put("GU", new int[]{1, 0, 4, 4});
        countryGroupAssignment.put("GW", new int[]{4, 4, 4, 0});
        countryGroupAssignment.put("GY", new int[]{3, 4, 1, 0});
        countryGroupAssignment.put("HK", new int[]{0, 2, 3, 4});
        countryGroupAssignment.put("HN", new int[]{3, 3, 2, 2});
        countryGroupAssignment.put("HR", new int[]{1, 0, 0, 2});
        countryGroupAssignment.put("HT", new int[]{3, 3, 3, 3});
        countryGroupAssignment.put("HU", new int[]{0, 0, 1, 0});
        countryGroupAssignment.put("ID", new int[]{2, 3, 3, 4});
        countryGroupAssignment.put("IE", new int[]{0, 0, 1, 1});
        countryGroupAssignment.put("IL", new int[]{0, 1, 1, 3});
        countryGroupAssignment.put("IM", new int[]{0, 1, 0, 1});
        countryGroupAssignment.put("IN", new int[]{2, 3, 3, 4});
        countryGroupAssignment.put("IO", new int[]{4, 2, 2, 2});
        countryGroupAssignment.put("IQ", new int[]{3, 3, 4, 3});
        countryGroupAssignment.put("IR", new int[]{3, 2, 4, 4});
        countryGroupAssignment.put("IS", new int[]{0, 0, 0, 0});
        countryGroupAssignment.put("IT", new int[]{1, 0, 1, 3});
        countryGroupAssignment.put("JE", new int[]{0, 0, 0, 1});
        countryGroupAssignment.put("JM", new int[]{3, 3, 3, 2});
        countryGroupAssignment.put("JO", new int[]{1, 1, 1, 2});
        countryGroupAssignment.put("JP", new int[]{0, 1, 1, 2});
        countryGroupAssignment.put("KE", new int[]{3, 3, 3, 3});
        countryGroupAssignment.put("KG", new int[]{2, 2, 3, 3});
        countryGroupAssignment.put("KH", new int[]{1, 0, 4, 4});
        countryGroupAssignment.put("KI", new int[]{4, 4, 4, 4});
        countryGroupAssignment.put("KM", new int[]{4, 4, 2, 2});
        countryGroupAssignment.put("KN", new int[]{1, 0, 1, 3});
        countryGroupAssignment.put("KP", new int[]{1, 2, 2, 2});
        countryGroupAssignment.put("KR", new int[]{0, 4, 0, 2});
        countryGroupAssignment.put("KW", new int[]{1, 2, 1, 2});
        countryGroupAssignment.put("KY", new int[]{1, 1, 0, 2});
        countryGroupAssignment.put("KZ", new int[]{1, 2, 2, 3});
        countryGroupAssignment.put("LA", new int[]{3, 2, 2, 2});
        countryGroupAssignment.put("LB", new int[]{3, 2, 0, 0});
        countryGroupAssignment.put("LC", new int[]{2, 2, 1, 0});
        countryGroupAssignment.put("LI", new int[]{0, 0, 1, 2});
        countryGroupAssignment.put("LK", new int[]{1, 1, 2, 2});
        countryGroupAssignment.put("LR", new int[]{3, 4, 3, 1});
        countryGroupAssignment.put("LS", new int[]{3, 3, 2, 0});
        countryGroupAssignment.put("LT", new int[]{0, 0, 0, 1});
        countryGroupAssignment.put("LU", new int[]{0, 0, 1, 0});
        countryGroupAssignment.put("LV", new int[]{0, 0, 0, 0});
        countryGroupAssignment.put("LY", new int[]{4, 4, 4, 4});
        countryGroupAssignment.put("MA", new int[]{2, 1, 2, 2});
        countryGroupAssignment.put("MC", new int[]{1, 0, 1, 0});
        countryGroupAssignment.put("MD", new int[]{1, 1, 0, 0});
        countryGroupAssignment.put("ME", new int[]{1, 2, 2, 3});
        countryGroupAssignment.put("MF", new int[]{1, 4, 3, 3});
        countryGroupAssignment.put("MG", new int[]{3, 4, 1, 2});
        countryGroupAssignment.put("MH", new int[]{4, 0, 2, 3});
        countryGroupAssignment.put("MK", new int[]{1, 0, 0, 1});
        countryGroupAssignment.put("ML", new int[]{4, 4, 4, 4});
        countryGroupAssignment.put("MM", new int[]{2, 3, 1, 2});
        countryGroupAssignment.put("MN", new int[]{2, 2, 2, 4});
        countryGroupAssignment.put("MO", new int[]{0, 1, 4, 4});
        countryGroupAssignment.put("MP", new int[]{0, 0, 4, 4});
        countryGroupAssignment.put("MQ", new int[]{1, 1, 1, 3});
        countryGroupAssignment.put("MR", new int[]{4, 2, 4, 2});
        countryGroupAssignment.put("MS", new int[]{1, 2, 1, 2});
        countryGroupAssignment.put("MT", new int[]{0, 0, 0, 0});
        countryGroupAssignment.put("MU", new int[]{2, 2, 4, 4});
        countryGroupAssignment.put("MV", new int[]{4, 2, 0, 1});
        countryGroupAssignment.put("MW", new int[]{3, 2, 1, 1});
        countryGroupAssignment.put("MX", new int[]{2, 4, 3, 1});
        countryGroupAssignment.put("MY", new int[]{2, 3, 3, 3});
        countryGroupAssignment.put("MZ", new int[]{3, 3, 2, 4});
        countryGroupAssignment.put("NA", new int[]{4, 2, 1, 1});
        countryGroupAssignment.put("NC", new int[]{2, 1, 3, 3});
        countryGroupAssignment.put("NE", new int[]{4, 4, 4, 4});
        countryGroupAssignment.put("NF", new int[]{0, 2, 2, 2});
        countryGroupAssignment.put("NG", new int[]{3, 4, 2, 2});
        countryGroupAssignment.put("NI", new int[]{3, 4, 3, 3});
        countryGroupAssignment.put("NL", new int[]{0, 1, 3, 2});
        countryGroupAssignment.put("NO", new int[]{0, 0, 1, 0});
        countryGroupAssignment.put("NP", new int[]{2, 3, 2, 2});
        countryGroupAssignment.put("NR", new int[]{4, 3, 4, 1});
        countryGroupAssignment.put("NU", new int[]{4, 2, 2, 2});
        countryGroupAssignment.put("NZ", new int[]{0, 0, 0, 1});
        countryGroupAssignment.put("OM", new int[]{2, 2, 1, 3});
        countryGroupAssignment.put("PA", new int[]{1, 3, 2, 3});
        countryGroupAssignment.put("PE", new int[]{2, 2, 4, 4});
        countryGroupAssignment.put("PF", new int[]{2, 2, 0, 1});
        countryGroupAssignment.put("PG", new int[]{4, 4, 4, 4});
        countryGroupAssignment.put("PH", new int[]{3, 0, 4, 4});
        countryGroupAssignment.put("PK", new int[]{3, 3, 3, 3});
        countryGroupAssignment.put("PL", new int[]{1, 0, 1, 3});
        countryGroupAssignment.put("PM", new int[]{0, 2, 2, 3});
        countryGroupAssignment.put("PR", new int[]{2, 3, 4, 3});
        countryGroupAssignment.put("PS", new int[]{2, 3, 0, 4});
        countryGroupAssignment.put("PT", new int[]{1, 1, 1, 1});
        countryGroupAssignment.put("PW", new int[]{3, 2, 3, 0});
        countryGroupAssignment.put("PY", new int[]{2, 1, 3, 3});
        countryGroupAssignment.put("QA", new int[]{2, 3, 1, 2});
        countryGroupAssignment.put("RE", new int[]{1, 1, 2, 2});
        countryGroupAssignment.put("RO", new int[]{0, 1, 1, 3});
        countryGroupAssignment.put("RS", new int[]{1, 1, 0, 0});
        countryGroupAssignment.put("RU", new int[]{0, 1, 1, 1});
        countryGroupAssignment.put("RW", new int[]{3, 4, 3, 1});
        countryGroupAssignment.put("SA", new int[]{3, 2, 2, 3});
        countryGroupAssignment.put("SB", new int[]{4, 4, 3, 0});
        countryGroupAssignment.put("SC", new int[]{4, 2, 0, 1});
        countryGroupAssignment.put("SD", new int[]{3, 4, 4, 4});
        countryGroupAssignment.put("SE", new int[]{0, 0, 0, 0});
        countryGroupAssignment.put("SG", new int[]{1, 2, 3, 3});
        countryGroupAssignment.put("SH", new int[]{4, 2, 2, 2});
        countryGroupAssignment.put("SI", new int[]{0, 1, 0, 0});
        countryGroupAssignment.put("SJ", new int[]{3, 2, 0, 2});
        countryGroupAssignment.put("SK", new int[]{0, 1, 0, 1});
        countryGroupAssignment.put("SL", new int[]{4, 3, 2, 4});
        countryGroupAssignment.put("SM", new int[]{1, 0, 1, 1});
        countryGroupAssignment.put("SN", new int[]{4, 4, 4, 2});
        countryGroupAssignment.put("SO", new int[]{4, 4, 4, 3});
        countryGroupAssignment.put("SR", new int[]{3, 2, 2, 3});
        countryGroupAssignment.put("SS", new int[]{4, 3, 4, 2});
        countryGroupAssignment.put("ST", new int[]{3, 2, 2, 2});
        countryGroupAssignment.put("SV", new int[]{2, 3, 2, 3});
        countryGroupAssignment.put("SX", new int[]{2, 4, 2, 0});
        countryGroupAssignment.put("SY", new int[]{4, 4, 2, 0});
        countryGroupAssignment.put("SZ", new int[]{3, 4, 1, 1});
        countryGroupAssignment.put("TC", new int[]{2, 1, 2, 1});
        countryGroupAssignment.put("TD", new int[]{4, 4, 4, 3});
        countryGroupAssignment.put("TG", new int[]{3, 2, 2, 0});
        countryGroupAssignment.put("TH", new int[]{1, 3, 4, 4});
        countryGroupAssignment.put("TJ", new int[]{4, 4, 4, 4});
        countryGroupAssignment.put("TL", new int[]{4, 2, 4, 4});
        countryGroupAssignment.put("TM", new int[]{4, 1, 3, 3});
        countryGroupAssignment.put("TN", new int[]{2, 2, 1, 2});
        countryGroupAssignment.put("TO", new int[]{2, 3, 3, 1});
        countryGroupAssignment.put("TR", new int[]{1, 2, 0, 2});
        countryGroupAssignment.put("TT", new int[]{2, 1, 1, 0});
        countryGroupAssignment.put("TV", new int[]{4, 2, 2, 4});
        countryGroupAssignment.put("TW", new int[]{0, 0, 0, 1});
        countryGroupAssignment.put("TZ", new int[]{3, 3, 3, 2});
        countryGroupAssignment.put("UA", new int[]{0, 2, 1, 3});
        countryGroupAssignment.put("UG", new int[]{4, 3, 2, 2});
        countryGroupAssignment.put("US", new int[]{0, 1, 3, 3});
        countryGroupAssignment.put("UY", new int[]{2, 1, 2, 2});
        countryGroupAssignment.put("UZ", new int[]{4, 3, 2, 4});
        countryGroupAssignment.put("VA", new int[]{1, 2, 2, 2});
        countryGroupAssignment.put("VC", new int[]{2, 0, 3, 2});
        countryGroupAssignment.put("VE", new int[]{3, 4, 4, 3});
        countryGroupAssignment.put("VG", new int[]{3, 1, 3, 4});
        countryGroupAssignment.put("VI", new int[]{1, 0, 2, 4});
        countryGroupAssignment.put("VN", new int[]{0, 2, 4, 4});
        countryGroupAssignment.put("VU", new int[]{4, 1, 3, 2});
        countryGroupAssignment.put("WS", new int[]{3, 2, 3, 0});
        countryGroupAssignment.put("XK", new int[]{1, 2, 1, 0});
        countryGroupAssignment.put("YE", new int[]{4, 4, 4, 2});
        countryGroupAssignment.put("YT", new int[]{3, 1, 1, 2});
        countryGroupAssignment.put("ZA", new int[]{2, 3, 1, 2});
        countryGroupAssignment.put("ZM", new int[]{3, 3, 3, 1});
        countryGroupAssignment.put("ZW", new int[]{3, 3, 2, 1});
        return Collections.unmodifiableMap(countryGroupAssignment);
    }

    public static final class Builder {
        @Nullable
        private final Context context;
        @Nullable
        private Handler eventHandler;
        @Nullable
        private EventListener eventListener;
        private SparseArray<Long> initialBitrateEstimates;
        private int slidingWindowMaxWeight;
        private Clock clock;

        /** @deprecated */
        @Deprecated
        public Builder() {
            this(null);
        }

        public Builder(@Nullable Context context) {
            this.context = context == null ? null : context.getApplicationContext();
            this.initialBitrateEstimates = getInitialBitrateEstimatesForCountry(Util.getCountryCode(context));
            this.slidingWindowMaxWeight = 2000;
            this.clock = Clock.DEFAULT;
        }

        public Builder setEventListener(Handler eventHandler, EventListener eventListener) {
            Assertions.checkArgument(eventHandler != null && eventListener != null);
            this.eventHandler = eventHandler;
            this.eventListener = eventListener;
            return this;
        }

        public Builder setSlidingWindowMaxWeight(int slidingWindowMaxWeight) {
            this.slidingWindowMaxWeight = slidingWindowMaxWeight;
            return this;
        }

        public Builder setInitialBitrateEstimate(long initialBitrateEstimate) {
            for(int i = 0; i < this.initialBitrateEstimates.size(); ++i) {
                this.initialBitrateEstimates.setValueAt(i, initialBitrateEstimate);
            }

            return this;
        }

        public Builder setInitialBitrateEstimate(int networkType, long initialBitrateEstimate) {
            this.initialBitrateEstimates.put(networkType, initialBitrateEstimate);
            return this;
        }

        public Builder setInitialBitrateEstimate(String countryCode) {
            this.initialBitrateEstimates = getInitialBitrateEstimatesForCountry(Util.toUpperInvariant(countryCode));
            return this;
        }

        public Builder setClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public DefaultBandwidthMeter build() {
            Long initialBitrateEstimate = (Long)this.initialBitrateEstimates.get(Util.getNetworkType(this.context));
            if (initialBitrateEstimate == null) {
                initialBitrateEstimate = (Long)this.initialBitrateEstimates.get(0);
            }

            DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(initialBitrateEstimate, this.slidingWindowMaxWeight, this.clock);
            if (this.eventHandler != null && this.eventListener != null) {
                bandwidthMeter.addEventListener(this.eventHandler, this.eventListener);
            }

            return bandwidthMeter;
        }

        private static SparseArray<Long> getInitialBitrateEstimatesForCountry(String countryCode) {
            int[] groupIndices = getCountryGroupIndices(countryCode);
            SparseArray<Long> result = new SparseArray<>(6);
            result.append(0, 1000000L);
            result.append(2, DefaultBandwidthMeter.DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI[groupIndices[0]]);
            result.append(3, DefaultBandwidthMeter.DEFAULT_INITIAL_BITRATE_ESTIMATES_2G[groupIndices[1]]);
            result.append(4, DefaultBandwidthMeter.DEFAULT_INITIAL_BITRATE_ESTIMATES_3G[groupIndices[2]]);
            result.append(5, DefaultBandwidthMeter.DEFAULT_INITIAL_BITRATE_ESTIMATES_4G[groupIndices[3]]);
            result.append(7, DefaultBandwidthMeter.DEFAULT_INITIAL_BITRATE_ESTIMATES_WIFI[groupIndices[0]]);
            return result;
        }

        private static int[] getCountryGroupIndices(String countryCode) {
            int[] groupIndices = (int[])DefaultBandwidthMeter.DEFAULT_INITIAL_BITRATE_COUNTRY_GROUPS.get(countryCode);
            return groupIndices == null ? new int[]{2, 2, 2, 2} : groupIndices;
        }
    }
}
