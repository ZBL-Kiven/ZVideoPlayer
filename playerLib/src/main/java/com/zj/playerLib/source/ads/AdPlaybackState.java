package com.zj.playerLib.source.ads;

import android.net.Uri;

import androidx.annotation.CheckResult;

import com.zj.playerLib.util.Assertions;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

public final class AdPlaybackState {
    public static final int AD_STATE_UNAVAILABLE = 0;
    public static final int AD_STATE_AVAILABLE = 1;
    public static final int AD_STATE_SKIPPED = 2;
    public static final int AD_STATE_PLAYED = 3;
    public static final int AD_STATE_ERROR = 4;
    public static final AdPlaybackState NONE = new AdPlaybackState();
    public final int adGroupCount;
    public final long[] adGroupTimesUs;
    public final AdGroup[] adGroups;
    public final long adResumePositionUs;
    public final long contentDurationUs;

    public AdPlaybackState(long... adGroupTimesUs) {
        int count = adGroupTimesUs.length;
        this.adGroupCount = count;
        this.adGroupTimesUs = Arrays.copyOf(adGroupTimesUs, count);
        this.adGroups = new AdGroup[count];

        for(int i = 0; i < count; ++i) {
            this.adGroups[i] = new AdGroup();
        }

        this.adResumePositionUs = 0L;
        this.contentDurationUs = -Long.MAX_VALUE;
    }

    private AdPlaybackState(long[] adGroupTimesUs, AdGroup[] adGroups, long adResumePositionUs, long contentDurationUs) {
        this.adGroupCount = adGroups.length;
        this.adGroupTimesUs = adGroupTimesUs;
        this.adGroups = adGroups;
        this.adResumePositionUs = adResumePositionUs;
        this.contentDurationUs = contentDurationUs;
    }

    public int getAdGroupIndexForPositionUs(long positionUs) {
        int index;
        for(index = this.adGroupTimesUs.length - 1; index >= 0 && this.isPositionBeforeAdGroup(positionUs, index); --index) {
        }

        return index >= 0 && this.adGroups[index].hasUnplayedAds() ? index : -1;
    }

    public int getAdGroupIndexAfterPositionUs(long positionUs) {
        int index;
        for(index = 0; index < this.adGroupTimesUs.length && this.adGroupTimesUs[index] != -9223372036854775808L && (positionUs >= this.adGroupTimesUs[index] || !this.adGroups[index].hasUnplayedAds()); ++index) {
        }

        return index < this.adGroupTimesUs.length ? index : -1;
    }

    @CheckResult
    public AdPlaybackState withAdCount(int adGroupIndex, int adCount) {
        Assertions.checkArgument(adCount > 0);
        if (this.adGroups[adGroupIndex].count == adCount) {
            return this;
        } else {
            AdGroup[] adGroups = Arrays.copyOf(this.adGroups, this.adGroups.length);
            adGroups[adGroupIndex] = this.adGroups[adGroupIndex].withAdCount(adCount);
            return new AdPlaybackState(this.adGroupTimesUs, adGroups, this.adResumePositionUs, this.contentDurationUs);
        }
    }

    @CheckResult
    public AdPlaybackState withAdUri(int adGroupIndex, int adIndexInAdGroup, Uri uri) {
        AdGroup[] adGroups = Arrays.copyOf(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdUri(uri, adIndexInAdGroup);
        return new AdPlaybackState(this.adGroupTimesUs, adGroups, this.adResumePositionUs, this.contentDurationUs);
    }

    @CheckResult
    public AdPlaybackState withPlayedAd(int adGroupIndex, int adIndexInAdGroup) {
        AdGroup[] adGroups = Arrays.copyOf(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdState(3, adIndexInAdGroup);
        return new AdPlaybackState(this.adGroupTimesUs, adGroups, this.adResumePositionUs, this.contentDurationUs);
    }

    @CheckResult
    public AdPlaybackState withSkippedAd(int adGroupIndex, int adIndexInAdGroup) {
        AdGroup[] adGroups = Arrays.copyOf(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdState(2, adIndexInAdGroup);
        return new AdPlaybackState(this.adGroupTimesUs, adGroups, this.adResumePositionUs, this.contentDurationUs);
    }

    @CheckResult
    public AdPlaybackState withAdLoadError(int adGroupIndex, int adIndexInAdGroup) {
        AdGroup[] adGroups = Arrays.copyOf(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdState(4, adIndexInAdGroup);
        return new AdPlaybackState(this.adGroupTimesUs, adGroups, this.adResumePositionUs, this.contentDurationUs);
    }

    @CheckResult
    public AdPlaybackState withSkippedAdGroup(int adGroupIndex) {
        AdGroup[] adGroups = Arrays.copyOf(this.adGroups, this.adGroups.length);
        adGroups[adGroupIndex] = adGroups[adGroupIndex].withAllAdsSkipped();
        return new AdPlaybackState(this.adGroupTimesUs, adGroups, this.adResumePositionUs, this.contentDurationUs);
    }

    @CheckResult
    public AdPlaybackState withAdDurationsUs(long[][] adDurationUs) {
        AdGroup[] adGroups = Arrays.copyOf(this.adGroups, this.adGroups.length);

        for(int adGroupIndex = 0; adGroupIndex < this.adGroupCount; ++adGroupIndex) {
            adGroups[adGroupIndex] = adGroups[adGroupIndex].withAdDurationsUs(adDurationUs[adGroupIndex]);
        }

        return new AdPlaybackState(this.adGroupTimesUs, adGroups, this.adResumePositionUs, this.contentDurationUs);
    }

    @CheckResult
    public AdPlaybackState withAdResumePositionUs(long adResumePositionUs) {
        return this.adResumePositionUs == adResumePositionUs ? this : new AdPlaybackState(this.adGroupTimesUs, this.adGroups, adResumePositionUs, this.contentDurationUs);
    }

    @CheckResult
    public AdPlaybackState withContentDurationUs(long contentDurationUs) {
        return this.contentDurationUs == contentDurationUs ? this : new AdPlaybackState(this.adGroupTimesUs, this.adGroups, this.adResumePositionUs, contentDurationUs);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            AdPlaybackState that = (AdPlaybackState)o;
            return this.adGroupCount == that.adGroupCount && this.adResumePositionUs == that.adResumePositionUs && this.contentDurationUs == that.contentDurationUs && Arrays.equals(this.adGroupTimesUs, that.adGroupTimesUs) && Arrays.equals(this.adGroups, that.adGroups);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = this.adGroupCount;
        result = 31 * result + (int)this.adResumePositionUs;
        result = 31 * result + (int)this.contentDurationUs;
        result = 31 * result + Arrays.hashCode(this.adGroupTimesUs);
        result = 31 * result + Arrays.hashCode(this.adGroups);
        return result;
    }

    private boolean isPositionBeforeAdGroup(long positionUs, int adGroupIndex) {
        long adGroupPositionUs = this.adGroupTimesUs[adGroupIndex];
        if (adGroupPositionUs != -9223372036854775808L) {
            return positionUs < adGroupPositionUs;
        } else {
            return this.contentDurationUs == -Long.MAX_VALUE || positionUs < this.contentDurationUs;
        }
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface AdState {
    }

    public static final class AdGroup {
        public final int count;
        public final Uri[] uris;
        public final int[] states;
        public final long[] durationsUs;

        public AdGroup() {
            this(-1, new int[0], new Uri[0], new long[0]);
        }

        private AdGroup(int count, int[] states, Uri[] uris, long[] durationsUs) {
            Assertions.checkArgument(states.length == uris.length);
            this.count = count;
            this.states = states;
            this.uris = uris;
            this.durationsUs = durationsUs;
        }

        public int getFirstAdIndexToPlay() {
            return this.getNextAdIndexToPlay(-1);
        }

        public int getNextAdIndexToPlay(int lastPlayedAdIndex) {
            int nextAdIndexToPlay;
            for(nextAdIndexToPlay = lastPlayedAdIndex + 1; nextAdIndexToPlay < this.states.length && this.states[nextAdIndexToPlay] != 0 && this.states[nextAdIndexToPlay] != 1; ++nextAdIndexToPlay) {
            }

            return nextAdIndexToPlay;
        }

        public boolean hasUnplayedAds() {
            return this.count == -1 || this.getFirstAdIndexToPlay() < this.count;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o != null && this.getClass() == o.getClass()) {
                AdGroup adGroup = (AdGroup)o;
                return this.count == adGroup.count && Arrays.equals(this.uris, adGroup.uris) && Arrays.equals(this.states, adGroup.states) && Arrays.equals(this.durationsUs, adGroup.durationsUs);
            } else {
                return false;
            }
        }

        public int hashCode() {
            int result = this.count;
            result = 31 * result + Arrays.hashCode(this.uris);
            result = 31 * result + Arrays.hashCode(this.states);
            result = 31 * result + Arrays.hashCode(this.durationsUs);
            return result;
        }

        @CheckResult
        public AdPlaybackState.AdGroup withAdCount(int count) {
            Assertions.checkArgument(this.count == -1 && this.states.length <= count);
            int[] states = copyStatesWithSpaceForAdCount(this.states, count);
            long[] durationsUs = copyDurationsUsWithSpaceForAdCount(this.durationsUs, count);
            Uri[] uris = Arrays.copyOf(this.uris, count);
            return new AdGroup(count, states, uris, durationsUs);
        }

        @CheckResult
        public AdPlaybackState.AdGroup withAdUri(Uri uri, int index) {
            Assertions.checkArgument(this.count == -1 || index < this.count);
            int[] states = copyStatesWithSpaceForAdCount(this.states, index + 1);
            Assertions.checkArgument(states[index] == 0);
            long[] durationsUs = this.durationsUs.length == states.length ? this.durationsUs : copyDurationsUsWithSpaceForAdCount(this.durationsUs, states.length);
            Uri[] uris = Arrays.copyOf(this.uris, states.length);
            uris[index] = uri;
            states[index] = 1;
            return new AdGroup(this.count, states, uris, durationsUs);
        }

        @CheckResult
        public AdPlaybackState.AdGroup withAdState(int state, int index) {
            Assertions.checkArgument(this.count == -1 || index < this.count);
            int[] states = copyStatesWithSpaceForAdCount(this.states, index + 1);
            Assertions.checkArgument(states[index] == 0 || states[index] == 1 || states[index] == state);
            long[] durationsUs = this.durationsUs.length == states.length ? this.durationsUs : copyDurationsUsWithSpaceForAdCount(this.durationsUs, states.length);
            Uri[] uris = this.uris.length == states.length ? this.uris : Arrays.copyOf(this.uris, states.length);
            states[index] = state;
            return new AdGroup(this.count, states, uris, durationsUs);
        }

        @CheckResult
        public AdPlaybackState.AdGroup withAdDurationsUs(long[] durationsUs) {
            Assertions.checkArgument(this.count == -1 || durationsUs.length <= this.uris.length);
            if (durationsUs.length < this.uris.length) {
                durationsUs = copyDurationsUsWithSpaceForAdCount(durationsUs, this.uris.length);
            }

            return new AdGroup(this.count, this.states, this.uris, durationsUs);
        }

        @CheckResult
        public AdPlaybackState.AdGroup withAllAdsSkipped() {
            if (this.count == -1) {
                return new AdGroup(0, new int[0], new Uri[0], new long[0]);
            } else {
                int count = this.states.length;
                int[] states = Arrays.copyOf(this.states, count);

                for(int i = 0; i < count; ++i) {
                    if (states[i] == 1 || states[i] == 0) {
                        states[i] = 2;
                    }
                }

                return new AdGroup(count, states, this.uris, this.durationsUs);
            }
        }

        @CheckResult
        private static int[] copyStatesWithSpaceForAdCount(int[] states, int count) {
            int oldStateCount = states.length;
            int newStateCount = Math.max(count, oldStateCount);
            states = Arrays.copyOf(states, newStateCount);
            Arrays.fill(states, oldStateCount, newStateCount, 0);
            return states;
        }

        @CheckResult
        private static long[] copyDurationsUsWithSpaceForAdCount(long[] durationsUs, int count) {
            int oldDurationsUsCount = durationsUs.length;
            int newDurationsUsCount = Math.max(count, oldDurationsUsCount);
            durationsUs = Arrays.copyOf(durationsUs, newDurationsUsCount);
            Arrays.fill(durationsUs, oldDurationsUsCount, newDurationsUsCount, -Long.MAX_VALUE);
            return durationsUs;
        }
    }
}
